package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.junhsiun.core.command.subcommands.vo.SongVO;
import com.junhsiun.core.config.ServerConfig;
import com.junhsiun.core.config.ServerConfigManager;
import com.junhsiun.core.utils.HttpCallback;

import java.util.ArrayList;

public abstract class BasePlatform implements IMusicPlatform {

    public String getBaseUrl() {
        return ServerConfigManager.getConfig().platformBaseUrl.get(getName());
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        ServerConfigManager.getConfig().platformBaseUrl.put(getName(), baseUrl);
        ServerConfigManager.saveConfig();
    }
}
