package com.junhsiun.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModLogger {
    public static final String MOD_ID = "客户端:musicplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void info(String log) {
        LOGGER.info(log);
    }

    public static void debug(String log) {
        LOGGER.debug(log);
    }

    public static void error(String log) {
        LOGGER.error(log);
    }

    public static void warn(String log) {
        LOGGER.warn(log);
    }

    public static void trace(String log) {
        LOGGER.trace(log);
    }
}
