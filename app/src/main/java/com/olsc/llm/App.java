package com.olsc.llm;

import android.content.Context;

import org.chromium.base.ApplicationStatus;
import org.xwalk.core.XWalkApplication;

public class App extends XWalkApplication {
    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mContext = getApplicationContext();
        } catch (Exception e) {
            e.printStackTrace();
        }
        ApplicationStatus.initialize(this);
        
        // 启动Web服务器
        WebServerService.startServer();
    }

    public static Context getContext() {
        return mContext;
    }
} 