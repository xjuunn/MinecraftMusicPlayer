package com.junhsiun.musicplayer.model;

public record RadioInfo(String id, String name, String description, String category, String secondCategory, String coverUrl, int programCount, int subCount, long playCount, int radioFeeType) {
}
