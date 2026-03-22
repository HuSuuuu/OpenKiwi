## OpenKiwi 3.2.2

预编译调试包 **OpenKiwi322.apk**（便于快速体验；生产环境请自行 release 签名构建）。

### 安装

1. 下载附件 `OpenKiwi322.apk`
2. 允许「安装未知来源应用」
3. 安装后：设置 → 模型配置 → 填入你的 OpenAI 兼容 API

### 本版更新摘要

- **工具**：支持按对话 BM25 检索后动态加载工具（设置 → 模型 →「工具检索后动态加载」）；新增 **`scheduled_task`** 定时任务工具；**`code_execution`** 与系统提示对齐（`code_execution` / `code_execute` 别名）；**`web_search`** 仅传 `query` 时自动视为 `search`
- **Python**：修复冷启动未 `Python.start()` 时误判不可用、无法走 Chaquopy 的问题；shell 中拦截 `python` 调用并提示使用 `code_execution`
- **方舟联网**：Chat Completions 下 `function.name=web_search` 与 Responses API 文档差异说明；模型配置页补充说明；`ArkWebSearchBodies` 供后续 Responses 接入
- **其他**：Vision 请求 JSON 附带 `tools`；模型配置项 `includeWebSearchTool` / `webSearchExclusive`（Room v10）；若干系统提示与工具描述优化

### 系统要求

- Android 8.0（API 26）及以上
- 需自备大模型 API；部分能力依赖无障碍、通知监听等权限

### 校验（可选）

```
SHA256: 74776D1C02BCCC6B610E3098DF514C12C4F88ECBE23365D8F563A1F21A5F8CD5
```

### 许可证

MIT — 见仓库根目录 `LICENSE`。
