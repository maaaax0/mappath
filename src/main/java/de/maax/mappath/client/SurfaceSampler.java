package de.maax.mappath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class SurfaceSampler {
    private static final int SEA_LEVEL = 63;
    private static final float OBJECT_HEIGHT_SCALE = 0.35F;
    private static final int MAX_OBJECT_HEIGHT_RELIEF = 7;
    private static final float WATER_OPACITY = 0.50F;
    private static final Map<TextureAtlasSprite, Integer> SPRITE_COLOR_CACHE = new IdentityHashMap<>();

    private SurfaceSampler() {
    }

    static Sample sampleLive(
        Minecraft minecraft,
        ClientLevel level,
        int worldX,
        int worldZ,
        BlockPos.MutableBlockPos pos,
        BlockPos.MutableBlockPos fluidPos,
        BlockPos.MutableBlockPos heightPos
    ) {
        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
            return null;
        }

        LevelChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
        if (chunk.isEmpty()) {
            return null;
        }

        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        if (surfaceY < level.getMinBuildHeight()) {
            return null;
        }

        pos.set(worldX, surfaceY, worldZ);
        BlockState state = chunk.getBlockState(pos);
        while (shouldSkipSurfaceBlock(state) && pos.getY() > level.getMinBuildHeight()) {
            pos.move(0, -1, 0);
            state = chunk.getBlockState(pos);
        }

        if (state.isAir()) {
            state = Blocks.BEDROCK.defaultBlockState();
        }

        boolean water = state.getFluidState().is(FluidTags.WATER);
        boolean lava = state.getFluidState().is(FluidTags.LAVA);
        int waterDepth = 0;
        BlockState underwaterState = Blocks.AIR.defaultBlockState();
        if (water) {
            fluidPos.set(pos);
            while (fluidPos.getY() > level.getMinBuildHeight() && chunk.getBlockState(fluidPos).getFluidState().is(FluidTags.WATER)) {
                waterDepth++;
                fluidPos.move(0, -1, 0);
            }
            underwaterState = chunk.getBlockState(fluidPos);
        }

        int baseColor;
        if (water) {
            int waterColor = BiomeColors.getAverageWaterColor(level, pos);
            int underwaterColor = getSurfaceColor(minecraft, level, fluidPos, underwaterState);
            baseColor = blendRgb(underwaterColor, waterColor, WATER_OPACITY);
        } else {
            baseColor = getSurfaceColor(minecraft, level, pos, state);
        }

        boolean landscapeSurface = water || lava || isLandscapeSurface(state);
        int displayHeight = landscapeSurface ? pos.getY() : dampenedObjectHeight(level, worldX, worldZ, pos.getY(), heightPos);
        float relief = 0.0F;
        float altitude = 0.0F;
        float directionalShade = 1.0F;
        float slopeShade = 1.0F;
        float contour = 1.0F;
        if (landscapeSurface || displayHeight != pos.getY()) {
            int northHeight = sampleDisplayHeight(level, worldX, worldZ - 1, heightPos, displayHeight);
            int southHeight = sampleDisplayHeight(level, worldX, worldZ + 1, heightPos, displayHeight);
            int westHeight = sampleDisplayHeight(level, worldX - 1, worldZ, heightPos, displayHeight);
            int eastHeight = sampleDisplayHeight(level, worldX + 1, worldZ, heightPos, displayHeight);
            float slope = Math.max(
                Math.max(Math.abs(displayHeight - northHeight), Math.abs(displayHeight - southHeight)),
                Math.max(Math.abs(displayHeight - westHeight), Math.abs(displayHeight - eastHeight))
            );
            float gradientX = eastHeight - westHeight;
            float gradientZ = southHeight - northHeight;
            relief = ((westHeight - eastHeight) + (northHeight - southHeight)) * 0.035F;
            altitude = Mth.clamp((displayHeight - SEA_LEVEL) * 0.0009F, -0.035F, 0.08F);
            directionalShade = computeDirectionalShade(gradientX, gradientZ);
            slopeShade = Mth.clamp(1.0F - slope * 0.015F, 0.76F, 1.0F);
            contour = landscapeSurface && isContour(displayHeight, northHeight, southHeight, westHeight, eastHeight) ? 0.90F : 1.0F;
        }
        float waterShade = 1.0F;
        float emissive = lava ? 0.18F : 0.0F;
        float brightness = Mth.clamp((1.0F + relief + altitude) * directionalShade * slopeShade * waterShade * contour + emissive, 0.68F, 1.24F);

        return new Sample(toNativeImageColor(shade(baseColor, brightness)), displayHeight);
    }

    private static int sampleDisplayHeight(
        ClientLevel level,
        int worldX,
        int worldZ,
        BlockPos.MutableBlockPos heightPos,
        int fallbackHeight
    ) {
        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
            return fallbackHeight;
        }

        LevelChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
        if (chunk.isEmpty()) {
            return fallbackHeight;
        }

        int surfaceHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        if (surfaceHeight < level.getMinBuildHeight()) {
            return fallbackHeight;
        }

        heightPos.set(worldX, surfaceHeight, worldZ);
        BlockState state = chunk.getBlockState(heightPos);
        while (shouldSkipSurfaceBlock(state) && heightPos.getY() > level.getMinBuildHeight()) {
            heightPos.move(0, -1, 0);
            state = chunk.getBlockState(heightPos);
        }

        if (state.getFluidState().is(FluidTags.WATER) || state.getFluidState().is(FluidTags.LAVA) || isLandscapeSurface(state)) {
            return heightPos.getY();
        }

        return dampenedObjectHeight(level, worldX, worldZ, heightPos.getY(), heightPos);
    }

    private static int sampleLandscapeHeight(ClientLevel level, int worldX, int worldZ, BlockPos.MutableBlockPos heightPos) {
        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
            return level.getMinBuildHeight();
        }

        LevelChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
        int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        if (height < level.getMinBuildHeight()) {
            return level.getMinBuildHeight();
        }

        heightPos.set(worldX, height, worldZ);
        BlockState state = chunk.getBlockState(heightPos);
        while ((shouldSkipSurfaceBlock(state) || !isLandscapeSurface(state)) && heightPos.getY() > level.getMinBuildHeight()) {
            heightPos.move(0, -1, 0);
            state = chunk.getBlockState(heightPos);
        }

        return heightPos.getY();
    }

    private static int dampenedObjectHeight(
        ClientLevel level,
        int worldX,
        int worldZ,
        int surfaceHeight,
        BlockPos.MutableBlockPos heightPos
    ) {
        int landscapeHeight = sampleLandscapeHeight(level, worldX, worldZ, heightPos);
        int objectHeight = Math.max(0, surfaceHeight - landscapeHeight);
        if (objectHeight == 0) {
            return surfaceHeight;
        }

        int scaledHeight = Mth.clamp(Mth.ceil(objectHeight * OBJECT_HEIGHT_SCALE), 1, MAX_OBJECT_HEIGHT_RELIEF);
        return Math.min(surfaceHeight, landscapeHeight + scaledHeight);
    }

    private static boolean shouldSkipSurfaceBlock(BlockState state) {
        return state.isAir()
            || state.is(BlockTags.CROPS)
            || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.LARGE_FERN)
            || state.is(Blocks.PINK_PETALS)
            || state.is(Blocks.VINE);
    }

    private static boolean isLandscapeSurface(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER)
            || state.getFluidState().is(FluidTags.LAVA)
            || state.is(BlockTags.BASE_STONE_OVERWORLD)
            || state.is(BlockTags.BASE_STONE_NETHER)
            || state.is(BlockTags.DIRT)
            || state.is(BlockTags.SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.CLAY)
            || state.is(Blocks.MUD)
            || state.is(Blocks.SNOW_BLOCK)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.ICE)
            || state.is(Blocks.PACKED_ICE)
            || state.is(Blocks.BLUE_ICE)
            || state.is(Blocks.TERRACOTTA)
            || state.is(Blocks.RED_TERRACOTTA)
            || state.is(Blocks.ORANGE_TERRACOTTA)
            || state.is(Blocks.YELLOW_TERRACOTTA)
            || state.is(Blocks.WHITE_TERRACOTTA)
            || state.is(Blocks.LIGHT_GRAY_TERRACOTTA)
            || state.is(Blocks.BROWN_TERRACOTTA);
    }

    private static boolean isContour(int height, int northHeight, int southHeight, int westHeight, int eastHeight) {
        if ((height & 3) != 0) {
            return false;
        }

        return northHeight > height || southHeight > height || westHeight > height || eastHeight > height;
    }

    private static int getSurfaceColor(Minecraft minecraft, ClientLevel level, BlockPos pos, BlockState state) {
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        List<BakedQuad> topQuads = model.getQuads(state, Direction.UP, RandomSource.create(0L));
        List<BakedQuad> quads = topQuads.isEmpty() ? model.getQuads(state, null, RandomSource.create(0L)) : topQuads;
        if (quads.isEmpty()) {
            return averageSpriteColor(minecraft.getBlockRenderer().getBlockModelShaper().getTexture(state, level, pos));
        }

        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        for (BakedQuad quad : quads) {
            int color = averageSpriteColor(quad.getSprite());
            if (quad.isTinted()) {
                color = multiplyRgb(color, minecraft.getBlockColors().getColor(state, level, pos, quad.getTintIndex()));
            }

            red += color >> 16 & 0xFF;
            green += color >> 8 & 0xFF;
            blue += color & 0xFF;
            count++;
        }

        return count == 0 ? 0xFF00FF : (int)(red / count) << 16 | (int)(green / count) << 8 | (int)(blue / count);
    }

    private static int averageSpriteColor(TextureAtlasSprite sprite) {
        Integer cachedColor = SPRITE_COLOR_CACHE.get(sprite);
        if (cachedColor != null) {
            return cachedColor;
        }

        int width = sprite.contents().width();
        int height = sprite.contents().height();
        long red = 0;
        long green = 0;
        long blue = 0;
        long alpha = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = sprite.getPixelRGBA(0, x, y);
                int pixelAlpha = pixel >> 24 & 0xFF;
                if (pixelAlpha == 0) {
                    continue;
                }

                red += (pixel & 0xFF) * pixelAlpha;
                green += (pixel >> 8 & 0xFF) * pixelAlpha;
                blue += (pixel >> 16 & 0xFF) * pixelAlpha;
                alpha += pixelAlpha;
            }
        }

        int color = alpha == 0 ? 0xFF00FF : (int)(red / alpha) << 16 | (int)(green / alpha) << 8 | (int)(blue / alpha);
        SPRITE_COLOR_CACHE.put(sprite, color);
        return color;
    }

    private static int multiplyRgb(int base, int tint) {
        int red = ((base >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int green = ((base >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int blue = (base & 0xFF) * (tint & 0xFF) / 255;
        return red << 16 | green << 8 | blue;
    }

    private static int blendRgb(int bottom, int top, float topOpacity) {
        float bottomOpacity = 1.0F - topOpacity;
        int red = Mth.clamp((int)(((bottom >> 16) & 0xFF) * bottomOpacity + ((top >> 16) & 0xFF) * topOpacity), 0, 255);
        int green = Mth.clamp((int)(((bottom >> 8) & 0xFF) * bottomOpacity + ((top >> 8) & 0xFF) * topOpacity), 0, 255);
        int blue = Mth.clamp((int)((bottom & 0xFF) * bottomOpacity + (top & 0xFF) * topOpacity), 0, 255);
        return red << 16 | green << 8 | blue;
    }

    private static float computeDirectionalShade(float gradientX, float gradientZ) {
        float normalX = -gradientX * 0.45F;
        float normalY = 1.0F;
        float normalZ = -gradientZ * 0.45F;
        float normalLength = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        normalX /= normalLength;
        normalY /= normalLength;
        normalZ /= normalLength;

        float lightX = -0.55F;
        float lightY = 0.78F;
        float lightZ = -0.30F;
        float diffuse = Math.max(0.0F, normalX * lightX + normalY * lightY + normalZ * lightZ);
        return Mth.clamp(0.58F + diffuse * 0.60F, 0.58F, 1.18F);
    }

    private static int shade(int color, float brightness) {
        int red = Mth.clamp((int)(((color >> 16) & 0xFF) * brightness), 0, 255);
        int green = Mth.clamp((int)(((color >> 8) & 0xFF) * brightness), 0, 255);
        int blue = Mth.clamp((int)((color & 0xFF) * brightness), 0, 255);
        return red << 16 | green << 8 | blue;
    }

    private static int toNativeImageColor(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return 0xFF000000 | blue << 16 | green << 8 | red;
    }

    record Sample(int color, int height) {
    }
}
