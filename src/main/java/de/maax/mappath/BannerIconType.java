package de.maax.mappath;

import net.minecraft.network.chat.Component;

public enum BannerIconType {
    BLACK("black"),
    BLUE("blue"),
    BROWN("brown"),
    CYAN("cyan"),
    GRAY("gray"),
    GREEN("green"),
    LIGHT_BLUE("light_blue"),
    LIGHT_GRAY("light_gray"),
    LIME("lime"),
    MAGENTA("magenta"),
    ORANGE("orange"),
    PINK("pink"),
    PURPLE("purple"),
    RED("red"),
    WHITE("white"),
    YELLOW("yellow");

    private final String id;

    BannerIconType(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public Component displayName() {
        return Component.translatable("gui.mappath.banner_icon." + this.id);
    }

    public String texturePath() {
        return "textures/gui/mag_icons/" + this.id + "_banner.png";
    }

    public static BannerIconType byId(String id) {
        for (BannerIconType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }

        return null;
    }
}
