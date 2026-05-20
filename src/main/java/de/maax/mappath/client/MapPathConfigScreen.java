package de.maax.mappath.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

public final class MapPathConfigScreen extends ConfigurationScreen.ConfigurationSectionScreen {
    public MapPathConfigScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title, (context, key, original) -> original);
    }

    @Override
    protected Component getTooltipComponent(String key, @Nullable ModConfigSpec.Range<?> range) {
        MutableComponent tooltip = Component.translatableWithFallback(this.getTranslationKey(key) + ".tooltip", this.getComment(key));
        ModConfigSpec.ValueSpec valueSpec = this.getValueSpec(key);
        if (range != null) {
            tooltip.append(Component.literal("\nRange: " + range).withStyle(ChatFormatting.GRAY));
        }
        if (valueSpec != null) {
            tooltip.append(Component.literal("\nDefault: " + valueSpec.getDefault()).withStyle(ChatFormatting.GRAY));
        }
        return tooltip;
    }
}
