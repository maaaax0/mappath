package de.maax.mappath.client;

import de.maax.mappath.BannerIconType;
import de.maax.mappath.MapPath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

final class WaypointCreateScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_GAP = 24;
    private static final int BANNER_TEXTURE_SIZE = 64;
    private static final int BANNER_PREVIEW_SIZE = 18;

    private final Screen parent;
    private final WorldMapManager worldMapManager;
    private final WaypointStore.Waypoint editingWaypoint;
    private final int defaultX;
    private final int defaultY;
    private final int defaultZ;
    private final boolean prefillCoordinates;
    private BannerIconType selectedIcon;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button confirmButton;

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, int defaultX, int defaultY, int defaultZ) {
        this(parent, worldMapManager, defaultX, defaultY, defaultZ, false);
    }

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, int defaultX, int defaultY, int defaultZ, boolean prefillCoordinates) {
        super(Component.translatable("screen.mappath.create_waypoint"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.editingWaypoint = null;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.defaultZ = defaultZ;
        this.prefillCoordinates = prefillCoordinates;
        BannerIconType[] icons = BannerIconType.values();
        this.selectedIcon = icons[ThreadLocalRandom.current().nextInt(icons.length)];
    }

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, WaypointStore.Waypoint editingWaypoint) {
        super(Component.translatable("screen.mappath.edit_waypoint"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.editingWaypoint = editingWaypoint;
        this.defaultX = editingWaypoint.worldX();
        this.defaultY = editingWaypoint.worldY();
        this.defaultZ = editingWaypoint.worldZ();
        this.prefillCoordinates = true;
        this.selectedIcon = editingWaypoint.icon();
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = Math.max(24, this.height / 2 - 95);
        int fieldLeft = left + 70;
        int fieldWidth = PANEL_WIDTH - 70;

        this.addRenderableWidget(
            CycleButton.builder(BannerIconType::displayName)
                .withValues(Arrays.asList(BannerIconType.values()))
                .withInitialValue(this.selectedIcon)
                .create(fieldLeft, top + 20, fieldWidth, FIELD_HEIGHT, Component.translatable("gui.mappath.waypoint_icon"), (button, value) -> this.selectedIcon = value)
        );

        this.nameBox = new EditBox(this.font, fieldLeft, top + 20 + ROW_GAP, fieldWidth, FIELD_HEIGHT, Component.translatable("gui.mappath.waypoint_name"));
        this.nameBox.setMaxLength(64);
        if (this.editingWaypoint != null) {
            this.nameBox.setValue(this.editingWaypoint.name());
        }
        this.addRenderableWidget(this.nameBox);

        this.xBox = this.coordinateBox(fieldLeft, top + 20 + ROW_GAP * 2, fieldWidth, this.defaultX);
        this.yBox = this.coordinateBox(fieldLeft, top + 20 + ROW_GAP * 3, fieldWidth, this.defaultY);
        this.zBox = this.coordinateBox(fieldLeft, top + 20 + ROW_GAP * 4, fieldWidth, this.defaultZ);
        if (this.prefillCoordinates) {
            this.xBox.setValue(Integer.toString(this.defaultX));
            this.yBox.setValue(Integer.toString(this.defaultY));
            this.zBox.setValue(Integer.toString(this.defaultZ));
        }
        this.addRenderableWidget(this.xBox);
        this.addRenderableWidget(this.yBox);
        this.addRenderableWidget(this.zBox);

        int buttonTop = top + 20 + ROW_GAP * 5 + 6;
        this.confirmButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.mappath.confirm"), button -> this.confirm())
                .bounds(left, buttonTop, 116, FIELD_HEIGHT)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.mappath.cancel"), button -> this.onClose())
                .bounds(left + 124, buttonTop, 116, FIELD_HEIGHT)
                .build()
        );
        this.updateConfirmState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = Math.max(24, this.height / 2 - 95);
        int labelX = left;
        int fieldLeft = left + 70;

        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_icon", labelX, top + 26);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_name", labelX, top + 26 + ROW_GAP);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_x", labelX, top + 26 + ROW_GAP * 2);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_y", labelX, top + 26 + ROW_GAP * 3);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_z", labelX, top + 26 + ROW_GAP * 4);

        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MapPath.MODID, this.selectedIcon.texturePath());
        guiGraphics.blit(texture, fieldLeft - BANNER_PREVIEW_SIZE - 6, top + 21, BANNER_PREVIEW_SIZE, BANNER_PREVIEW_SIZE, 0.0F, 0.0F, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private EditBox coordinateBox(int x, int y, int width, int defaultValue) {
        EditBox box = new EditBox(this.font, x, y, width, FIELD_HEIGHT, Component.empty());
        box.setMaxLength(12);
        box.setFilter(value -> value.isEmpty() || value.matches("-?\\d*"));
        box.setHint(Component.literal(Integer.toString(defaultValue)));
        box.setResponder(value -> this.updateConfirmState());
        return box;
    }

    private void drawLabel(GuiGraphics guiGraphics, String translationKey, int x, int y) {
        guiGraphics.drawString(this.font, Component.translatable(translationKey), x, y, 0xFFFFFFFF, true);
    }

    private void updateConfirmState() {
        if (this.confirmButton != null) {
            this.confirmButton.active = this.parseCoordinate(this.xBox, this.defaultX) != null
                && this.parseCoordinate(this.yBox, this.defaultY) != null
                && this.parseCoordinate(this.zBox, this.defaultZ) != null;
        }
    }

    private void confirm() {
        Integer worldX = this.parseCoordinate(this.xBox, this.defaultX);
        Integer worldY = this.parseCoordinate(this.yBox, this.defaultY);
        Integer worldZ = this.parseCoordinate(this.zBox, this.defaultZ);
        if (worldX == null || worldY == null || worldZ == null || this.minecraft == null) {
            return;
        }

        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) {
            name = "X: " + worldX + " Y: " + worldY + " Z: " + worldZ;
        }

        if (this.editingWaypoint == null) {
            this.worldMapManager.addWaypoint(this.minecraft, this.selectedIcon, name, worldX, worldY, worldZ);
        } else {
            this.worldMapManager.updateWaypoint(this.minecraft, this.editingWaypoint.id(), this.selectedIcon, name, worldX, worldY, worldZ);
        }
        this.minecraft.setScreen(this.parent);
    }

    private Integer parseCoordinate(EditBox box, int defaultValue) {
        String value = box.getValue().trim();
        if (value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
