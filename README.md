# OpenKiwi

**开源 Android AI Agent — 让每一台安卓手机都有自己的智能体**

OpenKiwi 是一个运行在 Android 上的开源 AI Agent，不是套壳聊天应用，而是能真正控制手机、执行任务的智能体。接入你自己的大模型 API，数据完全本地化。

## 核心能力

### 自主操控手机
- **GUI Agent** — 通过截屏分析 + 坐标操作，自主导航任意 App
- 支持多步骤任务分解、卡住检测、自动恢复

### 30+ 内置工具
| 类别 | 工具 |
|------|------|
| 系统 | Shell 命令、系统信息、剪贴板、App 管理、Intent |
| 文件 | 文件读写、媒体库查询、下载管理 |
| 网络 | 网页抓取、搜索引擎、FTP |
| 通信 | 电话/短信、通讯录、通知管理、飞书 |
| 硬件 | GPS 定位、相机、麦克风、传感器、蓝牙/WiFi |
| 语音 | 语音识别、语音合成 |
| 跨设备 | SSH 远程、USB 串口 |

### 智能通知处理
- 小模型自动分拣通知（验证码、快递、提醒等）
- 验证码自动提取并复制到剪贴板
- 笔记页汇总展示待处理和已处理通知

### AI 自主创建工具
- 在对话中让 AI 编写 Shell 脚本，自动创建可复用工具
- 工具页管理所有内置和自定义工具

### 记忆与技能
- **长期记忆** — 跨会话存储用户偏好、重要信息
- **技能学习** — 从成功任务中自动提炼多步骤工作流
- **上下文压缩** — LLM 驱动的智能对话摘要

### 多模型支持
- 兼容任意 OpenAI 格式 API（DeepSeek、Qwen、GPT、Claude 等）
- 智能模型调度 — 按任务类型自动选择最优模型
- 流式输出 + Token 用量追踪

## 快速开始

1. 从 [Releases](https://github.com/HuSuuuu/OpenKiwi/releases) 下载 APK 安装
2. 打开 OpenKiwi → 设置 → 模型配置 → 添加模型 API
3. （可选）开启无障碍服务、通知读取权限
4. 开始对话，让 AI 帮你做事！

## 系统要求

- Android 8.0 (API 26) 及以上
- 自备大模型 API Key

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **数据库**: Room
- **网络**: Ktor Client
- **架构**: MVVM + Repository + DI Container

## 项目结构

```
app/src/main/java/com/orizon/openkiwi/
├── core/
│   ├── agent/        # Agent 引擎、系统提示词、子 Agent
│   ├── tool/         # 工具注册、执行、30+ 内置工具
│   ├── gui/          # GUI Agent 自主操控
│   ├── model/        # 多模型管理、智能调度
│   ├── memory/       # 记忆系统
│   ├── skill/        # 技能学习
│   ├── notification/  # 通知处理
│   └── security/     # 安全审计
├── data/             # Room 数据库、Repository、DataStore
├── network/          # API 客户端、HTTP
├── service/          # 无障碍、通知监听、前台服务
├── ui/               # Compose UI 页面
└── di/               # 依赖注入
```

## PC 伴侣端

`companion-pc/` 目录包含 Python 桌面端，支持通过局域网与手机端联动。

## 开发者

**燃冰万象/Traintime**

## 许可证

MIT License
