package de.maax.mappath.client;

import com.mojang.math.Axis;
import de.maax.mappath.BannerIconType;
import de.maax.mappath.MapPath;
import de.maax.mappath.MapPathConfig;
import de.maax.mappath.StructureMarkerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.EnumMap;
import java.util.Map;

final class MinimapRenderer {
    private static final int MARGIN = 8;
    private static final int PLAYER_MARKER_SIZE = 8;
    private static final int MARKER_SIZE = 6;
    private static final int PLAYER_MARKER_TEXTURE_SIZE = 64;
    private static final int MARKER_TEXTURE_SIZE = 64;
    private static final int BACKGROUND_TEXTURE_SIZE = 512;
    private static final int BACKGROUND_CONTENT_INSET = 23;
    private static final int BACKGROUND_CONTENT_SIZE = BACKGROUND_TEXTURE_SIZE - BACKGROUND_CONTENT_INSET * 2;
    private static final ResourceLocation BACKGROUND_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/minimap_background.png");
    private static final ResourceLocation PLAYER_MARKER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mag_icons/player.png");
    private static final Map<StructureMarkerType, ResourceLocation> STRUCTURE_MARKER_TEXTURES = createStructureMarkerTextures();
    private static final Map<BannerIconType, ResourceLocation> BANNER_ICON_TEXTURES = createBannerIconTextures();

    private MinimapRenderer() {
    }

    static void render(RenderGuiEvent.Post event, WorldMapManager worldMapManager) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!MapPathConfig.CLIENT.showMinimap()
            || minecraft.level == null
            || minecraft.player == null
            || minecraft.screen != null
            || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        int contentSize = Mth.clamp(MapPathConfig.CLIENT.minimapSize(), 64, 256);
        int frameSize = Mth.ceil(contentSize * BACKGROUND_TEXTURE_SIZE / (float)BACKGROUND_CONTENT_SIZE);
        int contentInset = Math.max(1, Math.round(frameSize * BACKGROUND_CONTENT_INSET / (float)BACKGROUND_TEXTURE_SIZE));
        int left = minecraft.getWindow().getGuiScaledWidth() - frameSize - MARGIN;
        int top = MARGIN;
        int contentLeft = left + contentInset;
        int contentTop = top + contentInset;
        int innerSize = frameSize - contentInset * 2;
        double pixelsPerBlock = 1.0D / Math.max(1, MapPathConfig.CLIENT.minimapBlocksPerPixel());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double centerWorldX = Mth.lerp(partialTick, minecraft.player.xo, minecraft.player.getX());
        double centerWorldZ = Mth.lerp(partialTick, minecraft.player.zo, minecraft.player.getZ());
        GuiGraphics guiGraphics = event.getGuiGraphics();

        guiGraphics.blit(
            BACKGROUND_TEXTURE,
            left,
            top,
            frameSize,
            frameSize,
            0.0F,
            0.0F,
            BACKGROUND_TEXTURE_SIZE,
            BACKGROUND_TEXTURE_SIZE,
            BACKGROUND_TEXTURE_SIZE,
            BACKGROUND_TEXTURE_SIZE
        );
        SurfaceMapAtlas mapAtlas = worldMapManager.getMapAtlas(minecraft);
        if (mapAtlas != null) {
            mapAtlas.render(guiGraphics, contentLeft, contentTop, innerSize, centerWorldX, centerWorldZ, pixelsPerBlock);
        }

        guiGraphics.enableScissor(contentLeft, contentTop, contentLeft + innerSize, contentTop + innerSize);
        renderMarkers(guiGraphics, worldMapManager, minecraft, contentLeft, contentTop, innerSize, centerWorldX, centerWorldZ, pixelsPerBlock);
        renderPlayer(guiGraphics, minecraft, contentLeft + innerSize / 2.0F, contentTop + innerSize / 2.0F, partialTick);
        guiGraphics.disableScissor();
    }

    private static void renderMarkers(
        GuiGraphics guiGraphics,
        WorldMapManager worldMapManager,
        Minecraft minecraft,
        int left,
        int top,
        int size,
        double centerWorldX,
        double centerWorldZ,
        double pixelsPerBlock
    ) {
        if (MapPathConfig.CLIENT.showStructureMarkers()) {
            for (StructureMarkerStore.Marker marker : worldMapManager.structureMarkers(minecraft)) {
                renderMarker(
                    guiGraphics,
                    STRUCTURE_MARKER_TEXTURES.get(marker.type()),
                    worldToMinimapX(left, size, marker.worldX(), centerWorldX, pixelsPerBlock),
                    worldToMinimapY(top, size, marker.worldZ(), centerWorldZ, pixelsPerBlock)
                );
            }
        }

        if (MapPathConfig.CLIENT.showWaypoints()) {
            for (WaypointStore.Waypoint waypoint : worldMapManager.waypoints(minecraft)) {
                renderMarker(
                    guiGraphics,
                    BANNER_ICON_TEXTURES.get(waypoint.icon()),
                    worldToMinimapX(left, size, waypoint.worldX(), centerWorldX, pixelsPerBlock),
                    worldToMinimapY(top, size, waypoint.worldZ(), centerWorldZ, pixelsPerBlock)
                );
            }
        }
    }

    private static void renderMarker(GuiGraphics guiGraphics, ResourceLocation texture, float centerX, float centerY) {
        if (texture == null) {
            return;
        }

        guiGraphics.blit(
            texture,
            Mth.floor(centerX - MARKER_SIZE / 2.0F),
            Mth.floor(centerY - MARKER_SIZE / 2.0F),
            MARKER_SIZE,
            MARKER_SIZE,
            0.0F,
            0.0F,
            MARKER_TEXTURE_SIZE,
            MARKER_TEXTURE_SIZE,
            MARKER_TEXTURE_SIZE,
            MARKER_TEXTURE_SIZE
        );
    }

    private static void renderPlayer(GuiGraphics guiGraphics, Minecraft minecraft, float centerX, float centerY, float partialTick) {
        float playerYaw = Mth.rotLerp(partialTick, minecraft.player.yRotO, minecraft.player.getYRot());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0.0F);
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
    }

    private static float worldToMinimapX(int left, int size, double worldX, double centerWorldX, double pixelsPerBlock) {
        return (float)(left + size / 2.0D + (worldX - centerWorldX) * pixelsPerBlock);
    }

    private static float worldToMinimapY(int top, int size, double worldZ, double centerWorldZ, double pixelsPerBlock) {
        return (float)(top + size / 2.0D + (worldZ - centerWorldZ) * pixelsPerBlock);
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
