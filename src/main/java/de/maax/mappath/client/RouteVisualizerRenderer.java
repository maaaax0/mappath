package de.maax.mappath.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.maax.mappath.MapPathConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class RouteVisualizerRenderer {
    static final double TRAIL_Y_OFFSET = 2.25D;

    private static final double PULSE_LEAD_DISTANCE_BLOCKS = 28.0D;
    private static final double PULSE_TAIL_LENGTH_BLOCKS = 8.0D;
    private static final double PULSE_TAIL_SLICE_BLOCKS = 0.75D;
    private static final double TRAIL_OUTER_WIDTH_BLOCKS = 0.42D;
    private static final double TRAIL_INNER_WIDTH_BLOCKS = 0.24D;
    private static final double TRAIL_PLAYER_CLEARANCE_BLOCKS = 2.0D;
    private static final double TRAIL_VERTICAL_START_BLEND_BLOCKS = 6.0D;

    private static final double PLACEMENT_RENDER_DISTANCE_SQR = 64.0D * 64.0D;
    private static final long PLACEMENT_CACHE_DURATION_NANOS = 200_000_000L;
    private static final int MAX_PLACEMENT_MARKERS_PER_FRAME = 96;

    private static final int ROUTE_RED = 255;
    private static final int ROUTE_GREEN = 255;
    private static final int ROUTE_BLUE = 255;

    private static final int BUILD_RED = 255;
    private static final int BUILD_GREEN = 190;
    private static final int BUILD_BLUE = 64;
    private static final int BUILD_ALPHA = 230;

    private static final int TARGET_RED = 255;
    private static final int TARGET_GREEN = 72;
    private static final int TARGET_BLUE = 96;
    private static final int TARGET_ALPHA = 255;
    private static final double STRUCTURE_TARGET_Y_OFFSET = 0.08D;
    private static final double STRUCTURE_TARGET_PILLAR_HEIGHT = 2.0D;

    private static final int TAIL_MIN_ALPHA = 35;
    private static final int TAIL_MAX_ALPHA = 210;
    private static final int HUD_BACKGROUND_COLOR = 0x99000000;
    private static final int HUD_TEXT_COLOR = 0xFFEAFBFF;

    private static ActiveRoute finalPulseRoute;
    private static long finalPulseStartNanos;
    private static boolean finalPulseFinished;
    private static final PlacementRenderCache placementRenderCache = new PlacementRenderCache();

    private RouteVisualizerRenderer() {
    }

    static void render(RenderLevelStageEvent event, WorldMapManager worldMapManager) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !MapPathConfig.CLIENT.showRouteVisualizer()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ActiveRoute route = worldMapManager.activeRoute();

        if (minecraft.level == null
            || minecraft.player == null
            || minecraft.screen instanceof WorldMapScreen
            || minecraft.getDebugOverlay().showDebugScreen()
            || route == null
            || route.isEmpty()) {
            return;
        }

        List<RoutePoint> points = route.points();

        if (points.size() < 2) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());

        Vec3 pulseStartPosition = route.pulseStartPosition(new Vec3(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ()));

        renderPulseTrail(pose, cameraPosition, route, pulseStartPosition, points, route.nextPointIndex());
        renderPlacementMarkers(lineConsumer, pose, cameraPosition, route);

        if (MapPathConfig.CLIENT.showRouteTargetMarker()) {
            renderTargetMarker(lineConsumer, pose, cameraPosition, route.target());
        }

        bufferSource.endBatch(RenderType.lines());
    }

    static void renderGui(RenderGuiEvent.Post event, WorldMapManager worldMapManager) {
        Minecraft minecraft = Minecraft.getInstance();
        ActiveRoute route = worldMapManager.activeRoute();

        if (!MapPathConfig.CLIENT.showRouteVisualizer()
            || minecraft.level == null
            || minecraft.player == null
            || minecraft.screen instanceof WorldMapScreen
            || minecraft.getDebugOverlay().showDebugScreen()
            || route == null) {
            return;
        }

        double distance = Math.sqrt(distanceToTargetSqr(route, minecraft.player.position()));
        Component title = Component.translatable("gui.mappath.route_hud", route.target().label(), String.format(Locale.GERMANY, "%.1f", distance));
        Component cancel = Component.translatable("gui.mappath.route_cancel_hint");

        GuiGraphics guiGraphics = event.getGuiGraphics();

        int width = Math.max(minecraft.font.width(title), minecraft.font.width(cancel));
        int left = 8;
        int top = 8;
        int right = left + width + 10;
        int bottom = top + minecraft.font.lineHeight * 2 + 8;

        guiGraphics.fill(left, top, right, bottom, HUD_BACKGROUND_COLOR);
        guiGraphics.drawString(minecraft.font, title, left + 5, top + 4, HUD_TEXT_COLOR, false);
        guiGraphics.drawString(minecraft.font, cancel, left + 5, top + minecraft.font.lineHeight + 5, HUD_TEXT_COLOR, false);
    }

    private static void renderPulseTrail(
        PoseStack.Pose pose,
        Vec3 cameraPosition,
        ActiveRoute route,
        Vec3 playerPosition,
        List<RoutePoint> points,
        int nextPointIndex
    ) {
        int firstRoutePoint = Mth.clamp(nextPointIndex, 0, points.size() - 1);
        double totalLength = routeLength(playerPosition, points, firstRoutePoint);

        if (totalLength <= 0.0D) {
            return;
        }

        try (ByteBufferBuilder trailBuffer = new ByteBufferBuilder(1024)) {
            BufferBuilder trailBuilder = new BufferBuilder(trailBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            double headDistance = pulseHeadDistance(route, totalLength);
            double tailStartDistance = Math.max(TRAIL_PLAYER_CLEARANCE_BLOCKS, headDistance - PULSE_TAIL_LENGTH_BLOCKS);

            if (headDistance <= tailStartDistance) {
                return;
            }

            int slices = Math.max(1, Mth.ceil((headDistance - tailStartDistance) / PULSE_TAIL_SLICE_BLOCKS));
            List<TrailSample> samples = buildTrailSamples(playerPosition, points, firstRoutePoint, tailStartDistance, headDistance, slices, cameraPosition);

            for (int index = 1; index < samples.size(); index++) {
                addTrailSegment(trailBuilder, pose, cameraPosition, samples.get(index - 1), samples.get(index), TRAIL_OUTER_WIDTH_BLOCKS, 0.82D);
            }

            for (int index = 1; index < samples.size(); index++) {
                addTrailSegment(trailBuilder, pose, cameraPosition, samples.get(index - 1), samples.get(index), TRAIL_INNER_WIDTH_BLOCKS, 1.0D);
            }

            MeshData meshData = trailBuilder.build();

            if (meshData != null) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                BufferUploader.drawWithShader(meshData);
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
            }
        }
    }

    private static double pulseHeadDistance(ActiveRoute route, double totalLength) {
        if (totalLength <= TRAIL_PLAYER_CLEARANCE_BLOCKS) {
            return 0.0D;
        }

        if (totalLength <= PULSE_LEAD_DISTANCE_BLOCKS + TRAIL_PLAYER_CLEARANCE_BLOCKS) {
            if (route != finalPulseRoute) {
                finalPulseRoute = route;
                finalPulseStartNanos = System.nanoTime();
                finalPulseFinished = false;
            }

            if (finalPulseFinished) {
                return 0.0D;
            }

            double elapsedSeconds = (System.nanoTime() - finalPulseStartNanos) / 1_000_000_000.0D;
            double headDistance = TRAIL_PLAYER_CLEARANCE_BLOCKS + elapsedSeconds * MapPathConfig.CLIENT.routeTrailMaxSpeed();

            if (headDistance >= totalLength) {
                finalPulseFinished = true;
                return 0.0D;
            }

            return headDistance;
        }

        if (route == finalPulseRoute) {
            finalPulseRoute = null;
            finalPulseStartNanos = 0L;
            finalPulseFinished = false;
        }

        double elapsedSeconds = System.nanoTime() / 1_000_000_000.0D;
        return TRAIL_PLAYER_CLEARANCE_BLOCKS + (elapsedSeconds * MapPathConfig.CLIENT.routeTrailMaxSpeed()) % PULSE_LEAD_DISTANCE_BLOCKS;
    }

    private static double routeLength(Vec3 playerPosition, List<RoutePoint> points, int firstRoutePoint) {
        double length = 0.0D;

        double previousX = playerPosition.x;
        double previousY = playerPosition.y;
        double previousZ = playerPosition.z;

        for (int index = firstRoutePoint; index < points.size(); index++) {
            RoutePoint point = points.get(index);

            double currentX = point.worldX() + 0.5D;
            double currentY = point.worldY() + TRAIL_Y_OFFSET;
            double currentZ = point.worldZ() + 0.5D;

            length += distance(previousX, previousY, previousZ, currentX, currentY, currentZ);

            previousX = currentX;
            previousY = currentY;
            previousZ = currentZ;
        }

        return length;
    }

    private static RouteSample sampleRoute(Vec3 playerPosition, List<RoutePoint> points, int firstRoutePoint, double targetDistance) {
        double previousX = playerPosition.x;
        double previousY = playerPosition.y;
        double previousZ = playerPosition.z;
        double remainingDistance = targetDistance;

        for (int index = firstRoutePoint; index < points.size(); index++) {
            RoutePoint point = points.get(index);

            double currentX = point.worldX() + 0.5D;
            double currentY = point.worldY() + TRAIL_Y_OFFSET;
            double currentZ = point.worldZ() + 0.5D;

            double segmentLength = distance(previousX, previousY, previousZ, currentX, currentY, currentZ);

            if (segmentLength > 0.0D && remainingDistance <= segmentLength) {
                double t = remainingDistance / segmentLength;

                return new RouteSample(
                    Mth.lerp(t, previousX, currentX),
                    Mth.lerp(t, previousY, currentY),
                    Mth.lerp(t, previousZ, currentZ)
                );
            }

            remainingDistance -= segmentLength;

            previousX = currentX;
            previousY = currentY;
            previousZ = currentZ;
        }

        return new RouteSample(previousX, previousY, previousZ);
    }

    private static List<TrailSample> buildTrailSamples(
        Vec3 playerPosition,
        List<RoutePoint> points,
        int firstRoutePoint,
        double tailStartDistance,
        double headDistance,
        int slices,
        Vec3 cameraPosition
    ) {
        List<RouteSample> positions = new ArrayList<>(slices + 1);

        for (int index = 0; index <= slices; index++) {
            double progress = index / (double)slices;
            double distance = Mth.lerp(progress, tailStartDistance, headDistance);

            positions.add(sampleRoute(playerPosition, points, firstRoutePoint, distance));
        }

        List<TrailSample> samples = new ArrayList<>(positions.size());
        Vec3 previousSide = null;

        for (int index = 0; index < positions.size(); index++) {
            RouteSample position = positions.get(index);
            RouteSample previous = positions.get(Math.max(0, index - 1));
            RouteSample next = positions.get(Math.min(positions.size() - 1, index + 1));
            Vec3 tangent = new Vec3(next.x() - previous.x(), next.y() - previous.y(), next.z() - previous.z());
            double progress = index / (double)slices;
            double distance = Mth.lerp(progress, tailStartDistance, headDistance);
            Vec3 side = trailSide(tangent, position, cameraPosition, distance);

            if (previousSide != null && previousSide.dot(side) < 0.0D) {
                side = side.scale(-1.0D);
            }

            samples.add(new TrailSample(position, side, progress));
            previousSide = side;
        }

        return samples;
    }

    private static Vec3 trailSide(Vec3 tangent, RouteSample position, Vec3 cameraPosition, double distanceFromPlayer) {
        Vec3 safeTangent = tangent.lengthSqr() > 0.000001D ? tangent : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 toCamera = cameraPosition.subtract(position.x(), position.y(), position.z());

        if (toCamera.lengthSqr() <= 0.000001D) {
            toCamera = new Vec3(0.0D, 1.0D, 0.0D);
        }

        Vec3 side = safeTangent.cross(toCamera).normalize();

        if (side.lengthSqr() <= 0.000001D) {
            side = safeTangent.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        }

        if (side.lengthSqr() <= 0.000001D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }

        double verticalBlend = verticalStartBlend(distanceFromPlayer);
        if (verticalBlend > 0.0D) {
            Vec3 verticalSide = verticalTrailSide(safeTangent, side);
            side = verticalSide.scale(verticalBlend).add(side.scale(1.0D - verticalBlend)).normalize();
        }

        return side;
    }

    private static double verticalStartBlend(double distanceFromPlayer) {
        double progress = Mth.clamp((distanceFromPlayer - TRAIL_PLAYER_CLEARANCE_BLOCKS) / TRAIL_VERTICAL_START_BLEND_BLOCKS, 0.0D, 1.0D);
        double eased = progress * progress * (3.0D - 2.0D * progress);

        return 1.0D - eased;
    }

    private static Vec3 verticalTrailSide(Vec3 tangent, Vec3 fallbackSide) {
        Vec3 normalizedTangent = tangent.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 verticalSide = up.subtract(normalizedTangent.scale(up.dot(normalizedTangent)));

        if (verticalSide.lengthSqr() <= 0.000001D) {
            return fallbackSide;
        }

        return verticalSide.normalize();
    }

    private static void addTrailSegment(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        Vec3 cameraPosition,
        TrailSample start,
        TrailSample end,
        double maxWidth,
        double alphaMultiplier
    ) {
        RouteSample startPosition = start.position();
        RouteSample endPosition = end.position();
        Vec3 direction = new Vec3(endPosition.x() - startPosition.x(), endPosition.y() - startPosition.y(), endPosition.z() - startPosition.z());

        if (direction.lengthSqr() <= 0.000001D) {
            return;
        }

        double startHalfWidth = trailWidth(start.progress(), maxWidth) * 0.5D;
        double endHalfWidth = trailWidth(end.progress(), maxWidth) * 0.5D;
        int startAlpha = trailAlpha(start.progress(), alphaMultiplier);
        int endAlpha = trailAlpha(end.progress(), alphaMultiplier);
        Vec3 startSide = start.side();
        Vec3 endSide = end.side();

        addTrailVertex(consumer, pose, cameraPosition, startPosition.x() + startSide.x * startHalfWidth, startPosition.y() + startSide.y * startHalfWidth, startPosition.z() + startSide.z * startHalfWidth, startAlpha);
        addTrailVertex(consumer, pose, cameraPosition, startPosition.x() - startSide.x * startHalfWidth, startPosition.y() - startSide.y * startHalfWidth, startPosition.z() - startSide.z * startHalfWidth, startAlpha);
        addTrailVertex(consumer, pose, cameraPosition, endPosition.x() - endSide.x * endHalfWidth, endPosition.y() - endSide.y * endHalfWidth, endPosition.z() - endSide.z * endHalfWidth, endAlpha);
        addTrailVertex(consumer, pose, cameraPosition, endPosition.x() + endSide.x * endHalfWidth, endPosition.y() + endSide.y * endHalfWidth, endPosition.z() + endSide.z * endHalfWidth, endAlpha);
    }

    private static double trailWidth(double progress, double maxWidth) {
        return Math.sqrt(Mth.clamp(progress, 0.0D, 1.0D)) * maxWidth;
    }

    private static int trailAlpha(double progress, double multiplier) {
        double alphaProgress = Math.cbrt(Math.max(0.0D, Mth.clamp(progress, 0.0D, 1.0D) - 0.1D));
        return Mth.clamp((int)(Mth.lerp(alphaProgress, TAIL_MIN_ALPHA, TAIL_MAX_ALPHA) * multiplier), 0, TAIL_MAX_ALPHA);
    }

    private static void addTrailVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 cameraPosition, double x, double y, double z, int alpha) {
        consumer.addVertex(pose, (float)(x - cameraPosition.x), (float)(y - cameraPosition.y), (float)(z - cameraPosition.z))
            .setColor(ROUTE_RED, ROUTE_GREEN, ROUTE_BLUE, alpha);
    }

    private static void renderPlacementMarkers(VertexConsumer consumer, PoseStack.Pose pose, Vec3 cameraPosition, ActiveRoute route) {
        List<BlockPos> visiblePlacements = placementRenderCache.visiblePlacements(route, cameraPosition);

        for (BlockPos block : visiblePlacements) {
            addPlacementBlockOutline(consumer, pose, cameraPosition, block);
        }
    }

    private static double distanceToBlockCenterSqr(BlockPos block, Vec3 position) {
        double dx = block.getX() + 0.5D - position.x;
        double dy = block.getY() + 0.5D - position.y;
        double dz = block.getZ() + 0.5D - position.z;

        return dx * dx + dy * dy + dz * dz;
    }

    private static void addPlacementBlockOutline(VertexConsumer consumer, PoseStack.Pose pose, Vec3 cameraPosition, BlockPos block) {
        double minX = block.getX();
        double minY = block.getY();
        double minZ = block.getZ();

        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;

        addBuildLine(consumer, pose, cameraPosition, minX, minY, minZ, maxX, minY, minZ);
        addBuildLine(consumer, pose, cameraPosition, maxX, minY, minZ, maxX, minY, maxZ);
        addBuildLine(consumer, pose, cameraPosition, maxX, minY, maxZ, minX, minY, maxZ);
        addBuildLine(consumer, pose, cameraPosition, minX, minY, maxZ, minX, minY, minZ);

        addBuildLine(consumer, pose, cameraPosition, minX, maxY, minZ, maxX, maxY, minZ);
        addBuildLine(consumer, pose, cameraPosition, maxX, maxY, minZ, maxX, maxY, maxZ);
        addBuildLine(consumer, pose, cameraPosition, maxX, maxY, maxZ, minX, maxY, maxZ);
        addBuildLine(consumer, pose, cameraPosition, minX, maxY, maxZ, minX, maxY, minZ);

        addBuildLine(consumer, pose, cameraPosition, minX, minY, minZ, minX, maxY, minZ);
        addBuildLine(consumer, pose, cameraPosition, maxX, minY, minZ, maxX, maxY, minZ);
        addBuildLine(consumer, pose, cameraPosition, maxX, minY, maxZ, maxX, maxY, maxZ);
        addBuildLine(consumer, pose, cameraPosition, minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void renderTargetMarker(VertexConsumer consumer, PoseStack.Pose pose, Vec3 cameraPosition, RouteTarget target) {
        if (target.hasBounds()) {
            renderStructureTargetMarker(consumer, pose, cameraPosition, target);
            return;
        }

        double centerX = target.worldX() + 0.5D;
        double minY = target.worldY();
        double centerY = minY + 1.0D;
        double maxY = minY + 2.0D;
        double centerZ = target.worldZ() + 0.5D;
        double radius = 0.65D;

        addTargetLine(consumer, pose, cameraPosition, centerX, minY, centerZ, centerX, maxY, centerZ);

        addTargetLine(consumer, pose, cameraPosition, centerX - radius, centerY, centerZ, centerX, maxY, centerZ);
        addTargetLine(consumer, pose, cameraPosition, centerX, maxY, centerZ, centerX + radius, centerY, centerZ);
        addTargetLine(consumer, pose, cameraPosition, centerX + radius, centerY, centerZ, centerX, minY, centerZ);
        addTargetLine(consumer, pose, cameraPosition, centerX, minY, centerZ, centerX - radius, centerY, centerZ);

        addTargetLine(consumer, pose, cameraPosition, centerX, centerY, centerZ - radius, centerX, maxY, centerZ);
        addTargetLine(consumer, pose, cameraPosition, centerX, maxY, centerZ, centerX, centerY, centerZ + radius);
        addTargetLine(consumer, pose, cameraPosition, centerX, centerY, centerZ + radius, centerX, minY, centerZ);
        addTargetLine(consumer, pose, cameraPosition, centerX, minY, centerZ, centerX, centerY, centerZ - radius);
    }

    private static void renderStructureTargetMarker(VertexConsumer consumer, PoseStack.Pose pose, Vec3 cameraPosition, RouteTarget target) {
        double minX = target.minWorldX();
        double maxX = target.maxWorldX() + 1.0D;
        double minZ = target.minWorldZ();
        double maxZ = target.maxWorldZ() + 1.0D;
        double baseY = target.worldY() + STRUCTURE_TARGET_Y_OFFSET;
        double topY = baseY + STRUCTURE_TARGET_PILLAR_HEIGHT;

        addTargetLine(consumer, pose, cameraPosition, minX, baseY, minZ, maxX, baseY, minZ);
        addTargetLine(consumer, pose, cameraPosition, maxX, baseY, minZ, maxX, baseY, maxZ);
        addTargetLine(consumer, pose, cameraPosition, maxX, baseY, maxZ, minX, baseY, maxZ);
        addTargetLine(consumer, pose, cameraPosition, minX, baseY, maxZ, minX, baseY, minZ);

        addTargetLine(consumer, pose, cameraPosition, minX, baseY, minZ, minX, topY, minZ);
        addTargetLine(consumer, pose, cameraPosition, maxX, baseY, minZ, maxX, topY, minZ);
        addTargetLine(consumer, pose, cameraPosition, maxX, baseY, maxZ, maxX, topY, maxZ);
        addTargetLine(consumer, pose, cameraPosition, minX, baseY, maxZ, minX, topY, maxZ);
    }

    private static double distance(double startX, double startY, double startZ, double endX, double endY, double endZ) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void addLine(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        Vec3 cameraPosition,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        int alpha
    ) {
        float normalX = (float)(endX - startX);
        float normalY = (float)(endY - startY);
        float normalZ = (float)(endZ - startZ);
        float normalLength = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);

        if (normalLength <= 0.0F) {
            return;
        }

        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;

        consumer.addVertex(pose, (float)(startX - cameraPosition.x), (float)(startY - cameraPosition.y), (float)(startZ - cameraPosition.z))
            .setColor(ROUTE_RED, ROUTE_GREEN, ROUTE_BLUE, alpha)
            .setNormal(pose, normalX, normalY, normalZ);

        consumer.addVertex(pose, (float)(endX - cameraPosition.x), (float)(endY - cameraPosition.y), (float)(endZ - cameraPosition.z))
            .setColor(ROUTE_RED, ROUTE_GREEN, ROUTE_BLUE, alpha)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void addBuildLine(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        Vec3 cameraPosition,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ
    ) {
        float normalX = (float)(endX - startX);
        float normalY = (float)(endY - startY);
        float normalZ = (float)(endZ - startZ);
        float normalLength = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);

        if (normalLength <= 0.0F) {
            return;
        }

        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;

        consumer.addVertex(pose, (float)(startX - cameraPosition.x), (float)(startY - cameraPosition.y), (float)(startZ - cameraPosition.z))
            .setColor(BUILD_RED, BUILD_GREEN, BUILD_BLUE, BUILD_ALPHA)
            .setNormal(pose, normalX, normalY, normalZ);

        consumer.addVertex(pose, (float)(endX - cameraPosition.x), (float)(endY - cameraPosition.y), (float)(endZ - cameraPosition.z))
            .setColor(BUILD_RED, BUILD_GREEN, BUILD_BLUE, BUILD_ALPHA)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void addTargetLine(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        Vec3 cameraPosition,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ
    ) {
        float normalX = (float)(endX - startX);
        float normalY = (float)(endY - startY);
        float normalZ = (float)(endZ - startZ);
        float normalLength = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);

        if (normalLength <= 0.0F) {
            return;
        }

        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;

        consumer.addVertex(pose, (float)(startX - cameraPosition.x), (float)(startY - cameraPosition.y), (float)(startZ - cameraPosition.z))
            .setColor(TARGET_RED, TARGET_GREEN, TARGET_BLUE, TARGET_ALPHA)
            .setNormal(pose, normalX, normalY, normalZ);

        consumer.addVertex(pose, (float)(endX - cameraPosition.x), (float)(endY - cameraPosition.y), (float)(endZ - cameraPosition.z))
            .setColor(TARGET_RED, TARGET_GREEN, TARGET_BLUE, TARGET_ALPHA)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    private static double distanceToTargetSqr(ActiveRoute route, Vec3 playerPosition) {
        if (route.target().hasBounds()) {
            double closestX = Mth.clamp(playerPosition.x, route.target().minWorldX(), route.target().maxWorldX() + 1.0D);
            double closestY = Mth.clamp(playerPosition.y, route.target().minWorldY(), route.target().maxWorldY() + 1.0D);
            double closestZ = Mth.clamp(playerPosition.z, route.target().minWorldZ(), route.target().maxWorldZ() + 1.0D);
            double dx = closestX - playerPosition.x;
            double dy = closestY - playerPosition.y;
            double dz = closestZ - playerPosition.z;
            return dx * dx + dy * dy + dz * dz;
        }

        double dx = route.target().worldX() + 0.5D - playerPosition.x;
        double dy = route.target().worldY() - playerPosition.y;
        double dz = route.target().worldZ() + 0.5D - playerPosition.z;

        return dx * dx + dy * dy + dz * dz;
    }

    private record RouteSample(double x, double y, double z) {
    }

    private record TrailSample(RouteSample position, Vec3 side, double progress) {
    }

    private record PlacementCandidate(BlockPos block, double distanceSqr) {
    }

    private static final class PlacementRenderCache {
        private ActiveRoute route;
        private int nextPointIndex = -1;
        private int cameraBlockX = Integer.MIN_VALUE;
        private int cameraBlockY = Integer.MIN_VALUE;
        private int cameraBlockZ = Integer.MIN_VALUE;
        private long updateTimeNanos;
        private List<BlockPos> visiblePlacements = List.of();

        private List<BlockPos> visiblePlacements(ActiveRoute route, Vec3 cameraPosition) {
            int currentCameraBlockX = Mth.floor(cameraPosition.x);
            int currentCameraBlockY = Mth.floor(cameraPosition.y);
            int currentCameraBlockZ = Mth.floor(cameraPosition.z);
            long now = System.nanoTime();

            if (this.route != route
                || this.nextPointIndex != route.nextPointIndex()
                || this.cameraBlockX != currentCameraBlockX
                || this.cameraBlockY != currentCameraBlockY
                || this.cameraBlockZ != currentCameraBlockZ
                || now - this.updateTimeNanos >= PLACEMENT_CACHE_DURATION_NANOS) {
                this.route = route;
                this.nextPointIndex = route.nextPointIndex();
                this.cameraBlockX = currentCameraBlockX;
                this.cameraBlockY = currentCameraBlockY;
                this.cameraBlockZ = currentCameraBlockZ;
                this.updateTimeNanos = now;
                this.visiblePlacements = buildVisiblePlacements(route, cameraPosition);
            }

            return this.visiblePlacements;
        }

        private static List<BlockPos> buildVisiblePlacements(ActiveRoute route, Vec3 cameraPosition) {
            Set<BlockPos> uniqueBlocks = new HashSet<>();
            List<PlacementCandidate> candidates = new ArrayList<>();
            List<RoutePoint> points = route.points();
            int firstPoint = Mth.clamp(route.nextPointIndex() - 1, 0, points.size() - 1);

            for (int pointIndex = firstPoint; pointIndex < points.size(); pointIndex++) {
                for (BlockPos block : points.get(pointIndex).placementBlocks()) {
                    if (uniqueBlocks.add(block)) {
                        double distanceSqr = distanceToBlockCenterSqr(block, cameraPosition);
                        if (distanceSqr <= PLACEMENT_RENDER_DISTANCE_SQR) {
                            candidates.add(new PlacementCandidate(block, distanceSqr));
                        }
                    }
                }
            }

            candidates.sort(Comparator.comparingDouble(PlacementCandidate::distanceSqr));

            int count = Math.min(candidates.size(), MAX_PLACEMENT_MARKERS_PER_FRAME);
            List<BlockPos> visibleBlocks = new ArrayList<>(count);

            for (int index = 0; index < count; index++) {
                visibleBlocks.add(candidates.get(index).block());
            }

            return List.copyOf(visibleBlocks);
        }
    }
}
