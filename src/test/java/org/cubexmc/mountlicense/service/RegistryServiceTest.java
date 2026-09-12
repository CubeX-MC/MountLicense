package org.cubexmc.mountlicense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.withSettings;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.cubexmc.economy.EconomyResult;
import org.cubexmc.economy.VaultEconomy;
import org.cubexmc.mountlicense.MountLicensePlugin;
import org.cubexmc.mountlicense.config.ConfigManager;
import org.cubexmc.mountlicense.config.ProfileRegistry;
import org.cubexmc.mountlicense.lang.LanguageManager;
import org.cubexmc.mountlicense.model.VehicleFeature;
import org.cubexmc.mountlicense.model.VehicleProfile;
import org.cubexmc.mountlicense.model.VehicleRecord;
import org.cubexmc.mountlicense.persistence.VehicleIndex;
import org.junit.jupiter.api.Test;

class RegistryServiceTest {

    @Test
    void profileRequiringSaddleRejectsUnsaddledSteerableEntity() {
        MountLicensePlugin plugin = mock(MountLicensePlugin.class);
        PdcKeys keys = mock(PdcKeys.class);
        VehicleIndex index = mock(VehicleIndex.class);
        ProfileRegistry profiles = mock(ProfileRegistry.class);
        LanguageManager lang = mock(LanguageManager.class);
        ConfigManager config = mock(ConfigManager.class);
        ItemFactory itemFactory = mock(ItemFactory.class);

        Player player = mock(Player.class);
        ItemStack license = mock(ItemStack.class);
        Entity pig = mock(Entity.class, withSettings().extraInterfaces(Steerable.class));
        Steerable steerable = (Steerable) pig;

        VehicleProfile pigProfile = new VehicleProfile(
                "pig",
                EnumSet.of(EntityType.PIG),
                EnumSet.of(VehicleFeature.REGISTER),
                false,
                true
        );

        when(plugin.configManager()).thenReturn(config);
        when(plugin.itemFactory()).thenReturn(itemFactory);
        when(config.getRegisterCooldownSeconds()).thenReturn(0);
        when(config.isRejectAlreadyRegistered()).thenReturn(false);
        when(config.getMaxVehiclesPerPlayer()).thenReturn(-1);
        when(itemFactory.isLicense(license)).thenReturn(true);
        when(player.hasPermission("mountlicense.register")).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(pig.getType()).thenReturn(EntityType.PIG);
        when(steerable.hasSaddle()).thenReturn(false);
        when(profiles.byEntityType(EntityType.PIG)).thenReturn(pigProfile);

        RegistryService service = new RegistryService(plugin, keys, index, profiles, lang);

        assertEquals(RegistryService.Result.REQUIRES_SADDLE,
                service.tryRegister(player, pig, license));
        verify(lang).send(player, "registration.fail_requires_saddle", null);
    }

    @Test
    void rejectsRegistrationWhenThePlayerCannotAffordTheFee() {
        // Arrange
        Fixture fixture = new Fixture();
        VaultEconomy economy = fixture.withEconomy(10.0);
        when(economy.has(fixture.player, BigDecimal.valueOf(10.0))).thenReturn(false);

        // Act
        RegistryService.Result result = fixture.service().tryRegister(fixture.player, fixture.cow, fixture.license);

        // Assert
        assertEquals(RegistryService.Result.NOT_ENOUGH_MONEY, result);
        verify(economy, never()).charge(fixture.player, BigDecimal.valueOf(10.0));
    }

    @Test
    void chargesThroughTheSharedEconomySoTheFeeIsRoutedToTheConfiguredAccount() {
        // Arrange
        Fixture fixture = new Fixture();
        VaultEconomy economy = fixture.withEconomy(10.0);
        when(economy.has(fixture.player, BigDecimal.valueOf(10.0))).thenReturn(true);
        when(economy.charge(fixture.player, BigDecimal.valueOf(10.0))).thenReturn(EconomyResult.ok());
        when(economy.deposit(fixture.player, BigDecimal.valueOf(10.0))).thenReturn(EconomyResult.ok());
        fixture.failWriteAfterCharging();

        // Act
        RegistryService.Result result = fixture.service().tryRegister(fixture.player, fixture.cow, fixture.license);

        // Assert: charge() (withdraw + route), never a bare withdraw; the failed write refunds the player.
        assertEquals(RegistryService.Result.FAILED, result);
        verify(economy).charge(fixture.player, BigDecimal.valueOf(10.0));
        verify(economy, never()).withdraw(fixture.player, BigDecimal.valueOf(10.0));
        verify(economy).deposit(fixture.player, BigDecimal.valueOf(10.0));
    }

    @Test
    void skipsChargingWhenTheOwnerTurnedTheEconomyOff() {
        // Arrange
        Fixture fixture = new Fixture();
        VaultEconomy economy = fixture.withEconomy(10.0);
        when(fixture.config.isEconomyEnabled()).thenReturn(false);
        fixture.failWriteAfterCharging();

        // Act
        fixture.service().tryRegister(fixture.player, fixture.cow, fixture.license);

        // Assert: economy.enabled is a reloadable switch, so it is read per registration.
        verify(economy, never()).has(fixture.player, BigDecimal.valueOf(10.0));
        verify(economy, never()).charge(fixture.player, BigDecimal.valueOf(10.0));
    }

    /** Everything a registration needs to reach the charging step. */
    private static final class Fixture {
        final MountLicensePlugin plugin = mock(MountLicensePlugin.class);
        final PdcKeys keys = mock(PdcKeys.class);
        final VehicleIndex index = mock(VehicleIndex.class);
        final ProfileRegistry profiles = mock(ProfileRegistry.class);
        final LanguageManager lang = mock(LanguageManager.class);
        final ConfigManager config = mock(ConfigManager.class);
        final ItemFactory itemFactory = mock(ItemFactory.class);
        final Player player = mock(Player.class);
        final ItemStack license = mock(ItemStack.class);
        final Entity cow = mock(Entity.class);

        Fixture() {
            VehicleProfile cowProfile = new VehicleProfile(
                    "cow",
                    EnumSet.of(EntityType.COW),
                    EnumSet.of(VehicleFeature.REGISTER),
                    false,
                    false
            );
            when(plugin.configManager()).thenReturn(config);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("RegistryServiceTest"));
            when(plugin.itemFactory()).thenReturn(itemFactory);
            when(config.getRegisterCooldownSeconds()).thenReturn(0);
            when(config.isRejectAlreadyRegistered()).thenReturn(false);
            when(config.getMaxVehiclesPerPlayer()).thenReturn(-1);
            when(config.isRequireEmptyVehicle()).thenReturn(false);
            when(config.isEconomyEnabled()).thenReturn(true);
            when(itemFactory.isLicense(license)).thenReturn(true);
            when(player.hasPermission("mountlicense.register")).thenReturn(true);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(cow.getType()).thenReturn(EntityType.COW);
            when(profiles.byEntityType(EntityType.COW)).thenReturn(cowProfile);
        }

        /** Let the registration reach its write step, then make that step fail (the refund path). */
        void failWriteAfterCharging() {
            when(cow.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(cow.getLocation()).thenReturn(mock(Location.class));
            when(config.getDisplayNameFormat()).thenReturn("");
            doThrow(new IllegalStateException("index write failed")).when(index).put(any(VehicleRecord.class));
        }

        VaultEconomy withEconomy(double registerCost) {
            VaultEconomy economy = mock(VaultEconomy.class);
            when(config.getRegisterCost()).thenReturn(registerCost);
            when(plugin.economy()).thenReturn(economy);
            return economy;
        }

        RegistryService service() {
            return new RegistryService(plugin, keys, index, profiles, lang);
        }
    }
}
