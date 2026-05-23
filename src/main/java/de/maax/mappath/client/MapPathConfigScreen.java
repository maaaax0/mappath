package de.maax.mappath.client;

import de.maax.mappath.EntityMarkerMode;
import de.maax.mappath.EntityMarkerTarget;
import de.maax.mappath.EntityMarkerType;
import de.maax.mappath.MapPathConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class MapPathConfigScreen extends Screen {
    private static final int CONTENT_WIDTH = 660;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int COLUMN_GAP = 18;
    private static final int SEARCH_WIDTH = 300;

    private final Screen parent;
    private Category category = Category.WORLD_MAP;
    private String searchText = "";
    private boolean keepSearchFocused;
    private EditBox searchBox;

    public MapPathConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("mappath.configuration.title", Component.literal("MapPath")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.rebuildConfigWidgets();
    }

    private void rebuildConfigWidgets() {
        this.clearWidgets();

        int contentWidth = Math.min(CONTENT_WIDTH, this.width - 32);
        int left = (this.width - contentWidth) / 2;
        int columnWidth = (contentWidth - COLUMN_GAP) / 2;
        int rightColumn = left + columnWidth + COLUMN_GAP;

        this.searchBox = new EditBox(this.font, (this.width - SEARCH_WIDTH) / 2, 34, SEARCH_WIDTH, BUTTON_HEIGHT, Component.translatable("mappath.configuration.search"));
        this.searchBox.setHint(Component.translatable("mappath.configuration.search.hint"));
        this.searchBox.setValue(this.searchText);
        this.searchBox.setResponder(value -> {
            if (!this.searchText.equals(value)) {
                this.searchText = value;
                this.keepSearchFocused = true;
                this.rebuildConfigWidgets();
            }
        });
        this.addRenderableWidget(this.searchBox);
        if (this.keepSearchFocused) {
            this.searchBox.setFocused(true);
            this.searchBox.moveCursorToEnd(false);
            this.keepSearchFocused = false;
        }

        int y = 66;
        int categoryIndex = 0;
        for (Category entry : Category.values()) {
            int x = categoryIndex % 2 == 0 ? left : rightColumn;
            int buttonY = y + categoryIndex / 2 * (BUTTON_HEIGHT + BUTTON_GAP);
            this.addCategoryButton(x, buttonY, columnWidth, entry);
            categoryIndex++;
        }

        this.addSettings(left, rightColumn, y + 3 * (BUTTON_HEIGHT + BUTTON_GAP) + 12, columnWidth);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> this.onClose())
            .bounds((this.width - 220) / 2, this.height - 30, 220, BUTTON_HEIGHT)
            .build());
    }

    private void addCategoryButton(int x, int y, int width, Category targetCategory) {
        boolean selected = this.category == targetCategory && this.searchText.isBlank();
        Component label = selected
            ? Component.literal("> ").append(Component.translatable(targetCategory.translationKey)).append(" <")
            : Component.translatable(targetCategory.translationKey);
        this.addRenderableWidget(Button.builder(label, button -> {
            this.category = targetCategory;
            this.searchText = "";
            this.rebuildConfigWidgets();
        }).bounds(x, y, width, BUTTON_HEIGHT).build());
    }

    private void addSettings(int left, int rightColumn, int top, int columnWidth) {
        int index = 0;
        for (Category targetCategory : Category.values()) {
            if (!this.searchText.isBlank() || targetCategory == this.category) {
                index = this.addCategorySettings(targetCategory, left, rightColumn, top, columnWidth, index);
            }
        }
    }

    private int addCategorySettings(Category targetCategory, int left, int rightColumn, int top, int columnWidth, int index) {
        return switch (targetCategory) {
            case WORLD_MAP -> {
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.worldMap.showStructureMarkers", MapPathConfig.CLIENT.showStructureMarkers(), MapPathConfig.CLIENT::setShowStructureMarkers);
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.worldMap.showWaypoints", MapPathConfig.CLIENT.showWaypoints(), MapPathConfig.CLIENT::setShowWaypoints);
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.worldMap.showBetaFeatures", MapPathConfig.CLIENT.showBetaFeatures(), MapPathConfig.CLIENT::setShowBetaFeatures);
                yield index;
            }
            case MINIMAP -> {
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.minimap.showMinimap", MapPathConfig.CLIENT.showMinimap(), MapPathConfig.CLIENT::setShowMinimap);
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.minimap.minimapSize", MapPathConfig.CLIENT::minimapSize, MapPathConfig.CLIENT::setMinimapSize, new int[] {64, 96, 128, 160, 192, 224, 256});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.minimap.minimapBlocksPerPixel", MapPathConfig.CLIENT::minimapBlocksPerPixel, MapPathConfig.CLIENT::setMinimapBlocksPerPixel, new int[] {1, 2, 3, 4, 6, 8});
                yield index;
            }
            case ENTITY_RADAR -> {
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.WORLD_MAP, EntityMarkerType.PLAYER);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.WORLD_MAP, EntityMarkerType.ITEM);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.WORLD_MAP, EntityMarkerType.MOB);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.WORLD_MAP, EntityMarkerType.BOSS);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.MINIMAP, EntityMarkerType.PLAYER);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.MINIMAP, EntityMarkerType.ITEM);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.MINIMAP, EntityMarkerType.MOB);
                index = this.addEntitySetting(index, left, rightColumn, top, columnWidth, targetCategory, EntityMarkerTarget.MINIMAP, EntityMarkerType.BOSS);
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.entityMarkerVerticalRange", MapPathConfig.CLIENT::entityMarkerVerticalRange, MapPathConfig.CLIENT::setEntityMarkerVerticalRange, new int[] {0, 8, 16, 24, 32, 64, 128, 384});
                yield index;
            }
            case ROUTES -> {
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.showRouteVisualizer", MapPathConfig.CLIENT.showRouteVisualizer(), MapPathConfig.CLIENT::setShowRouteVisualizer);
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.showRouteTargetMarker", MapPathConfig.CLIENT.showRouteTargetMarker(), MapPathConfig.CLIENT::setShowRouteTargetMarker);
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.routeTrailMaxSpeed", MapPathConfig.CLIENT::routeTrailMaxSpeed, MapPathConfig.CLIENT::setRouteTrailMaxSpeed, new int[] {1, 2, 4, 6, 8, 12, 16, 32, 64});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.routePlanningDistance", MapPathConfig.CLIENT::routePlanningDistance, MapPathConfig.CLIENT::setRoutePlanningDistance, new int[] {32, 48, 64, 96, 128, 192, 256, 512});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.routeRecalculateDistance", MapPathConfig.CLIENT::routeRecalculateDistance, MapPathConfig.CLIENT::setRouteRecalculateDistance, new int[] {3, 6, 12, 24, 48, 96, 128});
                yield index;
            }
            case PERFORMANCE -> {
                index = this.addBooleanSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.showMapLoadStatus", MapPathConfig.CLIENT.showMapLoadStatus(), MapPathConfig.CLIENT::setShowMapLoadStatus);
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.liveRefreshRadius", MapPathConfig.CLIENT::liveRefreshRadius, MapPathConfig.CLIENT::setLiveRefreshRadius, new int[] {0, 32, 64, 96, 128, 192, 256, 512});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.recordIntervalTicks", MapPathConfig.CLIENT::recordIntervalTicks, MapPathConfig.CLIENT::setRecordIntervalTicks, new int[] {1, 2, 5, 10, 20, 40});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.liveRefreshColumnsPerTick", MapPathConfig.CLIENT::liveRefreshColumnsPerTick, MapPathConfig.CLIENT::setLiveRefreshColumnsPerTick, new int[] {8, 12, 16, 24, 32, 48, 64});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.chunkRefreshesPerTick", MapPathConfig.CLIENT::chunkRefreshesPerTick, MapPathConfig.CLIENT::setChunkRefreshesPerTick, new int[] {12, 16, 24, 32, 48, 64, 96, 128});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.missingChunkSearchIntervalTicks", MapPathConfig.CLIENT::missingChunkSearchIntervalTicks, MapPathConfig.CLIENT::setMissingChunkSearchIntervalTicks, new int[] {1, 5, 10, 20, 40, 100, 200});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.tileWritesPerTick", MapPathConfig.CLIENT::tileWritesPerTick, MapPathConfig.CLIENT::setTileWritesPerTick, new int[] {0, 1, 2, 4, 8, 16, 32, 64});
                index = this.addIntSetting(index, left, rightColumn, top, columnWidth, targetCategory, "mappath.configuration.initialMapLoadPerTick", MapPathConfig.CLIENT::initialMapLoadPerTick, MapPathConfig.CLIENT::setInitialMapLoadPerTick, new int[] {625, 1024, 1600, 2304, 4096});
                yield index;
            }
        };
    }

    private int addBooleanSetting(int index, int left, int rightColumn, int top, int columnWidth, Category targetCategory, String key, boolean value, BooleanSetter setter) {
        if (!this.matches(targetCategory, key)) {
            return index;
        }
        this.addSettingButton(index, left, rightColumn, top, columnWidth, booleanLabel(key, value), () -> {
            setter.set(!value);
            this.rebuildConfigWidgets();
        });
        return index + 1;
    }

    private int addIntSetting(int index, int left, int rightColumn, int top, int columnWidth, Category targetCategory, String key, IntSupplier getter, IntConsumer setter, int[] values) {
        if (!this.matches(targetCategory, key)) {
            return index;
        }
        int x = index % 2 == 0 ? left : rightColumn;
        int y = top + index / 2 * (BUTTON_HEIGHT + BUTTON_GAP);
        this.addRenderableWidget(new IntSettingSlider(x, y, columnWidth, BUTTON_HEIGHT, key, values, getter.getAsInt(), setter));
        return index + 1;
    }

    private int addEntitySetting(int index, int left, int rightColumn, int top, int columnWidth, Category targetCategory, EntityMarkerTarget target, EntityMarkerType type) {
        String key = "mappath.configuration." + target.configKey() + ".entityRadar." + type.configKey();
        if (!this.matches(targetCategory, key)) {
            return index;
        }
        EntityMarkerMode mode = MapPathConfig.CLIENT.entityMarkerMode(target, type);
        this.addSettingButton(index, left, rightColumn, top, columnWidth, valueLabel(key, mode.getTranslatedName()), () -> {
            MapPathConfig.CLIENT.setEntityMarkerMode(target, type, nextMode(mode));
            this.rebuildConfigWidgets();
        });
        return index + 1;
    }

    private void addSettingButton(int index, int left, int rightColumn, int top, int columnWidth, Component label, Runnable action) {
        int x = index % 2 == 0 ? left : rightColumn;
        int y = top + index / 2 * (BUTTON_HEIGHT + BUTTON_GAP);
        this.addRenderableWidget(Button.builder(label, button -> action.run()).bounds(x, y, columnWidth, BUTTON_HEIGHT).build());
    }

    private boolean matches(Category targetCategory, String key) {
        if (this.searchText.isBlank()) {
            return targetCategory == this.category;
        }
        String query = this.searchText.toLowerCase(Locale.ROOT);
        return targetCategory.translationKey.toLowerCase(Locale.ROOT).contains(query)
            || key.toLowerCase(Locale.ROOT).contains(query)
            || Component.translatable(key).getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static Component booleanLabel(String key, boolean value) {
        return valueLabel(key, Component.translatable(value ? "options.on" : "options.off"));
    }

    private static Component valueLabel(String key, int value) {
        return valueLabel(key, Component.literal(Integer.toString(value)));
    }

    private static Component valueLabel(String key, Component value) {
        return Component.literal("").append(Component.translatable(key)).append(": ").append(value);
    }

    private static EntityMarkerMode nextMode(EntityMarkerMode mode) {
        EntityMarkerMode[] values = EntityMarkerMode.values();
        return values[(mode.ordinal() + 1) % values.length];
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, 0x44000000);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        if (!this.searchText.isBlank()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("mappath.configuration.search.results"), this.width / 2, 120, 0xFFE0E0E0);
        } else {
            guiGraphics.drawCenteredString(this.font, Component.translatable(this.category.translationKey), this.width / 2, 120, 0xFFE0E0E0);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private enum Category {
        WORLD_MAP("mappath.configuration.category.worldMap"),
        MINIMAP("mappath.configuration.category.minimap"),
        ENTITY_RADAR("mappath.configuration.category.entityRadar"),
        ROUTES("mappath.configuration.category.routes"),
        PERFORMANCE("mappath.configuration.category.performance");

        private final String translationKey;

        Category(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }

    private static final class IntSettingSlider extends AbstractSliderButton {
        private final String key;
        private final int[] values;
        private final IntConsumer setter;
        private int currentValue;

        private IntSettingSlider(int x, int y, int width, int height, String key, int[] values, int currentValue, IntConsumer setter) {
            super(x, y, width, height, valueLabel(key, nearestValue(values, currentValue)), normalizedValue(values, currentValue));
            this.key = key;
            this.values = values;
            this.setter = setter;
            this.currentValue = nearestValue(values, currentValue);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(valueLabel(this.key, this.currentValue));
        }

        @Override
        protected void applyValue() {
            int nextValue = this.valueToSetting();
            if (this.currentValue != nextValue) {
                this.currentValue = nextValue;
                this.setter.accept(nextValue);
            }
        }

        private int valueToSetting() {
            if (this.values.length == 1) {
                return this.values[0];
            }

            int index = (int)Math.round(this.value * (this.values.length - 1));
            return this.values[Math.max(0, Math.min(this.values.length - 1, index))];
        }

        private static double normalizedValue(int[] values, int currentValue) {
            if (values.length <= 1) {
                return 0.0D;
            }

            int nearestIndex = 0;
            int nearestDistance = Math.abs(values[0] - currentValue);
            for (int i = 1; i < values.length; i++) {
                int distance = Math.abs(values[i] - currentValue);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = i;
                }
            }
            return nearestIndex / (double)(values.length - 1);
        }

        private static int nearestValue(int[] values, int currentValue) {
            int nearest = values[0];
            int nearestDistance = Math.abs(nearest - currentValue);
            for (int value : values) {
                int distance = Math.abs(value - currentValue);
                if (distance < nearestDistance) {
                    nearest = value;
                    nearestDistance = distance;
                }
            }
            return nearest;
        }
    }
}
