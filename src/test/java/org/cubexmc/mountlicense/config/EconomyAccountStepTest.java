package org.cubexmc.mountlicense.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.cubexmc.config.MigrationException;
import org.cubexmc.config.MigrationPlan;
import org.cubexmc.config.MigrationReport;
import org.cubexmc.config.MigrationRunner;
import org.cubexmc.config.NoOpMigrationStep;
import org.cubexmc.mountlicense.MountLicensePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EconomyAccountStepTest {

    @TempDir
    Path tempDir;

    @Test
    void addsEmptyEconomyAccountToVersionTwoConfigAndKeepsExistingKeys() throws Exception {
        // Arrange
        Files.writeString(tempDir.resolve("config.yml"), """
                config-version: 2
                economy:
                  enabled: true
                  register_cost: 25.0
                """);

        // Act
        MigrationReport report = run();

        // Assert
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tempDir.resolve("config.yml").toFile());
        assertTrue(report.migrated());
        assertEquals(3, config.getInt("config-version"));
        // 空串 = 保持旧行为（销毁），不替服主猜账户。
        assertEquals("", config.getString("economy.account"));
        assertTrue(config.getBoolean("economy.enabled"));
        assertEquals(25.0, config.getDouble("economy.register_cost"));
    }

    @Test
    void keepsAnAccountTheOwnerAlreadyConfigured() throws Exception {
        // Arrange
        Files.writeString(tempDir.resolve("config.yml"), """
                config-version: 2
                economy:
                  account: "name:cubex_bank"
                  enabled: true
                """);

        // Act
        run();

        // Assert
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tempDir.resolve("config.yml").toFile());
        assertEquals("name:cubex_bank", config.getString("economy.account"));
    }

    @Test
    void secondRunIsSkippedSoTheStepIsIdempotent() throws Exception {
        // Arrange
        Files.writeString(tempDir.resolve("config.yml"), """
                config-version: 2
                economy:
                  enabled: true
                """);

        // Act
        MigrationReport first = run();
        MigrationReport second = run();

        // Assert
        assertTrue(first.migrated());
        assertFalse(second.migrated());
        assertTrue(second.skipped());
    }

    private MigrationReport run() throws MigrationException {
        MountLicensePlugin plugin = mock(MountLicensePlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("EconomyAccountStepTest"));
        return new MigrationRunner(plugin).run(MigrationPlan.yaml("MountLicense config", "config.yml")
                .versionKey("config-version")
                .targetVersion(3)
                .addStep(new NoOpMigrationStep(1, 2, "Add MountLicense config-version."))
                .addStep(new EconomyAccountStep()));
    }
}
