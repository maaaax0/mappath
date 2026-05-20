package de.maax.mappath.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class SurfaceMapTexture implements AutoCloseable {
    public static final int MAP_SIZE = 512;
    private static final int MIN_TEXTURE_SIZE = MAP_SIZE;
    private static final int MAX_TEXTURE_SIZE = 4096;
    private static final int UNKNOWN_COLOR = 0xFF111417;
    private static final int REFRESH_LIVE_SAMPLE_BUDGET = 32768;

    private final WorldMapManager worldMapManager;
    private DynamicTexture texture;
    private ResourceLocation textureLocation;
    private float blocksPerPixel = 1.0F;
    private double centerX;
    private double centerZ;
    private int textureSize = MAP_SIZE;

    public SurfaceMapTexture(WorldMapManager worldMapManager) {
        this.worldMapManager = worldMapManager;
    }

    public void refresh(Minecraft minecraft, float blocksPerPixel) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        this.refresh(minecraft, blocksPerPixel, minecraft.player.getX(), minecraft.player.getZ(), MAP_SIZE);
    }

    public void refresh(Minecraft minecraft, float blocksPerPixel, double centerX, double centerZ) {
        this.refresh(minecraft, blocksPerPixel, centerX, centerZ, MAP_SIZE);
    }

    public void refresh(Minecraft minecraft, float blocksPerPixel, double centerX, double centerZ, int preferredTextureSize) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        MapTileStore tileStore = this.worldMapManager.getTileStore(minecraft);
        if (tileStore == null) {
            return;
        }

        this.blocksPerPixel = blocksPerPixel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.textureSize = this.calculateTextureSize(blocksPerPixel, preferredTextureSize);
        ClientLevel level = minecraft.level;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos heightPos = new BlockPos.MutableBlockPos();
        NativeImage image = new NativeImage(this.textureSize, this.textureSize, true);
        float texturePixelsToBlocks = blocksPerPixel * MAP_SIZE / (float) this.textureSize;
        int halfTextureSize = this.textureSize / 2;
        int[] liveSampleBudget = {REFRESH_LIVE_SAMPLE_BUDGET};
        for (int mapZ = 0; mapZ < this.textureSize; mapZ++) {
            int worldZ = Mth.floor(centerZ + (mapZ - halfTextureSize) * texturePixelsToBlocks);
            for (int mapX = 0; mapX < this.textureSize; mapX++) {
                int worldX = Mth.floor(centerX + (mapX - halfTextureSize) * texturePixelsToBlocks);
                image.setPixelRGBA(
                    mapX,
                    mapZ,
                    this.sampleDisplayColor(minecraft, level, tileStore, pos, fluidPos, heightPos, liveSampleBudget, worldX, worldZ)
                );
            }
        }

        tileStore.flush();

        if (this.texture != null) {
            this.texture.close();
        }

        this.texture = new DynamicTexture(image);
        this.texture.setFilter(false, false);
        this.textureLocation = minecraft.getTextureManager().register("mappath/world_map", this.texture);
    }

    public void render(GuiGraphics guiGraphics, int x, int y, int size, float targetBlocksPerPixel) {
        this.render(guiGraphics, x, y, size, targetBlocksPerPixel, this.centerX, this.centerZ);
    }

    public void render(GuiGraphics guiGraphics, int x, int y, int size, float targetBlocksPerPixel, double targetCenterX, double targetCenterZ) {
        if (this.textureLocation != null) {
            double scale = this.blocksPerPixel / (double) targetBlocksPerPixel;
            double scaledSize = Math.max(1.0D, size * scale);
            double pixelsPerBlock = size / (SurfaceMapTexture.MAP_SIZE * (double) targetBlocksPerPixel);
            double centerOffsetX = (this.centerX - targetCenterX) * pixelsPerBlock;
            double centerOffsetY = (this.centerZ - targetCenterZ) * pixelsPerBlock;
            double scaledX = x + (size - scaledSize) / 2.0D + centerOffsetX;
            double scaledY = y + (size - scaledSize) / 2.0D + centerOffsetY;
            float renderScale = (float)(scaledSize / this.textureSize);
            guiGraphics.enableScissor(x, y, x + size, y + size);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(scaledX, scaledY, 0.0D);
            guiGraphics.pose().scale(renderScale, renderScale, 1.0F);
            guiGraphics.blit(this.textureLocation, 0, 0, this.textureSize, this.textureSize, 0.0F, 0.0F, this.textureSize, this.textureSize, this.textureSize, this.textureSize);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
        }
    }

    public float blocksPerPixel() {
        return this.blocksPerPixel;
    }

    private int calculateTextureSize(float blocksPerPixel, int preferredTextureSize) {
        int blockSharpTextureSize = Mth.ceil(MAP_SIZE * blocksPerPixel);
        return Mth.clamp(Math.max(preferredTextureSize, blockSharpTextureSize), MIN_TEXTURE_SIZE, MAX_TEXTURE_SIZE);
    }

    private int sampleDisplayColor(
        Minecraft minecraft,
        ClientLevel level,
        MapTileStore tileStore,
        BlockPos.MutableBlockPos pos,
        BlockPos.MutableBlockPos fluidPos,
        BlockPos.MutableBlockPos heightPos,
        int[] liveSampleBudget,
        int worldX,
        int worldZ
    ) {
        int cachedColor = tileStore.getColor(worldX, worldZ);
        if (cachedColor != 0) {
            return cachedColor;
        }
        if (liveSampleBudget[0] <= 0) {
            return UNKNOWN_COLOR;
        }

        SurfaceSampler.Sample sample = SurfaceSampler.sampleLive(minecraft, level, worldX, worldZ, pos, fluidPos, heightPos);
        if (sample == null) {
            return UNKNOWN_COLOR;
        }

        liveSampleBudget[0]--;
        tileStore.put(worldX, worldZ, sample.color(), sample.height());
        return sample.color();
    }

    @Override
    public void close() {
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
            this.textureLocation = null;
            this.textureSize = MAP_SIZE;
        }
    }
}
