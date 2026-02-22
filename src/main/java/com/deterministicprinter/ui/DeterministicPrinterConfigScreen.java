package com.deterministicprinter.ui;

import com.deterministicprinter.PrinterConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class DeterministicPrinterConfigScreen extends Screen {
    private final Screen parent;
    private PrinterConfig.Data.ScaffoldWith scaffoldWith;
    private PrinterConfig.Data.WorkWith workWith;
    private PrinterConfig.Data.Pathfinding pathfinding;
    private boolean supportPillars;
    private boolean debugAutoGiveOnRefill;
    private int maxForbiddenSlots;
    private TextFieldWidget forbiddenSlotsField;

    public DeterministicPrinterConfigScreen(Screen parent) {
        super(Text.literal("DeterministicPrinter Settings"));
        this.parent = parent;
        this.scaffoldWith = PrinterConfig.get().scaffoldWith;
        this.workWith = PrinterConfig.get().workWith;
        this.pathfinding = PrinterConfig.get().pathfinding;
        this.supportPillars = PrinterConfig.get().supportPillars;
        this.debugAutoGiveOnRefill = PrinterConfig.get().debugAutoGiveOnRefill;
        this.maxForbiddenSlots = PrinterConfig.get().maxForbiddenSlots;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 4;

        this.forbiddenSlotsField = new TextFieldWidget(
            this.textRenderer,
            centerX - 100,
            y + 20,
            200,
            20,
            Text.literal("Max Forbidden Slots"));
        this.forbiddenSlotsField.setText(Integer.toString(this.maxForbiddenSlots));
        this.forbiddenSlotsField.setChangedListener(value -> this.maxForbiddenSlots = parseSlots(value));
        this.addDrawableChild(this.forbiddenSlotsField);

        this.addDrawableChild(CyclingButtonWidget.builder(this::modeLabel, this.scaffoldWith)
            .values(PrinterConfig.Data.ScaffoldWith.values())
            .build(centerX - 100, y + 50, 200, 20, Text.literal("Scaffold With"),
                (button, value) -> this.scaffoldWith = value));

        this.addDrawableChild(CyclingButtonWidget.builder(this::workWithLabel, this.workWith)
            .values(PrinterConfig.Data.WorkWith.values())
            .build(centerX - 100, y + 75, 200, 20, Text.literal("Work With"),
                (button, value) -> this.workWith = value));

        this.addDrawableChild(CyclingButtonWidget.builder(this::pathfindingLabel, this.pathfinding)
            .values(PrinterConfig.Data.Pathfinding.values())
            .build(centerX - 100, y + 100, 200, 20, Text.literal("Pathfinding"),
                (button, value) -> this.pathfinding = value));

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(this.supportPillars)
            .build(centerX - 100, y + 125, 200, 20, Text.literal("Support Pillars"),
                (button, value) -> this.supportPillars = value));

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(this.debugAutoGiveOnRefill)
            .build(centerX - 100, y + 150, 200, 20, Text.literal("Debug"),
                (button, value) -> this.debugAutoGiveOnRefill = value));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> {
                applyAndSave();
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            })
            .dimensions(centerX - 100, y + 180, 98, 20)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            })
            .dimensions(centerX + 2, y + 180, 98, 20)
            .build());
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 4 - 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("maxForbiddenSlots"), this.width / 2 - 100, this.height / 4 + 8, 0xA0A0A0);
    }

    private void applyAndSave() {
        this.maxForbiddenSlots = parseSlots(this.forbiddenSlotsField.getText());
        PrinterConfig.get().maxForbiddenSlots = this.maxForbiddenSlots;
        PrinterConfig.get().scaffoldWith = this.scaffoldWith;
        PrinterConfig.get().workWith = this.workWith;
        PrinterConfig.get().pathfinding = this.pathfinding;
        PrinterConfig.get().supportPillars = this.supportPillars;
        PrinterConfig.get().debugAutoGiveOnRefill = this.debugAutoGiveOnRefill;
        PrinterConfig.save();
    }

    private Text modeLabel(PrinterConfig.Data.ScaffoldWith mode) {
        return switch (mode) {
            case LEAVES -> Text.literal("Leaves");
            case ALL -> Text.literal("All");
            case SCAFFOLDING_BETA -> Text.literal("Scaffolding (Beta)");
        };
    }

    private Text workWithLabel(PrinterConfig.Data.WorkWith mode) {
        return switch (mode) {
            case INVENTORY -> Text.literal("Inventory");
            case HOTBAR_ONLY -> Text.literal("Hotbar only");
        };
    }

    private Text pathfindingLabel(PrinterConfig.Data.Pathfinding mode) {
        return switch (mode) {
            case FULLY_DETERMINISTIC -> Text.literal("Fully deterministic");
            case BARITONE -> Text.literal("Baritone");
        };
    }

    private int parseSlots(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(0, Math.min(36, parsed));
        } catch (NumberFormatException ex) {
            return this.maxForbiddenSlots;
        }
    }
}
