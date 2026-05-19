package org.cubexmc.mountlicense.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.bukkit.entity.EntityType;

public final class VehicleProfile {

    private final String id;
    private final Set<EntityType> entityTypes;
    private final Set<VehicleFeature> features;
    private final boolean requiresTamedOwner;

    public VehicleProfile(String id, Set<EntityType> entityTypes,
                          Set<VehicleFeature> features, boolean requiresTamedOwner) {
        this.id = id;
        this.entityTypes = Collections.unmodifiableSet(EnumSet.copyOf(entityTypes));
        this.features = features.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(VehicleFeature.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(features));
        this.requiresTamedOwner = requiresTamedOwner;
    }

    public String id() { return id; }
    public Set<EntityType> entityTypes() { return entityTypes; }
    public Set<VehicleFeature> features() { return features; }
    public boolean requiresTamedOwner() { return requiresTamedOwner; }

    public boolean matches(EntityType type) {
        return type != null && entityTypes.contains(type);
    }

    public boolean has(VehicleFeature f) {
        return features.contains(f);
    }
}
