package com.junhsiun.core.command.subcommands.vo;

public class SearchVO {
    long id;
    String name;
    String describe;
    boolean isVip;

    public SearchVO(long id, String name, String describe, String hover, boolean isVip) {
        this.id = id;
        this.name = name;
        this.describe = describe;
        this.isVip = isVip;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public boolean isVip() {
        return isVip;
    }

    public void setVip(boolean vip) {
        isVip = vip;
    }
}
