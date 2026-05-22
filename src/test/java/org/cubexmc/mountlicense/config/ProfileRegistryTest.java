package org.cubexmc.mountlicense.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.bukkit.entity.EntityType;
import org.cubexmc.mountlicense.MountLicensePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSkipsUnknownEntityTypesWithoutDroppingValidEntries() throws Exception {
        Files.writeString(tempDir.resolve("vehicle-profiles.yml"), """
                profiles:
                  mixed:
                    entityTypes:
                      - HORSE
                      - NOT_A_REAL_ENTITY
                    features:
                      register: true
                      summon: true
                    requiresTamedOwner: true
                  futureOnly:
                    entityTypes:
                      - NOT_A_REAL_ENTITY
                    features:
                      register: true
                    requiresTamedOwner: false
                """);

        MountLicensePlugin plugin = mock(MountLicensePlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ProfileRegistryTest"));

        ProfileRegistry registry = new ProfileRegistry(plugin);
        registry.load();

        assertNotNull(registry.byEntityType(EntityType.HORSE));
        assertTrue(registry.byId("mixed").matches(EntityType.HORSE));
        assertNotNull(registry.byId("futureOnly"));
        assertTrue(registry.byId("futureOnly").entityTypes().isEmpty());
        assertNull(registry.byEntityType(EntityType.PIG));
    }
}
