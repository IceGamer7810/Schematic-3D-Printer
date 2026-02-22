package com.deterministicprinter.schematic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class SchematicLoader {
    public SchematicData loadFromSchematicsFolder(String filename, BlockPos alignToPlayerPos) throws IOException {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new IllegalArgumentException("APPDATA is not set");
        }
        Path file = Path.of(appData, ".minecraft", "schematics", filename);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }
        if (!filename.endsWith(".litematic")) {
            throw new IllegalArgumentException("Only .litematic files are supported");
        }
        try (InputStream inputStream = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(inputStream, NbtSizeTracker.ofUnlimitedBytes());
            return parseLitematic(root, alignToPlayerPos);
        }
    }

    private SchematicData parseLitematic(NbtCompound root, BlockPos alignToPlayerPos) {
        NbtCompound regions = root.getCompound("Regions").orElseThrow(() -> new IllegalArgumentException("No Regions found in litematic"));
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("No Regions found in litematic");
        }

        Map<BlockPos, BlockState> schematicSpaceBlocks = new HashMap<>();
        BlockPos schematicMin = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        BlockPos schematicMax = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName).orElse(null);
            if (region == null) {
                continue;
            }
            int[] size = readVec3(region, "Size");
            int[] position = readVec3(region, "Position");
            if (size == null || position == null) {
                continue;
            }

            int sx = Math.abs(size[0]);
            int sy = Math.abs(size[1]);
            int sz = Math.abs(size[2]);
            int ox = position[0];
            int oy = position[1];
            int oz = position[2];

            List<BlockState> palette = decodePalette(region.getList("BlockStatePalette").orElse(new NbtList()));
            long[] packed = region.getLongArray("BlockStates").orElse(new long[0]);
            int volume = sx * sy * sz;
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size()) - 1));
            long mask = (1L << bits) - 1L;

            for (int index = 0; index < volume; index++) {
                int paletteIndex = unpackIndex(packed, index, bits, mask);
                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    continue;
                }
                BlockState state = palette.get(paletteIndex);
                if (state.isAir()) {
                    continue;
                }
                if (state.getBlock().asItem() == Items.AIR) {
                    if (state.isOf(Blocks.POWDER_SNOW)) {
                        // Powder snow is placed from bucket item.
                    } else if ((state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) && state.getFluidState().isStill()) {
                        // Source fluids are build targets and consume buckets.
                    } else {
                        // Ignore flowing fluid blocks and other non-placeable runtime states.
                        continue;
                    }
                }
                int x = index % sx;
                int z = (index / sx) % sz;
                int y = index / (sx * sz);

                BlockPos schematicPos = new BlockPos(ox + x, oy + y, oz + z);
                schematicSpaceBlocks.put(schematicPos, state);
                schematicMin = new BlockPos(
                    Math.min(schematicMin.getX(), schematicPos.getX()),
                    Math.min(schematicMin.getY(), schematicPos.getY()),
                    Math.min(schematicMin.getZ(), schematicPos.getZ()));
                schematicMax = new BlockPos(
                    Math.max(schematicMax.getX(), schematicPos.getX()),
                    Math.max(schematicMax.getY(), schematicPos.getY()),
                    Math.max(schematicMax.getZ(), schematicPos.getZ()));
            }
        }

        if (schematicSpaceBlocks.isEmpty()) {
            throw new IllegalArgumentException(
                "No non-air blocks parsed from litematic. Unsupported region format or empty schematic.");
        }

        // Anchor the schematic minimum corner to the player position so layer 0 starts at player Y.
        BlockPos translation = alignToPlayerPos.subtract(schematicMin);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos min = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        BlockPos max = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        for (Map.Entry<BlockPos, BlockState> entry : schematicSpaceBlocks.entrySet()) {
            BlockPos worldPos = entry.getKey().add(translation);
            blocks.put(worldPos, entry.getValue());
            min = new BlockPos(Math.min(min.getX(), worldPos.getX()), Math.min(min.getY(), worldPos.getY()), Math.min(min.getZ(), worldPos.getZ()));
            max = new BlockPos(Math.max(max.getX(), worldPos.getX()), Math.max(max.getY(), worldPos.getY()), Math.max(max.getZ(), worldPos.getZ()));
        }

        Map<Integer, List<SchematicData.TargetBlock>> layers = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            layers.computeIfAbsent(entry.getKey().getY(), y -> new ArrayList<>())
                .add(new SchematicData.TargetBlock(entry.getKey().toImmutable(), entry.getValue()));
        }

        return new SchematicData(Map.copyOf(blocks), Map.copyOf(layers), min, max);
    }

    private int[] readVec3(NbtCompound region, String key) {
        int[] arr = region.getIntArray(key).orElse(null);
        if (arr != null && arr.length >= 3) {
            return new int[]{arr[0], arr[1], arr[2]};
        }
        NbtCompound comp = region.getCompound(key).orElse(null);
        if (comp == null) {
            return null;
        }
        Integer x = readIntAny(comp, "x", "X");
        Integer y = readIntAny(comp, "y", "Y");
        Integer z = readIntAny(comp, "z", "Z");
        if (x == null || y == null || z == null) {
            return null;
        }
        return new int[]{x, y, z};
    }

    private Integer readIntAny(NbtCompound compound, String... keys) {
        for (String k : keys) {
            Integer value = compound.getInt(k).orElse(null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int unpackIndex(long[] packed, int index, int bits, long mask) {
        if (packed.length == 0) {
            return 0;
        }
        int bitIndex = index * bits;
        int firstLong = bitIndex >>> 6;
        int startBit = bitIndex & 63;
        if (firstLong >= packed.length) {
            return 0;
        }
        long value = packed[firstLong] >>> startBit;
        int available = 64 - startBit;
        if (available < bits && firstLong + 1 < packed.length) {
            value |= packed[firstLong + 1] << available;
        }
        return (int) (value & mask);
    }

    private List<BlockState> decodePalette(NbtList paletteList) {
        List<BlockState> palette = new ArrayList<>();
        for (NbtElement element : paletteList) {
            if (!(element instanceof NbtCompound compound)) {
                continue;
            }
            String name = compound.getString("Name").orElse("");
            if (name == null || name.isBlank()) {
                continue;
            }
            Block block = Registries.BLOCK.get(Identifier.of(name));
            BlockState state = block.getDefaultState();

            NbtCompound properties = compound.getCompound("Properties").orElse(null);
            if (properties != null) {
                for (String key : properties.getKeys()) {
                    String value = properties.getString(key).orElse("");
                    Property<?> property = block.getStateManager().getProperty(key);
                    if (property == null) {
                        continue;
                    }
                    state = applyProperty(state, property, value);
                }
            }
            palette.add(state);
        }
        if (palette.isEmpty()) {
            palette.add(net.minecraft.block.Blocks.AIR.getDefaultState());
        }
        return palette;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private BlockState applyProperty(BlockState state, Property property, String rawValue) {
        return (BlockState) property.parse(rawValue).map(value -> withUnchecked(state, property, (Comparable) value)).orElse(state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private BlockState withUnchecked(BlockState state, Property property, Comparable value) {
        return (BlockState) state.with(property, value);
    }
}
