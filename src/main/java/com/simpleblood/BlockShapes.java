package com.simpleblood;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockShapes {
    private BlockShapes() {}

    public static VoxelShape outline(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isAir() ? Shapes.empty() : state.getShape(level, pos);
    }

    public static VoxelShape collision(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isAir() ? Shapes.empty() : state.getCollisionShape(level, pos);
    }
}
