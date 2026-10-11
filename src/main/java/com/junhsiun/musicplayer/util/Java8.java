package com.junhsiun.musicplayer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Java 8 兼容工具，替代 Java 9+ 标准库 API。 */
public final class Java8 {
    private Java8() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static String repeat(String value, int count) {
        if (value == null || count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    public static <T> List<T> listOf() {
        return Collections.emptyList();
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
    }

    public static <T> List<T> copyOf(List<? extends T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
