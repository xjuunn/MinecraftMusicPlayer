package com.junhsiun.musicplayer.model;

public enum PlayOrder {
    SEQUENTIAL,
    REVERSE,
    SHUFFLE;

    public String displayName() {
        String result;
        switch (this) {
            case SEQUENTIAL:
                result = "正序";
                break;
            case REVERSE:
                result = "倒序";
                break;
            case SHUFFLE:
                result = "随机";
                break;
            default:
                result = "";
                break;
        }
        return result;
    }

    public String translationKey() {
        String result;
        switch (this) {
            case SEQUENTIAL:
                result = "musicplayer.play_order.sequential";
                break;
            case REVERSE:
                result = "musicplayer.play_order.reverse";
                break;
            case SHUFFLE:
                result = "musicplayer.play_order.shuffle";
                break;
            default:
                result = "";
                break;
        }
        return result;
    }

    public static PlayOrder fromString(String value) {
        PlayOrder result;
        switch (value.toLowerCase()) {
            case "reverse":
            case "倒序":
                result = REVERSE;
                break;
            case "shuffle":
            case "random":
            case "随机":
                result = SHUFFLE;
                break;
            default:
                result = SEQUENTIAL;
                break;
        }
        return result;
    }
}
