package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayListBean {

    @JsonProperty("code")
    public Integer code;
    @JsonProperty("relatedVideos")
    public Object relatedVideos;
    @JsonProperty("playlist")
    public PlaylistDTO playlist;
    @JsonProperty("urls")
    public Object urls;
    @JsonProperty("privileges")
    public List<PrivilegesDTO> privileges;
    @JsonProperty("sharedPrivilege")
    public Object sharedPrivilege;
    @JsonProperty("resEntrance")
    public Object resEntrance;
    @JsonProperty("fromUsers")
    public Object fromUsers;
    @JsonProperty("fromUserCount")
    public Integer fromUserCount;
    @JsonProperty("songFromUsers")
    public Object songFromUsers;
    @JsonProperty("tns")
    public List<?> tns;

    public static class PlaylistDTO {
        @JsonProperty("id")
        public Long id;
        @JsonProperty("name")
        public String name;
        @JsonProperty("coverImgId")
        public Long coverImgId;
        @JsonProperty("coverImgUrl")
        public String coverImgUrl;
        @JsonProperty("coverImgId_str")
        public String coverimgidStr;
        @JsonProperty("adType")
        public Integer adType;
        @JsonProperty("userId")
        public Long userId;
        @JsonProperty("createTime")
        public Long createTime;
        @JsonProperty("status")
        public Integer status;
        @JsonProperty("opRecommend")
        public Boolean opRecommend;
        @JsonProperty("highQuality")
        public Boolean highQuality;
        @JsonProperty("newImported")
        public Boolean newImported;
        @JsonProperty("updateTime")
        public Long updateTime;
        @JsonProperty("trackCount")
        public Integer trackCount;
        @JsonProperty("specialType")
        public Integer specialType;
        @JsonProperty("privacy")
        public Integer privacy;
        @JsonProperty("trackUpdateTime")
        public Long trackUpdateTime;
        @JsonProperty("commentThreadId")
        public String commentThreadId;
        @JsonProperty("playCount")
        public Integer playCount;
        @JsonProperty("trackNumberUpdateTime")
        public Long trackNumberUpdateTime;
        @JsonProperty("subscribedCount")
        public Integer subscribedCount;
        @JsonProperty("cloudTrackCount")
        public Integer cloudTrackCount;
        @JsonProperty("ordered")
        public Boolean ordered;
        @JsonProperty("description")
        public String description;
        @JsonProperty("tags")
        public List<String> tags;
        @JsonProperty("updateFrequency")
        public Object updateFrequency;
        @JsonProperty("backgroundCoverId")
        public Long backgroundCoverId;
        @JsonProperty("backgroundCoverUrl")
        public Object backgroundCoverUrl;
        @JsonProperty("titleImage")
        public String titleImage;
        @JsonProperty("titleImageUrl")
        public Object titleImageUrl;
        @JsonProperty("detailPageTitle")
        public Object detailPageTitle;
        @JsonProperty("englishTitle")
        public Object englishTitle;
        @JsonProperty("officialPlaylistType")
        public Object officialPlaylistType;
        @JsonProperty("copied")
        public Boolean copied;
        @JsonProperty("relateResType")
        public Object relateResType;
        @JsonProperty("coverStatus")
        public Integer coverStatus;
        @JsonProperty("subscribers")
        public List<?> subscribers;
        @JsonProperty("subscribed")
        public Object subscribed;
        @JsonProperty("creator")
        public CreatorDTO creator;
        @JsonProperty("tracks")
        public List<TracksDTO> tracks;
        @JsonProperty("videoIds")
        public Object videoIds;
        @JsonProperty("videos")
        public Object videos;
        @JsonProperty("trackIds")
        public List<TrackIdsDTO> trackIds;
        @JsonProperty("bannedTrackIds")
        public Object bannedTrackIds;
        @JsonProperty("mvResourceInfos")
        public Object mvResourceInfos;
        @JsonProperty("shareCount")
        public Integer shareCount;
        @JsonProperty("commentCount")
        public Integer commentCount;
        @JsonProperty("remixVideo")
        public Object remixVideo;
        @JsonProperty("newDetailPageRemixVideo")
        public Object newDetailPageRemixVideo;
        @JsonProperty("sharedUsers")
        public Object sharedUsers;
        @JsonProperty("historySharedUsers")
        public Object historySharedUsers;
        @JsonProperty("gradeStatus")
        public String gradeStatus;
        @JsonProperty("score")
        public Object score;
        @JsonProperty("algTags")
        public Object algTags;
        @JsonProperty("distributeTags")
        public List<?> distributeTags;
        @JsonProperty("trialMode")
        public Integer trialMode;
        @JsonProperty("displayTags")
        public Object displayTags;
        @JsonProperty("displayUserInfoAsTagOnly")
        public Boolean displayUserInfoAsTagOnly;
        @JsonProperty("playlistType")
        public String playlistType;
        @JsonProperty("bizExtInfo")
        public BizExtInfoDTO bizExtInfo;
        @JsonProperty("tns")
        public List<?> tns;

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
            public Long userId;
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
            @JsonProperty("anchor")
            public Boolean anchor;
            @JsonProperty("avatarImgIdStr")
            public String avatarImgIdStr;
            @JsonProperty("backgroundImgIdStr")
            public String backgroundImgIdStr;
            @JsonProperty("avatarImgId_str")
            public String avatarimgidStr;
            @JsonProperty("tns")
            public List<?> tns;
        }

        public static class BizExtInfoDTO {
        }

        public static class TracksDTO {
            @JsonProperty("name")
            public String name;
            @JsonProperty("mainTitle")
            public Object mainTitle;
            @JsonProperty("additionalTitle")
            public Object additionalTitle;
            @JsonProperty("id")
            public Long id;
            @JsonProperty("pst")
            public Integer pst;
            @JsonProperty("t")
            public Integer t;
            @JsonProperty("ar")
            public List<ArDTO> ar;
            @JsonProperty("alia")
            public List<?> alia;
            @JsonProperty("pop")
            public Integer pop;
            @JsonProperty("st")
            public Integer st;
            @JsonProperty("rt")
            public String rt;
            @JsonProperty("fee")
            public Integer fee;
            @JsonProperty("v")
            public Integer v;
            @JsonProperty("crbt")
            public Object crbt;
            @JsonProperty("cf")
            public String cf;
            @JsonProperty("al")
            public AlDTO al;
            @JsonProperty("dt")
            public Integer dt;
            @JsonProperty("h")
            public HDTO h;
            @JsonProperty("m")
            public HDTO m;
            @JsonProperty("l")
            public HDTO l;
            @JsonProperty("sq")
            public HDTO sq;
            @JsonProperty("hr")
            public Object hr;
            @JsonProperty("a")
            public Object a;
            @JsonProperty("cd")
            public String cd;
            @JsonProperty("no")
            public Integer no;
            @JsonProperty("rtUrl")
            public Object rtUrl;
            @JsonProperty("ftype")
            public Integer ftype;
            @JsonProperty("rtUrls")
            public List<?> rtUrls;
            @JsonProperty("djId")
            public Long djId;
            @JsonProperty("copyright")
            public Integer copyright;
            @JsonProperty("s_id")
            public Long sId;
            @JsonProperty("mark")
            public Long mark;
            @JsonProperty("originCoverType")
            public Integer originCoverType;
            @JsonProperty("originSongSimpleData")
            public Object originSongSimpleData;
            @JsonProperty("tagPicList")
            public Object tagPicList;
            @JsonProperty("resourceState")
            public Boolean resourceState;
            @JsonProperty("version")
            public Integer version;
            @JsonProperty("songJumpInfo")
            public Object songJumpInfo;
            @JsonProperty("entertainmentTags")
            public Object entertainmentTags;
            @JsonProperty("awardTags")
            public Object awardTags;
            @JsonProperty("displayTags")
            public Object displayTags;
            @JsonProperty("single")
            public Integer single;
            @JsonProperty("noCopyrightRcmd")
            public Object noCopyrightRcmd;
            @JsonProperty("alg")
            public Object alg;
            @JsonProperty("displayReason")
            public Object displayReason;
            @JsonProperty("rtype")
            public Integer rtype;
            @JsonProperty("rurl")
            public Object rurl;
            @JsonProperty("mst")
            public Integer mst;
            @JsonProperty("cp")
            public Integer cp;
            @JsonProperty("mv")
            public Integer mv;
            @JsonProperty("publishTime")
            public String publishTime;
            @JsonProperty("tns")
            public List<?> tns;

            public static class AlDTO {
                @JsonProperty("id")
                public Long id;
                @JsonProperty("name")
                public String name;
                @JsonProperty("picUrl")
                public String picUrl;
                @JsonProperty("tns")
                public List<?> tns;
                @JsonProperty("pic_str")
                public String picStr;
                @JsonProperty("pic")
                public Long pic;
                @JsonProperty("sr")
                public int sr;
            }

            public static class HDTO {
                @JsonProperty("br")
                public Integer br;
                @JsonProperty("fid")
                public Long fid;
                @JsonProperty("size")
                public Integer size;
                @JsonProperty("vd")
                public Integer vd;
                @JsonProperty("sr")
                public int sr;
                @JsonProperty("tns")
                public List<?> tns;
            }

            public static class ArDTO {
                @JsonProperty("id")
                public Long id;
                @JsonProperty("name")
                public String name;
                @JsonProperty("tns")
                public List<?> tns;
                @JsonProperty("alias")
                public List<?> alias;
                @JsonProperty("sr")
                public int sr;
            }
        }

        public static class TrackIdsDTO {
            @JsonProperty("id")
            public Long id;
            @JsonProperty("v")
            public Integer v;
            @JsonProperty("t")
            public Integer t;
            @JsonProperty("at")
            public Long at;
            @JsonProperty("alg")
            public Object alg;
            @JsonProperty("uid")
            public Long uid;
            @JsonProperty("rcmdReason")
            public String rcmdReason;
            @JsonProperty("rcmdReasonTitle")
            public String rcmdReasonTitle;
            @JsonProperty("sc")
            public Object sc;
            @JsonProperty("f")
            public Object f;
            @JsonProperty("sr")
            public Object sr;
            @JsonProperty("dpr")
            public Object dpr;
            @JsonProperty("tns")
            public List<?> tns;
        }
    }

    public static class PrivilegesDTO {
        @JsonProperty("id")
        public Long id;
        @JsonProperty("fee")
        public Integer fee;
        @JsonProperty("payed")
        public Integer payed;
        @JsonProperty("realPayed")
        public Integer realPayed;
        @JsonProperty("st")
        public Integer st;
        @JsonProperty("pl")
        public Integer pl;
        @JsonProperty("dl")
        public Integer dl;
        @JsonProperty("sp")
        public Integer sp;
        @JsonProperty("cp")
        public Integer cp;
        @JsonProperty("subp")
        public Integer subp;
        @JsonProperty("cs")
        public Boolean cs;
        @JsonProperty("maxbr")
        public Integer maxbr;
        @JsonProperty("fl")
        public Integer fl;
        @JsonProperty("pc")
        public Object pc;
        @JsonProperty("toast")
        public Boolean toast;
        @JsonProperty("flag")
        public Integer flag;
        @JsonProperty("paidBigBang")
        public Boolean paidBigBang;
        @JsonProperty("preSell")
        public Boolean preSell;
        @JsonProperty("playMaxbr")
        public Integer playMaxbr;
        @JsonProperty("downloadMaxbr")
        public Integer downloadMaxbr;
        @JsonProperty("maxBrLevel")
        public String maxBrLevel;
        @JsonProperty("playMaxBrLevel")
        public String playMaxBrLevel;
        @JsonProperty("downloadMaxBrLevel")
        public String downloadMaxBrLevel;
        @JsonProperty("plLevel")
        public String plLevel;
        @JsonProperty("dlLevel")
        public String dlLevel;
        @JsonProperty("flLevel")
        public String flLevel;
        @JsonProperty("rscl")
        public Integer rscl;
        @JsonProperty("freeTrialPrivilege")
        public FreeTrialPrivilegeDTO freeTrialPrivilege;
        @JsonProperty("rightSource")
        public Integer rightSource;
        @JsonProperty("chargeInfoList")
        public List<ChargeInfoListDTO> chargeInfoList;
        @JsonProperty("code")
        public Integer code;
        @JsonProperty("message")
        public Object message;
        @JsonProperty("plLevels")
        public Object plLevels;
        @JsonProperty("dlLevels")
        public Object dlLevels;
        @JsonProperty("ignoreCache")
        public Object ignoreCache;
        @JsonProperty("bd")
        public Object bd;
        @JsonProperty("tns")
        public List<?> tns;

        public static class FreeTrialPrivilegeDTO {
            @JsonProperty("resConsumable")
            public Boolean resConsumable;
            @JsonProperty("userConsumable")
            public Boolean userConsumable;
            @JsonProperty("listenType")
            public Object listenType;
            @JsonProperty("cannotListenReason")
            public Object cannotListenReason;
            @JsonProperty("playReason")
            public Object playReason;
            @JsonProperty("freeLimitTagType")
            public Object freeLimitTagType;
            @JsonProperty("tns")
            public List<?> tns;
        }

        public static class ChargeInfoListDTO {
            @JsonProperty("rate")
            public Integer rate;
            @JsonProperty("chargeUrl")
            public Object chargeUrl;
            @JsonProperty("chargeMessage")
            public Object chargeMessage;
            @JsonProperty("chargeType")
            public Integer chargeType;
            @JsonProperty("tns")
            public List<?> tns;
        }
    }
}
