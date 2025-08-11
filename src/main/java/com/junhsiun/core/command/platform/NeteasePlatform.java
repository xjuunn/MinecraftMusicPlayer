package com.junhsiun.core.command.platform;

import com.junhsiun.core.command.platform.beans.netease.*;
import com.junhsiun.core.command.subcommands.vo.PlayListVO;
import com.junhsiun.core.command.subcommands.vo.SearchVO;
import com.junhsiun.core.command.subcommands.vo.SongVO;
import com.junhsiun.core.command.subcommands.vo.UserVO;
import com.junhsiun.core.utils.HttpCallback;
import com.junhsiun.core.utils.ModLogger;
import com.junhsiun.core.utils.OkHttpUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Objects;

public class NeteasePlatform extends BasePlatform implements IPlayList, IUserPlayList {
    @Override
    public String getName() {
        return "网易云音乐";
    }

    @Override
    public void getMusicUrl(String musicID, HttpCallback<String> callback) {
        ModLogger.info(getName() + " " + "播放音乐： " + musicID);
        getMusicNetease2(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
        getMusicNetease1(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
        getMusicUrl1(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
        getMusicUrl1(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
        getMusicUrl1(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
        getMusicUrl1(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
        getMusicUrl1(musicID, response -> {
            if (response != null && !response.isEmpty()) callback.onSuccess(response);
        });
    }

    @Override
    public void getMusicInfo(String musicID, HttpCallback<SongVO> callback) {
        ModLogger.info(getName() + " 查询歌曲详情： " + musicID);
        OkHttpUtil.get(getBaseUrl() + "/song/detail?ids=" + musicID, SongDetailBean.class, new HttpCallback<SongDetailBean>() {
            @Override
            public void onSuccess(SongDetailBean songDetailBean) {
                SongVO songVO = new SongVO();
                if (songDetailBean == null || songDetailBean.songs.isEmpty()) {
                    callback.onSuccess(songVO);
                }
                if (songDetailBean != null) {
                    songVO.setId(String.valueOf(songDetailBean.songs.get(0).id));
                }
                if (songDetailBean != null) {
                    songVO.setName(songDetailBean.songs.get(0).name);
                }
                if (songDetailBean != null) {
                    songVO.setSinger(songDetailBean.songs.get(0).ar.get(0).name);
                }
                callback.onSuccess(songVO);
            }

            @Override
            public void onFailure(Exception e) {
                ModLogger.info("查询歌曲详情失败：" + e.toString());
            }
        });
    }

    // https://doc.vkeys.cn/api-doc/v2/%E9%9F%B3%E4%B9%90%E6%A8%A1%E5%9D%97/%E7%BD%91%E6%98%93%E4%BA%91%E9%9F%B3%E4%B9%90/1-netease.html
    // 速度慢 有时候接口出错
    private void getMusicUrl1(String musicId, HttpCallback<String> callback) {
        OkHttpUtil.get("https://api.vkeys.cn/v2/music/netease?id=" + musicId + "&quality=4", VKeysGetUrlBean.class, new HttpCallback<VKeysGetUrlBean>() {
            @Override
            public void onSuccess(VKeysGetUrlBean vKeysGetUrlBean) {
                callback.onSuccess(vKeysGetUrlBean.data.url);
            }

            @Override
            public void onFailure(Exception e) {
//                callback.onFailure(e);
            }
        });
    }

    // 网易云旧版音乐接口
    private void getMusicNetease1(String musicId, HttpCallback<String> callback) {
        OkHttpUtil.get(getBaseUrl() + "/song/url?id=" + musicId, NeteaseGetUrlBean.class, new HttpCallback<NeteaseGetUrlBean>() {
            @Override
            public void onSuccess(NeteaseGetUrlBean neteaseGetUrlBean) {
                callback.onSuccess(neteaseGetUrlBean.data.get(0).url);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    // 网易云直链获取 速度快 但是可能会重定向到404
    private void getMusicNetease2(String musicId, HttpCallback<String> callback) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(
                        "http://music.163.com/song/media/outer/url?id=" + musicId + ".mp3"
                ).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);

                String location = conn.getHeaderField("location");

                if (location != null && location.contains("music.163.com/404")) {
                    callback.onSuccess(null);
                } else {
                    callback.onSuccess(location);
                }
            } catch (Exception e) {
                callback.onFailure(e);
            }
        }).start();
    }


    @Override
    public void searchSong(String keyword, HttpCallback<ArrayList<SearchVO>> callback) {
        ModLogger.info(getName() + " " + "搜索音乐： " + keyword);
        OkHttpUtil.get(getBaseUrl() + "/cloudsearch?limit=10&type=1&keywords=" + keyword, SearchBean.class, new HttpCallback<SearchBean>() {
            @Override
            public void onSuccess(SearchBean searchBean) {
                ArrayList<SearchVO> searchVOS = new ArrayList<>();
                if (searchBean == null || searchBean.result == null || searchBean.result.songs == null || searchBean.result.songs.isEmpty()) {
                    callback.onSuccess(searchVOS);
                }
                if (searchBean != null) {
                    searchBean.result.songs.forEach((song) -> {
                        SearchVO searchVO = new SearchVO(song.id, song.name, song.al.name);
                        searchVOS.add(searchVO);
                    });
                }
                callback.onSuccess(searchVOS);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    @Override
    public void searchPlayList(String keyword, HttpCallback<ArrayList<SearchVO>> callback) {
        ModLogger.info(getName() + " " + "搜索歌单： " + keyword);
        OkHttpUtil.get(getBaseUrl() + "/cloudsearch?limit=10&type=1000&keywords=" + keyword, SearchPlayListBean.class, new HttpCallback<SearchPlayListBean>() {
            @Override
            public void onSuccess(SearchPlayListBean playListBean) {
                ArrayList<SearchVO> searchVOS = new ArrayList<>();
                if (playListBean == null || playListBean.result == null || playListBean.result.playlists == null || playListBean.result.playlists.isEmpty()) {
                    callback.onSuccess(searchVOS);
                }
                if (playListBean != null) {
                    playListBean.result.playlists.forEach(playlist -> {
                        SearchVO searchVO = new SearchVO(playlist.id, playlist.name, playlist.creator.nickname);
                        searchVOS.add(searchVO);
                    });
                }
                callback.onSuccess(searchVOS);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });

    }

    @Override
    public void searchUser(String keyword, HttpCallback<ArrayList<SearchVO>> callback) {
        ModLogger.info(getName() + " " + "搜索用户： " + keyword);
        OkHttpUtil.get(getBaseUrl() + "/cloudsearch?limit=10&type=1002&keywords=" + keyword, SearchUserBean.class, new HttpCallback<SearchUserBean>() {
            @Override
            public void onSuccess(SearchUserBean playListBean) {
                ArrayList<SearchVO> searchVOS = new ArrayList<>();
                if (playListBean == null || playListBean.result == null || playListBean.result.userprofiles == null || playListBean.result.userprofiles.isEmpty()) {
                    callback.onSuccess(searchVOS);
                }
                if (playListBean != null) {
                    playListBean.result.userprofiles.forEach(user -> {
                        SearchVO searchVO = new SearchVO(user.userId, user.nickname, user.signature);
                        searchVOS.add(searchVO);
                    });
                }
                callback.onSuccess(searchVOS);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });

    }

    @Override
    public void playListInfo(String id, HttpCallback<PlayListVO> callback) {
        ModLogger.info(getName() + " " + "查看歌单： " + id);
        OkHttpUtil.get(getBaseUrl() + "/playlist/detail?id=" + id + "&limit=10", PlayListBean.class, new HttpCallback<PlayListBean>() {
            @Override
            public void onSuccess(PlayListBean playListBean) {
                PlayListVO playListVO = new PlayListVO();
                if (playListBean == null || playListBean.playlist == null) {
                    callback.onSuccess(playListVO);
                }
                if (playListBean != null) {
                    playListVO.setId(String.valueOf(playListBean.playlist.id));
                }
                if (playListBean != null) {
                    playListVO.setName(playListBean.playlist.name);
                }
                if (playListBean != null) {
                    playListVO.setUserId(String.valueOf(playListBean.playlist.creator.userId));
                }
                if (playListBean != null) {
                    playListVO.setUsername(playListBean.playlist.creator.nickname);
                }
                ArrayList<SearchVO> searchVOS = new ArrayList<>();
                if (playListBean != null) {
                    playListBean.playlist.tracks.forEach(tracksDTO -> {
                        searchVOS.add(new SearchVO(tracksDTO.id, tracksDTO.name, tracksDTO.al.name));
                    });
                }
                playListVO.setSongsList(searchVOS);
                callback.onSuccess(playListVO);
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }

    @Override
    public void userPlayList(String id, HttpCallback<UserVO> callback) {
        ModLogger.info(getName() + " " + "查看用户歌单： " + id);
        OkHttpUtil.get(getBaseUrl() + "/user/playlist?uid=" + id, UserPlayListBean.class, new HttpCallback<UserPlayListBean>() {
            @Override
            public void onSuccess(UserPlayListBean userPlayListBean) {
                if (userPlayListBean == null || userPlayListBean.playlist == null || userPlayListBean.playlist.isEmpty()) {
                    callback.onSuccess(null);
                }
                if (userPlayListBean != null) {
                    UserVO userVO = new UserVO(Objects.toString(userPlayListBean.playlist.get(0).creator.userId),
                            userPlayListBean.playlist.get(0).creator.nickname,
                            userPlayListBean.playlist.get(0).creator.signature);
                    ArrayList<SearchVO> searchVOS = new ArrayList<>();
                    userPlayListBean.playlist.forEach(playlistDTO -> {
                        searchVOS.add(new SearchVO(playlistDTO.id, playlistDTO.name, playlistDTO.trackCount + "次播放"));
                    });
                    userVO.setPlaylist(searchVOS);
                    callback.onSuccess(userVO);
                }
            }

            @Override
            public void onFailure(Exception e) {
                callback.onFailure(e);
            }
        });
    }
}
