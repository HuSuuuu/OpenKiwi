# OpenKiwi

<p align="center">
  <b>开源 Android AI Agent — 让每一台安卓手机都有自己的智能体</b>
</p>

<p align="center">
  <a href="https://github.com/HuSuuuu/OpenKiwi/releases"><img src="https://img.shields.io/github/v/release/HuSuuuu/OpenKiwi?sort=semver&label=Release" alt="Release"></a>
  <a href="https://github.com/HuSuuuu/OpenKiwi/blob/main/README.md"><img src="https://img.shields.io/badge/Android-8.0%2B-green" alt="Android"></a>
  <a href="https://github.com/HuSuuuu/OpenKiwi/blob/main/README.md"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License"></a>
</p>

OpenKiwi 是运行在 **Android** 上的开源 AI Agent：不仅是聊天，还能通过 **工具调用、GUI 自动化、通知与系统能力** 真正执行任务。接入你信任的 **OpenAI 兼容 API**，对话与配置可保留在本地。

---

## 下载安装（推荐）

| 方式 | 说明 |
|------|------|
| **GitHub Releases** | [**Latest Release**](https://github.com/HuSuuuu/OpenKiwi/releases/latest) 下载附件 APK（当前推荐包名示例：**OpenKiwi328.apk**） |
| 自行编译 | 见下文 [从源码构建](#从源码构建) |

> **注意**：Release 上的 APK 为预编译调试包示例；生产环境请自行 **release 签名** 构建。仓库通过 `.gitignore` 忽略 `*.apk`，安装包请始终挂在 **Releases 资产**，不要强行提交进 Git。

### 首次使用

1. 安装 APK → 打开 **OpenKiwi**
2. **设置 → 模型配置** → 添加你的 API（DeepSeek、通义、OpenAI 兼容网关等）
3. 按需开启：**无障碍**（GUI Agent）、**通知使用权**（通知处理/自动回复）、**麦克风**（语音）等
4. 在对话中直接下任务，或让模型调用工具（见下表）

---

## 核心能力一览

### 自主操控界面（GUI Agent）

- 截屏 + 视觉模型理解界面，**点击、滑动、输入、打开应用** 等多步自动化  
- 任务分解、卡住与振荡检测、前台气泡提示  
- 需开启 **OpenKiwi 无障碍服务**

### MCP 协议（Model Context Protocol）

- 支持 **SSE / STDIO** 两种传输方式连接外部 MCP Server
- 侧边栏 **MCP 设置** 页面管理服务端配置
- MCP 工具自动桥接为 Agent 内置工具，无缝调用

### 多模型 Provider 抽象

- 统一 **LlmProvider** 接口，内置 **OpenAI / Anthropic / Gemini** 三种实现
- 每个模型可独立配置 Provider 类型，Agent 按需切换
- 兼容所有 OpenAI 兼容网关

### 插件系统

- **Plugin SDK**（`plugin-sdk/`）：标准化插件清单与接口
- 动态加载外部插件 JAR/DEX
- 插件管理页面，一键启停

### 内置工具（30+）

| 类别 | 代表能力 |
|------|----------|
| 系统 | Shell、系统信息、剪贴板、应用管理、Intent |
| 文件 / 媒体 | 文件读写、媒体库、下载 |
| 网络 | 网页抓取、搜索、FTP |
| 通信 | 电话/短信、通讯录、通知、**飞书** |
| 硬件 | 定位、相机、麦克风、传感器、连接与电源 |
| 语音 | 语音识别与合成 |
| 跨设备 | **SSH**、USB 串口、**PC 伴侣联动** |
| 代码 | **Chaquopy 本地 Python** + 可选 PC 端执行 |
| 记忆 / 技能 | 长期记忆、技能学习与执行、子 Agent |
| 扩展 | **AI 动态创建自定义工具**（Shell 等） |

### 飞书

- **Webhook** 或 **手机直连长连接**（设置中开关，需正确配置开放平台凭证）  
- **PC 伴侣** 亦可转发长连接事件到手机（避免与直连同时重复订阅）

### PC 伴侣（`companion-pc/`）

- 局域网 **WebSocket** 连接手机端 **Companion 服务**（默认端口 `8765`）  
- **聊天流式** 与手机端对齐：`stream`/`done` 与 `chat_stream`/`chat_end`  
- **文件上传 / 下载**（Base64，适合中小文件）  
- **mDNS**：手机端注册 `_openkiwi._tcp`，PC 端「发现手机」扫描局域网  

详见目录内说明与 `docs/RELEASING.md` 无关，运行依赖见 `companion-pc/requirements.txt`。

### 定时与自动化

- **定时任务**：WorkManager + Room，侧边栏 **「定时」**  
- **自动化配方**：多步 GuiAgent 目标流、工具 **`run_recipe`**、**配方** 页面管理预置与用户配方  

### 通知与剪贴板

- 通知 **智能分拣**、验证码提取、笔记汇总  
- **自动回复**（支持 RemoteInput 的应用，可按规则/白名单）  
- **剪贴板监听**（设置中开启，快捷动作）  

### 其他

- **寄生模式** / **微信·QQ 操控**（`app_reply_bot` 等，需无障碍与通知等配合）  
- **语音唤醒**（设置中开关与唤醒词）  
- **桌面小组件**：快捷向主会话发提示  
- **本地 RAG**：索引文档/下载目录文本，工具 **`local_rag`**；设置中 **重建索引**  

---

## 系统要求

- **Android 8.0（API 26）及以上**（以 `app/build.gradle.kts` 中 `minSdk` 为准）  
- **自备大模型 API**（OpenAI 兼容 HTTP API）  
- 部分功能需额外权限（无障碍、通知、悬浮窗、电池白名单等）

---

## 从源码构建

**环境**：JDK 17+（或 Android Studio 自带）、Android SDK  

```bash
# 调试 APK
./gradlew :app:assembleDebug

# 输出示例路径
# app/build/outputs/apk/debug/app-debug.apk
```

若你本地将输出重命名为 `OpenKiwi328.apk`，可直接用于 [发布到 GitHub Releases](#发布到-github-releases)。

---

## 发布到 GitHub Releases

维护者将 **OpenKiwi328.apk**（或任意版本 APK）上传到 Release 的完整步骤见：

**[docs/RELEASING.md](docs/RELEASING.md)**

快捷方式（需已安装 [`gh`](https://cli.github.com/) 且 `gh auth login`）：

```powershell
.\scripts\release-github.ps1 -Tag v3.2.8 -ApkPath .\OpenKiwi328.apk
```

发版说明模板：**[docs/RELEASE_NOTES_v3.2.8.md](docs/RELEASE_NOTES_v3.2.8.md)**（发布前可更新 SHA256 与文案；不传 `-NotesFile` 时脚本会按 tag 自动选 `docs/RELEASE_NOTES_v{version}.md`）。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 异步 | Kotlin Coroutines / Flow |
| 数据库 | Room + DataStore |
| 网络 | OkHttp 等（见 `gradle` 依赖） |
| 定时 | WorkManager |
| Python 运行时 | Chaquopy（应用内） |
| 架构 | MVVM、Repository、`AppContainer` 依赖组装 |

---

## 项目结构（摘要）

```
app/src/main/java/com/orizon/openkiwi/
├── core/
│   ├── agent/          # Agent 引擎 V2（规划/反思/子Agent）
│   ├── tool/           # 工具注册与 30+ 内置工具 + 持久终端
│   ├── gui/            # GUI Agent
│   ├── llm/            # LLM Provider 抽象（OpenAI/Anthropic/Gemini）
│   ├── mcp/            # MCP 协议客户端与工具桥接
│   ├── model/          # 模型与调度
│   ├── plugin/         # 插件加载与管理
│   ├── memory/         # 记忆
│   ├── skill/          # 技能
│   ├── notification/   # 通知处理、自动回复
│   ├── schedule/       # 定时任务
│   ├── rag/            # 本地文件索引与检索工具
│   ├── recipe/         # 自动化配方
│   ├── code/           # 代码沙箱
│   └── security/       # 审计与隐私相关
├── data/               # Room、Repository、Preferences
├── network/            # API、Companion、飞书、API Router
├── service/            # 无障碍、通知监听、语音、悬浮窗等
├── ui/                 # Compose 页面（纸质感杂志风主题）
├── widget/             # 桌面小组件
└── di/                 # AppContainer

companion-pc/           # Python + PyQt6 PC 伴侣
plugin-sdk/             # 插件开发 SDK
```

---

## 安全与隐私

- API Key、模型配置保存在本机；请使用可信 API 端点  
- **无障碍与 Shell 等能力风险高**，请在设置中保留「危险操作确认」等选项  
- 生产分发请使用 **自己的签名** 并建议公布 APK **SHA256**

---

## 开发者

**燃冰万象 / Traintime**  
仓库：<https://github.com/HuSuuuu/OpenKiwi>

## 许可证

MIT License
