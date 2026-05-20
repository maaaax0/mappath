package de.maax.mappath;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

public enum StructureMarkerType {
    DESERT_VILLAGE("desert_village", BuiltinStructures.VILLAGE_DESERT),
    JUNGLE_TEMPLE("jungle_temple", BuiltinStructures.JUNGLE_TEMPLE),
    OCEAN_MONUMENT("ocean_monument", BuiltinStructures.OCEAN_MONUMENT),
    PLAINS_VILLAGE("plains_village", BuiltinStructures.VILLAGE_PLAINS),
    SAVANNA_VILLAGE("savanna_village", BuiltinStructures.VILLAGE_SAVANNA),
    SNOWY_VILLAGE("snowy_village", BuiltinStructures.VILLAGE_SNOWY),
    SWAMP_HUT("swamp_hut", BuiltinStructures.SWAMP_HUT),
    TAIGA_VILLAGE("taiga_village", BuiltinStructures.VILLAGE_TAIGA),
    TRIAL_CHAMBERS("trial_chambers", BuiltinStructures.TRIAL_CHAMBERS),
    WOODLAND_MANSION("woodland_mansion", BuiltinStructures.WOODLAND_MANSION);

    private final String id;
    private final ResourceKey<Structure> structureKey;

    StructureMarkerType(String id, ResourceKey<Structure> structureKey) {
        this.id = id;
        this.structureKey = structureKey;
    }

    public String id() {
        return this.id;
    }

    public ResourceKey<Structure> structureKey() {
        return this.structureKey;
    }

    public String texturePath() {
        return "textures/gui/mag_icons/" + this.id + ".png";
    }

    public static StructureMarkerType byId(String id) {
        for (StructureMarkerType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }

        return null;
    }

    public static StructureMarkerType byStructureKey(ResourceKey<Structure> structureKey) {
        for (StructureMarkerType type : values()) {
            if (type.structureKey.equals(structureKey)) {
                return type;
            }
        }

        return null;
    }
}
