# 本机 SearXNG + OpenKiwi（仅自己用）

OpenKiwi 的 **`web_search`** 工具在 `action=search` 时需要模型在调用里带上 **`search_api_url`**（模板里用 `{query}`，会自动 URL 编码）。

自托管 **SearXNG** 后，推荐使用 **`format=json`**，OpenKiwi 会解析 `results[]` 里的 `title` / `url` / `content`，比把 JSON 当 HTML 抽正文更干净。

---

## 1. 用 Docker 跑 SearXNG（只监听本机）

在 **装 Docker 的电脑**上执行（示例端口 `8080`）：

```bash
docker run -d --name searxng \
  -p 127.0.0.1:8080:8080 \
  -v searxng-data:/etc/searxng \
  searxng/searxng:latest
```

- **`127.0.0.1:8080`**：只有本机能访问，局域网其它设备也连不上（适合「搜索服务只跑在这台 PC 上」）。
- 首次使用需在容器/配置里按 [SearXNG 文档](https://docs.searxng.org/) 完成基础设置；若 JSON 被关，在 `settings.yml` 里确保允许 `json` 输出格式（见官方 *Search API* 说明）。

验证（在 **同一台电脑** 的浏览器或终端）：

```bash
curl -s "http://127.0.0.1:8080/search?q=openkiwi&format=json" | head -c 400
```

应看到以 `{` 开头的 JSON，且含 `"results"` 数组。

---

## 2. 手机上的 OpenKiwi 怎么访问「本机」SearXNG？

`127.0.0.1` 是 **手机自己**，不是电脑。要分两种情况：

| 场景 | `search_api_url` 里填的主机 |
|------|-----------------------------|
| **Android 模拟器** 跑 OpenKiwi，SearXNG 在 **同一台电脑** | `http://10.0.2.2:8080/search?q={query}&format=json` |
| **真机** 与电脑在 **同一 WiFi** | `http://<电脑局域网IP>:8080/search?q={query}&format=json`（如 `http://192.168.1.23:8080/...`） |

真机场景下，Docker 需把端口绑到局域网可访问的地址，例如：

```bash
docker run -d --name searxng \
  -p 8080:8080 \
  -v searxng-data:/etc/searxng \
  searxng/searxng:latest
```

并在电脑防火墙里 **仅允许局域网** 访问 `8080`（不要暴露到公网）。

---

## 3. 在对话里怎么用？

模型调用 **`web_search`** 时需带上 **`search_api_url`**（可在系统提示或技能里写死你的模板）。示例参数：

```json
{
  "action": "search",
  "query": "Kotlin 协程",
  "search_api_url": "http://10.0.2.2:8080/search?q={query}&format=json",
  "max_results": "8"
}
```

- 若模型经常不传 `search_api_url`，可在 **设置 → 模型** 里为常用模型写更明确的说明，或在 **用户偏好/技能** 中注明「搜索必须使用上述 `search_api_url`」。

---

## 4. 与方舟「联网 web_search」同时开？

两者工具名都是 **`web_search`** 时会去重。只用本地 SearXNG 时，请在模型配置里 **关闭** 方舟联网插件（或「仅联网」类互斥选项），保留 **本地 function 工具**。

---

## 5. 安全建议（仅本机 / 家里用）

- 不要把未鉴权的 SearXNG 端口映射到公网。
- 可选：前面加 **HTTP 基本认证** 或 **仅 WireGuard/Tailscale** 内网访问，再把 URL 换成 VPN 网段地址。
