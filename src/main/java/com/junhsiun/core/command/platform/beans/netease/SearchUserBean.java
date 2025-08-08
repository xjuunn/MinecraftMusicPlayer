package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SearchUserBean {

    @JsonProperty("result")
    public ResultDTO result;
    @JsonProperty("code")
    public Integer code;

    public static class ResultDTO {
        @JsonProperty("userprofiles")
        public List<UserprofilesDTO> userprofiles;
        @JsonProperty("userprofileCount")
        public Integer userprofileCount;

        public static class UserprofilesDTO {
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
            public AvatarDetailDTO avatarDetail;
            @JsonProperty("avatarImgIdStr")
            public String avatarImgIdStr;
            @JsonProperty("backgroundImgIdStr")
            public String backgroundImgIdStr;
            @JsonProperty("anchor")
            public Boolean anchor;
            @JsonProperty("avatarImgId_str")
            public String avatarimgidStr;
            @JsonProperty("followeds")
            public Integer followeds;
            @JsonProperty("follows")
            public Integer follows;
            @JsonProperty("alg")
            public String alg;
            @JsonProperty("playlistCount")
            public Integer playlistCount;
            @JsonProperty("playlistBeSubscribedCount")
            public Integer playlistBeSubscribedCount;

            public static class AvatarDetailDTO {
                @JsonProperty("userType")
                public Integer userType;
                @JsonProperty("identityLevel")
                public Integer identityLevel;
                @JsonProperty("identityIconUrl")
                public String identityIconUrl;
            }
        }
    }
}
