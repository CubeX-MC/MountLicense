package org.cubexmc.mountlicense

import org.bukkit.command.PluginCommand
import org.cubexmc.config.LegacyTextToMiniMessageStep
import org.cubexmc.config.MigrationException
import org.cubexmc.config.MigrationPlan
import org.cubexmc.config.MigrationRunner
import org.cubexmc.config.NoOpMigrationStep
import org.cubexmc.config.ResourceFiles
import org.cubexmc.core.CubexPlugin
import org.cubexmc.economy.EconomyAccount
import org.cubexmc.economy.VaultEconomy
import org.cubexmc.mountlicense.command.MountLicenseCommand
import org.cubexmc.mountlicense.config.ConfigManager
import org.cubexmc.mountlicense.config.EconomyAccountStep
import org.cubexmc.mountlicense.config.ProfileRegistry
import org.cubexmc.mountlicense.lang.LanguageManager
import org.cubexmc.mountlicense.listener.AutoParkListener
import org.cubexmc.mountlicense.listener.KeyItemListener
import org.cubexmc.mountlicense.listener.LicenseHintListener
import org.cubexmc.mountlicense.listener.ProtectionListener
import org.cubexmc.mountlicense.listener.RegistrationListener
import org.cubexmc.mountlicense.persistence.VehicleIndex
import org.cubexmc.mountlicense.service.ItemFactory
import org.cubexmc.mountlicense.service.OwnershipService
import org.cubexmc.mountlicense.service.ParkingService
import org.cubexmc.mountlicense.service.PdcKeys
import org.cubexmc.mountlicense.service.RecallService
import org.cubexmc.mountlicense.service.RegistryService

class MountLicensePlugin : CubexPlugin() {
    private lateinit var configManagerField: ConfigManager
    private lateinit var languageManagerField: LanguageManager
    private lateinit var profileRegistryField: ProfileRegistry
    private lateinit var pdcKeysField: PdcKeys
    private lateinit var vehicleIndexField: VehicleIndex
    private lateinit var itemFactoryField: ItemFactory
    private lateinit var ownershipServiceField: OwnershipService
    private lateinit var parkingServiceField: ParkingService
    private lateinit var registryServiceField: RegistryService
    private lateinit var recallServiceField: RecallService
    private lateinit var resourceFiles: ResourceFiles

    // Vault 缺席时为 null：注册照常跑，只是不收费（接入共享模块前就是这个行为）。
    private var economyService: VaultEconomy? = null

    override fun enablePlugin() {
        resourceFiles = ResourceFiles(this)
        saveDefaultResources()
        try {
            migrateConfigAndLang()
        } catch (ex: MigrationException) {
            logger.severe("MountLicense enable aborted: migration failed. ${ex.message}")
            abortEnable("MountLicense migration failed. See logs for details.")
        }

        configManagerField = ConfigManager(this)
        configManagerField.load()

        languageManagerField = LanguageManager(this, configManagerField.getLanguage())
        languageManagerField.load()

        hookEconomy()

        profileRegistryField = ProfileRegistry(this)
        profileRegistryField.load()

        pdcKeysField = PdcKeys(this)
        vehicleIndexField = VehicleIndex(this)
        vehicleIndexField.load()
        bind(Runnable {
            if (::vehicleIndexField.isInitialized) {
                vehicleIndexField.flush()
            }
        })

        itemFactoryField = ItemFactory(this, pdcKeysField, languageManagerField)
        ownershipServiceField = OwnershipService(pdcKeysField, vehicleIndexField)
        parkingServiceField = ParkingService(this, pdcKeysField, vehicleIndexField, ownershipServiceField, languageManagerField)
        registryServiceField = RegistryService(this, pdcKeysField, vehicleIndexField, profileRegistryField, languageManagerField)
        recallServiceField = RecallService(this, ownershipServiceField, profileRegistryField, vehicleIndexField, languageManagerField)
        registryServiceField.refreshLoadedDisplayNames()

        server.pluginManager.registerEvents(RegistrationListener(this, registryServiceField, pdcKeysField), this)
        server.pluginManager.registerEvents(LicenseHintListener(this, itemFactoryField, languageManagerField), this)
        server.pluginManager.registerEvents(ProtectionListener(this, ownershipServiceField, languageManagerField), this)
        server.pluginManager.registerEvents(AutoParkListener(this, ownershipServiceField, parkingServiceField), this)
        server.pluginManager.registerEvents(
            KeyItemListener(this, itemFactoryField, ownershipServiceField, recallServiceField, languageManagerField),
            this,
        )

        val root: PluginCommand? = getCommand("mountlicense")
        if (root != null) {
            val executor = MountLicenseCommand(this)
            root.setExecutor(executor)
            root.tabCompleter = executor
        }

        Metrics(this, 31450)

        logger.info("MountLicense ${description.version} enabled.")
    }

    override fun disablePlugin() {
    }

    fun configManager(): ConfigManager = configManagerField

    fun languageManager(): LanguageManager = languageManagerField

    fun profileRegistry(): ProfileRegistry = profileRegistryField

    fun pdcKeys(): PdcKeys = pdcKeysField

    fun vehicleIndex(): VehicleIndex = vehicleIndexField

    fun itemFactory(): ItemFactory = itemFactoryField

    fun ownershipService(): OwnershipService = ownershipServiceField

    fun parkingService(): ParkingService = parkingServiceField

    fun registryService(): RegistryService = registryServiceField

    fun recallService(): RecallService = recallServiceField

    /**
     * Vault 经济封装；Vault 或经济插件缺席时为 null。
     *
     * 返回 null **不是错误路径**：MountLicense 在没有经济插件的服务器上照常工作，
     * 只是不收注册费 —— 和 StateCharge（没经济就 abortEnable）不同，
     * 收费在这里是可选玩法（`economy.enabled` / `register_cost: 0`）。
     */
    fun economy(): VaultEconomy? = economyService

    /**
     * 接上 Vault 并解析一次入账目标。
     *
     * 按名字找账户要查 usercache / 存档，因此只在 enable 与 reload 各做一次，
     * 绝不落进注册路径（解析结果由 [VaultEconomy] 持有）。
     */
    private fun hookEconomy() {
        economyService = VaultEconomy.hook(this, log())
        val economy = economyService
        if (economy == null) {
            logger.info("Vault economy provider not found; MountLicense will not charge for registration.")
            return
        }
        logger.info("Vault economy hooked: ${economy.provider()}")
        applyEconomyAccount()
    }

    /**
     * 把 `economy.account` 解析成入账目标 —— 玩家付的注册费转到哪个账户。
     *
     * 配置写错**不阻止插件启动**：注册照常、扣款照常，只是钱不入账；
     * 代价由 [VaultEconomy] 那边的 SEVERE 与每次扣款一条 WARNING 兜住。
     */
    private fun applyEconomyAccount() {
        val economy = economyService ?: return
        val account = try {
            EconomyAccount.parse(configManagerField.getEconomyAccount())
        } catch (ex: IllegalArgumentException) {
            logger.severe("MountLicense economy.account is invalid; charges will not be banked. ${ex.message}")
            EconomyAccount.None
        }
        economy.useAccount(account)
    }

    fun reloadAll() {
        saveDefaultResources()
        try {
            migrateConfigAndLang()
        } catch (ex: MigrationException) {
            logger.severe("MountLicense reload aborted: migration failed. ${ex.message}")
            return
        }
        configManagerField.load()
        // 经济提供方可能比本插件晚注册（softdepend 只担保 Vault 本体，不担保 EssentialsX 之类的 provider）。
        // 接不上时 reload 重试一次，服主不必为此重启服务器。
        if (economyService == null) hookEconomy() else applyEconomyAccount()
        languageManagerField.setLocale(configManagerField.getLanguage())
        languageManagerField.load()
        profileRegistryField.load()
        if (::registryServiceField.isInitialized) {
            registryServiceField.refreshLoadedDisplayNames()
        }
    }

    private fun saveDefaultResources() {
        resourceFiles.saveIfMissing(listOf("config.yml", "vehicle-profiles.yml", "lang/zh_CN.yml", "lang/en_US.yml"))
    }

    @Throws(MigrationException::class)
    private fun migrateConfigAndLang() {
        val migrations = MigrationRunner(this)
        migrations.run(
            MigrationPlan.yaml("MountLicense config", "config.yml")
                .versionKey("config-version")
                .targetVersion(3)
                .addStep(NoOpMigrationStep(1, 2, "Add MountLicense config-version."))
                .addStep(EconomyAccountStep()),
        )
        migrateLang(migrations, "zh_CN")
        migrateLang(migrations, "en_US")
    }

    @Throws(MigrationException::class)
    private fun migrateLang(migrations: MigrationRunner, locale: String) {
        migrations.run(
            MigrationPlan.yaml("MountLicense lang $locale", "lang/$locale.yml")
                .versionKey("lang-version")
                .targetVersion(2)
                .addStep(LegacyTextToMiniMessageStep(1, 2)),
        )
    }
}
