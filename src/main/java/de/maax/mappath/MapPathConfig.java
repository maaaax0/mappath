package de.maax.mappath;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class MapPathConfig {
    public static final int DEFAULT_LIVE_REFRESH_RADIUS = 64;
    public static final int MAX_LIVE_REFRESH_RADIUS = 512;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = specPair.getLeft();
        CLIENT_SPEC = specPair.getRight();
    }

    private MapPathConfig() {
    }

    public static final class Client {
        private final ModConfigSpec.IntValue liveRefreshRadius;

        private Client(ModConfigSpec.Builder builder) {
            this.liveRefreshRadius = builder
                .comment(
                    "Radius in blocks around the player that is regularly re-sampled for map updates.",
                    "0 disables regular live updates. Maximum: " + MAX_LIVE_REFRESH_RADIUS + "."
                )
                .translation("mappath.configuration.liveRefreshRadius")
                .defineInRange("liveRefreshRadius", DEFAULT_LIVE_REFRESH_RADIUS, 0, MAX_LIVE_REFRESH_RADIUS);
        }

        public int liveRefreshRadius() {
            return this.liveRefreshRadius.get();
        }
    }
}
