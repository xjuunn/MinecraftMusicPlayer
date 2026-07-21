package com.junhsiun.musicplayer.model;

public enum PlayOrder {
    SEQUENTIAL,
    REVERSE,
    SHUFFLE;

    public String displayName() {
        return switch (this) {
            case SEQUENTIAL -> "正序";
            case REVERSE -> "倒序";
            case SHUFFLE -> "随机";
        };
    }

    public static PlayOrder fromString(String value) {
        return switch (value.toLowerCase()) {
            case "reverse", "倒序" -> REVERSE;
            case "shuffle", "random", "随机" -> SHUFFLE;
            default -> SEQUENTIAL;
        };
    }
}
