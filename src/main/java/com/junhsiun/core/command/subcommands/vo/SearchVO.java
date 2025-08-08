package com.junhsiun.core.command.subcommands.vo;

public class SearchVO {
    long id;
    String name;
    String describe;
    boolean isVip;

    public SearchVO(long id, String name, String describe) {
        this.id = id;
        this.name = name;
        this.describe = describe;
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
}
