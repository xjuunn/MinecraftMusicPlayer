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

    public String translationKey() {
        return switch (this) {
            case SEQUENTIAL -> "musicplayer.play_order.sequential";
            case REVERSE -> "musicplayer.play_order.reverse";
            case SHUFFLE -> "musicplayer.play_order.shuffle";
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
