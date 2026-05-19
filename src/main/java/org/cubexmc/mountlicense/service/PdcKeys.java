package org.cubexmc.mountlicense.service;

import org.bukkit.NamespacedKey;
import org.cubexmc.mountlicense.MountLicensePlugin;

public final class PdcKeys {

    private final NamespacedKey itemRoleKey;
    private final NamespacedKey vehicleIdKey;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey profileKey;
    private final NamespacedKey stateKey;
    private final NamespacedKey createdAtKey;
    private final NamespacedKey schemaVersionKey;
    private final NamespacedKey keyBoundVehicleKey;

    public PdcKeys(MountLicensePlugin plugin) {
        this.itemRoleKey = new NamespacedKey(plugin, "item_role");
        this.vehicleIdKey = new NamespacedKey(plugin, "vehicle_id");
        this.ownerUuidKey = new NamespacedKey(plugin, "owner_uuid");
        this.profileKey = new NamespacedKey(plugin, "profile");
        this.stateKey = new NamespacedKey(plugin, "state");
        this.createdAtKey = new NamespacedKey(plugin, "created_at");
        this.schemaVersionKey = new NamespacedKey(plugin, "schema_version");
        this.keyBoundVehicleKey = new NamespacedKey(plugin, "key_bound_vehicle");
    }

    public NamespacedKey itemRole() { return itemRoleKey; }
    public NamespacedKey vehicleId() { return vehicleIdKey; }
    public NamespacedKey ownerUuid() { return ownerUuidKey; }
    public NamespacedKey profile() { return profileKey; }
    public NamespacedKey state() { return stateKey; }
    public NamespacedKey createdAt() { return createdAtKey; }
    public NamespacedKey schemaVersion() { return schemaVersionKey; }
    public NamespacedKey keyBoundVehicle() { return keyBoundVehicleKey; }

    public static final String ITEM_ROLE_LICENSE = "license";
    public static final String ITEM_ROLE_KEY = "key";
    public static final String ITEM_ROLE_STATION_PERMIT = "station_permit";
    public static final int SCHEMA_VERSION = 1;
}
