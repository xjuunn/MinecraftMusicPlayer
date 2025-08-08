package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchBean {

    @JsonProperty("result")
    public ResultDTO result;
    @JsonProperty("code")
    public Integer code;
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonProperty("queryRewriteInfo")
    private Object queryRewriteInfo;

    public static class ResultDTO {
        @JsonProperty("searchQcReminder")
        public Object searchQcReminder;
        @JsonProperty("songs")
        public List<SongsDTO> songs;
        @JsonProperty("songCount")
        public Integer songCount;
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonProperty("queryRewriteInfo")
        private Object queryRewriteInfo;

        public static class SongsDTO {
            @JsonProperty("name")
            public String name;
            @JsonProperty("id")
            public long id;
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
            public Object rt;
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
            public HDTO hr;
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
            public Integer djId;
            @JsonProperty("copyright")
            public Integer copyright;
            @JsonProperty("s_id")
            public long sId;
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
            @JsonProperty("single")
            public Integer single;
            @JsonProperty("noCopyrightRcmd")
            public Object noCopyrightRcmd;
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
            public Long publishTime;
            @JsonProperty("privilege")
            public PrivilegeDTO privilege;
            @JsonProperty("tns")
            public List<String> tns;
            @JsonIgnoreProperties(ignoreUnknown = true)
            @JsonProperty("queryRewriteInfo")
            private Object queryRewriteInfo;
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class AlDTO {
                @JsonProperty("id")
                public long id;
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
                @JsonIgnoreProperties(ignoreUnknown = true)
                @JsonProperty("queryRewriteInfo")
                private Object queryRewriteInfo;
            }
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class HDTO {
                @JsonProperty("br")
                public Integer br;
                @JsonProperty("fid")
                public Integer fid;
                @JsonProperty("size")
                public Integer size;
                @JsonProperty("vd")
                public Integer vd;
                @JsonProperty("sr")
                public Integer sr;
                @JsonIgnoreProperties(ignoreUnknown = true)
                @JsonProperty("queryRewriteInfo")
                private Object queryRewriteInfo;
            }
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class PrivilegeDTO {
                @JsonProperty("id")
                public long id;
                @JsonProperty("fee")
                public Integer fee;
                @JsonProperty("payed")
                public Integer payed;
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
                @JsonProperty("toast")
                public Boolean toast;
                @JsonProperty("flag")
                public Integer flag;
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
                @JsonIgnoreProperties(ignoreUnknown = true)
                @JsonProperty("queryRewriteInfo")
                private Object queryRewriteInfo;
                @JsonIgnoreProperties(ignoreUnknown = true)
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
                    @JsonIgnoreProperties(ignoreUnknown = true)
                    @JsonProperty("queryRewriteInfo")
                    private Object queryRewriteInfo;
                }
                @JsonIgnoreProperties(ignoreUnknown = true)
                public static class ChargeInfoListDTO {
                    @JsonProperty("rate")
                    public Integer rate;
                    @JsonProperty("chargeUrl")
                    public Object chargeUrl;
                    @JsonProperty("chargeMessage")
                    public Object chargeMessage;
                    @JsonProperty("chargeType")
                    public Integer chargeType;
                    @JsonIgnoreProperties(ignoreUnknown = true)
                    @JsonProperty("queryRewriteInfo")
                    private Object queryRewriteInfo;
                }
            }
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class ArDTO {
                @JsonProperty("id")
                public long id;
                @JsonProperty("name")
                public String name;
                @JsonProperty("tns")
                public List<?> tns;
                @JsonProperty("alias")
                @JsonIgnoreProperties(ignoreUnknown = true)
                public List<?> alias;
                @JsonIgnoreProperties(ignoreUnknown = true)
                @JsonProperty("queryRewriteInfo")
                private Object queryRewriteInfo;
            }
        }
    }
}
