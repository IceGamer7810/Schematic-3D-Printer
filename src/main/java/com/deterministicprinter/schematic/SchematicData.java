package com.deterministicprinter.schematic;

import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public record SchematicData(
    Map<BlockPos, BlockState> blocks,
    Map<Integer, List<TargetBlock>> layers,
    BlockPos min,
    BlockPos max
) {
    public boolean inBoundingBox(BlockPos pos) {
        return pos.getX() >= min.getX()
            && pos.getY() >= min.getY()
            && pos.getZ() >= min.getZ()
            && pos.getX() <= max.getX()
            && pos.getY() <= max.getY()
            && pos.getZ() <= max.getZ();
    }

    public record TargetBlock(BlockPos pos, BlockState state) {
    }
}
