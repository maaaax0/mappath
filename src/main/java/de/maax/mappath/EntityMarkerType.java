package de.maax.mappath;

public enum EntityMarkerType {
    PLAYER("player"),
    ITEM("item"),
    MOB("mob"),
    BOSS("boss");

    private final String configKey;

    EntityMarkerType(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return this.configKey;
    }
}
