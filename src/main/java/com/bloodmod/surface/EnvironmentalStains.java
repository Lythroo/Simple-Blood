package com.bloodmod.surface;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodKind;
import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodModConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EnvironmentalStains {

    private EnvironmentalStains() {}

    public static void onDamage(ClientLevel level, LivingEntity entity, DamageSource source, float damage) {
        BloodModConfig cfg = BloodModClient.getConfig();
        if (cfg == null || !cfg.surfaces.enabled || source == null) return;
        if (BloodMod.bloodKindOf(entity) != BloodKind.LIQUID) return;

        BloodColor.Color c = BloodColor.getBloodColor(entity);
        int colour = ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        float amount = Math.min(1.0f, damage / 12.0f);

        if (source.is(DamageTypes.STALAGMITE)) {
            stalagmites(level, entity, colour, amount);
        } else if (source.is(DamageTypes.CACTUS)) {
            cactus(level, entity, colour, amount);
        } else if (source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            berryBush(level, entity, colour, amount);
        }
    }

    private static void stalagmites(ClientLevel level, LivingEntity entity, int colour, float amount) {
        AABB box = entity.getBoundingBox();
        int y0 = (int) Math.floor(box.minY - 0.05);
        int found = 0;
        for (int y = y0; y >= y0 - 1 && found < 4; y--) {
            for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX) && found < 4; x++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ) && found < 4; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.POINTED_DRIPSTONE)) continue;
                    BloodSurfaces.coatDripstone(level, pos, state, colour, amount);
                    found++;
                }
            }
        }
    }

    private static void cactus(ClientLevel level, LivingEntity entity, int colour, float amount) {
        AABB box = entity.getBoundingBox().inflate(0.15, 0, 0.15);
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(Blocks.CACTUS)) continue;
                    Vec3 centre = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
                    double dx = centre.x - (x + 0.5), dz = centre.z - (z + 0.5);
                    Direction face = Math.abs(dx) > Math.abs(dz)
                            ? (dx > 0 ? Direction.EAST : Direction.WEST)
                            : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                    double fx = face.getAxis() == Direction.Axis.X ? (face == Direction.EAST ? x + 15 / 16.0 : x + 1 / 16.0) : centre.x;
                    double fz = face.getAxis() == Direction.Axis.Z ? (face == Direction.SOUTH ? z + 15 / 16.0 : z + 1 / 16.0) : centre.z;
                    double fy = Math.min(y + 0.9, Math.max(y + 0.1, centre.y - 0.2));
                    BloodSurfaces.Hit hit = new BloodSurfaces.Hit(pos, face, new Vec3(fx, fy, fz));
                    int pixels = Math.round((4 + amount * 10) * scale());
                    BloodSurfaces.deposit(level, hit, colour, Math.max(2, pixels), new Vec3(0, -1, 0));
                    return;
                }
            }
        }
    }

    private static void berryBush(ClientLevel level, LivingEntity entity, int colour, float amount) {
        Vec3 feet = entity.position();
        BloodSurfaces.Hit hit = BloodSurfaces.trace(level, feet.add(0, 0.2, 0), feet.add(0, -1.0, 0));
        if (hit == null) return;
        int pixels = Math.round((3 + amount * 6) * scale());
        BloodSurfaces.deposit(level, hit, colour, Math.max(2, pixels));
    }

    private static float scale() {
        BloodModConfig cfg = BloodModClient.getConfig();
        return (cfg.surfaces.resolution * cfg.surfaces.resolution / 256.0f) * cfg.surfaceAmount();
    }
}
