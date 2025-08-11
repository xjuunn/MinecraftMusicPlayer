package com.junhsiun.core.utils;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface HttpCallback<T> {
    void onSuccess(T response);

    default void onFailure(Exception e) {
        ModLogger.error(e.toString());
    }
}
