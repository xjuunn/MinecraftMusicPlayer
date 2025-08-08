package com.junhsiun.core.command.platform.beans.netease;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class NeteaseGetUrlBean {

    @JsonProperty("data")
    public List<DataDTO> data;
    @JsonProperty("code")
    public Integer code;

    public static class DataDTO {
        @JsonProperty("id")
        public long id;
        @JsonProperty("url")
        public String url;
        @JsonProperty("br")
        public Integer br;
        @JsonProperty("size")
        public Integer size;
        @JsonProperty("md5")
        public String md5;
        @JsonProperty("code")
        public Integer code;
        @JsonProperty("expi")
        public Integer expi;
        @JsonProperty("type")
        public String type;
        @JsonProperty("gain")
        public Double gain;
        @JsonProperty("peak")
        public Integer peak;
        @JsonProperty("closedGain")
        public Integer closedGain;
        @JsonProperty("closedPeak")
        public Double closedPeak;
        @JsonProperty("fee")
        public Integer fee;
        @JsonProperty("uf")
        public Object uf;
        @JsonProperty("payed")
        public Integer payed;
        @JsonProperty("flag")
        public Integer flag;
        @JsonProperty("canExtend")
        public Boolean canExtend;
        @JsonProperty("freeTrialInfo")
        public Object freeTrialInfo;
        @JsonProperty("level")
        public String level;
        @JsonProperty("encodeType")
        public String encodeType;
        @JsonProperty("channelLayout")
        public Object channelLayout;
        @JsonProperty("freeTrialPrivilege")
        public FreeTrialPrivilegeDTO freeTrialPrivilege;
        @JsonProperty("freeTimeTrialPrivilege")
        public FreeTimeTrialPrivilegeDTO freeTimeTrialPrivilege;
        @JsonProperty("urlSource")
        public Integer urlSource;
        @JsonProperty("rightSource")
        public Integer rightSource;
        @JsonProperty("podcastCtrp")
        public Object podcastCtrp;
        @JsonProperty("effectTypes")
        public Object effectTypes;
        @JsonProperty("time")
        public Integer time;
        @JsonProperty("message")
        public Object message;
        @JsonProperty("levelConfuse")
        public Object levelConfuse;
        @JsonProperty("musicId")
        public String musicId;
        @JsonProperty("accompany")
        public Object accompany;
        @JsonProperty("sr")
        public Integer sr;
        @JsonProperty("auEff")
        public Object auEff;

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
        }

        public static class FreeTimeTrialPrivilegeDTO {
            @JsonProperty("resConsumable")
            public Boolean resConsumable;
            @JsonProperty("userConsumable")
            public Boolean userConsumable;
            @JsonProperty("type")
            public Integer type;
            @JsonProperty("remainTime")
            public Integer remainTime;
        }
    }
}
