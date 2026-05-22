package de.maax.mappath.client;

import net.minecraft.core.BlockPos;

import java.util.List;

record RoutePoint(int worldX, int worldY, int worldZ, SegmentType segmentType, List<BlockPos> placementBlocks) {
    RoutePoint {
        placementBlocks = List.copyOf(placementBlocks);
    }

    RoutePoint(int worldX, int worldY, int worldZ) {
        this(worldX, worldY, worldZ, SegmentType.WALK, List.of());
    }

    double distanceToSqr(double x, double y, double z) {
        double dx = this.worldX + 0.5D - x;
        double dy = this.worldY - y;
        double dz = this.worldZ + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    boolean requiresPlacement() {
        return !this.placementBlocks.isEmpty();
    }

    enum SegmentType {
        WALK,
        BRIDGE,
        STAIR_BUILD,
        PILLAR_BUILD
    }
}
