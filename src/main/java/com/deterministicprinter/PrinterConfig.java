package com.deterministicprinter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class PrinterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("deterministicprinter.json");
    private static Data data = new Data();

    private PrinterConfig() {
    }

    public static Data get() {
        return data;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            boolean migrated = false;
            if (!root.has("dp_debug") && root.has("debugAutoGiveOnRefill")) {
                root.add("dp_debug", root.get("debugAutoGiveOnRefill"));
                migrated = true;
            }
            Data loaded = GSON.fromJson(root, Data.class);
            if (loaded != null) {
                if (loaded.scaffoldWith == null) {
                    loaded.scaffoldWith = loaded.useScaffold ? Data.ScaffoldWith.SCAFFOLDING_BETA : Data.ScaffoldWith.LEAVES;
                }
                if (loaded.workWith == null) {
                    loaded.workWith = Data.WorkWith.INVENTORY;
                }
                if (loaded.pathfinding == null) {
                    loaded.pathfinding = Data.Pathfinding.FULLY_DETERMINISTIC;
                }
                data = loaded;
                if (migrated) {
                    save();
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static final class Data {
        public int maxForbiddenSlots = 3;
        public ScaffoldWith scaffoldWith = ScaffoldWith.LEAVES;
        public WorkWith workWith = WorkWith.INVENTORY;
        public Pathfinding pathfinding = Pathfinding.FULLY_DETERMINISTIC;
        public boolean supportPillars = true;
        public float previewR = 1.0f;
        public float previewG = 0.0f;
        public float previewB = 0.0f;
        public float previewA = 0.5f;
        @SerializedName("dp_debug")
        public boolean debugAutoGiveOnRefill = false;
        @Deprecated
        public boolean useScaffold = false;

        public enum ScaffoldWith {
            LEAVES,
            ALL,
            SCAFFOLDING_BETA
        }

        public enum WorkWith {
            INVENTORY,
            HOTBAR_ONLY
        }

        public enum Pathfinding {
            FULLY_DETERMINISTIC,
            BARITONE
        }
    }
}
