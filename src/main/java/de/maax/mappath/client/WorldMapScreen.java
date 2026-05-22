package de.maax.mappath.client;

import de.maax.mappath.BannerIconType;
import de.maax.mappath.MapPath;
import de.maax.mappath.MapPathConfig;
import de.maax.mappath.StructureMarkerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorldMapScreen extends Screen {
    private static final int SCREEN_PADDING = 12;
    private static final int PLAYER_MARKER_SIZE = 8;
    private static final int PLAYER_MARKER_TEXTURE_SIZE = 64;
    private static final int MIN_STRUCTURE_MARKER_SIZE = 4;
    private static final int MAX_STRUCTURE_MARKER_SIZE = 14;
    private static final int STRUCTURE_MARKER_TEXTURE_SIZE = 64;
    private static final int STRUCTURE_MARKER_TOGGLE_SIZE = 20;
    private static final int MARKER_TOGGLE_GAP = 4;
    private static final int CONTEXT_MENU_WIDTH = 150;
    private static final int CONTEXT_MENU_ITEM_HEIGHT = 18;
    private static final int CONTEXT_MENU_ITEM_COUNT = 3;
    private static final int WAYPOINT_CONTEXT_MENU_ITEM_COUNT = 5;
    private static final int WAYPOINT_CONTEXT_MENU_WIDTH = 170;
    private static final int STRUCTURE_CONTEXT_MENU_ITEM_COUNT = 5;
    private static final int STRUCTURE_CONTEXT_MENU_WIDTH = 170;
    private static final int CONTEXT_MENU_MARGIN = 4;
    private static final int UNEXPLORED_MAP_COLOR = 0xFF111417;
    private static final int CONTEXT_MENU_BACKGROUND_COLOR = 0xEE16191D;
    private static final int CONTEXT_MENU_BORDER_COLOR = 0xFF5D646C;
    private static final int CONTEXT_MENU_HOVER_COLOR = 0xFF3A3F45;
    private static final int CONTEXT_MENU_TEXT_COLOR = 0xFFE8EAED;
    private static final int PENDING_DELETE_LABEL_COLOR = 0xFF9A9A9A;
    private static final int PENDING_DELETE_LABEL_BACKGROUND_COLOR = 0x66000000;
    private static final float PENDING_DELETE_MARKER_TINT = 0.45F;
    private static final float PENDING_DELETE_MARKER_ALPHA = 0.55F;
    private static final int TOGGLE_BACKGROUND_COLOR = 0xCC16191D;
    private static final int TOGGLE_BORDER_COLOR = 0xFF5D646C;
    private static final int TOGGLE_HOVER_COLOR = 0xEE26303A;
    private static final float MIN_ZOOM = 0.0025F;
    private static final float MAX_ZOOM = 50.0F;
    private static final float SCROLL_ZOOM_STEP = 1.16F;
    private static final ResourceLocation PLAYER_MARKER_TEXTURE = ResourceLocation.fromNamespaceAndPath("mappath", "textures/gui/mag_icons/player.png");
    private static final Map<StructureMarkerType, ResourceLocation> STRUCTURE_MARKER_TEXTURES = createStructureMarkerTextures();
    private static final Map<BannerIconType, ResourceLocation> BANNER_ICON_TEXTURES = createBannerIconTextures();

    private SurfaceMapAtlas mapAtlas;
    private int mapX;
    private int mapY;
    private int mapSize;
    private float zoom = 1.0F;
    private double centerWorldX;
    private double centerWorldZ;
    private boolean draggingMap;
    private double lastDragMouseX;
    private double lastDragMouseY;
    private double currentMouseX;
    private double currentMouseY;
    private boolean contextMenuOpen;
    private int contextMenuX;
    private int contextMenuY;
    private int contextWorldX;
    private int contextWorldZ;
    private boolean waypointContextMenuOpen;
    private int waypointContextMenuX;
    private int waypointContextMenuY;
    private UUID waypointContextMenuId;
    private boolean structureContextMenuOpen;
    private int structureContextMenuX;
    private int structureContextMenuY;
    private String structureContextMenuKey;
    private final Set<UUID> pendingDeletedWaypoints = new HashSet<>();
    private final Set<String> pendingDeletedStructureMarkers = new HashSet<>();

    private final WorldMapManager worldMapManager;

    public WorldMapScreen(WorldMapManager worldMapManager) {
        super(Component.translatable("screen.mappath.world_map"));
        this.worldMapManager = worldMapManager;
    }

    @Override
    protected void init() {
        if (this.minecraft != null && this.minecraft.player != null && this.centerWorldX == 0.0D && this.centerWorldZ == 0.0D) {
            this.centerWorldX = this.minecraft.player.getX();
            this.centerWorldZ = this.minecraft.player.getZ();
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            this.zoom = 1.0F;
            this.centerWorldX = this.minecraft.player.getX();
            this.centerWorldZ = this.minecraft.player.getZ();
        }

        this.recalculateLayout();
        if (this.mapAtlas == null) {
            this.mapAtlas = this.worldMapManager.getMapAtlas(this.minecraft);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        this.recalculateLayout();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, UNEXPLORED_MAP_COLOR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.mapAtlas != null) {
            this.mapAtlas.render(guiGraphics, this.mapX, this.mapY, this.mapSize, this.centerWorldX, this.centerWorldZ, this.screenPixelsPerBlock());
        }

        this.renderMapMarkers(guiGraphics);

        float markerCenterX = this.worldToScreenX(this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getX() : this.centerWorldX);
        float markerCenterY = this.worldToScreenY(this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getZ() : this.centerWorldZ);
        float playerYaw = this.minecraft != null && this.minecraft.player != null
            ? Mth.rotLerp(partialTick, this.minecraft.player.yRotO, this.minecraft.player.getYRot())
            : 180.0F;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(markerCenterX, markerCenterY, 0.0F);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(playerYaw + 180.0F));
        guiGraphics.blit(
            PLAYER_MARKER_TEXTURE,
            -PLAYER_MARKER_SIZE / 2,
            -PLAYER_MARKER_SIZE / 2,
            PLAYER_MARKER_SIZE,
            PLAYER_MARKER_SIZE,
            0.0F,
            0.0F,
            PLAYER_MARKER_TEXTURE_SIZE,
            PLAYER_MARKER_TEXTURE_SIZE,
            PLAYER_MARKER_TEXTURE_SIZE,
            PLAYER_MARKER_TEXTURE_SIZE
        );
        guiGraphics.pose().popPose();

        guiGraphics.drawString(
            this.font,
            this.getFooterText(mouseX, mouseY),
            SCREEN_PADDING,
            this.height - SCREEN_PADDING - this.font.lineHeight,
            0xFFE0E0E0,
            false
        );
        guiGraphics.drawString(
            this.font,
            Component.translatable("gui.mappath.zoom", this.getZoomLabel()),
            SCREEN_PADDING,
            SCREEN_PADDING,
            0xFFE0E0E0,
            false
        );
        if (MapPathConfig.CLIENT.showMapLoadStatus() && !this.worldMapManager.initialLoadComplete(this.minecraft)) {
            guiGraphics.drawString(
                this.font,
                Component.translatable(
                    "gui.mappath.loading_map",
                    this.worldMapManager.loadedTileCount(this.minecraft),
                    this.worldMapManager.totalTileCount(this.minecraft)
                ),
                SCREEN_PADDING,
                SCREEN_PADDING + this.font.lineHeight + 2,
                0xFFE0E0E0,
                false
            );
        }

        this.renderContextMenu(guiGraphics, mouseX, mouseY);
        this.renderWaypointContextMenu(guiGraphics, mouseX, mouseY);
        this.renderStructureContextMenu(guiGraphics, mouseX, mouseY);
        this.renderBetaFeaturesToggle(guiGraphics, mouseX, mouseY);
        this.renderWaypointsToggle(guiGraphics, mouseX, mouseY);
        this.renderStructureMarkersToggle(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_M) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            WaypointStore.Waypoint hoveredWaypoint = this.waypointAt(this.currentMouseX, this.currentMouseY);
            if (hoveredWaypoint != null) {
                this.deleteWaypoint(hoveredWaypoint.id());
                this.closeAllContextMenus();
                return true;
            }
            StructureMarkerStore.Marker hoveredStructureMarker = this.structureMarkerAt(this.currentMouseX, this.currentMouseY);
            if (hoveredStructureMarker != null) {
                this.deleteStructureMarker(hoveredStructureMarker.key());
                this.closeAllContextMenus();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            this.closeAllContextMenus();
            this.changeZoom(mouseX, mouseY, scrollY);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.isMouseOverBetaFeaturesToggle(mouseX, mouseY)) {
            this.closeAllContextMenus();
            MapPathConfig.CLIENT.setShowBetaFeatures(!MapPathConfig.CLIENT.showBetaFeatures());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.isMouseOverStructureMarkersToggle(mouseX, mouseY)) {
            this.closeAllContextMenus();
            MapPathConfig.CLIENT.setShowStructureMarkers(!MapPathConfig.CLIENT.showStructureMarkers());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.isMouseOverWaypointsToggle(mouseX, mouseY)) {
            this.closeAllContextMenus();
            MapPathConfig.CLIENT.setShowWaypoints(!MapPathConfig.CLIENT.showWaypoints());
            return true;
        }

        if (this.waypointContextMenuOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int waypointMenuItem = this.waypointContextMenuItemAt(mouseX, mouseY);
                if (waypointMenuItem == 0) {
                    this.copyWaypointContextCoordinates();
                    this.waypointContextMenuOpen = false;
                    return true;
                }
                if (this.isWaypointPendingDelete(this.waypointContextMenuId)) {
                    if (waypointMenuItem == 1) {
                        this.deleteContextWaypoint();
                        this.waypointContextMenuOpen = false;
                        return true;
                    }
                    if (waypointMenuItem == 2) {
                        this.restoreContextWaypoint();
                        this.waypointContextMenuOpen = false;
                        return true;
                    }
                }
                int itemIndex = 1;
                if (this.canShowTeleportOptions() && waypointMenuItem == itemIndex++) {
                    this.teleportToWaypointContextPosition();
                    this.waypointContextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && waypointMenuItem == itemIndex++) {
                    this.startRouteToWaypointContextPosition();
                    this.waypointContextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && waypointMenuItem == itemIndex && this.worldMapManager.hasActiveRoute()) {
                    this.worldMapManager.cancelRoute(this.minecraft);
                    this.waypointContextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && this.worldMapManager.hasActiveRoute()) {
                    itemIndex++;
                }
                if (waypointMenuItem == itemIndex++) {
                    this.openWaypointEditScreen();
                    this.waypointContextMenuOpen = false;
                    return true;
                }
                if (waypointMenuItem == itemIndex++) {
                    this.toggleContextWaypointHighlight();
                    this.waypointContextMenuOpen = false;
                    return true;
                }
                if (waypointMenuItem == itemIndex++) {
                    this.markContextWaypointForDeletion();
                    this.waypointContextMenuOpen = false;
                    return true;
                }
            }

            this.waypointContextMenuOpen = false;
            if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !this.isMouseOverMap(mouseX, mouseY)) {
                return true;
            }
        }

        if (this.structureContextMenuOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int structureMenuItem = this.structureContextMenuItemAt(mouseX, mouseY);
                if (structureMenuItem == 0) {
                    this.copyStructureContextCoordinates();
                    this.structureContextMenuOpen = false;
                    return true;
                }
                if (this.isStructureMarkerPendingDelete(this.structureContextMenuKey)) {
                    if (structureMenuItem == 1) {
                        this.deleteContextStructure();
                        this.structureContextMenuOpen = false;
                        return true;
                    }
                    if (structureMenuItem == 2) {
                        this.restoreContextStructure();
                        this.structureContextMenuOpen = false;
                        return true;
                    }
                }
                int itemIndex = 1;
                if (this.canShowTeleportOptions() && structureMenuItem == itemIndex++) {
                    this.teleportToStructureContextPosition();
                    this.structureContextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && structureMenuItem == itemIndex++) {
                    this.startRouteToStructureContextPosition();
                    this.structureContextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && structureMenuItem == itemIndex && this.worldMapManager.hasActiveRoute()) {
                    this.worldMapManager.cancelRoute(this.minecraft);
                    this.structureContextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && this.worldMapManager.hasActiveRoute()) {
                    itemIndex++;
                }
                if (structureMenuItem == itemIndex++) {
                    this.openStructureEditScreen();
                    this.structureContextMenuOpen = false;
                    return true;
                }
                if (structureMenuItem == itemIndex++) {
                    this.toggleContextStructureHighlight();
                    this.structureContextMenuOpen = false;
                    return true;
                }
                if (structureMenuItem == itemIndex++) {
                    this.markContextStructureForDeletion();
                    this.structureContextMenuOpen = false;
                    return true;
                }
            }

            this.structureContextMenuOpen = false;
            if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !this.isMouseOverMap(mouseX, mouseY)) {
                return true;
            }
        }

        if (this.contextMenuOpen) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int contextMenuItem = this.contextMenuItemAt(mouseX, mouseY);
                if (contextMenuItem == 0) {
                    this.copyContextCoordinates();
                    this.contextMenuOpen = false;
                    return true;
                }
                int itemIndex = 1;
                if (this.canShowTeleportOptions() && contextMenuItem == itemIndex++) {
                    this.teleportToContextPosition();
                    this.contextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && contextMenuItem == itemIndex++) {
                    this.startRouteToContextPosition();
                    this.contextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && contextMenuItem == itemIndex && this.worldMapManager.hasActiveRoute()) {
                    this.worldMapManager.cancelRoute(this.minecraft);
                    this.contextMenuOpen = false;
                    return true;
                }
                if (this.canShowBetaFeatures() && this.worldMapManager.hasActiveRoute()) {
                    itemIndex++;
                }
                if (contextMenuItem == itemIndex++) {
                    this.openWaypointCreateScreen();
                    this.contextMenuOpen = false;
                    return true;
                }
            }

            this.contextMenuOpen = false;
            if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !this.isMouseOverMap(mouseX, mouseY)) {
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && this.isMouseOverMap(mouseX, mouseY)) {
            WaypointStore.Waypoint waypoint = this.waypointAt(mouseX, mouseY);
            if (waypoint != null) {
                this.openWaypointContextMenu(mouseX, mouseY, waypoint);
            } else {
                StructureMarkerStore.Marker structureMarker = this.structureMarkerAt(mouseX, mouseY);
                if (structureMarker != null) {
                    this.openStructureContextMenu(mouseX, mouseY, structureMarker);
                } else {
                    this.openContextMenu(mouseX, mouseY);
                }
            }
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.isMouseOverMap(mouseX, mouseY)) {
            this.draggingMap = true;
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingMap) {
            this.panMap(mouseX - this.lastDragMouseX, mouseY - this.lastDragMouseY);
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingMap) {
            this.draggingMap = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        this.mapAtlas = null;
    }

    private void recalculateLayout() {
        this.mapSize = Math.max(1, Math.max(this.width, this.height));
        this.mapX = (this.width - this.mapSize) / 2;
        this.mapY = (this.height - this.mapSize) / 2;
    }

    private String getFooterText(int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null || !this.isMouseOverMap(mouseX, mouseY)) {
            return Component.translatable("gui.mappath.move_mouse").getString();
        }

        int worldX = Mth.floor(this.screenToWorldX(mouseX));
        int worldZ = Mth.floor(this.screenToWorldZ(mouseY));
        int worldY = this.worldMapManager.defaultWaypointY(this.minecraft, worldX, worldZ);
        return Component.translatable("gui.mappath.cursor_position", worldX, worldY, worldZ).getString();
    }

    private boolean isMouseOverMap(double mouseX, double mouseY) {
        return mouseX >= this.mapX && mouseX < this.mapX + this.mapSize && mouseY >= this.mapY && mouseY < this.mapY + this.mapSize;
    }

    private void renderMapMarkers(GuiGraphics guiGraphics) {
        int markerSize = this.structureMarkerSize();
        if (MapPathConfig.CLIENT.showStructureMarkers()) {
            this.renderStructureMarkers(guiGraphics, markerSize);
        }
        if (MapPathConfig.CLIENT.showWaypoints()) {
            this.renderWaypoints(guiGraphics, markerSize);
        }
    }

    private void renderStructureMarkers(GuiGraphics guiGraphics, int markerSize) {
        if (!MapPathConfig.CLIENT.showStructureMarkers()) {
            return;
        }

        for (StructureMarkerStore.Marker marker : this.worldMapManager.structureMarkers(this.minecraft)) {
            float markerCenterX = this.worldToScreenX(marker.worldX());
            float markerCenterY = this.worldToScreenY(marker.worldZ());
            if (markerCenterX < this.mapX - markerSize
                || markerCenterX > this.mapX + this.mapSize + markerSize
                || markerCenterY < this.mapY - markerSize
                || markerCenterY > this.mapY + this.mapSize + markerSize) {
                continue;
            }

            boolean pendingDelete = this.isStructureMarkerPendingDelete(marker.key());
            if (pendingDelete) {
                guiGraphics.setColor(PENDING_DELETE_MARKER_TINT, PENDING_DELETE_MARKER_TINT, PENDING_DELETE_MARKER_TINT, PENDING_DELETE_MARKER_ALPHA);
            }
            guiGraphics.blit(
                STRUCTURE_MARKER_TEXTURES.get(marker.type()),
                Mth.floor(markerCenterX - markerSize / 2.0F),
                Mth.floor(markerCenterY - markerSize / 2.0F),
                markerSize,
                markerSize,
                0.0F,
                0.0F,
                STRUCTURE_MARKER_TEXTURE_SIZE,
                STRUCTURE_MARKER_TEXTURE_SIZE,
                STRUCTURE_MARKER_TEXTURE_SIZE,
                STRUCTURE_MARKER_TEXTURE_SIZE
            );
            if (pendingDelete) {
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (!marker.name().isEmpty()) {
                this.renderMarkerName(guiGraphics, marker.name(), markerCenterX, markerCenterY + markerSize / 2.0F + 2.0F, markerSize, pendingDelete);
            }
        }
    }

    private void renderWaypoints(GuiGraphics guiGraphics, int markerSize) {
        for (WaypointStore.Waypoint waypoint : this.worldMapManager.waypoints(this.minecraft)) {
            float markerCenterX = this.worldToScreenX(waypoint.worldX());
            float markerCenterY = this.worldToScreenY(waypoint.worldZ());
            if (markerCenterX < this.mapX - markerSize
                || markerCenterX > this.mapX + this.mapSize + markerSize
                || markerCenterY < this.mapY - markerSize
                || markerCenterY > this.mapY + this.mapSize + markerSize + this.font.lineHeight + 4) {
                continue;
            }

            boolean pendingDelete = this.isWaypointPendingDelete(waypoint.id());
            if (pendingDelete) {
                guiGraphics.setColor(PENDING_DELETE_MARKER_TINT, PENDING_DELETE_MARKER_TINT, PENDING_DELETE_MARKER_TINT, PENDING_DELETE_MARKER_ALPHA);
            }
            guiGraphics.blit(
                BANNER_ICON_TEXTURES.get(waypoint.icon()),
                Mth.floor(markerCenterX - markerSize / 2.0F),
                Mth.floor(markerCenterY - markerSize / 2.0F),
                markerSize,
                markerSize,
                0.0F,
                0.0F,
                STRUCTURE_MARKER_TEXTURE_SIZE,
                STRUCTURE_MARKER_TEXTURE_SIZE,
                STRUCTURE_MARKER_TEXTURE_SIZE,
                STRUCTURE_MARKER_TEXTURE_SIZE
            );
            if (pendingDelete) {
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            this.renderMarkerName(guiGraphics, waypoint.name(), markerCenterX, markerCenterY + markerSize / 2.0F + 2.0F, markerSize, pendingDelete);
        }
    }

    private void renderMarkerName(GuiGraphics guiGraphics, String name, float centerX, float topY, int markerSize, boolean pendingDelete) {
        float labelScale = markerSize / (float) MAX_STRUCTURE_MARKER_SIZE;
        int textWidth = this.font.width(name);
        int left = Mth.floor(-textWidth / 2.0F - 2.0F);
        int top = 0;
        int right = left + textWidth + 4;
        int bottom = top + this.font.lineHeight + 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, topY, 0.0F);
        guiGraphics.pose().scale(labelScale, labelScale, 1.0F);
        guiGraphics.fill(left, top, right, bottom, pendingDelete ? PENDING_DELETE_LABEL_BACKGROUND_COLOR : 0x88000000);
        guiGraphics.drawString(this.font, name, left + 2, top + 1, pendingDelete ? PENDING_DELETE_LABEL_COLOR : 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private int structureMarkerSize() {
        return Mth.clamp(Math.round(MAX_STRUCTURE_MARKER_SIZE * Mth.sqrt(Math.min(this.zoom, 1.0F))), MIN_STRUCTURE_MARKER_SIZE, MAX_STRUCTURE_MARKER_SIZE);
    }

    private void renderStructureMarkersToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = this.structureMarkersToggleX();
        int top = this.structureMarkersToggleY();
        this.renderMarkerToggle(guiGraphics, left, top, this.isMouseOverStructureMarkersToggle(mouseX, mouseY), MapPathConfig.CLIENT.showStructureMarkers() ? "S" : "s");
    }

    private void renderWaypointsToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = this.waypointsToggleX();
        int top = this.waypointsToggleY();
        this.renderMarkerToggle(guiGraphics, left, top, this.isMouseOverWaypointsToggle(mouseX, mouseY), MapPathConfig.CLIENT.showWaypoints() ? "W" : "w");
    }

    private void renderBetaFeaturesToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = this.betaFeaturesToggleX();
        int top = this.betaFeaturesToggleY();
        this.renderMarkerToggle(guiGraphics, left, top, this.isMouseOverBetaFeaturesToggle(mouseX, mouseY), MapPathConfig.CLIENT.showBetaFeatures() ? "B" : "b");
    }

    private void renderMarkerToggle(GuiGraphics guiGraphics, int left, int top, boolean hovered, String label) {
        int right = left + STRUCTURE_MARKER_TOGGLE_SIZE;
        int bottom = top + STRUCTURE_MARKER_TOGGLE_SIZE;
        guiGraphics.fill(left, top, right, bottom, hovered ? TOGGLE_HOVER_COLOR : TOGGLE_BACKGROUND_COLOR);
        guiGraphics.fill(left, top, right, top + 1, TOGGLE_BORDER_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, TOGGLE_BORDER_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, TOGGLE_BORDER_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, TOGGLE_BORDER_COLOR);

        guiGraphics.drawString(
            this.font,
            label,
            left + (STRUCTURE_MARKER_TOGGLE_SIZE - this.font.width(label)) / 2,
            top + (STRUCTURE_MARKER_TOGGLE_SIZE - this.font.lineHeight) / 2,
            CONTEXT_MENU_TEXT_COLOR,
            false
        );
    }

    private boolean isMouseOverStructureMarkersToggle(double mouseX, double mouseY) {
        int left = this.structureMarkersToggleX();
        int top = this.structureMarkersToggleY();
        return this.isMouseOverMarkerToggle(mouseX, mouseY, left, top);
    }

    private boolean isMouseOverWaypointsToggle(double mouseX, double mouseY) {
        int left = this.waypointsToggleX();
        int top = this.waypointsToggleY();
        return this.isMouseOverMarkerToggle(mouseX, mouseY, left, top);
    }

    private boolean isMouseOverBetaFeaturesToggle(double mouseX, double mouseY) {
        int left = this.betaFeaturesToggleX();
        int top = this.betaFeaturesToggleY();
        return this.isMouseOverMarkerToggle(mouseX, mouseY, left, top);
    }

    private boolean isMouseOverMarkerToggle(double mouseX, double mouseY, int left, int top) {
        return mouseX >= left
            && mouseX < left + STRUCTURE_MARKER_TOGGLE_SIZE
            && mouseY >= top
            && mouseY < top + STRUCTURE_MARKER_TOGGLE_SIZE;
    }

    private int structureMarkersToggleX() {
        return this.width - SCREEN_PADDING - STRUCTURE_MARKER_TOGGLE_SIZE;
    }

    private int structureMarkersToggleY() {
        return this.height - SCREEN_PADDING - STRUCTURE_MARKER_TOGGLE_SIZE;
    }

    private int waypointsToggleX() {
        return this.structureMarkersToggleX();
    }

    private int waypointsToggleY() {
        return this.structureMarkersToggleY() - STRUCTURE_MARKER_TOGGLE_SIZE - MARKER_TOGGLE_GAP;
    }

    private int betaFeaturesToggleX() {
        return this.structureMarkersToggleX();
    }

    private int betaFeaturesToggleY() {
        return this.waypointsToggleY() - STRUCTURE_MARKER_TOGGLE_SIZE - MARKER_TOGGLE_GAP;
    }

    private void openContextMenu(double mouseX, double mouseY) {
        this.draggingMap = false;
        this.waypointContextMenuOpen = false;
        this.structureContextMenuOpen = false;
        this.contextWorldX = Mth.floor(this.screenToWorldX(mouseX));
        this.contextWorldZ = Mth.floor(this.screenToWorldZ(mouseY));
        this.contextMenuX = Mth.clamp((int)mouseX, CONTEXT_MENU_MARGIN, this.width - CONTEXT_MENU_WIDTH - CONTEXT_MENU_MARGIN);
        this.contextMenuY = Mth.clamp((int)mouseY, CONTEXT_MENU_MARGIN, this.height - contextMenuHeight() - CONTEXT_MENU_MARGIN);
        this.contextMenuOpen = true;
    }

    private void openWaypointContextMenu(double mouseX, double mouseY, WaypointStore.Waypoint waypoint) {
        this.draggingMap = false;
        this.contextMenuOpen = false;
        this.structureContextMenuOpen = false;
        this.waypointContextMenuId = waypoint.id();
        this.waypointContextMenuX = Mth.clamp((int)mouseX, CONTEXT_MENU_MARGIN, this.width - WAYPOINT_CONTEXT_MENU_WIDTH - CONTEXT_MENU_MARGIN);
        this.waypointContextMenuY = Mth.clamp((int)mouseY, CONTEXT_MENU_MARGIN, this.height - waypointContextMenuHeight() - CONTEXT_MENU_MARGIN);
        this.waypointContextMenuOpen = true;
    }

    private void openStructureContextMenu(double mouseX, double mouseY, StructureMarkerStore.Marker marker) {
        this.draggingMap = false;
        this.contextMenuOpen = false;
        this.waypointContextMenuOpen = false;
        this.structureContextMenuKey = marker.key();
        this.structureContextMenuX = Mth.clamp((int)mouseX, CONTEXT_MENU_MARGIN, this.width - STRUCTURE_CONTEXT_MENU_WIDTH - CONTEXT_MENU_MARGIN);
        this.structureContextMenuY = Mth.clamp((int)mouseY, CONTEXT_MENU_MARGIN, this.height - structureContextMenuHeight() - CONTEXT_MENU_MARGIN);
        this.structureContextMenuOpen = true;
    }

    private void renderContextMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.contextMenuOpen) {
            return;
        }

        int left = this.contextMenuX;
        int top = this.contextMenuY;
        int right = left + CONTEXT_MENU_WIDTH;
        int bottom = top + contextMenuHeight();
        guiGraphics.fill(left, top, right, bottom, CONTEXT_MENU_BACKGROUND_COLOR);
        guiGraphics.fill(left, top, right, top + 1, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, CONTEXT_MENU_BORDER_COLOR);
        int hoveredItem = this.contextMenuItemAt(mouseX, mouseY);
        if (hoveredItem >= 0) {
            int itemTop = top + hoveredItem * CONTEXT_MENU_ITEM_HEIGHT;
            guiGraphics.fill(left + 1, itemTop + 1, right - 1, itemTop + CONTEXT_MENU_ITEM_HEIGHT - 1, CONTEXT_MENU_HOVER_COLOR);
        }

        int itemIndex = 0;
        this.renderContextMenuItem(guiGraphics, Component.literal(this.contextCoordinatesLabel()), left, top, itemIndex++);
        if (this.canShowTeleportOptions()) {
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.teleport_here"), left, top, itemIndex++);
        }
        if (this.canShowBetaFeatures()) {
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.show_route"), left, top, itemIndex++);
            if (this.worldMapManager.hasActiveRoute()) {
                this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.cancel_route"), left, top, itemIndex++);
            }
        }
        this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.create_waypoint"), left, top, itemIndex);
    }

    private void renderWaypointContextMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.waypointContextMenuOpen) {
            return;
        }

        int left = this.waypointContextMenuX;
        int top = this.waypointContextMenuY;
        int right = left + WAYPOINT_CONTEXT_MENU_WIDTH;
        int bottom = top + waypointContextMenuHeight();
        guiGraphics.fill(left, top, right, bottom, CONTEXT_MENU_BACKGROUND_COLOR);
        guiGraphics.fill(left, top, right, top + 1, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, CONTEXT_MENU_BORDER_COLOR);
        int hoveredItem = this.waypointContextMenuItemAt(mouseX, mouseY);
        if (hoveredItem >= 0) {
            int itemTop = top + hoveredItem * CONTEXT_MENU_ITEM_HEIGHT;
            guiGraphics.fill(left + 1, itemTop + 1, right - 1, itemTop + CONTEXT_MENU_ITEM_HEIGHT - 1, CONTEXT_MENU_HOVER_COLOR);
        }

        if (this.isWaypointPendingDelete(this.waypointContextMenuId)) {
            this.renderContextMenuItem(guiGraphics, Component.literal(this.waypointContextCoordinatesLabel()), left, top, 0);
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.delete_waypoint_final"), left, top, 1);
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.restore_waypoint"), left, top, 2);
        } else {
            int itemIndex = 0;
            this.renderContextMenuItem(guiGraphics, Component.literal(this.waypointContextCoordinatesLabel()), left, top, itemIndex++);
            if (this.canShowTeleportOptions()) {
                this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.teleport_to_waypoint"), left, top, itemIndex++);
            }
            if (this.canShowBetaFeatures()) {
                this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.show_route"), left, top, itemIndex++);
                if (this.worldMapManager.hasActiveRoute()) {
                    this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.cancel_route"), left, top, itemIndex++);
                }
            }
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.edit_waypoint"), left, top, itemIndex++);
            WaypointStore.Waypoint waypoint = this.contextWaypoint();
            String highlightKey = waypoint != null && waypoint.highlighted() ? "gui.mappath.disable_highlight" : "gui.mappath.enable_highlight";
            this.renderContextMenuItem(guiGraphics, Component.translatable(highlightKey), left, top, itemIndex++);
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.delete_waypoint"), left, top, itemIndex++);
        }
    }

    private void renderStructureContextMenu(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.structureContextMenuOpen) {
            return;
        }

        int left = this.structureContextMenuX;
        int top = this.structureContextMenuY;
        int right = left + STRUCTURE_CONTEXT_MENU_WIDTH;
        int bottom = top + structureContextMenuHeight();
        guiGraphics.fill(left, top, right, bottom, CONTEXT_MENU_BACKGROUND_COLOR);
        guiGraphics.fill(left, top, right, top + 1, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, CONTEXT_MENU_BORDER_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, CONTEXT_MENU_BORDER_COLOR);
        int hoveredItem = this.structureContextMenuItemAt(mouseX, mouseY);
        if (hoveredItem >= 0) {
            int itemTop = top + hoveredItem * CONTEXT_MENU_ITEM_HEIGHT;
            guiGraphics.fill(left + 1, itemTop + 1, right - 1, itemTop + CONTEXT_MENU_ITEM_HEIGHT - 1, CONTEXT_MENU_HOVER_COLOR);
        }

        if (this.isStructureMarkerPendingDelete(this.structureContextMenuKey)) {
            this.renderContextMenuItem(guiGraphics, Component.literal(this.structureContextCoordinatesLabel()), left, top, 0);
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.delete_structure_final"), left, top, 1);
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.restore_structure"), left, top, 2);
        } else {
            int itemIndex = 0;
            this.renderContextMenuItem(guiGraphics, Component.literal(this.structureContextCoordinatesLabel()), left, top, itemIndex++);
            if (this.canShowTeleportOptions()) {
                this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.teleport_to_structure"), left, top, itemIndex++);
            }
            if (this.canShowBetaFeatures()) {
                this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.show_route"), left, top, itemIndex++);
                if (this.worldMapManager.hasActiveRoute()) {
                    this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.cancel_route"), left, top, itemIndex++);
                }
            }
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.edit_structure"), left, top, itemIndex++);
            StructureMarkerStore.Marker marker = this.contextStructureMarker();
            String highlightKey = marker != null && marker.highlighted() ? "gui.mappath.disable_highlight" : "gui.mappath.enable_highlight";
            this.renderContextMenuItem(guiGraphics, Component.translatable(highlightKey), left, top, itemIndex++);
            this.renderContextMenuItem(guiGraphics, Component.translatable("gui.mappath.delete_structure"), left, top, itemIndex++);
        }
    }

    private void renderContextMenuItem(GuiGraphics guiGraphics, Component label, int left, int top, int index) {
        guiGraphics.drawString(
            this.font,
            label,
            left + 6,
            top + index * CONTEXT_MENU_ITEM_HEIGHT + (CONTEXT_MENU_ITEM_HEIGHT - this.font.lineHeight) / 2,
            CONTEXT_MENU_TEXT_COLOR,
            false
        );
    }

    private int contextMenuItemAt(double mouseX, double mouseY) {
        if (mouseX < this.contextMenuX
            || mouseX >= this.contextMenuX + CONTEXT_MENU_WIDTH
            || mouseY < this.contextMenuY
            || mouseY >= this.contextMenuY + contextMenuHeight()) {
            return -1;
        }

        return Mth.floor((mouseY - this.contextMenuY) / CONTEXT_MENU_ITEM_HEIGHT);
    }

    private int waypointContextMenuItemAt(double mouseX, double mouseY) {
        if (mouseX < this.waypointContextMenuX
            || mouseX >= this.waypointContextMenuX + WAYPOINT_CONTEXT_MENU_WIDTH
            || mouseY < this.waypointContextMenuY
            || mouseY >= this.waypointContextMenuY + waypointContextMenuHeight()) {
            return -1;
        }

        return Mth.floor((mouseY - this.waypointContextMenuY) / CONTEXT_MENU_ITEM_HEIGHT);
    }

    private int structureContextMenuItemAt(double mouseX, double mouseY) {
        if (mouseX < this.structureContextMenuX
            || mouseX >= this.structureContextMenuX + STRUCTURE_CONTEXT_MENU_WIDTH
            || mouseY < this.structureContextMenuY
            || mouseY >= this.structureContextMenuY + structureContextMenuHeight()) {
            return -1;
        }

        return Mth.floor((mouseY - this.structureContextMenuY) / CONTEXT_MENU_ITEM_HEIGHT);
    }

    private int contextMenuHeight() {
        return CONTEXT_MENU_ITEM_HEIGHT * (this.contextItemCount(CONTEXT_MENU_ITEM_COUNT, true) + 1);
    }

    private int waypointContextMenuHeight() {
        return CONTEXT_MENU_ITEM_HEIGHT * (this.isWaypointPendingDelete(this.waypointContextMenuId) ? 3 : this.contextItemCount(WAYPOINT_CONTEXT_MENU_ITEM_COUNT, true) + 1);
    }

    private int structureContextMenuHeight() {
        return CONTEXT_MENU_ITEM_HEIGHT * (this.isStructureMarkerPendingDelete(this.structureContextMenuKey) ? 3 : this.contextItemCount(STRUCTURE_CONTEXT_MENU_ITEM_COUNT, true) + 1);
    }

    private int contextItemCount(int baseItemCount, boolean includesTeleport) {
        int itemCount = baseItemCount;
        if (includesTeleport && !this.canShowTeleportOptions()) {
            itemCount--;
        }
        if (!this.canShowBetaFeatures()) {
            itemCount--;
        }
        return itemCount + (this.canShowBetaFeatures() && this.worldMapManager.hasActiveRoute() ? 1 : 0);
    }

    private boolean canShowBetaFeatures() {
        return MapPathConfig.CLIENT.showBetaFeatures();
    }

    private boolean canShowTeleportOptions() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (this.minecraft.player.hasPermissions(2)) {
            return true;
        }
        if (!this.minecraft.isLocalServer() || !this.minecraft.hasSingleplayerServer()) {
            return false;
        }

        IntegratedServer server = this.minecraft.getSingleplayerServer();
        return server != null && server.getWorldData().isAllowCommands();
    }

    private void copyContextCoordinates() {
        this.copyCoordinatesToClipboard(this.contextCoordinatesClipboardText());
    }

    private void copyWaypointContextCoordinates() {
        this.copyCoordinatesToClipboard(this.waypointContextCoordinatesLabel());
    }

    private void copyStructureContextCoordinates() {
        this.copyCoordinatesToClipboard(this.structureContextCoordinatesLabel());
    }

    private void copyCoordinatesToClipboard(String coordinates) {
        if (this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(coordinates);
        }
    }

    private String contextCoordinatesLabel() {
        return this.formatCoordinates(this.contextWorldX, this.contextWorldY(), this.contextWorldZ);
    }

    private String contextCoordinatesClipboardText() {
        return this.contextCoordinatesLabel();
    }

    private String waypointContextCoordinatesLabel() {
        WaypointStore.Waypoint waypoint = this.contextWaypoint();
        return waypoint == null
            ? ""
            : this.formatCoordinates(waypoint.worldX(), waypoint.worldY(), waypoint.worldZ());
    }

    private String structureContextCoordinatesLabel() {
        StructureMarkerStore.Marker marker = this.contextStructureMarker();
        if (marker == null) {
            return "";
        }
        if (marker.worldY() == Integer.MIN_VALUE) {
            return this.formatCoordinates(marker.worldX(), this.worldMapManager.defaultWaypointY(this.minecraft, marker.worldX(), marker.worldZ()), marker.worldZ());
        }

        return this.formatCoordinates(marker.worldX(), marker.worldY(), marker.worldZ());
    }

    private int contextWorldY() {
        return this.worldMapManager.defaultWaypointY(this.minecraft, this.contextWorldX, this.contextWorldZ);
    }

    private String formatCoordinates(int worldX, int worldY, int worldZ) {
        return "X: " + worldX + " Y: " + worldY + " Z: " + worldZ;
    }

    private void teleportToContextPosition() {
        this.worldMapManager.teleportToTopBlock(this.minecraft, this.contextWorldX, this.contextWorldZ);
        this.onClose();
    }

    private void startRouteToContextPosition() {
        int defaultY = this.worldMapManager.defaultWaypointY(this.minecraft, this.contextWorldX, this.contextWorldZ);
        this.worldMapManager.startRouteToPosition(
            this.minecraft,
            Component.translatable("gui.mappath.route_position_label", this.contextWorldX, this.contextWorldZ).getString(),
            this.contextWorldX,
            defaultY,
            this.contextWorldZ,
            RouteTarget.Type.POSITION
        );
        this.onClose();
    }

    private void openWaypointCreateScreen() {
        int defaultY = this.worldMapManager.defaultWaypointY(this.minecraft, this.contextWorldX, this.contextWorldZ);
        if (this.minecraft != null) {
            this.minecraft.setScreen(new WaypointCreateScreen(this, this.worldMapManager, this.contextWorldX, defaultY, this.contextWorldZ));
        }
    }

    private void teleportToWaypointContextPosition() {
        WaypointStore.Waypoint waypoint = this.contextWaypoint();
        if (waypoint != null) {
            this.worldMapManager.teleportToPosition(this.minecraft, waypoint.worldX(), waypoint.worldY(), waypoint.worldZ());
            this.onClose();
        }
    }

    private void startRouteToWaypointContextPosition() {
        WaypointStore.Waypoint waypoint = this.contextWaypoint();
        if (waypoint != null) {
            this.worldMapManager.startRouteToPosition(
                this.minecraft,
                waypoint.name(),
                waypoint.worldX(),
                waypoint.worldY(),
                waypoint.worldZ(),
                RouteTarget.Type.WAYPOINT
            );
            this.onClose();
        }
    }

    private void openWaypointEditScreen() {
        WaypointStore.Waypoint waypoint = this.contextWaypoint();
        if (waypoint != null && this.minecraft != null) {
            this.minecraft.setScreen(new WaypointCreateScreen(this, this.worldMapManager, waypoint));
        }
    }

    private void deleteContextWaypoint() {
        if (this.waypointContextMenuId != null) {
            this.deleteWaypoint(this.waypointContextMenuId);
        }
    }

    private void markContextWaypointForDeletion() {
        if (this.waypointContextMenuId != null) {
            this.pendingDeletedWaypoints.add(this.waypointContextMenuId);
        }
    }

    private void restoreContextWaypoint() {
        if (this.waypointContextMenuId != null) {
            this.pendingDeletedWaypoints.remove(this.waypointContextMenuId);
        }
    }

    private void toggleContextWaypointHighlight() {
        WaypointStore.Waypoint waypoint = this.contextWaypoint();
        if (waypoint != null) {
            this.worldMapManager.setWaypointHighlighted(this.minecraft, waypoint.id(), !waypoint.highlighted());
        }
    }

    private WaypointStore.Waypoint contextWaypoint() {
        return this.waypointContextMenuId == null ? null : this.worldMapManager.waypoint(this.minecraft, this.waypointContextMenuId);
    }

    private void teleportToStructureContextPosition() {
        StructureMarkerStore.Marker marker = this.contextStructureMarker();
        if (marker != null) {
            this.worldMapManager.teleportToTopBlock(this.minecraft, marker.worldX(), marker.worldZ());
            this.onClose();
        }
    }

    private void startRouteToStructureContextPosition() {
        StructureMarkerStore.Marker marker = this.contextStructureMarker();
        if (marker != null) {
            int worldY = marker.worldY() == Integer.MIN_VALUE
                ? this.worldMapManager.defaultWaypointY(this.minecraft, marker.worldX(), marker.worldZ())
                : marker.worldY();
            this.worldMapManager.startRouteToStructure(
                this.minecraft,
                this.structureRouteLabel(marker),
                marker,
                worldY
            );
            this.onClose();
        }
    }

    private void openStructureEditScreen() {
        StructureMarkerStore.Marker marker = this.contextStructureMarker();
        if (marker != null && this.minecraft != null) {
            this.minecraft.setScreen(new StructureMarkerEditScreen(this, this.worldMapManager, marker));
        }
    }

    private void deleteContextStructure() {
        if (this.structureContextMenuKey != null) {
            this.deleteStructureMarker(this.structureContextMenuKey);
        }
    }

    private void markContextStructureForDeletion() {
        if (this.structureContextMenuKey != null) {
            this.pendingDeletedStructureMarkers.add(this.structureContextMenuKey);
        }
    }

    private void restoreContextStructure() {
        if (this.structureContextMenuKey != null) {
            this.pendingDeletedStructureMarkers.remove(this.structureContextMenuKey);
        }
    }

    private void toggleContextStructureHighlight() {
        StructureMarkerStore.Marker marker = this.contextStructureMarker();
        if (marker != null) {
            this.worldMapManager.setStructureMarkerHighlighted(this.minecraft, marker.key(), !marker.highlighted());
        }
    }

    private StructureMarkerStore.Marker contextStructureMarker() {
        return this.structureContextMenuKey == null ? null : this.worldMapManager.structureMarker(this.minecraft, this.structureContextMenuKey);
    }

    private String structureRouteLabel(StructureMarkerStore.Marker marker) {
        if (!marker.name().isEmpty()) {
            return marker.name();
        }

        String[] words = marker.type().id().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                label.append(word.substring(1));
            }
        }

        return label.toString();
    }

    private void deleteWaypoint(UUID id) {
        this.pendingDeletedWaypoints.remove(id);
        this.worldMapManager.deleteWaypoint(this.minecraft, id);
    }

    private void deleteStructureMarker(String key) {
        this.pendingDeletedStructureMarkers.remove(key);
        this.worldMapManager.deleteStructureMarker(this.minecraft, key);
    }

    private boolean isWaypointPendingDelete(UUID id) {
        return id != null && this.pendingDeletedWaypoints.contains(id);
    }

    private boolean isStructureMarkerPendingDelete(String key) {
        return key != null && this.pendingDeletedStructureMarkers.contains(key);
    }

    private void closeAllContextMenus() {
        this.contextMenuOpen = false;
        this.waypointContextMenuOpen = false;
        this.structureContextMenuOpen = false;
    }

    private WaypointStore.Waypoint waypointAt(double mouseX, double mouseY) {
        if (!MapPathConfig.CLIENT.showWaypoints()) {
            return null;
        }

        int markerSize = this.structureMarkerSize();
        float hitRadius = Math.max(6.0F, markerSize / 2.0F + 2.0F);
        WaypointStore.Waypoint nearestWaypoint = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (WaypointStore.Waypoint waypoint : this.worldMapManager.waypoints(this.minecraft)) {
            float markerCenterX = this.worldToScreenX(waypoint.worldX());
            float markerCenterY = this.worldToScreenY(waypoint.worldZ());
            double deltaX = mouseX - markerCenterX;
            double deltaY = mouseY - markerCenterY;
            if (Math.abs(deltaX) > hitRadius || Math.abs(deltaY) > hitRadius) {
                continue;
            }

            double distanceSquared = deltaX * deltaX + deltaY * deltaY;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestWaypoint = waypoint;
            }
        }

        return nearestWaypoint;
    }

    private StructureMarkerStore.Marker structureMarkerAt(double mouseX, double mouseY) {
        if (!MapPathConfig.CLIENT.showStructureMarkers()) {
            return null;
        }

        int markerSize = this.structureMarkerSize();
        float hitRadius = Math.max(6.0F, markerSize / 2.0F + 2.0F);
        StructureMarkerStore.Marker nearestMarker = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (StructureMarkerStore.Marker marker : this.worldMapManager.structureMarkers(this.minecraft)) {
            float markerCenterX = this.worldToScreenX(marker.worldX());
            float markerCenterY = this.worldToScreenY(marker.worldZ());
            double deltaX = mouseX - markerCenterX;
            double deltaY = mouseY - markerCenterY;
            if (Math.abs(deltaX) > hitRadius || Math.abs(deltaY) > hitRadius) {
                continue;
            }

            double distanceSquared = deltaX * deltaX + deltaY * deltaY;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestMarker = marker;
            }
        }

        return nearestMarker;
    }

    private void changeZoom(double mouseX, double mouseY, double scrollY) {
        boolean zoomingOnMap = this.isMouseOverMap(mouseX, mouseY);
        double anchorWorldX = zoomingOnMap ? this.screenToWorldX(mouseX) : 0.0D;
        double anchorWorldZ = zoomingOnMap ? this.screenToWorldZ(mouseY) : 0.0D;
        float newZoom = Mth.clamp((float)(this.zoom * Math.pow(SCROLL_ZOOM_STEP, scrollY)), MIN_ZOOM, MAX_ZOOM);
        if (newZoom == this.zoom) {
            return;
        }

        this.zoom = newZoom;
        if (zoomingOnMap) {
            double screenOffsetX = mouseX - this.mapX - this.mapSize / 2.0D;
            double screenOffsetY = mouseY - this.mapY - this.mapSize / 2.0D;
            this.centerWorldX = anchorWorldX - screenOffsetX / this.screenPixelsPerBlock();
            this.centerWorldZ = anchorWorldZ - screenOffsetY / this.screenPixelsPerBlock();
        }
    }

    private void panMap(double screenDeltaX, double screenDeltaY) {
        if (screenDeltaX == 0.0D && screenDeltaY == 0.0D) {
            return;
        }

        this.centerWorldX -= screenDeltaX / this.screenPixelsPerBlock();
        this.centerWorldZ -= screenDeltaY / this.screenPixelsPerBlock();
    }

    private float worldToScreenX(double worldX) {
        return (float)(this.mapX + this.mapSize / 2.0D + (worldX - this.centerWorldX) * this.screenPixelsPerBlock());
    }

    private float worldToScreenY(double worldZ) {
        return (float)(this.mapY + this.mapSize / 2.0D + (worldZ - this.centerWorldZ) * this.screenPixelsPerBlock());
    }

    private double screenPixelsPerBlock() {
        return this.zoom;
    }

    private double screenToWorldX(double screenX) {
        return this.centerWorldX + (screenX - this.mapX - this.mapSize / 2.0D) / this.screenPixelsPerBlock();
    }

    private double screenToWorldZ(double screenY) {
        return this.centerWorldZ + (screenY - this.mapY - this.mapSize / 2.0D) / this.screenPixelsPerBlock();
    }

    private String getZoomLabel() {
        if (this.zoom >= 10.0F) {
            return String.format(Locale.ROOT, "x%.0f", this.zoom);
        }
        if (this.zoom >= 1.0F) {
            return String.format(Locale.ROOT, "x%.2f", this.zoom);
        }
        if (this.zoom >= 0.01F) {
            return String.format(Locale.ROOT, "x%.4f", this.zoom);
        }

        return String.format(Locale.ROOT, "x%.5f", this.zoom);
    }

    private static Map<StructureMarkerType, ResourceLocation> createStructureMarkerTextures() {
        Map<StructureMarkerType, ResourceLocation> textures = new EnumMap<>(StructureMarkerType.class);
        for (StructureMarkerType type : StructureMarkerType.values()) {
            textures.put(type, ResourceLocation.fromNamespaceAndPath(MapPath.MODID, type.texturePath()));
        }

        return textures;
    }

    private static Map<BannerIconType, ResourceLocation> createBannerIconTextures() {
        Map<BannerIconType, ResourceLocation> textures = new EnumMap<>(BannerIconType.class);
        for (BannerIconType type : BannerIconType.values()) {
            textures.put(type, ResourceLocation.fromNamespaceAndPath(MapPath.MODID, type.texturePath()));
        }

        return textures;
    }
}
