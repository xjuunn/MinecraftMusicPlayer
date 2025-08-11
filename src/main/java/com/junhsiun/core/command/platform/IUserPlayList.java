package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.subcommands.vo.UserVO;
import com.junhsiun.core.utils.HttpCallback;

// 查看用户歌单
public interface IUserPlayList {
    void userPlayList(String id, HttpCallback<UserVO> callback);
}
