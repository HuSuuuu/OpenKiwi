# OpenKiwi 架构说明（一页纸）

本文描述 **Android 应用本体**（`app/`）的职责划分与主数据流，便于对照参考仓库（如 `openclaw-main`、`reference/claude_code_src-master`）时不混淆边界。

---

## 1. 仓库里分别是什么

| 路径 | 说明 |
|------|------|
| **`app/`** | OpenKiwi 唯一参与 Gradle 编译的产品代码。 |
| **`docs/`** | 发布、架构等文档（本文件、RELEASING 等）。 |
| **`companion-pc/`** | PC 伴侣（Python），与手机通过局域网 WebSocket 等联动。 |
| **`openclaw-main/`**（若存在） | **上游 OpenClaw 参考**，非本 App 子模块；不随 `app` 编译。 |
| **`reference/`**（若存在） | 其他大型参考源码（如 Claude Code 树），**只读**，不参与 APK 构建。 |

根目录 **`plugin-sdk/`** 并非 OpenKiwi 内路径；动态插件相关代码在 **`app/.../core/plugin`**。

---

## 2. 主数据流（对话 → 工具）

```
用户输入
  → UI：ChatScreen / ChatViewModel
  → AgentEngine：组装消息、可选工具检索（BM25）、请求 LLM（流式 + tool_calls）
  → ToolExecutor：执行具体 Tool（权限、超时、审计）
  → 结果写回会话 / 通知 / ProactiveMessageBus 等
```

- **系统提示**：`AgentSystemPrompt` + 可选 `AgentWorkspace` 上下文 + **OpenClaw 技能目录**（`OpenClawSkillRegistry.buildSkillCatalog()`）。
- **依赖入口**：`di/AppContainer` 单例组装网络、数据库、`ToolRegistry`、`AgentEngine` 等。

---

## 3. `com.orizon.openkiwi` 包职责（摘要）

| 包 | 职责 |
|----|------|
| **`core/agent`** | `AgentEngine` 主循环、反思、主动消息等。 |
| **`core/tool`** | `Tool` 契约、`ToolRegistry` / `ToolExecutor`、内置工具实现。 |
| **`core/gui`** | GuiAgent：截图、解析动作、执行自动化。 |
| **`core/llm`** | 多厂商 LLM 封装。 |
| **`core/mcp`** | MCP 客户端；远端工具桥接进同一 `ToolRegistry`。 |
| **`core/openclaw`** | OpenClaw **集成层**：SKILL.md 解析与注册、可选 Gateway 远程工具（非 OpenClaw 本体实现）。 |
| **`core/skill`** | **工作流技能**（Room 持久化、多步工具链），与 OpenClaw SKILL **不同概念**。 |
| **`core/memory` / `schedule` / `reminder` / `notification`** | 记忆、定时任务、提醒、通知处理。 |
| **`data/`** | Room、DataStore、Repository。 |
| **`network/`** | HTTP API、Companion、飞书等。 |
| **`service/`** | 前台服务、无障碍相关、语音、悬浮窗等。 |
| **`ui/`** | Jetpack Compose 界面与导航。 |
| **`di/`** | `AppContainer`。 |

---

## 4. 两种「技能」不要混用

| 名称 | 代码入口 | 含义 |
|------|----------|------|
| **工作流技能** | `SkillManager` + 侧栏「技能 → 工作流」 | Room 存的多步 `SkillDefinition`，由 `SkillExecutor` 按步调工具。 |
| **OpenClaw 技能** | `OpenClawSkillRegistry` + 侧栏「技能 → OpenClaw」 | `SKILL.md`（YAML + Markdown），给模型的**操作说明**；可内置 assets 或文件导入。 |

---

## 5. `AppContainer` 里工具注册分两块

1. **`ToolRegistry().apply { ... }`**：绝大多数内置工具（系统 / 网络 / 设备 / OpenClaw 技能工具等）。
2. **`init { toolRegistry.register(...) }`**：依赖其他单例较晚注册的工具（子 Agent、GuiAgent、MCP 动态工具在连接后注册等）。

详见 `AppContainer` 源码内分段注释。

---

## 6. 与 OpenClaw 上游的关系

- **本地**：`assets/openclaw_skills/` 下的 SKILL.md 由 `OpenClawSkillLoader` 解析，**不依赖** OpenClaw 服务器。
- **可选远程**：`openclaw` 工具 + `OpenClawPluginManager` 连接自建 Gateway，用于拉取远端扩展工具；与上文本地 SKILL 并行存在。

---

## 7. 维护建议（对齐大型参考仓的习惯）

- 大改动前先读本文件与 `README`「仓库布局」。
- 新增能力优先落在 **`core/<领域>`**，避免在 `AppContainer` 无限堆逻辑（可抽 `*Module` 或注册函数）。
- 参考树勿在业务 PR 里修改；仅更新 **拷贝进 `assets` 的 SKILL** 或文档说明。
