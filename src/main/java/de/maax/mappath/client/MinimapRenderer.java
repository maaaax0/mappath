package de.maax.mappath.client;

import com.mojang.math.Axis;
import de.maax.mappath.BannerIconType;
import de.maax.mappath.EntityMarkerTarget;
import de.maax.mappath.MapPath;
import de.maax.mappath.MapPathConfig;
import de.maax.mappath.StructureMarkerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.EnumMap;
import java.util.Map;

final class MinimapRenderer {
    private static final int MARGIN = 8;
    private static final int COORDINATE_LABEL_COLOR = 0xFFFFFFFF;
    private static final int COORDINATE_LABEL_SHADOW_COLOR = 0x99000000;
    private static final int PLAYER_MARKER_SIZE = 8;
    private static final int MARKER_SIZE = Math.round(PLAYER_MARKER_SIZE * 4.0F / 5.0F);
    private static final int MOB_MARKER_SIZE = MARKER_SIZE;
    private static final int PLAYER_MARKER_TEXTURE_SIZE = 64;
    private static final int MARKER_TEXTURE_SIZE = 64;
    private static final double MOB_MARKER_RENDER_DISTANCE_BLOCKS = 96.0D;
    private static final double MOB_MARKER_RENDER_DISTANCE_SQR = MOB_MARKER_RENDER_DISTANCE_BLOCKS * MOB_MARKER_RENDER_DISTANCE_BLOCKS;
    private static final double EXTENDED_ENTITY_MARKER_RENDER_DISTANCE_BLOCKS = MOB_MARKER_RENDER_DISTANCE_BLOCKS * 2.0D;
    private static final double EXTENDED_ENTITY_MARKER_RENDER_DISTANCE_SQR =
        EXTENDED_ENTITY_MARKER_RENDER_DISTANCE_BLOCKS * EXTENDED_ENTITY_MARKER_RENDER_DISTANCE_BLOCKS;
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
        double centerWorldY = Mth.lerp(partialTick, minecraft.player.yo, minecraft.player.getY());
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
        renderMarkers(guiGraphics, worldMapManager, minecraft, contentLeft, contentTop, innerSize, centerWorldX, centerWorldZ, pixelsPerBlock, partialTick);
        renderPlayer(guiGraphics, minecraft, contentLeft + innerSize / 2.0F, contentTop + innerSize / 2.0F, partialTick);
        guiGraphics.disableScissor();

        if (MapPathConfig.CLIENT.showMinimapCoordinates()) {
            renderCoordinateLabel(guiGraphics, minecraft, left, top + frameSize + 2, frameSize, centerWorldX, centerWorldY, centerWorldZ);
        }
    }

    private static void renderCoordinateLabel(
        GuiGraphics guiGraphics,
        Minecraft minecraft,
        int left,
        int top,
        int width,
        double worldX,
        double worldY,
        double worldZ
    ) {
        String label = "X: " + Mth.floor(worldX) + " / Y: " + Mth.floor(worldY) + " / Z: " + Mth.floor(worldZ);
        int labelWidth = minecraft.font.width(label);
        int x = left + (width - labelWidth) / 2;
        guiGraphics.fill(x - 3, top - 1, x + labelWidth + 3, top + minecraft.font.lineHeight + 1, COORDINATE_LABEL_SHADOW_COLOR);
        guiGraphics.drawString(minecraft.font, label, x, top, COORDINATE_LABEL_COLOR, false);
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
        double pixelsPerBlock,
        float partialTick
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
                float markerX = worldToMinimapX(left, size, waypoint.worldX(), centerWorldX, pixelsPerBlock);
                float markerY = worldToMinimapY(top, size, waypoint.worldZ(), centerWorldZ, pixelsPerBlock);
                renderMarker(
                    guiGraphics,
                    BANNER_ICON_TEXTURES.get(waypoint.icon()),
                    clampMarkerToMinimap(markerX, left, size, MARKER_SIZE),
                    clampMarkerToMinimap(markerY, top, size, MARKER_SIZE)
                );
            }
        }

        if (MapPathConfig.CLIENT.showAnyEntityMarkers(EntityMarkerTarget.MINIMAP)) {
            renderMobMarkers(guiGraphics, minecraft, left, top, size, centerWorldX, centerWorldZ, pixelsPerBlock, partialTick);
        }
    }

    private static void renderMobMarkers(
        GuiGraphics guiGraphics,
        Minecraft minecraft,
        int left,
        int top,
        int size,
        double centerWorldX,
        double centerWorldZ,
        double pixelsPerBlock,
        float partialTick
    ) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!EntityMapMarkerRenderer.shouldRenderEntityMarker(entity, minecraft, EntityMarkerTarget.MINIMAP)
                || !EntityMapMarkerRenderer.isWithinVerticalRange(entity, minecraft, partialTick)
                || minecraft.player.distanceToSqr(entity) > entityMarkerRenderDistanceSqr(entity)) {
                continue;
            }

            double worldX = Mth.lerp(partialTick, entity.xo, entity.getX());
            double worldZ = Mth.lerp(partialTick, entity.zo, entity.getZ());
            float markerX = worldToMinimapX(left, size, worldX, centerWorldX, pixelsPerBlock);
            float markerY = worldToMinimapY(top, size, worldZ, centerWorldZ, pixelsPerBlock);
            EntityMapMarkerRenderer.render(
                guiGraphics,
                minecraft,
                entity,
                EntityMarkerTarget.MINIMAP,
                clampMarkerToMinimap(markerX, left, size, MOB_MARKER_SIZE),
                clampMarkerToMinimap(markerY, top, size, MOB_MARKER_SIZE),
                MOB_MARKER_SIZE
            );
        }
    }

    private static double entityMarkerRenderDistanceSqr(Entity entity) {
        return EntityMapMarkerRenderer.hasExtendedRenderRange(entity)
            ? EXTENDED_ENTITY_MARKER_RENDER_DISTANCE_SQR
            : MOB_MARKER_RENDER_DISTANCE_SQR;
    }

    private static float clampMarkerToMinimap(float coordinate, int start, int size, int markerSize) {
        float markerOverlap = markerSize / 4.0F;
        return Mth.clamp(coordinate, start + markerOverlap, start + size - markerOverlap);
    }

    private static void renderMarker(GuiGraphics guiGraphics, ResourceLocation texture, float centerX, float centerY) {
        if (texture == null) {
            return;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX - MARKER_SIZE / 2.0F, centerY - MARKER_SIZE / 2.0F, 0.0F);
        guiGraphics.blit(
            texture,
            0,
            0,
            MARKER_SIZE,
            MARKER_SIZE,
            0.0F,
            0.0F,
            MARKER_TEXTURE_SIZE,
            MARKER_TEXTURE_SIZE,
            MARKER_TEXTURE_SIZE,
            MARKER_TEXTURE_SIZE
        );
        guiGraphics.pose().popPose();
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
