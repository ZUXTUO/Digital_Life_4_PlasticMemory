package com.olsc.llm;

import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pakdata.xwalk.refactor.XWalkClient;
import com.pakdata.xwalk.refactor.XWalkSettings;
import com.pakdata.xwalk.refactor.XWalkUIClient;
import com.pakdata.xwalk.refactor.XWalkView;

import org.xwalk.core.XWalkInitializer;

import java.io.IOException;

public class WebViewActivity extends AppCompatActivity implements XWalkInitializer.XWalkInitListener {

    private static final String TAG = "WebViewActivity";
    private XWalkView mXWalkView;
    private XWalkInitializer mXWalkInitializer;
    private FrameLayout parent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);
        
        try {
            mXWalkInitializer = new XWalkInitializer(this, this);
            mXWalkInitializer.initAsync();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize XWalk: " + e.getMessage());
            Toast.makeText(this, "XWalk初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            finish();
        }
    }

    private void init() {
        try {
            mXWalkView.setUIClient(new WebUIClient(mXWalkView));
            mXWalkView.setXWalkClient(new WebXWalkClient(mXWalkView));
            mXWalkView.getSettings().setAppCacheEnabled(true);
            mXWalkView.getSettings().setJavaScriptEnabled(true);
            mXWalkView.getSettings().setDomStorageEnabled(true);
            mXWalkView.getSettings().setAllowFileAccess(true);
            mXWalkView.getSettings().setAllowContentAccess(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize XWalkView settings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        parent = findViewById(R.id.web_parent);
        parent.removeAllViews();
        finish();
        super.onBackPressed();
    }

    @Override
    public void onXWalkInitStarted() {
        Log.i(TAG, "onXWalkInitStarted");
    }

    @Override
    public void onXWalkInitCancelled() {
        Log.i(TAG, "onXWalkInitCancelled");
    }

    @Override
    public void onXWalkInitFailed() {
        Log.e(TAG, "onXWalkInitFailed");
        Toast.makeText(this, "XWalk初始化失败，请重启应用", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public void onXWalkInitCompleted() {
        Log.i(TAG, "onXWalkInitCompleted");

        try {
            // 检查Web服务器是否正在运行
            if (!WebServerService.isRunning()) {
                WebServerService.startServer();
            }

            mXWalkView = new XWalkView(this);
            init();

            mXWalkView.clearCache(true);
            parent = findViewById(R.id.web_parent);
            if (parent != null) {
                parent.addView(mXWalkView);
                
                // 直接加载主页面
                mXWalkView.load("http://localhost:8080/index.html", "");
                Log.i(TAG, "Loading main page");
                Log.i(TAG, "version:" + mXWalkView.getSettings().getUserAgentString());
            } else {
                Log.e(TAG, "Parent view is null");
                Toast.makeText(this, "界面初始化失败", Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize WebView: " + e.getMessage());
            Toast.makeText(this, "网页加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        try {
            if (mXWalkView != null) {
                mXWalkView.onDestroy();
                mXWalkView = null;
            }
            if (parent != null) {
                parent.removeAllViews();
                parent = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage());
        }
        
        // 注意：不在这里停止Web服务器，因为它是全局的
    }



    private class WebXWalkClient extends XWalkClient {
        public WebXWalkClient(XWalkView view) {
            super(view);
        }
    }

    private class WebUIClient extends XWalkUIClient {
        public WebUIClient(XWalkView view) {
            super(view);
        }

        @Override
        public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
            Log.i(TAG + "-xwalk", sourceId + " " + lineNumber + ": " + message);
            return super.onConsoleMessage(view, message, lineNumber, sourceId, messageType);
        }
    }
} 