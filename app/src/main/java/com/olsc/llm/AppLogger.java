package com.olsc.llm;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 应用日志工具类，将日志同时输出到 Logcat 和本地文件。
 */
public class AppLogger {

    // 日志文件名
    public static final String LOG_FILE_NAME = "app_logs.txt";
    private static Context applicationContext;
    // 日期格式，用于日志时间戳
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

    /**
     * 初始化日志工具，必须在应用启动时调用。
     * @param context 应用上下文
     */
    public static void init(Context context) {
        applicationContext = context.getApplicationContext();
        // 可以在此处选择清空旧日志文件，但为了保持历史记录，此处不进行清空。
    }

    /**
     * 将日志写入本地文件。
     * @param level 日志级别 (如 INFO, ERROR, DEBUG)
     * @param tag 日志标签
     * @param message 日志消息
     * @param tr 异常对象 (可选)
     */
    private static void writeLogToFile(String level, String tag, String message, Throwable tr) {
        // 如果未初始化，则仅输出到 Logcat
        if (applicationContext == null) {
            Log.e("AppLogger", "AppLogger 未初始化。请先调用 init() 方法。");
            return;
        }

        // 格式化日志行
        String timestamp = dateFormat.format(new Date());
        String logLine = String.format(Locale.getDefault(), "%s [%s/%s]: %s", timestamp, level, tag, message);

        // 如果存在异常，则添加堆栈跟踪信息
        if (tr != null) {
            logLine += "\n" + Log.getStackTraceString(tr);
        }
        logLine += "\n"; // 每条日志末尾添加换行符

        // 获取日志文件路径，并以追加模式写入
        File logFile = new File(applicationContext.getFilesDir(), LOG_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(logFile, true); // true 表示追加模式
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            osw.write(logLine);
        } catch (IOException e) {
            // 如果写入文件失败，则仅输出到 Logcat
            Log.e("AppLogger", "写入日志文件失败: " + e.getMessage(), e);
        }
    }

    // ===================================================================================
    // Logcat 和文件同步日志方法
    // ===================================================================================

    public static void logV(String tag, String msg) {
        Log.v(tag, msg);
        writeLogToFile("VERBOSE", tag, msg, null);
    }

    public static void logV(String tag, String msg, Throwable tr) {
        Log.v(tag, msg, tr);
        writeLogToFile("VERBOSE", tag, msg, tr);
    }

    public static void logD(String tag, String msg) {
        Log.d(tag, msg);
        writeLogToFile("DEBUG", tag, msg, null);
    }

    public static void logD(String tag, String msg, Throwable tr) {
        Log.d(tag, msg, tr);
        writeLogToFile("DEBUG", tag, msg, tr);
    }

    public static void logI(String tag, String msg) {
        Log.i(tag, msg);
        writeLogToFile("INFO", tag, msg, null);
    }

    public static void logI(String tag, String msg, Throwable tr) {
        Log.i(tag, msg, tr);
        writeLogToFile("INFO", tag, msg, tr);
    }

    public static void logW(String tag, String msg) {
        Log.w(tag, msg);
        writeLogToFile("WARN", tag, msg, null);
    }

    public static void logW(String tag, String msg, Throwable tr) {
        Log.w(tag, msg, tr);
        writeLogToFile("WARN", tag, msg, tr);
    }

    public static void logE(String tag, String msg) {
        Log.e(tag, msg);
        writeLogToFile("ERROR", tag, msg, null);
    }

    public static void logE(String tag, String msg, Throwable tr) {
        Log.e(tag, msg, tr);
        writeLogToFile("ERROR", tag, msg, tr);
    }

    public static void logWtf(String tag, String msg) {
        Log.wtf(tag, msg);
        writeLogToFile("ASSERT", tag, msg, null);
    }

    public static void logWtf(String tag, String msg, Throwable tr) {
        Log.wtf(tag, msg, tr);
        writeLogToFile("ASSERT", tag, msg, tr);
    }

    /**
     * 清空本地日志文件。
     */
    public static void clearLogs() {
        if (applicationContext == null) {
            Log.e("AppLogger", "AppLogger 未初始化。请先调用 init() 方法。");
            return;
        }
        File logFile = new File(applicationContext.getFilesDir(), LOG_FILE_NAME);
        if (logFile.exists()) {
            try (FileOutputStream fos = new FileOutputStream(logFile, false)) { // false 表示覆盖模式
                fos.write("".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.e("AppLogger", "清空日志文件失败: " + e.getMessage(), e);
            }
        }
    }
}
