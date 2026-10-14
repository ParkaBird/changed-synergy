package com.atsuishio.superbwarfare.entity.vehicle.base;

/** Private API-hierarchy probe. Never packaged; the real optional mod is absent from this fixture. */
public class VehicleEntity extends net.minecraft.world.entity.vehicle.Boat {
    public VehicleEntity(net.minecraft.world.level.Level level) {
        super(net.minecraft.world.entity.EntityType.BOAT, level);
    }
    @Override public double getEyeY() { return getY() + 2.0D; }
}
