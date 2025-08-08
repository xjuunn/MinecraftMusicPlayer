package com.junhsiun;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

public class Test {
    public static void main(String[] args) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL("http://music.163.com/song/media/outer/url?id=1473721397.mp3").openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            String location = conn.getHeaderField("location");
            System.out.println(location);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
