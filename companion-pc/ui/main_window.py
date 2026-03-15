"""Main window for OpenKiwi Companion PC application."""

import asyncio
import logging
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
from .chat_panel import ChatPanel
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
        elif msg.type in (MessageType.PONG, "pong"):
            pass
        elif msg.type in ("thinking",):
            pass
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
        self.chat_panel.add_system_message(f"文件上传功能需要配合手机端 CompanionServer 的 file_upload 支持")

    def _on_file_download(self, remote: str, local: str):
        self.chat_panel.add_system_message(f"文件下载功能需要配合手机端 CompanionServer 的 file_download 支持")

    def _on_device_refresh(self):
        if self.conn.state != ConnectionState.CONNECTED:
            return
        _run_async(self.conn.request_device_info())
