package de.maax.mappath.client;

import de.maax.mappath.BannerIconType;
import de.maax.mappath.MapPath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HighlightedTargetRenderer {
    private static final double HIDE_DISTANCE_BLOCKS = 5.0D;
    private static final double FADE_START_DISTANCE_BLOCKS = 64.0D;
    private static final double FADE_END_DISTANCE_BLOCKS = 512.0D;
    private static final double WAYPOINT_LABEL_Y_OFFSET = 1.5D;
    private static final double STRUCTURE_LABEL_Y_OFFSET = 1.5D;
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 1;
    private static final int TEXT_PADDING_X = 2;
    private static final int TEXT_PADDING_Y = 1;
    private static final int TEXT_ROW_GAP = 1;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_BACKGROUND_COLOR = 0x88000000;
    private static final float MAX_DISTANCE_SCALE = 0.85F;
    private static final float MIN_DISTANCE_SCALE = 0.45F;
    private static final float MIN_GUI_SCALE_ADJUSTED_MAX_SCALE = 0.65F;
    private static final float GUI_SCALE_SIZE_STEP = 0.06F;
    private static final List<ProjectedLabel> PROJECTED_LABELS = new ArrayList<>();

    private HighlightedTargetRenderer() {
    }

    static void render(RenderLevelStageEvent event, WorldMapManager worldMapManager) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        PROJECTED_LABELS.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
            || minecraft.player == null
            || minecraft.screen instanceof WorldMapScreen
            || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        for (WaypointStore.Waypoint waypoint : worldMapManager.waypoints(minecraft)) {
            if (waypoint.highlighted()) {
                projectLabel(
                    minecraft,
                    event.getModelViewMatrix(),
                    event.getProjectionMatrix(),
                    cameraPosition,
                    waypoint.name(),
                    bannerTexture(waypoint.icon()),
                    waypoint.worldX() + 0.5D,
                    waypoint.worldY() + WAYPOINT_LABEL_Y_OFFSET,
                    waypoint.worldZ() + 0.5D
                );
            }
        }

        for (StructureMarkerStore.Marker marker : worldMapManager.structureMarkers(minecraft)) {
            if (marker.highlighted()) {
                int worldY = marker.worldY() == Integer.MIN_VALUE
                    ? worldMapManager.defaultWaypointY(minecraft, marker.worldX(), marker.worldZ())
                    : marker.worldY();
                projectLabel(
                    minecraft,
                    event.getModelViewMatrix(),
                    event.getProjectionMatrix(),
                    cameraPosition,
                    markerLabel(marker),
                    structureTexture(marker),
                    marker.worldX() + 0.5D,
                    worldY + STRUCTURE_LABEL_Y_OFFSET,
                    marker.worldZ() + 0.5D
                );
            }
        }
    }

    static void renderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
            || minecraft.player == null
            || minecraft.screen instanceof WorldMapScreen
            || minecraft.getDebugOverlay().showDebugScreen()
            || PROJECTED_LABELS.isEmpty()) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        for (ProjectedLabel label : PROJECTED_LABELS) {
            renderProjectedLabel(minecraft, guiGraphics, label);
        }
    }

    private static void projectLabel(
        Minecraft minecraft,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix,
        Vec3 cameraPosition,
        String name,
        ResourceLocation iconTexture,
        double worldX,
        double worldY,
        double worldZ
    ) {
        double distance = minecraft.player.distanceToSqr(worldX, worldY, worldZ);
        if (distance < HIDE_DISTANCE_BLOCKS * HIDE_DISTANCE_BLOCKS) {
            return;
        }

        Vector4f projected = new Vector4f(
            (float)(worldX - cameraPosition.x),
            (float)(worldY - cameraPosition.y),
            (float)(worldZ - cameraPosition.z),
            1.0F
        );
        projected.mul(modelViewMatrix);
        projected.mul(projectionMatrix);
        if (projected.w() <= 0.0F) {
            return;
        }

        float normalizedX = projected.x() / projected.w();
        float normalizedY = projected.y() / projected.w();
        if (normalizedX < -1.25F || normalizedX > 1.25F || normalizedY < -1.25F || normalizedY > 1.25F) {
            return;
        }

        double blockDistance = Math.sqrt(distance);
        float distanceFade = distanceFade(blockDistance);
        float scale = Mth.lerp(distanceFade, maxScale(minecraft), MIN_DISTANCE_SCALE);
        String distanceLabel = String.format(Locale.GERMANY, "%.1f m", blockDistance);
        float screenX = (normalizedX * 0.5F + 0.5F) * minecraft.getWindow().getGuiScaledWidth();
        float screenY = (0.5F - normalizedY * 0.5F) * minecraft.getWindow().getGuiScaledHeight();
        PROJECTED_LABELS.add(new ProjectedLabel(Component.literal(name), Component.literal(distanceLabel), iconTexture, screenX, screenY, scale));
    }

    private static void renderProjectedLabel(Minecraft minecraft, GuiGraphics guiGraphics, ProjectedLabel label) {
        int top = 0;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(label.screenX(), label.screenY(), 0.0F);
        guiGraphics.pose().scale(label.scale(), label.scale(), 1.0F);

        if (label.iconTexture() != null) {
            guiGraphics.blit(
                label.iconTexture(),
                Mth.floor(-ICON_SIZE / 2.0F),
                top,
                ICON_SIZE,
                ICON_SIZE,
                0.0F,
                0.0F,
                64,
                64,
                64,
                64
            );
        }

        int nameTop = top + (label.iconTexture() == null ? 0 : ICON_SIZE + ICON_GAP);
        int distanceTop = nameTop + minecraft.font.lineHeight + TEXT_PADDING_Y * 2 + TEXT_ROW_GAP;
        renderTextRow(minecraft, guiGraphics, label.name(), nameTop);
        renderTextRow(minecraft, guiGraphics, label.distance(), distanceTop);
        guiGraphics.pose().popPose();
    }

    private static void renderTextRow(Minecraft minecraft, GuiGraphics guiGraphics, Component text, int top) {
        int textWidth = minecraft.font.width(text);
        int left = Mth.floor(-textWidth / 2.0F) - TEXT_PADDING_X;
        int right = left + textWidth + TEXT_PADDING_X * 2;
        int bottom = top + minecraft.font.lineHeight + TEXT_PADDING_Y * 2;
        guiGraphics.fill(left, top, right, bottom, TEXT_BACKGROUND_COLOR);
        guiGraphics.drawString(minecraft.font, text, left + TEXT_PADDING_X, top + TEXT_PADDING_Y, TEXT_COLOR, true);
    }

    private static float distanceFade(double distance) {
        if (distance <= FADE_START_DISTANCE_BLOCKS) {
            return 0.0F;
        }
        if (distance >= FADE_END_DISTANCE_BLOCKS) {
            return 1.0F;
        }

        return (float)((distance - FADE_START_DISTANCE_BLOCKS) / (FADE_END_DISTANCE_BLOCKS - FADE_START_DISTANCE_BLOCKS));
    }

    private static float maxScale(Minecraft minecraft) {
        double guiScale = minecraft.getWindow().getGuiScale();
        float guiScaleReduction = (float)Math.max(0.0D, guiScale - 1.0D) * GUI_SCALE_SIZE_STEP;
        return Mth.clamp(MAX_DISTANCE_SCALE - guiScaleReduction, MIN_GUI_SCALE_ADJUSTED_MAX_SCALE, MAX_DISTANCE_SCALE);
    }

    private static ResourceLocation bannerTexture(BannerIconType icon) {
        return ResourceLocation.fromNamespaceAndPath(MapPath.MODID, icon.texturePath());
    }

    private static ResourceLocation structureTexture(StructureMarkerStore.Marker marker) {
        return ResourceLocation.fromNamespaceAndPath(MapPath.MODID, marker.type().texturePath());
    }

    private static String markerLabel(StructureMarkerStore.Marker marker) {
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

    private record ProjectedLabel(Component name, Component distance, ResourceLocation iconTexture, float screenX, float screenY, float scale) {
    }
}
