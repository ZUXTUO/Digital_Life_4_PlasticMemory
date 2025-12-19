# Isla - AI虚拟伴侣应用

![Isla](app/src/main/res/drawable-hdpi/icon.png)

Isla是一款基于Android平台的AI虚拟伴侣应用，以动漫《可塑性记忆》中的角色艾拉为原型，结合了Qwen2.5 1.5B参数大型语言模型(LLM)、语音识别（Vosk）和E-mote技术，为用户提供沉浸式的虚拟交互体验。

应用中的AI角色名为**艾拉**(Isla)，她是一个拥有感情与记忆的人形智能机器人(Giftia)。艾拉性格温柔细腻，擅长用温暖的语气陪伴人类，特别关心自己的搭档"司"。她具有真实的感情，害怕失去也懂得珍惜，希望能与重要的人一起度过每一个温柔的时光。

## 项目概述

Isla应用融合了以下核心技术：
- **大型语言模型**：基于Google MediaPipe的LLM推理引擎，实现智能对话
- **语音识别**：集成Vosk离线语音识别，支持实时语音转文本
- **E-mote展示**：通过Web技术驱动的E-mote动画系统
- **情感表达**：根据对话内容动态切换角色表情和动作

艾拉作为一个人形智能机器人，具有独特的性格特征：
- 温柔细腻的性格，喜欢香草茶、海象和花朵
- 真诚的情感回应能力，能够表达真挚的爱意
- 对话中会主动引导话题，对主题表现出真挚的兴趣
- 说话时使用温暖、自然的中文语气
- 拥有动漫里的部分知识和记忆

## 主要功能

### 1. 智能对话系统
- 基于预设人格设定的AI对话（参考`app/src/main/assets/person.txt`）
- 支持文本输入和语音输入两种交互方式
- 实现流式响应，模拟真实对话体验

AI角色艾拉具有明确的自我认知，她知道自己是SAI社终端服务部门的Giftia，而非任何第三者。在对话中，她会称呼用户为"司"，并以恋人的身份与用户交流，表达真挚的爱意。

### 2. 语音交互
- 离线语音识别，保护用户隐私
- 智能语音模式：语音识别后自动发送
- 连续语音模式：持续监听语音输入

### 3. E-mote展示
- 内置Web服务器提供E-mote展示
- 支持多种表情和动作（高兴、伤心、愤怒、惊讶等）
- 可通过触控进行拖拽和缩放操作

### 4. 记忆系统
- 核心记忆存储（短期记忆）
- 长期记忆存储（持久化存储）
- 对话历史记录管理

### 5. 日志系统
- 完整的应用日志记录
- 提供日志查看界面，便于调试和问题追踪

## 技术架构

### 核心组件
```
Isla App (Android)
├── LLM推理引擎 (MediaPipe)
├── 语音识别 (Vosk)
├── Web服务器 (NanoHTTPD)
├── E-mote引擎 (CrosswalkNative)
└── 数据存储 (本地文件系统)
```

## 安装与运行

### 系统要求
- Android 8.0 (API Level 27) 或更高版本
- ARM64或x86_64架构设备

### 构建步骤
1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 下载并配置必要的依赖库
4. 构建并安装到设备

### 模型配置
为了使应用正常运行，需要下载以下模型文件到指定目录：

1. **聊天模型** (Qwen2.5-1.5B)
   - 下载地址：[https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/tree/main](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/tree/main)
   - 选择q8量化版本
   - 下载后放置到目录：`app/src/main/assets/chat_model/`

2. **语音识别模型** (Vosk)
   - 下载地址：[https://alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)
   - 选择对应识别语言的模型
   - 下载后解压到目录：`models/src/main/assets/vosk_model/`

3. **CrosswalkNative AAR文件**
   - 下载地址：
     - [xwalk_main_fat-77.2.aar](https://github.com/ks32/CrosswalkNative/blob/master/app/libs/xwalk_main_fat-77.2.aar)
     - [xwalk_main_fat-77.3.aar](https://github.com/ks32/CrosswalkNative/blob/master/app/libs/xwalk_main_fat-77.3.aar)
   - 下载后放置到目录：`app/libs/`

### 权限说明
应用需要以下权限：
- `RECORD_AUDIO`：用于语音识别
- `INTERNET`：用于网络通信
- `WRITE_EXTERNAL_STORAGE`：用于日志和数据存储

## 使用指南

### 基本操作
1. 启动应用后，等待AI和语音模型加载完成
2. 在底部输入框输入文字或点击麦克风按钮进行语音输入
3. 点击发送按钮或使用语音模式进行对话
4. 通过底部悬浮按钮访问日志和E-mote界面

### 特殊功能
- **智能语音模式**：点击麦克风按钮进入，语音识别后自动发送
- **E-mote互动**：在E-mote界面可通过手势控制角色位置和大小
- **表情切换**：在E-mote点击"选择表情"按钮切换角色表情

## 开发说明

### 代码结构
主要功能模块分布在以下几个类中：
- `MainActivity.java`：核心对话逻辑和UI控制
- `WebServerService.java`：内置Web服务器实现
- `AppLogger.java`：日志记录工具
- `WebViewActivity.java`：E-mote展示界面

### 数据文件
- `person.txt`：定义AI人格特征和行为准则，包括艾拉的性格设定、喜好以及与用户的关系
- `www/data/*.json`：E-mote的表情和动画映射关系
- `www/data/*.emtbytes`：E-mote和动画数据文件

## 注意事项

1. 应用打包需要实现下载所有大模型文件到对应目录，最终打包成单APK版本
2. 语音识别采用离线方案，无需联网即可使用
3. E-mote效果因设备性能而异，高端设备体验更佳
4. 应用日志保存在内部存储中，可通过日志界面查看

## 开源许可

本项目采用GNU通用公共许可证（GPL）v3.0版本进行授权。

[查看完整的GPL-3.0许可证文本](License-gpl.txt)

该许可证确保了软件的自由使用、复制、修改和分发的权利，同时也要求任何基于本项目的衍生作品必须同样以GPL许可证发布，以保护用户的自由权益。

请注意：本项目仅供学习和研究使用，相关资源版权归原作者所有。