package com.olsc.llm;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements RecognitionListener {

    // 将 TAG 改为中文，并使用 AppLogger
    private static final String TAG = "Chat Isla 日志";
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int MAX_USER_INPUT_LENGTH = 8192; // 最大用户输入长度

    // 模型和服务声明
    private Model model;
    private SpeechService speechService;
    private LlmInference llmInference;

    // UI控件
    private LinearLayout chatMessagesContainer;
    private EditText userInputEditText;
    private ImageButton recordButton;
    private ImageButton sendButton;
    private ScrollView chatScrollView;
    private ImageButton logFloatButton;
    private ImageButton webFloatButton;

    // 状态变量
    private boolean isRecording = false;
    private boolean isModelReady = false;
    private boolean isLlmReady = false;
    private boolean isProcessingAI = false;
    private String lastUserInput = ""; // 存储上一条用户输入，用于长期记忆

    // 流式响应相关变量
    private TextView currentStreamingTextView = null; // 当前正在流式更新的TextView
    private StringBuilder currentStreamingContent = new StringBuilder(); // 当前流式内容缓存
    private String currentUserInputForMemory = ""; // 当前用户输入，用于记忆存储

    // 数据存储
    private StringBuilder currentRecognitionResult;

    // 语音识别超时机制
    private Timer speechTimeoutTimer;
    private TimerTask speechTimeoutTask;
    private static final long SPEECH_TIMEOUT_DELAY = 2000;
    private boolean isContinuousMode = false; // 连续语音模式标志
    private boolean isSmartVoiceMode = false; // 智能语音模式标志 - 点击进入，自动循环，再点击退出
    private String lastRecognizedText = ""; // 上次识别的文本，用于检测是否有新内容

    private String lastTimeoutCheckText = "";

    // 预加载配置和知识库
    private String personaContext = "";
    private List<KnowledgeEntry> knowledgeBase = new ArrayList<>();
    private List<BadDataEntry> badDataList = new ArrayList<>(); // 新增：不良数据列表
    private Random random = new Random();

    // ===================================================================================
    // 记忆系统 (核心 & 长期)
    // ===================================================================================
    private List<CoreMemoryEntry> coreMemories = new ArrayList<>();
    private List<LongTermMemoryEntry> longTermMemories = new ArrayList<>();
    private static final String CORE_MEMORY_FILE = "core_memory.json";
    private static final String LONG_TERM_MEMORY_FILE = "long_term_memory.json"; // 长期记忆使用单个文件
    private static final double CORE_MEMORY_SIMILARITY_THRESHOLD = 0.5;
    private static final double LONG_TERM_MEMORY_SIMILARITY_THRESHOLD = 0.6;

    // 记忆系统优化配置
    private static final int MAX_CORE_MEMORIES = 100; // 最大核心记忆数量
    private static final int MAX_LONG_TERM_MEMORIES = 500; // 最大长期记忆数量
    private static final long MEMORY_CLEANUP_INTERVAL = 24 * 60 * 60 * 1000; // 24小时清理一次
    private static final double MEMORY_DEDUPLICATION_THRESHOLD = 0.85; // 去重相似度阈值
    private long lastMemoryCleanupTime = 0;


    private static final String[] TIME_QUESTIONS = {
            "现在是几点", "现在是什么时间", "当前时间", "现在时间", "现在几点了",
            "几点钟了", "几点了", "现在是早上吗", "现在是早晨吗", "现在是中午吗",
            "现在是晚上吗", "现在是夜晚吗", "现在是下午吗",
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // 初始化 AppLogger
        AppLogger.init(getApplicationContext());

        // 初始化UI和变量
        initViews();
        initVariables();
        setupListeners();

        // 设置Vosk日志级别
        LibVosk.setLogLevel(LogLevel.INFO);

        // 检查并请求麦克风权限
        checkPermissions();

        showLongToast("此为Olsc的免费项目，且无广告，谨防上当受骗", 10);
        showLongToast("可前往BiliBili搜索Olsc，感谢关注!", 10);
    }

    private void showLongToast(String message, int durationInSeconds) {
        final Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
        final int delay = 2000;

        new Thread(() -> {
            int repeat = durationInSeconds * 1000 / delay;
            for (int i = 0; i < repeat; i++) {
                runOnUiThread(toast::show);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    AppLogger.logE(TAG, "Toast 线程中断", e); // 使用 AppLogger
                }
            }
        }).start();
    }

    private void initViews() {
        chatMessagesContainer = findViewById(R.id.chat_messages_container);
        userInputEditText = findViewById(R.id.user_input_edit_text);
        recordButton = findViewById(R.id.record_button);
        sendButton = findViewById(R.id.send_button);
        chatScrollView = findViewById(R.id.chat_scroll_view);
        logFloatButton = findViewById(R.id.log_float_button); // 初始化悬浮按钮
        webFloatButton = findViewById(R.id.web_float_button); // 初始化网页悬浮按钮

        addMessageToChat("艾拉正在准备中...", false);
        //addMessageToChat("Web服务器状态: " + (WebServerService.isRunning() ? "已启动" : "未启动"), false);
        recordButton.setEnabled(false);
        sendButton.setEnabled(false);
    }

    private void initVariables() {
        currentRecognitionResult = new StringBuilder();
        lastMemoryCleanupTime = System.currentTimeMillis(); // 初始化清理时间
        speechTimeoutTimer = new Timer();
    }

    private void setupListeners() {
        // 修改录音按钮为长按启动连续模式，单击为普通模式
        recordButton.setOnClickListener(v -> toggleRecording());
        recordButton.setOnLongClickListener(v -> {
            startContinuousRecording();
            return true;
        });
        sendButton.setOnClickListener(v -> sendMessage());
        // 设置悬浮按钮点击事件
        logFloatButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LogDisplayActivity.class)));
        webFloatButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, WebViewActivity.class)));

        userInputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().trim().isEmpty()) {
                    sendButton.setVisibility(View.GONE);
                    recordButton.setVisibility(View.VISIBLE);
                } else {
                    sendButton.setVisibility(View.VISIBLE);
                    recordButton.setVisibility(View.GONE);
                }
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModelsAndData();
        }
    }

    private void initModelsAndData() {
        loadPersonaContext();
        loadKnowledgeBase();
        loadBadData(); // 新增：加载不良数据
        loadCoreMemory();
        loadLongTermMemory(); // 加载长期记忆
        initVoskModel();
        initLlmModel();
    }

    // ===================================================================================
    // 资源加载 (人设, 知识库)
    // ===================================================================================

    private void loadPersonaContext() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("person.txt"), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            personaContext = sb.toString().trim();
            if (!personaContext.isEmpty()) {
                AppLogger.logI(TAG, "人设上下文已加载。"); // 使用 AppLogger
            } else {
                AppLogger.logW(TAG, "人设文件为空。"); // 使用 AppLogger
            }
        } catch (IOException e) {
            AppLogger.logE(TAG, "加载人设文件失败: " + e.getMessage()); // 使用 AppLogger
        }
    }

    private void loadKnowledgeBase() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("data.jsonl"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    String text = jsonObject.optString("text", "");
                    if (!text.isEmpty()) {
                        String[] parts = text.split("\\n\\n", 2);
                        if (parts.length >= 2) {
                            String userPart = parts[0].replaceFirst("User:\\s*", "").trim();
                            String assistantPart = parts[1].replaceFirst("Assistant:\\s*", "").trim();
                            if (!userPart.isEmpty() && !assistantPart.isEmpty()) {
                                knowledgeBase.add(new KnowledgeEntry(userPart, assistantPart));
                            }
                        }
                    }
                } catch (JSONException e) {
                    AppLogger.logW(TAG, "解析data.jsonl中的JSON行失败: " + line + ", 错误: " + e.getMessage()); // 使用 AppLogger
                }
            }
            if (!knowledgeBase.isEmpty()) {
                AppLogger.logI(TAG, "知识库已加载: " + knowledgeBase.size() + " 条目。"); // 使用 AppLogger
            } else {
                AppLogger.logW(TAG, "知识库文件为空或无有效条目。"); // 使用 AppLogger
            }
        } catch (IOException e) {
            AppLogger.logE(TAG, "加载知识库文件失败: " + e.getMessage()); // 使用 AppLogger
        }
    }

    /**
     * 加载不良数据配置
     */
    private void loadBadData() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("bad_data.jsonl"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    String badText = jsonObject.optString("bad_text", "");
                    String addText = jsonObject.optString("add_text", "");
                    if (!badText.isEmpty() && !addText.isEmpty()) {
                        badDataList.add(new BadDataEntry(badText, addText));
                    }
                } catch (JSONException e) {
                    AppLogger.logW(TAG, "解析bad_data.jsonl中的JSON行失败: " + line + ", 错误: " + e.getMessage()); // 使用 AppLogger
                }
            }
            if (!badDataList.isEmpty()) {
                AppLogger.logI(TAG, "不良数据配置已加载: " + badDataList.size() + " 条目。"); // 使用 AppLogger
            } else {
                AppLogger.logW(TAG, "不良数据配置文件为空或无有效条目。"); // 使用 AppLogger
            }
        } catch (IOException e) {
            AppLogger.logE(TAG, "加载不良数据配置文件失败: " + e.getMessage()); // 使用 AppLogger
        }
    }

    // ===================================================================================
    // 记忆系统 (核心 & 长期)
    // ===================================================================================

    /**
     * 从文件中读取JSON内容
     *
     * @param file 要读取的文件
     * @return 文件内容的字符串，如果失败则返回null
     */
    private String readJsonFromFile(File file) {
        if (!file.exists()) return null;
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            AppLogger.logE(TAG, "从文件读取JSON失败: " + file.getName(), e); // 使用 AppLogger
            return null;
        }
    }

    /**
     * 将JSON数组写入文件
     *
     * @param file      要写入的文件
     * @param jsonArray 要写入的JSON数组
     */
    private void writeJsonToFile(File file, JSONArray jsonArray) {
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            osw.write(jsonArray.toString(4)); // 格式化输出
        } catch (IOException | JSONException e) {
            AppLogger.logE(TAG, "写入JSON到文件失败: " + file.getName(), e); // 使用 AppLogger
        }
    }

    /**
     * 加载核心记忆
     */
    private void loadCoreMemory() {
        File memoryFile = new File(getFilesDir(), CORE_MEMORY_FILE);
        String jsonContent = readJsonFromFile(memoryFile);

        if (jsonContent != null) {
            try {
                JSONArray jsonArray = new JSONArray(jsonContent);
                coreMemories.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    coreMemories.add(new CoreMemoryEntry(
                            obj.getString("id"),
                            obj.getString("time"),
                            obj.getString("text")
                    ));
                }
                AppLogger.logI(TAG, "核心记忆加载成功，共 " + coreMemories.size() + " 条。"); // 使用 AppLogger
            } catch (JSONException e) {
                AppLogger.logE(TAG, "解析核心记忆文件失败。", e); // 使用 AppLogger
            }
        } else {
            AppLogger.logI(TAG, "核心记忆文件不存在，创建新的。"); // 使用 AppLogger
            addCoreMemory("这是我们的第一次相遇。"); // 创建一个初始记忆
        }
    }

    /**
     * 保存所有核心记忆到文件
     */
    private void saveCoreMemory() {
        JSONArray jsonArray = new JSONArray();
        for (CoreMemoryEntry entry : coreMemories) {
            jsonArray.put(entry.toJson());
        }
        File memoryFile = new File(getFilesDir(), CORE_MEMORY_FILE);
        writeJsonToFile(memoryFile, jsonArray);
        AppLogger.logI(TAG, "核心记忆已保存。"); // 使用 AppLogger
    }

    /**
     * 添加一条新的核心记忆（优化版本）
     *
     * @param memoryText 记忆内容
     */
    public void addCoreMemory(String memoryText) {
        if (memoryText == null || memoryText.trim().isEmpty()) {
            AppLogger.logW(TAG, "尝试添加空的核心记忆，已忽略。");
            return;
        }

        // 清理和标准化文本
        String cleanedText = cleanAndValidateText(memoryText);
        if (cleanedText.isEmpty()) {
            AppLogger.logW(TAG, "记忆文本清理后为空，已忽略。");
            return;
        }

        // 检查是否存在重复记忆
        if (isDuplicateCoreMemory(cleanedText)) {
            AppLogger.logI(TAG, "发现重复的核心记忆，已忽略: " + cleanedText);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        String id = UUID.randomUUID().toString();

        CoreMemoryEntry newEntry = new CoreMemoryEntry(id, timestamp, cleanedText);
        coreMemories.add(newEntry);

        // 检查是否需要清理旧记忆
        cleanupCoreMemoriesIfNeeded();

        saveCoreMemory(); // 添加后立即保存
        AppLogger.logI(TAG, "添加了新的核心记忆: " + cleanedText);
    }

    /**
     * 加载长期记忆 (从单个文件)
     */
    private void loadLongTermMemory() {
        File memoryFile = new File(getFilesDir(), LONG_TERM_MEMORY_FILE);
        String jsonContent = readJsonFromFile(memoryFile);

        if (jsonContent != null) {
            try {
                JSONArray jsonArray = new JSONArray(jsonContent);
                longTermMemories.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    longTermMemories.add(LongTermMemoryEntry.fromJson(jsonArray.getJSONObject(i)));
                }
                AppLogger.logI(TAG, "长期记忆加载成功，共 " + longTermMemories.size() + " 条。"); // 使用 AppLogger
            } catch (JSONException e) {
                AppLogger.logE(TAG, "解析长期记忆文件失败。", e); // 使用 AppLogger
            }
        } else {
            AppLogger.logI(TAG, "长期记忆文件不存在，将自动创建。"); // 使用 AppLogger
        }
    }

    /**
     * 异步添加一条长期记忆（使用LLM生成标签）
     *
     * @param userMessage 用户消息
     * @param aiMessage   AI回复
     */
    private void addLongTermMemory(String userMessage, String aiMessage) {
        new Thread(() -> {
            try {
                // 验证输入
                if (userMessage == null || userMessage.trim().isEmpty() ||
                        aiMessage == null || aiMessage.trim().isEmpty()) {
                    AppLogger.logW(TAG, "用户消息或AI回复为空，跳过长期记忆存储。");
                    return;
                }

                // 清理文本
                String cleanedUserMessage = cleanAndValidateText(userMessage);
                String cleanedAiMessage = cleanAndValidateText(aiMessage);

                if (cleanedUserMessage.isEmpty() || cleanedAiMessage.isEmpty()) {
                    AppLogger.logW(TAG, "消息清理后为空，跳过长期记忆存储。");
                    return;
                }

                // 验证内容有效性
                if (!isValidMemoryContent(cleanedUserMessage) || !isValidMemoryContent(cleanedAiMessage)) {
                    AppLogger.logW(TAG, "检测到可能的乱码内容，跳过长期记忆存储。");
                    return;
                }

                // 检查是否为重复对话
                if (isDuplicateLongTermMemory(cleanedUserMessage, cleanedAiMessage)) {
                    AppLogger.logI(TAG, "发现重复的长期记忆对话，已忽略。");
                    return;
                }

                // 1. 使用算法进行分类和标签生成
                MemoryClassificationResult result = classifyAndTagMemory(cleanedUserMessage, cleanedAiMessage);

                AppLogger.logI(TAG, "记忆分类结果: 分类=" + result.category + ", 标签=" + result.tag +
                        ", 重要性=" + String.format("%.2f", result.importance) +
                        ", 应存储=" + result.shouldStore);

                // 2. 检查是否应该存储
                if (!result.shouldStore) {
                    AppLogger.logI(TAG, "根据算法判断，此对话不需要存入长期记忆。");
                    return;
                }

                // 3. 创建增强的长期记忆条目
                long timestamp = System.currentTimeMillis();
                LongTermMemoryEntry entry = new LongTermMemoryEntry(
                        timestamp,
                        result.tag,
                        result.category,
                        cleanedUserMessage,
                        cleanedAiMessage,
                        result.keywords,
                        result.importance
                );

                // 4. 更新内存和文件
                longTermMemories.add(entry);

                // 检查是否需要清理旧记忆
                cleanupLongTermMemoriesIfNeeded();

                addCoreMemory(result.tag); // 将摘要也作为核心记忆

                saveLongTermMemory();

                AppLogger.logI(TAG, "长期记忆已更新并保存。");

            } catch (Exception e) {
                AppLogger.logE(TAG, "添加长期记忆失败。", e);
            }
        }).start();
    }

    /**
     * 异步添加长期记忆（流式响应专用）
     * 为了避免与流式响应冲突，使用简化的标签生成策略
     *
     * @param userMessage 用户消息
     * @param aiMessage   AI回复
     */
    private void addLongTermMemoryAsync(String userMessage, String aiMessage) {
        new Thread(() -> {
            try {
                // 验证输入
                if (userMessage == null || userMessage.trim().isEmpty() ||
                        aiMessage == null || aiMessage.trim().isEmpty()) {
                    AppLogger.logW(TAG, "用户消息或AI回复为空，跳过长期记忆存储。");
                    return;
                }

                // 清理文本
                String cleanedUserMessage = cleanAndValidateText(userMessage);
                String cleanedAiMessage = cleanAndValidateText(aiMessage);

                if (cleanedUserMessage.isEmpty() || cleanedAiMessage.isEmpty()) {
                    AppLogger.logW(TAG, "消息清理后为空，跳过长期记忆存储。");
                    return;
                }

                // 检查是否为重复对话
                if (isDuplicateLongTermMemory(cleanedUserMessage, cleanedAiMessage)) {
                    AppLogger.logI(TAG, "发现重复的长期记忆对话，已忽略。");
                    return;
                }

                // 使用算法进行分类和标签生成（异步版本）
                MemoryClassificationResult result = classifyAndTagMemory(cleanedUserMessage, cleanedAiMessage);

                AppLogger.logI(TAG, "记忆分类结果（异步）: 分类=" + result.category + ", 标签=" + result.tag +
                        ", 重要性=" + String.format("%.2f", result.importance) +
                        ", 应存储=" + result.shouldStore);

                // 检查是否应该存储
                if (!result.shouldStore) {
                    AppLogger.logI(TAG, "根据算法判断，此对话不需要存入长期记忆（异步）。");
                    return;
                }

                // 创建增强的长期记忆条目
                long timestamp = System.currentTimeMillis();
                LongTermMemoryEntry entry = new LongTermMemoryEntry(
                        timestamp,
                        result.tag,
                        result.category,
                        cleanedUserMessage,
                        cleanedAiMessage,
                        result.keywords,
                        result.importance
                );

                // 更新内存和文件
                longTermMemories.add(entry);

                // 检查是否需要清理旧记忆
                cleanupLongTermMemoriesIfNeeded();

                addCoreMemory(result.tag); // 将摘要也作为核心记忆

                saveLongTermMemory();

                AppLogger.logI(TAG, "长期记忆已更新并保存（异步版本）。");

            } catch (Exception e) {
                AppLogger.logE(TAG, "添加长期记忆失败（异步版本）。", e);
            }
        }).start();
    }

    // ===================================================================================
    // 基于算法的记忆分类和标签生成系统
    // ===================================================================================

    // 预定义的分类关键词库
    /**
     * 预定义的分类关键词库，用于对用户输入进行分类。
     * 每个内部数组包含两个元素：[0] 为类别名称，[1] 为以逗号分隔的关键词字符串。
     */
    public static final String[][] CATEGORY_KEYWORDS = {
            {"时间查询", "时间,几点,现在,当前,什么时候,多久,日期,年月日,星期,周几,早上,中午,下午,晚上,夜里,今天,昨天,明天,上个月,下个月,去年,明年,时刻"},
            {"天气询问", "天气,温度,下雨,晴天,阴天,雪,风,湿度,气温,冷,热,暖和,预报,气象,穿衣,紫外线,空气质量,雾霾"},
            {"学习知识", "学习,知识,了解,明白,懂,教,解释,原理,为什么,怎么样,什么是,百科,查阅,资料,课本,教程,解说"},
            {"技术问题", "代码,编程,软件,系统,bug,错误,调试,开发,技术,算法,数据,服务器,数据库,网络,框架,编程语言,硬件"},
            {"生活日常", "吃饭,睡觉,工作,家庭,朋友,购物,娱乐,电影,音乐,游戏,运动,家务,健身,育儿,社交,日常,生活,琐事"},
            {"情感交流", "开心,难过,生气,担心,害怕,兴奋,紧张,放松,压力,心情,感觉,理解,倾听,鼓励,安慰,爱,孤独,幸福"},
            {"健康医疗", "健康,医生,医院,药,病,疼,痛,治疗,检查,症状,身体,营养,运动,疫苗,处方,复诊,诊断,感冒,发烧"},
            {"工作职场", "工作,职业,公司,老板,同事,会议,项目,任务,薪水,升职,辞职,报告,绩效,团队,客户,离职,面试"},
            {"旅行出行", "旅行,旅游,出差,交通,飞机,火车,汽车,酒店,景点,路线,行程,机票,火车票,订酒店,签证,攻略,目的地"},
            {"美食料理", "吃,菜,饭,做饭,烹饪,食材,味道,餐厅,外卖,零食,饮料,菜谱,食谱,饭店,烘焙,烧烤,小吃,甜点"},
            {"购物消费", "买,购买,商品,价格,便宜,贵,打折,优惠,网购,商店,超市,退换,优惠券,包邮,评价,快递,付款,消费,订单"},
            {"教育学校", "学校,老师,学生,考试,作业,课程,专业,大学,毕业,学位,辅导,考研,留学,奖学金,招生,校园"},
            {"家庭关系", "父母,孩子,家人,亲戚,结婚,恋爱,夫妻,兄弟,姐妹,家庭,婆媳,亲子,婆家,娘家,孩子,子女"},
            {"兴趣爱好", "爱好,兴趣,喜欢,收藏,画画,唱歌,跳舞,读书,写作,摄影,运动,阅读,手工,乐器,运动,听歌"},
            {"问候寒暄", "你好,再见,谢谢,不客气,对不起,没关系,早安,晚安,祝福,早上好,晚上好,节日快乐,新年快乐,恭喜,问候"},
            {"求助帮忙", "帮助,帮忙,请问,能否,可以,麻烦,求,救,支援,协助,指导,拜托,能不能,怎么做,找,帮我"},
            {"金融理财", "银行,贷款,股票,基金,投资,账单,存款,理财,账户,还款,支付,收入,开销,汇款"},
            {"交通出行", "地铁,公交,出租车,自行车,步行,导航,路况,开车,打车,班车,交通,路线,车站"},
            {"法律咨询", "法律,律师,合同,官司,权益,纠纷,起诉,法规,公证,协议,证件"},
            {"娱乐休闲", "电影,电视剧,综艺,演唱会,明星,偶像,追剧,影院,KTV,酒吧,度假,放松,休闲"}
    };


    /**
     * 基于算法生成对话标签和分类
     *
     * @param userMessage 用户消息
     * @param aiMessage   AI回复
     * @return 记忆条目信息
     */
    private MemoryClassificationResult classifyAndTagMemory(String userMessage, String aiMessage) {
        String cleanUserMessage = userMessage.toLowerCase(Locale.ROOT);
        String cleanAiMessage = aiMessage.toLowerCase(Locale.ROOT);

        // 1. 基于关键词的分类
        String category = classifyByKeywords(cleanUserMessage);

        // 2. 生成结构化标签
        String tag = generateStructuredTag(userMessage, category);

        // 3. 提取关键词
        List<String> keywords = extractKeywords(userMessage, aiMessage);

        // 4. 计算重要性分数
        double importance = calculateImportanceScore(userMessage, aiMessage, category);

        // 5. 判断是否应该存储
        boolean shouldStore = shouldStoreMemory(userMessage, aiMessage, category, importance);

        return new MemoryClassificationResult(category, tag, keywords, importance, shouldStore);
    }

    /**
     * 基于关键词进行分类
     */
    private String classifyByKeywords(String message) {
        int maxMatches = 0;
        String bestCategory = "日常闲聊";

        for (String[] categoryData : CATEGORY_KEYWORDS) {
            String category = categoryData[0];
            String[] keywords = categoryData[1].split(",");

            int matches = 0;
            for (String keyword : keywords) {
                if (message.contains(keyword.trim())) {
                    matches++;
                }
            }

            if (matches > maxMatches) {
                maxMatches = matches;
                bestCategory = category;
            }
        }

        return maxMatches > 0 ? bestCategory : "日常闲聊";
    }

    /**
     * 生成结构化标签
     */
    private String generateStructuredTag(String userMessage, String category) {
        // 提取主要动词和名词
        String[] words = userMessage.split("[\\s\\p{Punct}]+");
        StringBuilder tagBuilder = new StringBuilder();

        // 添加分类前缀
        tagBuilder.append("[").append(category).append("] ");

        // 提取关键信息
        for (String word : words) {
            if (word.length() >= 2 && isImportantWord(word)) {
                tagBuilder.append(word).append(" ");
                if (tagBuilder.length() > 15) break; // 限制标签长度
            }
        }

        String tag = tagBuilder.toString().trim();
        return tag.length() > 20 ? tag.substring(0, 20) + "..." : tag;
    }

    /**
     * 提取关键词
     */
    private List<String> extractKeywords(String userMessage, String aiMessage) {
        List<String> keywords = new ArrayList<>();
        String combinedText = (userMessage + " " + aiMessage).toLowerCase(Locale.ROOT);

        // 分词并过滤
        String[] words = combinedText.split("[\\s\\p{Punct}]+");
        for (String word : words) {
            if (word.length() >= 2 && isImportantWord(word) && !keywords.contains(word)) {
                keywords.add(word);
                if (keywords.size() >= 10) break; // 限制关键词数量
            }
        }

        return keywords;
    }

    /**
     * 判断是否为重要词汇
     */
    private boolean isImportantWord(String word) {
        // 过滤停用词和无意义词汇
        String[] stopWords = {"的", "了", "是", "在", "有", "和", "就", "不", "人", "都", "一", "个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "什么", "怎么", "可以", "知道", "现在", "如果", "因为", "所以", "但是", "然后", "还是", "或者", "虽然", "已经", "正在", "应该", "可能", "一些", "这些", "那些", "我们", "他们", "她们"};

        for (String stopWord : stopWords) {
            if (word.equals(stopWord)) {
                return false;
            }
        }

        // 检查是否包含有意义的字符
        return word.matches(".*[\\p{IsHan}\\p{IsLatin}].*") && word.length() <= 10;
    }

    /**
     * 计算记忆重要性分数
     */
    private double calculateImportanceScore(String userMessage, String aiMessage, String category) {
        double score = 0.0;

        // 基于分类的基础分数
        switch (category) {
            case "学习知识":
            case "技术问题":
            case "工作职场":
                score += 0.8;
                break;
            case "健康医疗":
            case "家庭关系":
                score += 0.7;
                break;
            case "情感交流":
            case "求助帮忙":
                score += 0.6;
                break;
            case "问候寒暄":
            case "日常闲聊":
                score += 0.2;
                break;
            default:
                score += 0.5;
        }

        // 基于长度的调整
        int totalLength = userMessage.length() + aiMessage.length();
        if (totalLength > 100) score += 0.2;
        if (totalLength > 200) score += 0.1;

        // 基于特殊词汇的调整
        String combined = (userMessage + " " + aiMessage).toLowerCase(Locale.ROOT);
        if (combined.contains("重要") || combined.contains("记住") || combined.contains("不要忘记")) {
            score += 0.3;
        }

        return Math.min(1.0, score);
    }

    /**
     * 判断是否应该存储记忆
     */
    private boolean shouldStoreMemory(String userMessage, String aiMessage, String category, double importance) {
        // 过滤掉不重要的对话
        if (importance < 0.3) {
            return false;
        }

        // 过滤掉过短的对话
        if (userMessage.length() < 5 || aiMessage.length() < 10) {
            return false;
        }

        // 过滤掉纯问候
        if (category.equals("问候寒暄") && importance < 0.5) {
            return false;
        }

        return true;
    }

    /**
     * 生成简化的对话标签（保留作为备用）
     *
     * @param userMessage 用户消息
     * @param aiMessage   AI回复
     * @return 简化标签
     */
    private String generateSimpleTag(String userMessage, String aiMessage) {
        MemoryClassificationResult result = classifyAndTagMemory(userMessage, aiMessage);
        return result.tag;
    }

    // ===================================================================================
    // 记忆系统优化方法
    // ===================================================================================

    /**
     * 清理和验证文本，防止乱码和无效字符
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String cleanAndValidateText(String text) {
        if (text == null) {
            return "";
        }

        // 移除控制字符和不可见字符
        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                .replaceAll("\\p{C}", "") // 移除其他控制字符
                .trim();

        // 移除多余的空白字符
        cleaned = cleaned.replaceAll("\\s+", " ");

        // 检查是否包含有效内容（至少包含一个字母、数字或中文字符）
        if (!cleaned.matches(".*[\\p{L}\\p{N}\\p{IsHan}].*")) {
            return "";
        }

        // 限制长度，防止过长的文本
        if (cleaned.length() > 1000) {
            cleaned = cleaned.substring(0, 1000) + "...";
        }

        return cleaned;
    }

    /**
     * 检查是否为重复的核心记忆
     *
     * @param newMemoryText 新的记忆文本
     * @return 如果重复则返回true
     */
    private boolean isDuplicateCoreMemory(String newMemoryText) {
        for (CoreMemoryEntry entry : coreMemories) {
            double similarity = calculateSimilarity(newMemoryText, entry.text);
            if (similarity >= MEMORY_DEDUPLICATION_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否为重复的长期记忆
     *
     * @param userMessage 用户消息
     * @param aiMessage   AI回复
     * @return 如果重复则返回true
     */
    private boolean isDuplicateLongTermMemory(String userMessage, String aiMessage) {
        for (LongTermMemoryEntry entry : longTermMemories) {
            double userSimilarity = calculateSimilarity(userMessage, entry.userMessage);
            double aiSimilarity = calculateSimilarity(aiMessage, entry.aiMessage);

            // 如果用户消息和AI回复都高度相似，则认为是重复
            if (userSimilarity >= MEMORY_DEDUPLICATION_THRESHOLD &&
                    aiSimilarity >= MEMORY_DEDUPLICATION_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理核心记忆，如果数量超过限制
     */
    private void cleanupCoreMemoriesIfNeeded() {
        if (coreMemories.size() > MAX_CORE_MEMORIES) {
            // 按时间排序，移除最旧的记忆
            coreMemories.sort((a, b) -> a.time.compareTo(b.time));

            int toRemove = coreMemories.size() - MAX_CORE_MEMORIES;
            for (int i = 0; i < toRemove; i++) {
                coreMemories.remove(0);
            }

            AppLogger.logI(TAG, "清理了 " + toRemove + " 条旧的核心记忆");
        }
    }

    /**
     * 清理长期记忆，如果数量超过限制
     */
    private void cleanupLongTermMemoriesIfNeeded() {
        if (longTermMemories.size() > MAX_LONG_TERM_MEMORIES) {
            // 按时间排序，移除最旧的记忆
            longTermMemories.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

            int toRemove = longTermMemories.size() - MAX_LONG_TERM_MEMORIES;
            for (int i = 0; i < toRemove; i++) {
                longTermMemories.remove(0);
            }

            AppLogger.logI(TAG, "清理了 " + toRemove + " 条旧的长期记忆");
        }

        // 定期清理过期记忆
        performPeriodicMemoryCleanup();
    }

    /**
     * 执行定期记忆清理
     */
    private void performPeriodicMemoryCleanup() {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMemoryCleanupTime > MEMORY_CLEANUP_INTERVAL) {
            // 移除超过30天的长期记忆
            long thirtyDaysAgo = currentTime - (30L * 24 * 60 * 60 * 1000);

            int originalSize = longTermMemories.size();
            longTermMemories.removeIf(entry -> entry.timestamp < thirtyDaysAgo);
            int removedCount = originalSize - longTermMemories.size();

            if (removedCount > 0) {
                AppLogger.logI(TAG, "定期清理移除了 " + removedCount + " 条过期长期记忆");
            }

            lastMemoryCleanupTime = currentTime;
        }
    }

    /**
     * 保存长期记忆到文件
     */
    private void saveLongTermMemory() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (LongTermMemoryEntry ltmEntry : longTermMemories) {
                jsonArray.put(ltmEntry.toJson());
            }

            File ltmFile = new File(getFilesDir(), LONG_TERM_MEMORY_FILE);
            writeJsonToFile(ltmFile, jsonArray);

            AppLogger.logI(TAG, "长期记忆已保存，共 " + longTermMemories.size() + " 条。");
        } catch (Exception e) {
            AppLogger.logE(TAG, "保存长期记忆失败。", e);
        }
    }

    /**
     * 验证记忆内容的有效性
     *
     * @param content 记忆内容
     * @return 是否有效
     */
    private boolean isValidMemoryContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含过多的重复字符（可能是乱码）
        String cleaned = content.replaceAll("\\s", "");
        if (cleaned.length() > 0) {
            char firstChar = cleaned.charAt(0);
            int sameCharCount = 0;
            for (char c : cleaned.toCharArray()) {
                if (c == firstChar) {
                    sameCharCount++;
                }
            }

            // 如果超过80%都是同一个字符，可能是乱码
            if ((double) sameCharCount / cleaned.length() > 0.8) {
                return false;
            }
        }

        // 检查是否包含过多的特殊字符
        long specialCharCount = content.chars()
                .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
                .count();

        // 如果特殊字符超过50%，可能是乱码
        if ((double) specialCharCount / content.length() > 0.5) {
            return false;
        }

        return true;
    }

    /**
     * 修复常见的乱码文本问题
     *
     * @param text 可能包含乱码的文本
     * @return 修复后的文本
     */
    private String fixCommonGarbledText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String fixed = text;

        // 移除连续的重复字符（超过3个相同字符）
        fixed = fixed.replaceAll("(.)\\1{3,}", "$1$1$1");

        // 移除异常的Unicode字符
        fixed = fixed.replaceAll("[\\uFFFD\\uFEFF]", ""); // 替换字符和BOM

        // 移除过多的标点符号
        fixed = fixed.replaceAll("[!]{3,}", "!!!");
        fixed = fixed.replaceAll("[?]{3,}", "???");
        fixed = fixed.replaceAll("[.]{4,}", "...");

        // 如果修复后的文本太短或无效，返回一个默认消息
        if (fixed.trim().length() < 3 || !isValidMemoryContent(fixed)) {
            return "[AI响应异常，请重试]";
        }

        return fixed.trim();
    }

    /**
     * 获取记忆系统统计信息
     *
     * @return 统计信息字符串
     */
    public String getMemoryStats() {
        return String.format(Locale.getDefault(),
                "记忆统计 - 核心记忆: %d/%d, 长期记忆: %d/%d",
                coreMemories.size(), MAX_CORE_MEMORIES,
                longTermMemories.size(), MAX_LONG_TERM_MEMORIES);
    }

    /**
     * 手动触发记忆清理
     */
    public void manualMemoryCleanup() {
        new Thread(() -> {
            try {
                int originalCoreSize = coreMemories.size();
                int originalLongTermSize = longTermMemories.size();

                cleanupCoreMemoriesIfNeeded();
                cleanupLongTermMemoriesIfNeeded();

                int cleanedCore = originalCoreSize - coreMemories.size();
                int cleanedLongTerm = originalLongTermSize - longTermMemories.size();

                if (cleanedCore > 0 || cleanedLongTerm > 0) {
                    saveCoreMemory();
                    saveLongTermMemory();
                    AppLogger.logI(TAG, String.format("手动清理完成 - 核心记忆: %d, 长期记忆: %d", cleanedCore, cleanedLongTerm));
                } else {
                    AppLogger.logI(TAG, "手动清理完成 - 无需清理");
                }
            } catch (Exception e) {
                AppLogger.logE(TAG, "手动记忆清理失败", e);
            }
        }).start();
    }


    /**
     * 【新】增强的记忆匹配检测
     *
     * @param userInput           用户输入
     * @param memoryContent       记忆内容
     * @param similarityThreshold 相似度阈值
     * @return 如果匹配则为true
     */
    private boolean isMemoryMatch(String userInput, String memoryContent, double similarityThreshold) {
        if (userInput == null || userInput.isEmpty() || memoryContent == null || memoryContent.isEmpty()) {
            return false;
        }
        String lowerUserInput = userInput.toLowerCase(Locale.ROOT);
        String lowerMemoryContent = memoryContent.toLowerCase(Locale.ROOT);

        // 检查是否包含对方 (直接匹配)
        if (lowerMemoryContent.contains(lowerUserInput) || lowerUserInput.contains(lowerMemoryContent)) {
            return true;
        }

        // 检查相似度 (模糊匹配)
        return calculateSimilarity(userInput, memoryContent) >= similarityThreshold;
    }


    /**
     * 在核心记忆中查找与用户输入相关的条目
     *
     * @param userInput 用户输入
     * @return 格式化的相关记忆字符串，若无则为空
     */
    private String findCoreMemories(String userInput) {
        if (coreMemories.isEmpty()) {
            return "";
        }

        StringBuilder foundMemories = new StringBuilder();
        for (CoreMemoryEntry entry : coreMemories) {
            // 使用增强的匹配逻辑
            if (isMemoryMatch(userInput, entry.text, CORE_MEMORY_SIMILARITY_THRESHOLD)) {
                AppLogger.logD(TAG, "核心记忆匹配: " + entry.text); // 使用 AppLogger
                foundMemories.append("- ").append(entry.text).append("\n");
            }
        }

        if (foundMemories.length() > 0) {
            return "[核心记忆]\n" + foundMemories.toString() + "\n";
        }

        return "";
    }


    /**
     * 查找相关的长期记忆（增强版本）
     *
     * @param userInput 用户输入
     * @return 格式化的相关记忆字符串，若无则为空
     */
    private String findLongTermMemories(String userInput) {
        if (longTermMemories.isEmpty()) {
            return "";
        }

        // 对用户输入进行分类，帮助匹配
        MemoryClassificationResult inputClassification = classifyAndTagMemory(userInput, "");

        List<MemoryMatchResult> matches = new ArrayList<>();
        SimpleDateFormat displaySdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (LongTermMemoryEntry entry : longTermMemories) {
            double matchScore = calculateMemoryMatchScore(userInput, inputClassification, entry);

            if (matchScore >= LONG_TERM_MEMORY_SIMILARITY_THRESHOLD) {
                matches.add(new MemoryMatchResult(entry, matchScore));
            }
        }

        // 按匹配分数和重要性排序
        matches.sort((a, b) -> {
            double scoreA = a.score * a.entry.importance;
            double scoreB = b.score * b.entry.importance;
            return Double.compare(scoreB, scoreA);
        });

        // 限制返回数量，避免提示词过长
        int maxResults = 3;
        StringBuilder foundMemories = new StringBuilder();

        for (int i = 0; i < Math.min(maxResults, matches.size()); i++) {
            MemoryMatchResult match = matches.get(i);
            LongTermMemoryEntry entry = match.entry;

            String conversation = "用户: " + entry.userMessage + "\n艾拉: " + entry.aiMessage;
            AppLogger.logD(TAG, "长期记忆匹配: 分数=" + String.format("%.2f", match.score) +
                    ", 分类=" + entry.category + ", 内容=" + entry.tag);

            foundMemories.append(String.format("[%s | %s | 相关度:%.1f]\n%s\n\n",
                    displaySdf.format(new Date(entry.timestamp)),
                    entry.category,
                    match.score * 10,
                    conversation
            ));
        }

        if (foundMemories.length() > 0) {
            return "[相关历史对话]\n" + foundMemories.toString();
        }

        return "";
    }

    /**
     * 计算记忆匹配分数
     */
    private double calculateMemoryMatchScore(String userInput, MemoryClassificationResult inputClassification, LongTermMemoryEntry entry) {
        double score = 0.0;

        // 1. 分类匹配（权重：30%）
        if (inputClassification.category.equals(entry.category)) {
            score += 0.3;
        }

        // 2. 标签匹配（权重：25%）
        double tagSimilarity = calculateSimilarity(userInput, entry.tag);
        score += tagSimilarity * 0.25;

        // 3. 内容匹配（权重：25%）
        double contentSimilarity = calculateSimilarity(userInput, entry.userMessage);
        score += contentSimilarity * 0.25;

        // 4. 关键词匹配（权重：20%）
        double keywordMatch = calculateKeywordMatch(userInput, entry.keywords);
        score += keywordMatch * 0.20;

        // 5. 时间衰减因子（越新的记忆权重越高）
        long daysSinceCreation = (System.currentTimeMillis() - entry.timestamp) / (24 * 60 * 60 * 1000);
        double timeDecay = Math.max(0.5, 1.0 - (daysSinceCreation * 0.01)); // 每天衰减1%，最低50%
        score *= timeDecay;

        return score;
    }

    /**
     * 计算关键词匹配度
     */
    private double calculateKeywordMatch(String userInput, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0.0;
        }

        String lowerInput = userInput.toLowerCase(Locale.ROOT);
        int matches = 0;

        for (String keyword : keywords) {
            if (lowerInput.contains(keyword.toLowerCase(Locale.ROOT))) {
                matches++;
            }
        }

        return (double) matches / keywords.size();
    }


    // ===================================================================================
    // 模型初始化
    // ===================================================================================

    private void initVoskModel() {
        StorageService.unpack(this, "vosk_model", "vosk_model",
                (model) -> {
                    this.model = model;
                    this.isModelReady = true;
                    runOnUiThread(this::checkAllModelsReady);
                    AppLogger.logI(TAG, "语音识别模型初始化成功。"); // 使用 AppLogger
                },
                (exception) -> {
                    AppLogger.logE(TAG, "语音模型初始化失败: " + exception.getMessage()); // 使用 AppLogger
                    showError("语音识别初始化失败，请重启应用。");
                    // 尝试重新复制模型文件
                    String modelPath = copyModelFromAssets("vosk_model", "vosk_model");
                    if (modelPath != null) {
                        AppLogger.logI(TAG, "重新复制模型文件成功，尝试再次初始化模型...");
                        initVoskModel(); // 重新初始化模型
                    } else {
                        showError("重新复制模型文件失败，请检查assets文件夹。");
                    }
                });
    }

    private void initLlmModel() {
        new Thread(() -> {
            try {
                String modelPath = copyModelFromAssets("chat_model/chat.litertlm", "chat.litertlm");
                if (modelPath == null) {
                    runOnUiThread(() -> showError("AI模型复制失败，请检查assets文件夹。"));
                    return;
                }

                LlmInference.LlmInferenceOptions taskOptions = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(MAX_USER_INPUT_LENGTH)
                        .setMaxTopK(64)
                        .build();

                llmInference = LlmInference.createFromOptions(this, taskOptions);
                isLlmReady = true;
                runOnUiThread(this::checkAllModelsReady);
                AppLogger.logI(TAG, "LLM模型初始化成功（支持流式响应）。"); // 使用 AppLogger

            } catch (Exception e) {
                AppLogger.logE(TAG, "LLM模型初始化失败: " + e.getMessage(), e); // 使用 AppLogger
                runOnUiThread(() -> showError("AI模型初始化失败: " + e.getMessage()));
            }
        }).start();
    }

    private String copyModelFromAssets(String assetsPath, String fileName) {
        try {
            File internalDir = new File(getFilesDir(), "llm_models");
            if (!internalDir.exists() && !internalDir.mkdirs()) {
                AppLogger.logE(TAG, "无法创建模型目录。"); // 使用 AppLogger
                return null;
            }
            File modelFile = new File(internalDir, fileName);
            if (modelFile.exists() && modelFile.length() > 0) {
                AppLogger.logI(TAG, "模型文件已存在: " + modelFile.getAbsolutePath()); // 使用 AppLogger
                return modelFile.getAbsolutePath();
            }
            AppLogger.logI(TAG, "正在从assets复制模型文件..."); // 使用 AppLogger
            try (InputStream inputStream = getAssets().open(assetsPath);
                 FileOutputStream outputStream = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }
            AppLogger.logI(TAG, "模型文件复制完成: " + modelFile.getAbsolutePath()); // 使用 AppLogger
            return modelFile.getAbsolutePath();
        } catch (Exception e) {
            AppLogger.logE(TAG, "复制模型文件失败: " + e.getMessage(), e); // 使用 AppLogger
            return null;
        }
    }

    private void checkAllModelsReady() {
        if (isModelReady && isLlmReady) {
            recordButton.setEnabled(true);
            sendButton.setEnabled(true);
            addMessageToChat("艾拉已准备就绪，可以开始对话了。", false);
        }
    }

    // ===================================================================================
    // 语音识别超时处理
    // ===================================================================================

    /**
     * 启动语音识别超时定时器
     */
    private void startSpeechTimeoutTimer() {
        cancelSpeechTimeoutTimer(); // 先取消之前的定时器

        speechTimeoutTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    AppLogger.logI(TAG, "语音识别超时，自动发送当前识别内容");
                    handleSpeechTimeout();
                });
            }
        };

        speechTimeoutTimer.schedule(speechTimeoutTask, SPEECH_TIMEOUT_DELAY);
        AppLogger.logD(TAG, "启动语音识别超时定时器");
    }

    /**
     * 取消语音识别超时定时器
     */
    private void cancelSpeechTimeoutTimer() {
        if (speechTimeoutTask != null) {
            speechTimeoutTask.cancel();
            speechTimeoutTask = null;
            AppLogger.logD(TAG, "取消语音识别超时定时器");
        }
    }

    /**
     * 处理语音识别超时
     */
    private void handleSpeechTimeout() {
        AppLogger.logI(TAG, "handleSpeechTimeout被调用，当前状态：isRecording=" + isRecording + ", isProcessingAI=" + isProcessingAI);

        if (!isRecording || isProcessingAI) {
            AppLogger.logW(TAG, "超时处理被跳过：录音状态=" + isRecording + ", AI处理状态=" + isProcessingAI);
            return;
        }

        String currentText = userInputEditText.getText().toString().trim();
        AppLogger.logI(TAG, "当前识别文本: '" + currentText + "'");
        AppLogger.logI(TAG, "上次检查文本: '" + lastTimeoutCheckText + "'");

        // 检查文本是否变化
        if (!currentText.equals(lastTimeoutCheckText)) {
            // 文本有变化，用户还在说话，重新计时
            AppLogger.logI(TAG, "文本仍在变化，重新开始倒计时");
            lastTimeoutCheckText = currentText;
            startSpeechTimeoutTimer();
            return;
        }

        // 文本没有变化，用户停止说话了，发送消息
        AppLogger.logI(TAG, "自动发送: " + currentText);

        // 强制停止录音服务，无论是否有文本
        try {
            if (speechService != null) {
                speechService.stop();
                speechService.shutdown();
                speechService = null;
                AppLogger.logI(TAG, "录音服务已强制停止");
            }
        } catch (Exception e) {
            AppLogger.logE(TAG, "停止录音服务时出错", e);
        }

        // 强制重置录音状态
        isRecording = false;

        // 取消超时定时器
        cancelSpeechTimeoutTimer();

        // 重置识别文本追踪
        lastRecognizedText = "";

        if (!currentText.isEmpty()) {
            AppLogger.logI(TAG, "自动发送: " + currentText);

            // 自动发送当前识别的文字
            addMessageToChat(currentText, true);
            lastUserInput = currentText;
            currentUserInputForMemory = currentText; // 设置用于记忆存储
            sendToAI(currentText);

            // 清空输入框和识别结果
            userInputEditText.setText("");
            currentRecognitionResult.setLength(0);

            // 更新UI状态
            if (isSmartVoiceMode) {
                userInputEditText.setHint("智能语音模式 - 艾拉正在思考...");

                // 清空输入框
                userInputEditText.setText("");

                recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                AppLogger.logI(TAG, "智能语音模式：等待AI响应完成后重新开始录音");
            } else if (isContinuousMode) {
                userInputEditText.setHint("连续语音模式 - 艾拉正在思考...");

                // 清空输入框
                userInputEditText.setText("");

                recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                AppLogger.logI(TAG, "连续模式：等待AI响应完成后重新开始录音");
            } else {
                userInputEditText.setHint("输入消息...");
                recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);
            }
        } else {
            AppLogger.logW(TAG, "超时但没有识别到文字，仅停止录音");
            // 即使没有文字也要更新UI状态
            if (isSmartVoiceMode) {
                userInputEditText.setHint("智能语音模式 - 请重新开始说话");
                recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                // 在智能语音模式下，即使没有文字也要重新开始录音
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        runOnUiThread(() -> {
                            if (isSmartVoiceMode && !isRecording && !isProcessingAI) {
                                AppLogger.logI(TAG, "智能语音模式：超时无文字，重新开始录音");
                                startRecording();
                            }
                        });
                    } catch (InterruptedException e) {
                        AppLogger.logE(TAG, "智能语音模式重启录音延迟被中断", e);
                    }
                }).start();
            } else if (isContinuousMode) {
                userInputEditText.setHint("连续语音模式 - 请重新开始说话");
                recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);
                // 在连续模式下，即使没有文字也要重新开始录音
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        runOnUiThread(() -> {
                            if (isContinuousMode && !isRecording && !isProcessingAI) {
                                AppLogger.logI(TAG, "连续模式：超时无文字，重新开始录音");
                                startRecording();
                            }
                        });
                    } catch (InterruptedException e) {
                        AppLogger.logE(TAG, "连续模式重启录音延迟被中断", e);
                    }
                }).start();
            } else {
                userInputEditText.setHint("输入消息...");
                recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);
            }
        }
    }

    /**
     * 重新开始语音识别（用于连续模式）
     */
    private void restartRecognition() {
        try {
            if (speechService != null) {
                speechService.stop();
                speechService.shutdown();
            }

            // 短暂延迟后重新开始
            new Thread(() -> {
                try {
                    Thread.sleep(500); // 等待500ms
                    runOnUiThread(() -> {
                        if (isContinuousMode && !isProcessingAI) {
                            startRecording();
                        }
                    });
                } catch (InterruptedException e) {
                    AppLogger.logE(TAG, "重启识别延迟被中断", e);
                }
            }).start();

        } catch (Exception e) {
            AppLogger.logE(TAG, "重新开始语音识别失败", e);
        }
    }

    // ===================================================================================
    // 核心交互逻辑 (录音, 发送消息)
    // ===================================================================================

    private void toggleRecording() {
        if (!isModelReady || !isLlmReady) {
            Toast.makeText(this, "系统尚未完全准备就绪。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isProcessingAI) {
            Toast.makeText(this, "艾拉正在思考中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSmartVoiceMode) {
            // 如果已经在智能语音模式，点击退出
            exitSmartVoiceMode();
        } else {
            // 如果不在智能语音模式，点击进入
            enterSmartVoiceMode();
        }
    }

    /**
     * 进入智能语音模式
     */
    private void enterSmartVoiceMode() {
        isSmartVoiceMode = true;
        Toast.makeText(this, "智能语音模式已启动 - 语音识别后自动发送，再次点击退出", Toast.LENGTH_LONG).show();
        AppLogger.logI(TAG, "进入智能语音模式");

        // 立即开始录音
        if (!isRecording) {
            startRecording();
        }
    }

    /**
     * 退出智能语音模式
     */
    private void exitSmartVoiceMode() {
        isSmartVoiceMode = false;

        // 停止当前录音
        if (isRecording) {
            stopRecording();
        }

        // 取消超时定时器
        cancelSpeechTimeoutTimer();

        // 重置UI状态
        userInputEditText.setHint("输入消息...");
        recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);

        Toast.makeText(this, "智能语音模式已关闭", Toast.LENGTH_SHORT).show();
        AppLogger.logI(TAG, "退出智能语音模式");
    }

    /**
     * 启动连续录音模式
     */
    private void startContinuousRecording() {
        if (!isModelReady || !isLlmReady) {
            Toast.makeText(this, "系统尚未完全准备就绪。", Toast.LENGTH_SHORT).show();

            // 清空输入框
            userInputEditText.setText("");

            return;
        }
        if (isProcessingAI) {
            Toast.makeText(this, "艾拉正在思考中，请稍候...", Toast.LENGTH_SHORT).show();

            // 清空输入框
            userInputEditText.setText("");

            return;
        }

        isContinuousMode = true;
        Toast.makeText(this, "连续语音模式已启动", Toast.LENGTH_LONG).show();
        AppLogger.logI(TAG, "启动连续语音模式");

        if (!isRecording) {
            startRecording();
        }
    }

    /**
     * 停止连续录音模式
     */
    private void stopContinuousRecording() {
        isContinuousMode = false;
        cancelSpeechTimeoutTimer();
        if (isRecording) {
            stopRecording();
        }
        Toast.makeText(this, "连续语音模式已关闭", Toast.LENGTH_SHORT).show();
        AppLogger.logI(TAG, "停止连续语音模式");
    }

    // 修改 startRecording 方法，初始化超时检查文本
    private void startRecording() {
        try {
            Recognizer recognizer = new Recognizer(model, 16000.0f);
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(this);
            isRecording = true;
            currentRecognitionResult.setLength(0);
            lastRecognizedText = ""; // 重置上次识别的文本
            lastTimeoutCheckText = ""; // 重置超时检查文本

            // 无论什么模式都启动超时定时器
            startSpeechTimeoutTimer();

            if (isSmartVoiceMode) {
                userInputEditText.setHint("智能语音模式");
                recordButton.setImageResource(android.R.drawable.ic_media_pause);
                AppLogger.logI(TAG, "智能语音模式录音开始");
            } else if (isContinuousMode) {
                userInputEditText.setHint("连续语音模式");
                recordButton.setImageResource(android.R.drawable.ic_media_pause);
                AppLogger.logI(TAG, "连续模式录音开始");
            } else {
                userInputEditText.setHint("正在聆听...");
                recordButton.setImageResource(android.R.drawable.ic_media_pause);
                AppLogger.logI(TAG, "普通模式录音开始");
            }

            AppLogger.logI(TAG, "开始录音识别，智能语音模式: " + isSmartVoiceMode + ", 连续模式: " + isContinuousMode);
        } catch (IOException e) {
            showError("启动录音失败: " + e.getMessage());
        }
    }

    private void stopRecording() {
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
            AppLogger.logI(TAG, "停止录音识别");
        }

        // 取消超时定时器
        cancelSpeechTimeoutTimer();

        isRecording = false;

        if (isSmartVoiceMode) {
            // 在智能语音模式下，手动停止不退出模式，只是停止当前录音
            userInputEditText.setHint("智能语音模式 - 点击麦克风退出");
            recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);

            if (currentRecognitionResult.length() > 0) {
                userInputEditText.setText(currentRecognitionResult.toString());
                userInputEditText.setSelection(userInputEditText.length());
            }
        } else if (isContinuousMode) {
            // 在连续模式下，如果手动停止，则退出连续模式
            stopContinuousRecording();
        } else {
            userInputEditText.setHint("输入消息...");
            recordButton.setImageResource(android.R.drawable.ic_btn_speak_now);

            if (currentRecognitionResult.length() > 0) {
                userInputEditText.setText(currentRecognitionResult.toString());
                userInputEditText.setSelection(userInputEditText.length());
            }
        }
    }

    private void sendMessage() {
        String userInput = userInputEditText.getText().toString().trim();
        if (userInput.isEmpty()) {
            Toast.makeText(this, "消息不能为空。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isProcessingAI) {
            Toast.makeText(this, "请等待上一条消息处理完成。", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRecording) {
            stopRecording();
        }

        addMessageToChat(userInput, true);
        lastUserInput = userInput; // 保存用户输入，用于长期记忆
        sendToAI(userInput);
        userInputEditText.setText("");
        currentRecognitionResult.setLength(0);
    }

    // ===================================================================================
    // AI 推理与提示词构建
    // ===================================================================================

    private void sendToAI(String userInput) {
        isProcessingAI = true;
        recordButton.setEnabled(false);
        sendButton.setEnabled(false);

        // 保存用户输入用于记忆存储
        currentUserInputForMemory = userInput;
        lastUserInput = userInput;

        if (userInput.length() > MAX_USER_INPUT_LENGTH) {
            AppLogger.logW(TAG, "用户输入长度 (" + userInput.length() + ") 超过最大限制 (" + MAX_USER_INPUT_LENGTH + ")，已截断。"); // 使用 AppLogger
            userInput = userInput.substring(0, MAX_USER_INPUT_LENGTH);
            runOnUiThread(() -> Toast.makeText(this, "您的输入已截断，以避免潜在问题。", Toast.LENGTH_LONG).show());
        }

        String prompt = buildEnhancedPrompt(userInput);

        if (prompt.length() > MAX_USER_INPUT_LENGTH) {
            AppLogger.logW(TAG, "警告：生成的LLM提示词过长，长度为 " + prompt.length() + " 字符。这可能导致性能问题或崩溃。"); // 使用 AppLogger
            runOnUiThread(() -> Toast.makeText(this, "提示词过长，可能导致问题", Toast.LENGTH_LONG).show());
        }

        // 初始化流式响应
        initStreamingResponse();

        AppLogger.logD(TAG, "发送给LLM的完整提示词:\n" + prompt); // 使用 AppLogger

        // 使用异步流式生成
        new Thread(() -> {
            try {
                llmInference.generateResponseAsync(prompt, (partialResult, done) -> {
                    // 在主线程中处理流式响应
                    runOnUiThread(() -> onStreamingResponse(partialResult, done));
                });
            } catch (Exception e) {
                AppLogger.logE(TAG, "AI流式处理失败: " + e.getMessage(), e); // 使用 AppLogger
                runOnUiThread(() -> onStreamingComplete("[错误，艾拉没有听懂，再试一次吧]"));
            }
        }).start();

        // 清空输入框
        userInputEditText.setText("");
    }

    private String buildEnhancedPrompt(String userMessage) {
        StringBuilder promptBuilder = new StringBuilder();
        boolean isTimeQuery = isTimeQuestion(userMessage); // 判断是否是时间问题

        // 1. 人设上下文
        if (!personaContext.isEmpty()) {
            promptBuilder.append("[你必须遵守以下设定：]\n").append(personaContext).append("\n\n");
        }

        // 2. 对用户输入进行分类分析
        MemoryClassificationResult inputAnalysis = classifyAndTagMemory(userMessage, "");
        AppLogger.logI(TAG, "用户输入分析: 分类=" + inputAnalysis.category + ", 关键词=" + inputAnalysis.keywords);

        // 3. 核心记忆检索 (如果不是时间问题才进行记忆检索)
        if (!isTimeQuery) {
            String coreMemoryContext = findCoreMemories(userMessage);
            if (!coreMemoryContext.isEmpty()) {
                promptBuilder.append(coreMemoryContext);
            }
        } else {
            AppLogger.logI(TAG, "用户询问时间，跳过核心记忆检索。");
        }

        // 4. 增强的长期记忆检索 (如果不是时间问题才进行记忆检索)
        if (!isTimeQuery) {
            String longTermMemoryContext = findLongTermMemories(userMessage);
            if (!longTermMemoryContext.isEmpty()) {
                promptBuilder.append(longTermMemoryContext);

                // 添加记忆使用指导
                promptBuilder.append("[记忆使用指导]\n");
                promptBuilder.append("- 以上历史对话按相关度排序，数字越高越相关\n");
                promptBuilder.append("- 请结合历史对话的上下文来理解用户当前的问题\n");
                promptBuilder.append("- 如果历史对话中有相关信息，请在回复中体现出连续性\n");
                promptBuilder.append("- 分类信息帮助你理解对话的主题和背景\n\n");
            }
        } else {
            AppLogger.logI(TAG, "用户询问时间，跳过长期记忆检索。");
        }

        // 5. 时间信息
        if (isTimeQuery) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String nowStr = sdf.format(new Date());
            promptBuilder.append("\n[这是现在的时间，当你要回复的时候，你必须准确无误的回答: ").append(nowStr).append("]\n");
            AppLogger.logI(TAG, "用户询问时间，在提示词中添加当前时间。");
        }

        // 6. 知识库匹配
        KnowledgeMatchResult matchResult = findBestKnowledgeMatches(userMessage);
        if (matchResult != null && matchResult.maxSimilarity >= 0.85) {
            KnowledgeEntry selectedEntry = matchResult.topMatches.get(random.nextInt(matchResult.topMatches.size()));
            promptBuilder.append("\n[知识库匹配 - 高可信度]\nQuestion: ")
                    .append(selectedEntry.userQuery)
                    .append("\nAnswer: ")
                    .append(selectedEntry.assistantResponse)
                    .append("\n(请参考以上知识库信息来回复)\n");

            AppLogger.logD(TAG, "知识库匹配成功 - 匹配度: " + String.format("%.2f", matchResult.maxSimilarity));
        }

        // 7. 上下文分析提示
        if (!isTimeQuery && !inputAnalysis.keywords.isEmpty()) {
            promptBuilder.append("\n[当前对话分析]\n");
            promptBuilder.append("话题分类: ").append(inputAnalysis.category).append("\n");
            promptBuilder.append("关键词: ").append(String.join(", ", inputAnalysis.keywords.subList(0, Math.min(5, inputAnalysis.keywords.size())))).append("\n");
            promptBuilder.append("(请基于以上分析来理解用户意图并给出相应回复)\n\n");
        }

        // 8. 当前对话
        promptBuilder.append("[当前对话]\nUser: ").append(userMessage).append("\nAssistant: ");

        return promptBuilder.toString();
    }


    private void onAiResponse(String response, String originalUserInput) {
        // 替换"正在思考"的消息
        if (chatMessagesContainer.getChildCount() > 0) {
            View lastChild = chatMessagesContainer.getChildAt(chatMessagesContainer.getChildCount() - 1);
            if (lastChild instanceof LinearLayout) {
                LinearLayout lastMessageLayout = (LinearLayout) lastChild;
                TextView lastMessageTextView = (TextView) lastMessageLayout.getChildAt(0);
                if (lastMessageTextView != null && lastMessageTextView.getText().toString().equals("[艾拉正在思考...]")) {

                    // 清空输入框
                    userInputEditText.setText("");

                    lastMessageTextView.setText(response);
                    lastMessageTextView.setBackgroundResource(R.drawable.bubble_ai);
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lastMessageLayout.getLayoutParams();
                    params.gravity = Gravity.START;
                    params.setMargins(0, dpToPx(8), dpToPx(60), 0);
                    lastMessageLayout.setLayoutParams(params);
                } else {
                    addMessageToChat(response, false);
                }
            } else {
                addMessageToChat(response, false);
            }
        } else {
            addMessageToChat(response, false);
        }

        scrollToBottom();

        // 异步添加长期记忆 (如果不是时间问题才添加)
        if (!isTimeQuestion(originalUserInput)) {
            addLongTermMemory(lastUserInput, response);
            AppLogger.logI(TAG, "用户问题非时间相关，添加长期记忆。"); // 使用 AppLogger
        } else {
            AppLogger.logI(TAG, "用户询问时间，跳过添加长期记忆。"); // 使用 AppLogger
        }


        isProcessingAI = false;
        recordButton.setEnabled(true);
        sendButton.setEnabled(true);
    }

    // ===================================================================================
    // 流式响应处理方法
    // ===================================================================================

    /**
     * 初始化流式响应 - 创建一个空的AI消息框用于流式更新
     */
    private void initStreamingResponse() {
        // 清空之前的流式内容
        currentStreamingContent.setLength(0);

        // 创建一个新的AI消息框
        LinearLayout messageLayout = new LinearLayout(this);
        messageLayout.setOrientation(LinearLayout.VERTICAL);

        TextView messageTextView = new TextView(this);
        messageTextView.setText(""); // 开始时为空
        messageTextView.setTextSize(16);
        messageTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        messageTextView.setBackgroundResource(R.drawable.bubble_ai);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.START;
        params.setMargins(0, dpToPx(8), dpToPx(60), 0);
        messageTextView.setLayoutParams(params);
        messageTextView.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

        messageLayout.addView(messageTextView);
        chatMessagesContainer.addView(messageLayout);

        // 保存当前流式更新的TextView引用
        currentStreamingTextView = messageTextView;

        scrollToBottom();
    }

    /**
     * 处理流式响应的每个部分
     *
     * @param partialResult 部分结果
     * @param done          是否完成
     */
    private void onStreamingResponse(String partialResult, boolean done) {
        if (currentStreamingTextView == null) {
            AppLogger.logW(TAG, "流式响应TextView为空，忽略更新。");
            return;
        }

        // 累积内容
        if (partialResult != null && !partialResult.isEmpty()) {
            currentStreamingContent.append(partialResult);

            // 检测是否包含不良内容
            BadDataEntry detectedBadData = detectBadContent(currentStreamingContent.toString());
            if (detectedBadData != null) {
                AppLogger.logW(TAG, "检测到不良内容: " + detectedBadData.badText + "，打断响应并重新发送");

                // 打断当前响应
                onStreamingComplete("[检测到不当内容，正在重新生成回复...]");

                // 重新发送修正后的用户输入
                String correctedUserInput = currentUserInputForMemory + detectedBadData.addText;
                AppLogger.logI(TAG, "重新发送修正后的用户输入: " + correctedUserInput);

                // 延迟重新发送，避免过快请求
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // 等待500ms
                        runOnUiThread(() -> {
                            if (!isProcessingAI) {
                                sendToAI(correctedUserInput);
                            }
                        });
                    } catch (InterruptedException e) {
                        AppLogger.logE(TAG, "重新发送延迟被中断", e);
                    }
                }).start();

                return;
            }

            currentStreamingTextView.setText(currentStreamingContent.toString());
            scrollToBottom();

            //AppLogger.logD(TAG, "流式响应更新: " + partialResult);
        }

        // 如果完成，进行最终处理
        if (done) {
            onStreamingComplete(currentStreamingContent.toString());
        }
    }

    /**
     * 流式响应完成时的处理
     *
     * @param finalResponse 最终完整响应
     */
    private void onStreamingComplete(String finalResponse) {
        AppLogger.logI(TAG, "流式响应完成，最终内容长度: " + finalResponse.length());

        // 清理和验证最终响应
        String cleanedResponse = cleanAndValidateText(finalResponse);
        if (cleanedResponse.isEmpty()) {
            cleanedResponse = "[响应内容无效]";
            AppLogger.logW(TAG, "AI响应清理后为空，使用默认内容。");
        }

        // 验证响应内容有效性
        if (!isValidMemoryContent(cleanedResponse)) {
            AppLogger.logW(TAG, "检测到AI响应可能包含乱码，将进行额外处理。");
            // 尝试修复常见的乱码问题
            cleanedResponse = fixCommonGarbledText(cleanedResponse);
        }

        // 确保最终内容显示正确
        if (currentStreamingTextView != null) {
            currentStreamingTextView.setText(cleanedResponse);
        }

        scrollToBottom();

        // 异步添加长期记忆 (如果不是时间问题才添加)
        if (!isTimeQuestion(currentUserInputForMemory)) {
            // 只有当用户输入和AI响应都有效时才存储记忆
            if (!currentUserInputForMemory.trim().isEmpty() && !cleanedResponse.trim().isEmpty()) {
                addLongTermMemoryAsync(currentUserInputForMemory, cleanedResponse);
                AppLogger.logI(TAG, "用户问题非时间相关，添加长期记忆。");
            } else {
                AppLogger.logW(TAG, "用户输入或AI响应为空，跳过长期记忆存储。");
            }
        } else {
            AppLogger.logI(TAG, "用户询问时间，跳过添加长期记忆。");
        }

        // 重置状态
        isProcessingAI = false;
        recordButton.setEnabled(true);
        sendButton.setEnabled(true);
        currentStreamingTextView = null;
        currentStreamingContent.setLength(0);

        // 在智能语音模式或连续模式下，AI响应完成后自动重新开始录音
        if (isSmartVoiceMode || isContinuousMode) {
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // 等待1秒让用户看到AI回复
                    runOnUiThread(() -> {
                        if (isSmartVoiceMode && !isRecording && !isProcessingAI) {
                            AppLogger.logI(TAG, "智能语音模式：AI响应完成，重新开始录音");
                            startRecording();
                        } else if (isContinuousMode && !isRecording && !isProcessingAI) {
                            AppLogger.logI(TAG, "连续模式：AI响应完成，重新开始录音");
                            startRecording();
                        }
                    });
                } catch (InterruptedException e) {
                    AppLogger.logE(TAG, "自动重启录音延迟被中断", e);
                }
            }).start();
        }
    }

    /**
     * 检测内容中是否包含不良数据
     *
     * @param content 要检测的内容
     * @return 如果检测到不良内容则返回对应的BadDataEntry，否则返回null
     */
    private BadDataEntry detectBadContent(String content) {
        if (content == null || content.isEmpty() || badDataList.isEmpty()) {
            return null;
        }

        for (BadDataEntry badData : badDataList) {
            if (content.contains(badData.badText)) {
                AppLogger.logI(TAG, "检测到不良内容: " + badData.badText + " -> 需要添加: " + badData.addText);
                return badData;
            }
        }

        return null;
    }

    // ===================================================================================
    // UI & 辅助方法
    // ===================================================================================

    private void addMessageToChat(String message, boolean isUser) {
        LinearLayout messageLayout = new LinearLayout(this);
        messageLayout.setOrientation(LinearLayout.VERTICAL);

        TextView messageTextView = new TextView(this);
        messageTextView.setText(message);
        messageTextView.setTextSize(16);
        messageTextView.setTextColor(ContextCompat.getColor(this, android.R.color.black));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        if (isUser) {
            messageTextView.setBackgroundResource(R.drawable.bubble_user);
            params.gravity = Gravity.END;
            params.setMargins(dpToPx(60), dpToPx(8), 0, 0);
        } else {
            messageTextView.setBackgroundResource(R.drawable.bubble_ai);
            params.gravity = Gravity.START;
            params.setMargins(0, dpToPx(8), dpToPx(60), 0);
        }
        messageTextView.setLayoutParams(params);
        messageTextView.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));

        messageLayout.addView(messageTextView);
        chatMessagesContainer.addView(messageLayout);

        scrollToBottom();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void scrollToBottom() {
        chatScrollView.post(() -> chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void showError(String message) {
        addMessageToChat("错误: " + message, false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }


    // ===================================================================================
    // 文本相似度计算
    // ===================================================================================
    private boolean isTimeQuestion(String userMessage) {
        for (String question : TIME_QUESTIONS) {
            if (calculateSimilarity(userMessage, question) >= 0.6) {
                return true;
            }
        }
        return false;
    }

    private KnowledgeMatchResult findBestKnowledgeMatches(String userMessage) {
        List<KnowledgeEntry> topMatches = new ArrayList<>();
        double maxSimilarity = 0.0;

        for (KnowledgeEntry entry : knowledgeBase) {
            double similarity = calculateSimilarity(userMessage, entry.userQuery);

            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                topMatches.clear();
                topMatches.add(entry);
            } else if (Math.abs(similarity - maxSimilarity) < 0.001) {
                topMatches.add(entry);
            }
        }

        if (topMatches.isEmpty()) {
            return null;
        }

        return new KnowledgeMatchResult(maxSimilarity, topMatches);
    }

    private double calculateSimilarity(String s1, String s2) {
        s1 = stripPunctuation(s1).toLowerCase(Locale.ROOT);
        s2 = stripPunctuation(s2).toLowerCase(Locale.ROOT);

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0 && len2 == 0) return 1.0;
        if (len1 == 0 || len2 == 0) return 0.0;

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }

        int maxLen = Math.max(len1, len2);
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) dp[len1][len2] / maxLen;
    }

    private String stripPunctuation(String str) {
        return str.replaceAll("[^\\p{L}\\p{N}\\s]", "");
    }


    // ===================================================================================
    // 安卓生命周期 & 权限回调
    // ===================================================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModelsAndData();
            } else {
                showError("需要麦克风权限才能使用语音对话功能。");
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 清理语音识别资源
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        // 清理定时器资源
        cancelSpeechTimeoutTimer();
        if (speechTimeoutTimer != null) {
            speechTimeoutTimer.cancel();
            speechTimeoutTimer.purge();
        }

        // 清理LLM资源
        if (llmInference != null) {
            llmInference.close();
        }
    }

    // ===================================================================================
    // Vosk RecognitionListener 接口实现
    // ===================================================================================

    @Override
    public void onFinalResult(String hypothesis) {
        try {
            JSONObject jsonObject = new JSONObject(hypothesis);
            String text = jsonObject.optString("text", "").trim().replaceAll("\\s+", "");
            if (!text.isEmpty()) {
                // 将最终识别的文本追加到已有内容后面
                currentRecognitionResult.append(text).append(" "); // 添加空格以分隔句子
                runOnUiThread(() -> {
                    userInputEditText.setText(currentRecognitionResult.toString());
                    userInputEditText.setSelection(userInputEditText.length());

                    // 更新最后一次检查的文本
                    lastTimeoutCheckText = currentRecognitionResult.toString();
                });

                // 只要有新的最终识别结果，就重新开始倒计时
                if (isRecording && (isSmartVoiceMode || isContinuousMode)) {
                    AppLogger.logD(TAG, "检测到用户说完一句话，重置倒计时。");
                    startSpeechTimeoutTimer();
                }
            }
            AppLogger.logI(TAG, "收到最终识别结果: " + text);
        } catch (JSONException e) {
            AppLogger.logW(TAG, "onFinalResult - JSON解析失败: " + hypothesis);
        }
    }

    @SuppressLint("SetTextI19n")
    @Override
    public void onPartialResult(String hypothesis) {
        try {
            JSONObject jsonObject = new JSONObject(hypothesis);
            String partialText = jsonObject.optString("partial", "").replaceAll("\\s+", "");
            if (!partialText.isEmpty()) {
                final String finalPartialText = partialText;
                runOnUiThread(() -> {
                    // 实时显示用户正在说的内容
                    userInputEditText.setText(currentRecognitionResult.toString() + finalPartialText);
                    userInputEditText.setSelection(userInputEditText.length());
                });

                // 【保持不变】
                // 用户说话过程中的中间结果不应该重置定时器，
                // 否则会导致用户一句话还没说完，计时器就不断重置，永远无法触发发送。
                // AppLogger.logD(TAG, "收到部分识别结果: " + partialText);
            }
        } catch (JSONException e) {
            AppLogger.logW(TAG, "onPartialResult - JSON解析失败: " + hypothesis);
        }
    }

    @Override
    public void onResult(String hypothesis) {
    }

    @Override
    public void onError(Exception e) {
        AppLogger.logE(TAG, "语音识别错误: " + e.getMessage(), e); // 使用 AppLogger
        runOnUiThread(() -> {
            stopRecording();
            Toast.makeText(this, "识别出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onTimeout() {
        AppLogger.logW(TAG, "语音识别超时。"); // 使用 AppLogger
        runOnUiThread(() -> Toast.makeText(this, "语音识别超时，请继续说话或手动停止", Toast.LENGTH_SHORT).show());
    }

    // ===================================================================================
    // 内部数据类
    // ===================================================================================

    /**
     * 不良数据条目
     */
    private static class BadDataEntry {
        String badText;   // 不良文本
        String addText;   // 需要添加的修正文本

        BadDataEntry(String badText, String addText) {
            this.badText = badText;
            this.addText = addText;
        }
    }

    /**
     * 记忆分类结果
     */
    private static class MemoryClassificationResult {
        String category;        // 分类
        String tag;            // 标签
        List<String> keywords; // 关键词
        double importance;     // 重要性分数
        boolean shouldStore;   // 是否应该存储

        MemoryClassificationResult(String category, String tag, List<String> keywords,
                                   double importance, boolean shouldStore) {
            this.category = category;
            this.tag = tag;
            this.keywords = keywords;
            this.importance = importance;
            this.shouldStore = shouldStore;
        }
    }

    /**
     * 记忆匹配结果
     */
    private static class MemoryMatchResult {
        LongTermMemoryEntry entry;
        double score;

        MemoryMatchResult(LongTermMemoryEntry entry, double score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private static class KnowledgeEntry {
        String userQuery;
        String assistantResponse;

        public KnowledgeEntry(String userQuery, String assistantResponse) {
            this.userQuery = userQuery;
            this.assistantResponse = assistantResponse;
        }
    }

    private static class KnowledgeMatchResult {
        double maxSimilarity;
        List<KnowledgeEntry> topMatches;

        public KnowledgeMatchResult(double maxSimilarity, List<KnowledgeEntry> topMatches) {
            this.maxSimilarity = maxSimilarity;
            this.topMatches = topMatches;
        }
    }

    private static class CoreMemoryEntry {
        String id;
        String time;
        String text;

        CoreMemoryEntry(String id, String time, String text) {
            this.id = id;
            this.time = time;
            this.text = text;
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", this.id);
                obj.put("time", this.time);
                obj.put("text", this.text);
            } catch (JSONException e) {
                AppLogger.logE(TAG, "核心记忆条目转JSON失败", e); // 使用 AppLogger
            }
            return obj;
        }
    }

    private static class LongTermMemoryEntry {
        long timestamp;
        String tag;
        String category;
        String userMessage;
        String aiMessage;
        List<String> keywords;
        double importance;

        // 兼容旧版本的构造函数
        LongTermMemoryEntry(long timestamp, String tag, String userMessage, String aiMessage) {
            this.timestamp = timestamp;
            this.tag = tag;
            this.category = "日常闲聊";
            this.userMessage = userMessage;
            this.aiMessage = aiMessage;
            this.keywords = new ArrayList<>();
            this.importance = 0.5;
        }

        // 新版本的构造函数
        LongTermMemoryEntry(long timestamp, String tag, String category, String userMessage,
                            String aiMessage, List<String> keywords, double importance) {
            this.timestamp = timestamp;
            this.tag = tag;
            this.category = category;
            this.userMessage = userMessage;
            this.aiMessage = aiMessage;
            this.keywords = keywords != null ? keywords : new ArrayList<>();
            this.importance = importance;
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("timestamp", this.timestamp);
                obj.put("tag", this.tag);
                obj.put("category", this.category);
                obj.put("userMessage", this.userMessage);
                obj.put("aiMessage", this.aiMessage);
                obj.put("importance", this.importance);

                // 关键词数组
                JSONArray keywordArray = new JSONArray();
                for (String keyword : this.keywords) {
                    keywordArray.put(keyword);
                }
                obj.put("keywords", keywordArray);

            } catch (JSONException e) {
                AppLogger.logE(TAG, "长期记忆条目转JSON失败", e);
            }
            return obj;
        }

        static LongTermMemoryEntry fromJson(JSONObject obj) throws JSONException {
            // 处理关键词数组
            List<String> keywords = new ArrayList<>();
            if (obj.has("keywords")) {
                JSONArray keywordArray = obj.getJSONArray("keywords");
                for (int i = 0; i < keywordArray.length(); i++) {
                    keywords.add(keywordArray.getString(i));
                }
            }

            return new LongTermMemoryEntry(
                    obj.getLong("timestamp"),
                    obj.getString("tag"),
                    obj.optString("category", "日常闲聊"),
                    obj.getString("userMessage"),
                    obj.getString("aiMessage"),
                    keywords,
                    obj.optDouble("importance", 0.5)
            );
        }
    }
}