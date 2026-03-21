"""Main window for OpenKiwi Companion PC application."""

import asyncio
import base64
import logging
from pathlib import Path
from typing import Optional

from PyQt6.QtWidgets import (
    QMainWindow, QTabWidget, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QPushButton, QLineEdit, QStatusBar, QGroupBox,
    QComboBox, QSystemTrayIcon, QMenu
)
from PyQt6.QtCore import Qt, QTimer
from PyQt6.QtGui import QIcon, QAction, QPixmap, QPainter, QColor

from core.connection import ConnectionManager, ConnectionState
from core.protocol import WsMessage, MessageType, DeviceInfo
from core.usb_bridge import USBBridge
from core.feishu_client import FeishuWsForwarder
from core.discovery import discover_openkiwi_phones
from .chat_panel import ChatPanel
from .feishu_panel import FeishuPanel
from .terminal_panel import TerminalPanel
from .file_panel import FilePanel
from .device_panel import DevicePanel

logger = logging.getLogger(__name__)


def _run_async(coro):
    """Schedule a coroutine on the running event loop (qasync-safe)."""
    try:
        loop = asyncio.get_running_loop()
        return loop.create_task(coro)
    except RuntimeError:
        return asyncio.ensure_future(coro)


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("OpenKiwi Companion")
        self.setMinimumSize(800, 600)
        self.resize(960, 680)

        self.conn = ConnectionManager()
        self.usb_bridge = USBBridge()
        self.feishu_forwarder: Optional[FeishuWsForwarder] = None
        self.conn.set_callbacks(self._on_message, self._on_state_change)

        self._setup_ui()
        self._setup_tray()

    def _setup_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        main_layout = QVBoxLayout(central)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        conn_bar = QWidget()
        conn_bar.setStyleSheet("background-color: #161B22; border-bottom: 1px solid #30363D;")
        conn_layout = QHBoxLayout(conn_bar)
        conn_layout.setContentsMargins(12, 8, 12, 8)

        conn_layout.addWidget(QLabel("连接:"))

        self.conn_mode = QComboBox()
        self.conn_mode.addItems(["Wi-Fi", "USB (ADB)"])
        self.conn_mode.setFixedWidth(100)
        self.conn_mode.currentIndexChanged.connect(self._on_mode_change)
        conn_layout.addWidget(self.conn_mode)

        self.host_input = QLineEdit()
        self.host_input.setPlaceholderText("手机 IP 地址 (如 192.168.1.100)")
        self.host_input.setFixedWidth(200)
        conn_layout.addWidget(self.host_input)

        self.port_input = QLineEdit("8765")
        self.port_input.setFixedWidth(60)
        conn_layout.addWidget(self.port_input)

        scan_btn = QPushButton("发现手机")
        scan_btn.setToolTip("mDNS 扫描 _openkiwi._tcp（需手机端 Companion 已启动）")
        scan_btn.clicked.connect(self._on_scan_openkiwi)
        conn_layout.addWidget(scan_btn)

        self.connect_btn = QPushButton("连接")
        self.connect_btn.setObjectName("primary")
        self.connect_btn.setFixedWidth(80)
        self.connect_btn.clicked.connect(self._on_connect_click)
        conn_layout.addWidget(self.connect_btn)

        conn_layout.addStretch()

        self.status_label = QLabel("● 未连接")
        self.status_label.setObjectName("status-disconnected")
        conn_layout.addWidget(self.status_label)

        main_layout.addWidget(conn_bar)

        self.tabs = QTabWidget()

        self.chat_panel = ChatPanel()
        self.chat_panel.message_sent.connect(self._on_chat_send)
        self.tabs.addTab(self.chat_panel, "💬 聊天")

        self.terminal_panel = TerminalPanel()
        self.terminal_panel.command_sent.connect(self._on_terminal_send)
        self.tabs.addTab(self.terminal_panel, "⬛ 终端")

        self.file_panel = FilePanel()
        self.file_panel.upload_requested.connect(self._on_file_upload)
        self.file_panel.download_requested.connect(self._on_file_download)
        self.tabs.addTab(self.file_panel, "📁 文件")

        self.device_panel = DevicePanel()
        self.device_panel.refresh_requested.connect(self._on_device_refresh)
        self.tabs.addTab(self.device_panel, "📱 设备")

        self.feishu_panel = FeishuPanel(self)
        self.feishu_panel.start_requested.connect(self._on_feishu_start)
        self.feishu_panel.stop_requested.connect(self._on_feishu_stop)
        self.tabs.addTab(self.feishu_panel, "📨 飞书")

        main_layout.addWidget(self.tabs)

        self.statusBar().showMessage("就绪")

    @staticmethod
    def _make_tray_icon() -> QIcon:
        px = QPixmap(64, 64)
        px.fill(QColor(0, 0, 0, 0))
        p = QPainter(px)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.setBrush(QColor("#3FB950"))
        p.setPen(Qt.PenStyle.NoPen)
        p.drawEllipse(8, 8, 48, 48)
        p.end()
        return QIcon(px)

    def _setup_tray(self):
        self.tray = QSystemTrayIcon(self)
        self.tray.setIcon(self._make_tray_icon())
        self.tray.setToolTip("OpenKiwi Companion")

        tray_menu = QMenu()
        show_action = QAction("显示窗口", self)
        show_action.triggered.connect(self.show)
        tray_menu.addAction(show_action)

        quit_action = QAction("退出", self)
        quit_action.triggered.connect(self._on_quit)
        tray_menu.addAction(quit_action)

        self.tray.setContextMenu(tray_menu)
        self.tray.activated.connect(self._on_tray_activated)
        self.tray.show()

    def closeEvent(self, event):
        event.ignore()
        self.hide()
        self.tray.showMessage("OpenKiwi", "已最小化到系统托盘", QSystemTrayIcon.MessageIcon.Information, 2000)

    def _on_quit(self):
        _run_async(self.conn.disconnect())
        from PyQt6.QtWidgets import QApplication
        QApplication.instance().quit()

    def _on_tray_activated(self, reason):
        if reason == QSystemTrayIcon.ActivationReason.Trigger:
            self.show()
            self.activateWindow()

    def _on_mode_change(self, index: int):
        is_wifi = index == 0
        self.host_input.setEnabled(is_wifi)
        if not is_wifi:
            self.host_input.setText("127.0.0.1")

    def _on_connect_click(self):
        if self.conn.state == ConnectionState.CONNECTED:
            _run_async(self.conn.disconnect())
        else:
            _run_async(self._do_connect())

    def _on_scan_openkiwi(self):
        async def _go():
            self.statusBar().showMessage("正在扫描局域网 OpenKiwi (_openkiwi._tcp)…")
            found = await asyncio.to_thread(discover_openkiwi_phones, 3.0)
            if not found:
                self.chat_panel.add_system_message(
                    "未发现 OpenKiwi 服务。请确认手机已连接同一 Wi‑Fi，且本机 Companion 服务已启动。"
                )
                self.statusBar().showMessage("未发现 OpenKiwi")
                return
            host, port = found[0]
            self.host_input.setText(host)
            self.port_input.setText(str(port))
            self.chat_panel.add_system_message(f"已填入 {host}:{port}（共发现 {len(found)} 台，使用第一台）")
            self.statusBar().showMessage(f"发现 OpenKiwi {host}:{port}")

        _run_async(_go())

    async def _do_connect(self):
        try:
            if self.conn_mode.currentIndex() == 1:
                self.statusBar().showMessage("正在设置 ADB 端口转发...")
                devices = await self.usb_bridge.get_devices()
                if not devices:
                    self.chat_panel.add_system_message("未发现 ADB 设备，请检查 USB 连接和 ADB 调试是否开启")
                    return
                ok = await self.usb_bridge.setup_forward(devices[0])
                if not ok:
                    self.chat_panel.add_system_message("ADB 端口转发失败")
                    return
                host, port = self.usb_bridge.get_local_address()
            else:
                host = self.host_input.text().strip()
                port = int(self.port_input.text().strip() or "8765")

            if not host:
                self.chat_panel.add_system_message("请输入手机 IP 地址")
                return

            self.statusBar().showMessage(f"正在连接 {host}:{port}...")
            await self.conn.connect(host, port)

        except Exception as e:
            self.chat_panel.add_system_message(f"连接失败: {e}")
            self.statusBar().showMessage("连接失败")

    def _on_state_change(self, state: ConnectionState):
        if state == ConnectionState.CONNECTED:
            self.status_label.setText("● 已连接")
            self.status_label.setObjectName("status-connected")
            self.connect_btn.setText("断开")
            self.statusBar().showMessage(f"已连接到 {self.conn.address}")
            _run_async(self.conn.request_device_info())
        elif state == ConnectionState.DISCONNECTED:
            self.status_label.setText("● 未连接")
            if self.feishu_forwarder:
                self.feishu_forwarder.stop()
                self.feishu_forwarder = None
                self.feishu_panel.set_start_mode(True)
            self.status_label.setObjectName("status-disconnected")
            self.connect_btn.setText("连接")
            self.statusBar().showMessage("已断开")
        elif state == ConnectionState.ERROR:
            self.status_label.setText("● 连接错误")
            self.status_label.setObjectName("status-error")
            self.connect_btn.setText("连接")
        self.status_label.style().unpolish(self.status_label)
        self.status_label.style().polish(self.status_label)

    def _on_message(self, msg: WsMessage):
        if msg.type in (MessageType.CHAT_STREAM, "chat_stream", "stream"):
            self.chat_panel.append_streaming(msg.content)
        elif msg.type in (MessageType.CHAT_END, "chat_end", "end", "done"):
            self.chat_panel.end_streaming()
        elif msg.type in (MessageType.CHAT, "chat", "response"):
            self.chat_panel.add_assistant_message(msg.content)
        elif msg.type in ("connected",):
            self.chat_panel.add_system_message(msg.content)
        elif msg.type in (MessageType.TERMINAL_OUTPUT, "terminal_output"):
            self.terminal_panel.append_output(msg.content)
        elif msg.type in (MessageType.DEVICE_INFO_RESPONSE, "device_info_response"):
            info = DeviceInfo.from_dict(msg.extra)
            self.device_panel.update_info(info)
        elif msg.type in (MessageType.CODE_EXECUTE, "code_execute"):
            self._handle_code_execute(msg)
        elif msg.type in (MessageType.PONG, "pong"):
            pass
        elif msg.type in ("thinking",):
            pass
        elif msg.type in (MessageType.FILE_DATA, "file_data"):
            pass  # correlation handled in ConnectionManager._dispatch_file_data
        elif msg.type in (MessageType.ERROR, "error"):
            self.chat_panel.add_system_message(f"错误: {msg.content}")

    def _on_chat_send(self, text: str):
        if self.conn.state != ConnectionState.CONNECTED:
            self.chat_panel.add_system_message("未连接，请先连接到手机")
            return
        self.chat_panel.begin_streaming()
        _run_async(self.conn.send_chat(text))

    def _on_terminal_send(self, cmd: str):
        if self.conn.state != ConnectionState.CONNECTED:
            self.terminal_panel.append_output("[未连接]")
            return
        _run_async(self.conn.send_terminal(cmd))

    def _on_file_upload(self, local: str, remote: str):
        async def _go():
            if self.conn.state != ConnectionState.CONNECTED:
                self.chat_panel.add_system_message("未连接，无法上传")
                return
            self.chat_panel.add_system_message(f"正在上传 {local} …")
            r = await self.conn.send_file_upload(local, remote)
            if r.get("ok"):
                self.chat_panel.add_system_message(f"上传成功: {r.get('path', '')} ({r.get('bytes', 0)} bytes)")
            else:
                self.chat_panel.add_system_message(f"上传失败: {r.get('error', r)}")

        _run_async(_go())

    def _on_file_download(self, remote: str, local: str):
        async def _go():
            if self.conn.state != ConnectionState.CONNECTED:
                self.chat_panel.add_system_message("未连接，无法下载")
                return
            self.chat_panel.add_system_message(f"正在从手机拉取 {remote} …")
            r = await self.conn.request_file_download(remote)
            if not r.get("ok"):
                self.chat_panel.add_system_message(f"下载失败: {r.get('error', r)}")
                return
            try:
                raw = base64.b64decode(r.get("data", ""))
                Path(local).parent.mkdir(parents=True, exist_ok=True)
                Path(local).write_bytes(raw)
                self.chat_panel.add_system_message(f"已保存到 {local} ({len(raw)} bytes)")
            except Exception as e:
                self.chat_panel.add_system_message(f"保存本地文件失败: {e}")

        _run_async(_go())

    def _on_device_refresh(self):
        if self.conn.state != ConnectionState.CONNECTED:
            return
        _run_async(self.conn.request_device_info())

    def _on_feishu_start(self):
        if self.conn.state != ConnectionState.CONNECTED:
            self.feishu_panel.set_status("请先连接 OpenKiwi 手机")
            self.feishu_panel.set_running(False)
            return
        app_id = self.feishu_panel.get_app_id()
        app_secret = self.feishu_panel.get_app_secret()
        if not app_id or not app_secret:
            self.feishu_panel.set_status("请填写 App ID 和 App Secret")
            self.feishu_panel.set_running(False)
            return
        android_url = self.conn.android_http_url
        if not android_url:
            self.feishu_panel.set_status("无法获取手机地址")
            self.feishu_panel.set_running(False)
            return
        self.feishu_forwarder = FeishuWsForwarder(
            app_id=app_id,
            app_secret=app_secret,
            android_base_url=android_url,
            on_status=lambda s: self.feishu_panel.set_status(s),
        )
        if self.feishu_forwarder.start():
            self.feishu_panel.set_running(True)
        else:
            self.feishu_panel.set_running(False)

    def _on_feishu_stop(self):
        if self.feishu_forwarder:
            self.feishu_forwarder.stop()
            self.feishu_forwarder = None
        self.feishu_panel.set_running(False)
        self.feishu_panel.set_status("飞书长连接未启动")

    def _handle_code_execute(self, msg: WsMessage):
        import threading
        code = msg.content
        language = msg.extra.get("language", "python")
        timeout = int(msg.extra.get("timeout", 30))
        request_id = msg.extra.get("request_id", "")

        self.terminal_panel.append_output(f"[PC执行 {language}] {code[:80]}...")

        def run():
            from core.code_runner import execute_code
            result = execute_code(code, language, timeout)
            self.terminal_panel.append_output(
                f"[PC完成 {language}] exit={result.exit_code} {result.execution_time_ms}ms"
            )
            import json
            reply = WsMessage(
                type="code_result",
                content=json.dumps(result.to_dict(), ensure_ascii=False),
                extra={"request_id": request_id},
            )
            _run_async(self.conn.send(reply))

        threading.Thread(target=run, daemon=True).start()
