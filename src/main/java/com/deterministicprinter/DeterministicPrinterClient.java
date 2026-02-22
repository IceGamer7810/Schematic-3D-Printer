package com.deterministicprinter;

import com.deterministicprinter.build.BuildEngine;
import com.deterministicprinter.build.InventoryPlanner;
import com.deterministicprinter.build.LayerPlanner;
import com.deterministicprinter.build.PathController;
import com.deterministicprinter.build.PlacementController;
import com.deterministicprinter.build.PreviewRenderer;
import com.deterministicprinter.build.ScaffoldManager;
import com.deterministicprinter.build.StateMachine;
import com.deterministicprinter.schematic.SchematicData;
import com.deterministicprinter.schematic.SchematicLoader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class DeterministicPrinterClient implements ClientModInitializer {
    public static final String MOD_ID = "deterministicprinter";

    private final StateMachine stateMachine = new StateMachine();
    private final SchematicLoader schematicLoader = new SchematicLoader();
    private final InventoryPlanner inventoryPlanner = new InventoryPlanner();
    private final LayerPlanner layerPlanner = new LayerPlanner();
    private final PathController pathController = new PathController();
    private final PlacementController placementController = new PlacementController();
    private final ScaffoldManager scaffoldManager = new ScaffoldManager(placementController);
    private final BuildEngine buildEngine =
        new BuildEngine(stateMachine, inventoryPlanner, layerPlanner, pathController, placementController, scaffoldManager);

    @Override
    public void onInitializeClient() {
        PrinterConfig.load();
        registerCommands();
        ClientTickEvents.END_CLIENT_TICK.register(buildEngine::onTick);
        PreviewRenderer.register(stateMachine);
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("sch")
                .then(ClientCommandManager.literal("load")
                    .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                        .executes(this::loadCommand)))
                .then(ClientCommandManager.literal("save")
                    .then(ClientCommandManager.argument("schematic", StringArgumentType.string())
                        .then(ClientCommandManager.argument("gcode", StringArgumentType.string())
                            .executes(this::saveCommand))))
                .then(ClientCommandManager.literal("run")
                    .then(ClientCommandManager.argument("gcode", StringArgumentType.string())
                        .executes(this::runCommand)))
                .then(ClientCommandManager.literal("give")
                    .executes(this::giveCommand)
                    .then(ClientCommandManager.argument("layerCount", IntegerArgumentType.integer(1))
                        .executes(this::giveWithCountCommand)))
                .then(ClientCommandManager.literal("pause")
                    .executes(this::pauseCommand))
                .then(ClientCommandManager.literal("resume")
                    .executes(this::resumeCommand)
                    .then(ClientCommandManager.argument("layerCount", IntegerArgumentType.integer(1))
                        .executes(this::resumeWithCountCommand)))
                .then(ClientCommandManager.literal("fill")
                    .requires(source -> PrinterConfig.get().debugAutoGiveOnRefill)
                    .executes(this::fillCommand))
        ));
    }

    private int loadCommand(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendError(Text.literal("/sch load is deprecated. Use /sch save <schematic> <gcode> then /sch run <gcode>."));
        return 0;
    }

    private int saveCommand(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            context.getSource().sendError(Text.literal("Player/world not ready"));
            return 0;
        }

        String schematicName = StringArgumentType.getString(context, "schematic");
        String gcodeName = StringArgumentType.getString(context, "gcode");
        try {
            SchematicData data = schematicLoader.loadFromSchematicsFolder(schematicName, client.player.getBlockPos());
            java.nio.file.Path savedPath = buildEngine.saveFromSchematic(gcodeName, data, client);
            context.getSource().sendFeedback(Text.literal("Saved g-code: " + savedPath.getFileName()));
            context.getSource().sendFeedback(Text.literal("Bounding box: " + data.min().toShortString() + " -> " + data.max().toShortString()));
            return 1;
        } catch (IOException | IllegalArgumentException ex) {
            context.getSource().sendError(Text.literal("Failed to save g-code: " + ex.getMessage()));
            return 0;
        }
    }

    private int runCommand(CommandContext<FabricClientCommandSource> context) {
        String gcodeName = StringArgumentType.getString(context, "gcode");
        try {
            buildEngine.runProgram(gcodeName);
            buildEngine.printRunSummary(text -> context.getSource().sendFeedback(Text.literal(text)));
            context.getSource().sendFeedback(Text.literal("Use /sch resume to start execution."));
            return 1;
        } catch (IOException | IllegalArgumentException ex) {
            context.getSource().sendError(Text.literal("Failed to run g-code: " + ex.getMessage()));
            return 0;
        }
    }

    private int pauseCommand(CommandContext<FabricClientCommandSource> context) {
        buildEngine.pause();
        context.getSource().sendFeedback(Text.literal("Paused deterministic printer"));
        return 1;
    }

    private int giveCommand(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            context.getSource().sendError(Text.literal("Player/world not ready"));
            return 0;
        }
        boolean ok = buildEngine.giveMissingMaterials(client, text -> context.getSource().sendFeedback(Text.literal(text)));
        return ok ? 1 : 0;
    }

    private int giveWithCountCommand(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            context.getSource().sendError(Text.literal("Player/world not ready"));
            return 0;
        }
        int layerCount = IntegerArgumentType.getInteger(context, "layerCount");
        boolean ok = buildEngine.giveMissingMaterials(client, text -> context.getSource().sendFeedback(Text.literal(text)), layerCount);
        return ok ? 1 : 0;
    }

    private int resumeCommand(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            context.getSource().sendError(Text.literal("Player/world not ready"));
            return 0;
        }

        boolean resumed = buildEngine.resume(client, text -> context.getSource().sendFeedback(Text.literal(text)));
        if (!resumed) {
            return 0;
        }
        context.getSource().sendFeedback(Text.literal("Resuming deterministic printer"));
        return 1;
    }

    private int resumeWithCountCommand(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            context.getSource().sendError(Text.literal("Player/world not ready"));
            return 0;
        }
        int layerCount = IntegerArgumentType.getInteger(context, "layerCount");
        boolean resumed = buildEngine.resume(client, text -> context.getSource().sendFeedback(Text.literal(text)), layerCount);
        if (!resumed) {
            return 0;
        }
        context.getSource().sendFeedback(Text.literal("Resuming deterministic printer"));
        return 1;
    }

    private int fillCommand(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            context.getSource().sendError(Text.literal("Player/world not ready"));
            return 0;
        }
        boolean ok = buildEngine.fillCurrentLayer(client, text -> context.getSource().sendFeedback(Text.literal(text)));
        return ok ? 1 : 0;
    }
}
