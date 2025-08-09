package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class UserPlayListBean {

    @JsonProperty("more")
    public Boolean more;
    @JsonProperty("playlist")
    public List<PlaylistDTO> playlist;
    @JsonProperty("code")
    public Integer code;

    public static class PlaylistDTO {
        @JsonProperty("subscribers")
        public List<?> subscribers;
        @JsonProperty("subscribed")
        public Object subscribed;
        @JsonProperty("creator")
        public CreatorDTO creator;
        @JsonProperty("artists")
        public Object artists;
        @JsonProperty("tracks")
        public Object tracks;
        @JsonProperty("top")
        public Boolean top;
        @JsonProperty("updateFrequency")
        public Object updateFrequency;
        @JsonProperty("backgroundCoverId")
        public Integer backgroundCoverId;
        @JsonProperty("backgroundCoverUrl")
        public Object backgroundCoverUrl;
        @JsonProperty("titleImage")
        public Integer titleImage;
        @JsonProperty("titleImageUrl")
        public Object titleImageUrl;
        @JsonProperty("englishTitle")
        public Object englishTitle;
        @JsonProperty("opRecommend")
        public Boolean opRecommend;
        @JsonProperty("recommendInfo")
        public Object recommendInfo;
        @JsonProperty("subscribedCount")
        public Integer subscribedCount;
        @JsonProperty("cloudTrackCount")
        public Integer cloudTrackCount;
        @JsonProperty("userId")
        public long userId;
        @JsonProperty("totalDuration")
        public Integer totalDuration;
        @JsonProperty("coverImgId")
        public Long coverImgId;
        @JsonProperty("privacy")
        public Integer privacy;
        @JsonProperty("trackUpdateTime")
        public Long trackUpdateTime;
        @JsonProperty("trackCount")
        public Integer trackCount;
        @JsonProperty("updateTime")
        public Long updateTime;
        @JsonProperty("commentThreadId")
        public String commentThreadId;
        @JsonProperty("coverImgUrl")
        public String coverImgUrl;
        @JsonProperty("specialType")
        public Integer specialType;
        @JsonProperty("anonimous")
        public Boolean anonimous;
        @JsonProperty("createTime")
        public Long createTime;
        @JsonProperty("highQuality")
        public Boolean highQuality;
        @JsonProperty("newImported")
        public Boolean newImported;
        @JsonProperty("trackNumberUpdateTime")
        public Long trackNumberUpdateTime;
        @JsonProperty("playCount")
        public Integer playCount;
        @JsonProperty("adType")
        public Integer adType;
        @JsonProperty("description")
        public Object description;
        @JsonProperty("tags")
        public List<?> tags;
        @JsonProperty("ordered")
        public Boolean ordered;
        @JsonProperty("status")
        public Integer status;
        @JsonProperty("name")
        public String name;
        @JsonProperty("id")
        public Long id;
        @JsonProperty("coverImgId_str")
        public String coverimgidStr;
        @JsonProperty("sharedUsers")
        public Object sharedUsers;
        @JsonProperty("shareStatus")
        public Object shareStatus;
        @JsonProperty("copied")
        public Boolean copied;
        @JsonProperty("containsTracks")
        public Boolean containsTracks;

        public static class CreatorDTO {
            @JsonProperty("defaultAvatar")
            public Boolean defaultAvatar;
            @JsonProperty("province")
            public Integer province;
            @JsonProperty("authStatus")
            public Integer authStatus;
            @JsonProperty("followed")
            public Boolean followed;
            @JsonProperty("avatarUrl")
            public String avatarUrl;
            @JsonProperty("accountStatus")
            public Integer accountStatus;
            @JsonProperty("gender")
            public Integer gender;
            @JsonProperty("city")
            public Integer city;
            @JsonProperty("birthday")
            public Integer birthday;
            @JsonProperty("userId")
            public long userId;
            @JsonProperty("userType")
            public Integer userType;
            @JsonProperty("nickname")
            public String nickname;
            @JsonProperty("signature")
            public String signature;
            @JsonProperty("description")
            public String description;
            @JsonProperty("detailDescription")
            public String detailDescription;
            @JsonProperty("avatarImgId")
            public Long avatarImgId;
            @JsonProperty("backgroundImgId")
            public Long backgroundImgId;
            @JsonProperty("backgroundUrl")
            public String backgroundUrl;
            @JsonProperty("authority")
            public Integer authority;
            @JsonProperty("mutual")
            public Boolean mutual;
            @JsonProperty("expertTags")
            public Object expertTags;
            @JsonProperty("experts")
            public Object experts;
            @JsonProperty("djStatus")
            public Integer djStatus;
            @JsonProperty("vipType")
            public Integer vipType;
            @JsonProperty("remarkName")
            public Object remarkName;
            @JsonProperty("authenticationTypes")
            public Integer authenticationTypes;
            @JsonProperty("avatarDetail")
            public Object avatarDetail;
            @JsonProperty("avatarImgIdStr")
            public String avatarImgIdStr;
            @JsonProperty("backgroundImgIdStr")
            public String backgroundImgIdStr;
            @JsonProperty("anchor")
            public Boolean anchor;
            @JsonProperty("avatarImgId_str")
            public String avatarimgidStr;
        }
    }
}
