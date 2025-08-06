package com.junhsiun.core.command.platform;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MusicPlatformRegistry {
    private static final Map<String, BasePlatform> platforms = new HashMap<>();

    public static void register(BasePlatform platform) {
        platforms.put(platform.getName().toLowerCase(), platform);
    }

    public static IMusicPlatform get(String name) {
        return platforms.get(name.toLowerCase());
    }

    public static Collection<BasePlatform> all() {
        return platforms.values();
    }
}
