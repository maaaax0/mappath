package de.maax.mappath.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SurfaceMapAtlas implements AutoCloseable {
    static final int CHUNKS_PER_PAGE = 16;
    static final int CHUNK_SIZE = 16;
    static final int PAGE_SIZE = CHUNKS_PER_PAGE * CHUNK_SIZE;

    private final Map<Long, AtlasPage> pages = new HashMap<>();
    private final int[] chunkColors = new int[CHUNK_SIZE * CHUNK_SIZE];
    private int textureSerial;

    void tick(Minecraft minecraft, MapTileStore store, int chunkBudget) {
        if (minecraft == null || store == null || chunkBudget <= 0) {
            return;
        }

        int updatedChunks = 0;
        Long chunkKey;
        Set<AtlasPage> updatedPages = new HashSet<>();
        while (updatedChunks < chunkBudget && (chunkKey = store.pollAtlasUpdateChunk()) != null) {
            int chunkX = MapTileStore.unpackX(chunkKey);
            int chunkZ = MapTileStore.unpackZ(chunkKey);
            updatedPages.add(this.updateChunk(minecraft, store, chunkX, chunkZ));
            updatedChunks++;
        }
        for (AtlasPage page : updatedPages) {
            page.texture.upload();
        }
    }

    void render(GuiGraphics guiGraphics, int x, int y, int size, double centerWorldX, double centerWorldZ, double pixelsPerBlock) {
        if (this.pages.isEmpty() || pixelsPerBlock <= 0.0D) {
            return;
        }

        double halfViewBlocks = size / (pixelsPerBlock * 2.0D);
        double minWorldX = centerWorldX - halfViewBlocks;
        double maxWorldX = centerWorldX + halfViewBlocks;
        double minWorldZ = centerWorldZ - halfViewBlocks;
        double maxWorldZ = centerWorldZ + halfViewBlocks;

        guiGraphics.enableScissor(x, y, x + size, y + size);
        for (AtlasPage page : this.pages.values()) {
            int pageWorldX = page.pageX * PAGE_SIZE;
            int pageWorldZ = page.pageZ * PAGE_SIZE;
            if (pageWorldX + PAGE_SIZE < minWorldX || pageWorldX > maxWorldX || pageWorldZ + PAGE_SIZE < minWorldZ || pageWorldZ > maxWorldZ) {
                continue;
            }

            double screenX = x + size / 2.0D + (pageWorldX - centerWorldX) * pixelsPerBlock;
            double screenY = y + size / 2.0D + (pageWorldZ - centerWorldZ) * pixelsPerBlock;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(screenX, screenY, 0.0D);
            guiGraphics.pose().scale((float)pixelsPerBlock, (float)pixelsPerBlock, 1.0F);
            guiGraphics.blit(page.location, 0, 0, PAGE_SIZE, PAGE_SIZE, 0.0F, 0.0F, PAGE_SIZE, PAGE_SIZE, PAGE_SIZE, PAGE_SIZE);
            guiGraphics.pose().popPose();
        }
        guiGraphics.disableScissor();
    }

    private AtlasPage updateChunk(Minecraft minecraft, MapTileStore store, int chunkX, int chunkZ) {
        int pageX = Math.floorDiv(chunkX, CHUNKS_PER_PAGE);
        int pageZ = Math.floorDiv(chunkZ, CHUNKS_PER_PAGE);
        AtlasPage page = this.getOrCreatePage(minecraft, pageX, pageZ);
        int localChunkX = Math.floorMod(chunkX, CHUNKS_PER_PAGE);
        int localChunkZ = Math.floorMod(chunkZ, CHUNKS_PER_PAGE);
        int startX = localChunkX * CHUNK_SIZE;
        int startZ = localChunkZ * CHUNK_SIZE;

        store.copyColors(chunkX, chunkZ, this.chunkColors);
        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int color = this.chunkColors[localZ * CHUNK_SIZE + localX];
                page.image.setPixelRGBA(startX + localX, startZ + localZ, color);
            }
        }
        return page;
    }

    private AtlasPage getOrCreatePage(Minecraft minecraft, int pageX, int pageZ) {
        long key = asLong(pageX, pageZ);
        AtlasPage page = this.pages.get(key);
        if (page != null) {
            return page;
        }

        NativeImage image = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        ResourceLocation location = minecraft.getTextureManager().register("mappath/world_map_atlas_" + this.textureSerial++, texture);
        page = new AtlasPage(pageX, pageZ, image, texture, location);
        this.pages.put(key, page);
        return page;
    }

    @Override
    public void close() {
        for (AtlasPage page : this.pages.values()) {
            page.texture.close();
        }
        this.pages.clear();
    }

    private static long asLong(int x, int z) {
        return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record AtlasPage(int pageX, int pageZ, NativeImage image, DynamicTexture texture, ResourceLocation location) {
    }
}
