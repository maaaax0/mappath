package de.maax.mappath;

public enum EntityMarkerTarget {
    WORLD_MAP("worldMap"),
    MINIMAP("minimap");

    private final String configKey;

    EntityMarkerTarget(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return this.configKey;
    }
}
