package com.olsc.llm;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class WebViewActivity extends AppCompatActivity {

    private static final String TAG = "WebViewActivity";
    private WebView mWebView;
    private FrameLayout parent;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        // 设置返回按钮逻辑
        View backBtn = findViewById(R.id.btn_web_back);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> onBackPressed());
        }

        try {
            // 检查 Web 服务器是否正在运行
            if (!WebServerService.isRunning()) {
                WebServerService.startServer();
            }

            parent = findViewById(R.id.web_parent);
            if (parent != null) {
                parent.removeAllViews();
                mWebView = new WebView(this);
                WebSettings settings = mWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(true);
                settings.setAllowContentAccess(true);
                settings.setAllowFileAccessFromFileURLs(true);
                settings.setAllowUniversalAccessFromFileURLs(true);
                
                parent.addView(mWebView);
                mWebView.loadUrl("http://localhost:8080/index.html");
                Log.i(TAG, "Successfully loaded page using WebView");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize WebView: " + e.getMessage());
            Toast.makeText(this, "网页加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            cleanup();
            finish();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        try {
            if (mWebView != null) {
                mWebView.stopLoading();
                mWebView.destroy();
                mWebView = null;
            }
            if (parent != null) {
                parent.removeAllViews();
                parent = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage());
        }
    }
}