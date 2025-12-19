package com.olsc.llm;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 用于显示应用日志内容的活动页面。
 */
public class LogDisplayActivity extends Activity {

    private TextView logContentTextView;
    private ScrollView logScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置布局文件
        setContentView(R.layout.log_display);

        // 初始化 UI 控件
        logContentTextView = findViewById(R.id.log_content_text_view);
        logScrollView = findViewById(R.id.log_scroll_view);

        // 加载并显示日志内容
        loadLogsAndDisplay();
    }

    /**
     * 从本地文件加载日志内容并显示到 TextView。
     */
    private void loadLogsAndDisplay() {
        // 获取日志文件路径
        File logFile = new File(getFilesDir(), AppLogger.LOG_FILE_NAME);

        // 检查日志文件是否存在或是否为空
        if (!logFile.exists() || logFile.length() == 0) {
            logContentTextView.setText(getString(R.string.log_empty_message));
            return;
        }

        StringBuilder logContent = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(logFile);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            // 逐行读取日志文件内容
            while ((line = reader.readLine()) != null) {
                logContent.append(line).append("\n");
            }
            // 将读取到的内容设置到 TextView
            logContentTextView.setText(logContent.toString());
            // 滚动到日志底部，显示最新内容
            logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        } catch (Exception e) {
            // 如果读取文件失败，显示错误信息
            logContentTextView.setText(getString(R.string.log_error_reading, e.getMessage()));
            // 注意：这里不能使用 AppLogger，因为它可能依赖于此 Activity 上下文
            // Log.e("LogDisplayActivity", "读取日志文件失败", e);
        }
    }
}
