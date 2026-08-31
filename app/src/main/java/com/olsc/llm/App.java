package com.olsc.llm;

import android.annotation.SuppressLint;
import android.content.Context;

import android.app.Application;

public class App extends Application {
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mContext = getApplicationContext();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 启动Web服务器
        WebServerService.startServer();
    }

    public static Context getContext() {
        return mContext;
    }
} 