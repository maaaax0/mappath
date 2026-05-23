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
    YELLOW("yellow"),
    DEATH("death", "textures/gui/mag_icons/death.png");

    private final String id;
    private final String texturePath;

    BannerIconType(String id) {
        this(id, "textures/gui/mag_icons/" + id + "_banner.png");
    }

    BannerIconType(String id, String texturePath) {
        this.id = id;
        this.texturePath = texturePath;
    }

    public String id() {
        return this.id;
    }

    public Component displayName() {
        return Component.translatable("gui.mappath.banner_icon." + this.id);
    }

    public String texturePath() {
        return this.texturePath;
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
