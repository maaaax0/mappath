package de.maax.mappath;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

public enum EntityMarkerMode implements TranslatableEnum {
    ICON,
    DOTS,
    PLAYER_LIST,
    OFF;

    public boolean visible() {
        return this != OFF;
    }

    public boolean useIcons() {
        return this == ICON || this == PLAYER_LIST;
    }

    public boolean playerListOnly() {
        return this == PLAYER_LIST;
    }

    @Override
    public Component getTranslatedName() {
        return Component.translatable("mappath.configuration.entityMarkerMode." + this.name().toLowerCase(java.util.Locale.ROOT));
    }
}
