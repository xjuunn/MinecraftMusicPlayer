package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.PlayListVO;

// 歌单
public interface IPlayList {
    PlayListVO playListInfo(String id);
}
