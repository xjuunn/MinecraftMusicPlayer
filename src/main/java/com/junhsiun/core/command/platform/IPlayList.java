package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.PlayListVO;
import com.junhsiun.core.utils.HttpCallback;

// 歌单
public interface IPlayList {
    void playListInfo(String id, HttpCallback<PlayListVO> callback);
}
