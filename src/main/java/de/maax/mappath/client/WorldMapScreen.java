package de.maax.mappath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class WorldMapScreen extends Screen {
    private static final int SCREEN_PADDING = 12;
    private static final int PLAYER_MARKER_SIZE = 8;
    private static final int PLAYER_MARKER_TEXTURE_SIZE = 64;
    private static final int CHUNK_SIZE = 16;
    private static final float MIN_HOVERED_CHUNK_SCREEN_SIZE = 2.0F;
    private static final int UNEXPLORED_MAP_COLOR = 0xFF111417;
    private static final int HOVERED_CHUNK_FILL_COLOR = 0x44FFFFFF;
    private static final int HOVERED_CHUNK_BORDER_COLOR = 0xAAFFFFFF;
    private static final float MIN_ZOOM = 0.0025F;
    private static final float MAX_ZOOM = 50.0F;
    private static final float SCROLL_ZOOM_STEP = 1.16F;
    private static final int ZOOM_REFRESH_DELAY_TICKS = 3;
    private static final int DATA_REFRESH_DELAY_TICKS = 5;
    private static final ResourceLocation PLAYER_MARKER_TEXTURE = ResourceLocation.fromNamespaceAndPath("mappath", "textures/gui/player.png");

    private SurfaceMapTexture mapTexture;
    private int mapX;
    private int mapY;
    private int mapSize;
    private float zoom = 1.0F;
    private int pendingZoomRefreshTicks;
    private int pendingDataRefreshTicks;
    private long observedStoreRevision;
    private double centerWorldX;
    private double centerWorldZ;
    private boolean draggingMap;
    private double lastDragMouseX;
    private double lastDragMouseY;

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

        this.recalculateLayout();
        if (this.mapTexture == null) {
            this.mapTexture = new SurfaceMapTexture(this.worldMapManager);
            this.refreshMapTexture();
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        this.recalculateLayout();
        if (this.mapTexture != null) {
            this.refreshMapTexture();
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, UNEXPLORED_MAP_COLOR);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        this.mapTexture.render(guiGraphics, this.mapX, this.mapY, this.mapSize, this.currentBlocksPerPixel(), this.centerWorldX, this.centerWorldZ);
        this.renderHoveredChunk(guiGraphics, mouseX, mouseY);

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

    }

    @Override
    public void tick() {
        boolean refresh = false;
        if (this.pendingZoomRefreshTicks > 0) {
            this.pendingZoomRefreshTicks--;
            refresh = this.pendingZoomRefreshTicks == 0;
        }

        long storeRevision = this.worldMapManager.storeRevision(this.minecraft);
        if (storeRevision != this.observedStoreRevision && this.pendingDataRefreshTicks == 0) {
            this.pendingDataRefreshTicks = DATA_REFRESH_DELAY_TICKS;
        }

        if (this.pendingDataRefreshTicks > 0) {
            this.pendingDataRefreshTicks--;
            refresh = refresh || this.pendingDataRefreshTicks == 0;
        }

        if (refresh && this.mapTexture != null) {
            this.refreshMapTexture();
            this.pendingZoomRefreshTicks = 0;
            this.pendingDataRefreshTicks = 0;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_M) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            this.changeZoom(mouseX, mouseY, scrollY);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingMap) {
            this.panMap(mouseX - this.lastDragMouseX, mouseY - this.lastDragMouseY);
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.draggingMap) {
            this.draggingMap = false;
            if (this.mapTexture != null) {
                this.refreshMapTexture();
                this.pendingZoomRefreshTicks = 0;
                this.pendingDataRefreshTicks = 0;
            }
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
        if (this.mapTexture != null) {
            this.mapTexture.close();
            this.mapTexture = null;
        }
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
        return Component.translatable("gui.mappath.cursor_position", worldX, worldZ).getString();
    }

    private boolean isMouseOverMap(double mouseX, double mouseY) {
        return mouseX >= this.mapX && mouseX < this.mapX + this.mapSize && mouseY >= this.mapY && mouseY < this.mapY + this.mapSize;
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
        this.pendingZoomRefreshTicks = ZOOM_REFRESH_DELAY_TICKS;
    }

    private void panMap(double screenDeltaX, double screenDeltaY) {
        if (screenDeltaX == 0.0D && screenDeltaY == 0.0D) {
            return;
        }

        double screenPixelsToBlocks = SurfaceMapTexture.MAP_SIZE / (double) this.mapSize * this.currentBlocksPerPixel();
        this.centerWorldX -= screenDeltaX * screenPixelsToBlocks;
        this.centerWorldZ -= screenDeltaY * screenPixelsToBlocks;
    }

    private void renderHoveredChunk(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.isMouseOverMap(mouseX, mouseY)) {
            return;
        }

        int worldX = Mth.floor(this.screenToWorldX(mouseX));
        int worldZ = Mth.floor(this.screenToWorldZ(mouseY));
        int chunkStartX = Math.floorDiv(worldX, CHUNK_SIZE) * CHUNK_SIZE;
        int chunkStartZ = Math.floorDiv(worldZ, CHUNK_SIZE) * CHUNK_SIZE;

        float chunkScreenSize = (float)(CHUNK_SIZE * this.screenPixelsPerBlock());
        if (chunkScreenSize < MIN_HOVERED_CHUNK_SCREEN_SIZE) {
            return;
        }

        int size = Math.max(1, Math.round(chunkScreenSize));
        int left = Math.round(this.worldToScreenX(chunkStartX + CHUNK_SIZE / 2.0D) - size / 2.0F);
        int top = Math.round(this.worldToScreenY(chunkStartZ + CHUNK_SIZE / 2.0D) - size / 2.0F);
        int right = left + size;
        int bottom = top + size;

        guiGraphics.enableScissor(0, 0, this.width, this.height);
        guiGraphics.fill(left, top, right, bottom, HOVERED_CHUNK_FILL_COLOR);
        guiGraphics.fill(left, top, right, top + 1, HOVERED_CHUNK_BORDER_COLOR);
        guiGraphics.fill(left, bottom - 1, right, bottom, HOVERED_CHUNK_BORDER_COLOR);
        guiGraphics.fill(left, top, left + 1, bottom, HOVERED_CHUNK_BORDER_COLOR);
        guiGraphics.fill(right - 1, top, right, bottom, HOVERED_CHUNK_BORDER_COLOR);
        guiGraphics.disableScissor();
    }

    private float worldToScreenX(double worldX) {
        return (float)(this.mapX + this.mapSize / 2.0D + (worldX - this.centerWorldX) * this.screenPixelsPerBlock());
    }

    private float worldToScreenY(double worldZ) {
        return (float)(this.mapY + this.mapSize / 2.0D + (worldZ - this.centerWorldZ) * this.screenPixelsPerBlock());
    }

    private double screenPixelsPerBlock() {
        return this.mapSize / (SurfaceMapTexture.MAP_SIZE * (double) this.currentBlocksPerPixel());
    }

    private double screenToWorldX(double screenX) {
        return this.centerWorldX + (screenX - this.mapX - this.mapSize / 2.0D) / this.screenPixelsPerBlock();
    }

    private double screenToWorldZ(double screenY) {
        return this.centerWorldZ + (screenY - this.mapY - this.mapSize / 2.0D) / this.screenPixelsPerBlock();
    }

    private float currentBlocksPerPixel() {
        return 1.0F / this.zoom;
    }

    private void refreshMapTexture() {
        if (this.mapTexture == null) {
            return;
        }

        this.mapTexture.refresh(this.minecraft, this.currentBlocksPerPixel(), this.centerWorldX, this.centerWorldZ, this.mapSize);
        this.observedStoreRevision = this.worldMapManager.storeRevision(this.minecraft);
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
}
