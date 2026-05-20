package de.maax.mappath.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class StructureMarkerEditScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private final Screen parent;
    private final WorldMapManager worldMapManager;
    private final StructureMarkerStore.Marker marker;
    private EditBox nameBox;

    StructureMarkerEditScreen(Screen parent, WorldMapManager worldMapManager, StructureMarkerStore.Marker marker) {
        super(Component.translatable("screen.mappath.edit_structure"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.marker = marker;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = Math.max(24, this.height / 2 - 48);
        int fieldLeft = left + 70;
        int fieldWidth = PANEL_WIDTH - 70;

        this.nameBox = new EditBox(this.font, fieldLeft, top + 20, fieldWidth, FIELD_HEIGHT, Component.translatable("gui.mappath.structure_name"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(this.marker.name());
        this.addRenderableWidget(this.nameBox);

        int buttonTop = top + 20 + ROW_GAP + 6;
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.mappath.confirm"), button -> this.confirm())
                .bounds(left, buttonTop, 116, FIELD_HEIGHT)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.mappath.cancel"), button -> this.onClose())
                .bounds(left + 124, buttonTop, 116, FIELD_HEIGHT)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = Math.max(24, this.height / 2 - 48);

        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.structure_name"), left, top + 26, 0xFFFFFFFF, true);
        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void confirm() {
        if (this.minecraft == null) {
            return;
        }

        this.worldMapManager.updateStructureMarkerName(this.minecraft, this.marker.key(), this.nameBox.getValue().trim());
        this.minecraft.setScreen(this.parent);
    }
}
