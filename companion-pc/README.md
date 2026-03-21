# OpenKiwi Companion PC

桌面端配套应用，通过 Wi-Fi (WebSocket) 或 USB (ADB Forward) 与手机端 OpenKiwi Agent 通信。

## 功能

- **双模连接**：Wi-Fi 局域网自动发现 + 手动 IP，或 USB Type-C 通过 ADB 端口转发
- **聊天交互**：向手机 Agent 发送指令，实时接收流式回复
- **远程终端**：在手机上远程执行命令
- **文件传输**：PC 与手机双向传输文件
- **设备信息**：查看手机电池、存储、网络等状态

## 安装

```bash
pip install -r requirements.txt
```

## 运行

```bash
python main.py
```

## USB 连接

确保 ADB 已安装并在 PATH 中，手机已开启 USB 调试：

```bash
adb forward tcp:8765 tcp:8765
```

然后在应用中使用 `localhost:8765` 连接。

## 飞书长连接（推荐）

接收飞书消息时，使用 **长连接模式** 无需公网 IP 或 ngrok：

1. 确保已连接 OpenKiwi 手机
2. 打开「飞书」页签，填写 App ID 和 App Secret（飞书开放平台 → 应用凭证）
3. 点击「启动飞书长连接」
4. 在飞书开放平台 → 事件订阅中，选择 **使用长连接接收事件**

事件将通过 PC 主动连接飞书，并转发到手机端，全程加密，无需端口映射。
