package de.maax.mappath.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

record RouteTarget(
    Type type,
    String label,
    ResourceLocation dimensionId,
    int worldX,
    int worldY,
    int worldZ,
    double arrivalRadius,
    int minWorldX,
    int minWorldY,
    int minWorldZ,
    int maxWorldX,
    int maxWorldY,
    int maxWorldZ
) {
    static RouteTarget point(Type type, String label, ResourceLocation dimensionId, int worldX, int worldY, int worldZ, double arrivalRadius) {
        return new RouteTarget(type, label, dimensionId, worldX, worldY, worldZ, arrivalRadius, worldX, worldY, worldZ, worldX, worldY, worldZ);
    }

    static RouteTarget structure(
        String label,
        ResourceLocation dimensionId,
        int worldX,
        int worldY,
        int worldZ,
        double arrivalRadius,
        int minWorldX,
        int minWorldY,
        int minWorldZ,
        int maxWorldX,
        int maxWorldY,
        int maxWorldZ
    ) {
        return new RouteTarget(Type.STRUCTURE, label, dimensionId, worldX, worldY, worldZ, arrivalRadius, minWorldX, minWorldY, minWorldZ, maxWorldX, maxWorldY, maxWorldZ);
    }

    boolean hasBounds() {
        return this.type == Type.STRUCTURE;
    }

    boolean contains(double worldX, double worldY, double worldZ) {
        int blockX = Mth.floor(worldX);
        int blockY = Mth.floor(worldY);
        int blockZ = Mth.floor(worldZ);
        return blockX >= this.minWorldX
            && blockX <= this.maxWorldX
            && blockY >= this.minWorldY
            && blockY <= this.maxWorldY
            && blockZ >= this.minWorldZ
            && blockZ <= this.maxWorldZ;
    }

    RouteTarget withPoint(int worldX, int worldY, int worldZ) {
        return new RouteTarget(
            this.type,
            this.label,
            this.dimensionId,
            worldX,
            worldY,
            worldZ,
            this.arrivalRadius,
            this.minWorldX,
            this.minWorldY,
            this.minWorldZ,
            this.maxWorldX,
            this.maxWorldY,
            this.maxWorldZ
        );
    }

    enum Type {
        POSITION,
        WAYPOINT,
        STRUCTURE
    }
}
