package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SearchPlayListBean {

    @JsonProperty("result")
    public ResultDTO result;
    @JsonProperty("code")
    public Integer code;

    public static class ResultDTO {
        @JsonProperty("searchQcReminder")
        public Object searchQcReminder;
        @JsonProperty("playlists")
        public List<PlaylistsDTO> playlists;
        @JsonProperty("playlistCount")
        public Integer playlistCount;

        public static class PlaylistsDTO {
            @JsonProperty("id")
            public Long id;
            @JsonProperty("name")
            public String name;
            @JsonProperty("coverImgUrl")
            public String coverImgUrl;
            @JsonProperty("creator")
            public CreatorDTO creator;
            @JsonProperty("subscribed")
            public Boolean subscribed;
            @JsonProperty("trackCount")
            public Integer trackCount;
            @JsonProperty("userId")
            public long userId;
            @JsonProperty("playCount")
            public Integer playCount;
            @JsonProperty("bookCount")
            public Integer bookCount;
            @JsonProperty("specialType")
            public Integer specialType;
            @JsonProperty("officialTags")
            public Object officialTags;
            @JsonProperty("action")
            public Object action;
            @JsonProperty("actionType")
            public Object actionType;
            @JsonProperty("recommendText")
            public Object recommendText;
            @JsonProperty("score")
            public Object score;
            @JsonProperty("officialPlaylistTitle")
            public Object officialPlaylistTitle;
            @JsonProperty("playlistType")
            public String playlistType;
            @JsonProperty("description")
            public Object description;
            @JsonProperty("highQuality")
            public Boolean highQuality;

            public static class CreatorDTO {
                @JsonProperty("nickname")
                public String nickname;
                @JsonProperty("userId")
                public long userId;
                @JsonProperty("userType")
                public Integer userType;
                @JsonProperty("avatarUrl")
                public Object avatarUrl;
                @JsonProperty("authStatus")
                public Integer authStatus;
                @JsonProperty("expertTags")
                public Object expertTags;
                @JsonProperty("experts")
                public Object experts;
            }
        }
    }
}
