package com.junhsiun.musicplayer.model;

import net.minecraft.network.chat.Component;

/**
 * 详情页上的一枚数据小标签：左边是名称、右边是数值。
 *
 * <p>用「名称 + 数值」而不是拼好的一句话，是因为界面要把它们排成一行等宽的玻璃小卡片；
 * 拼成一整句就没法对齐，也没法只把数值加粗。</p>
 */
public record MediaStat(Component label, Component value) {

    public MediaStat {
        label = label == null ? Component.empty() : label;
        value = value == null ? Component.empty() : value;
    }

    /** 数值为空的标签没有信息量，直接丢掉，免得详情页出现一排空的玻璃片。 */
    public boolean isEmpty() {
        return value.getString().isBlank();
    }
}
