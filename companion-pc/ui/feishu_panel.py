"""Feishu long-connection settings panel."""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QLineEdit, QPushButton, QGroupBox,
)
from PyQt6.QtCore import pyqtSignal


class FeishuPanel(QWidget):
    start_requested = pyqtSignal()
    stop_requested = pyqtSignal()
    status_updated = pyqtSignal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        layout = QVBoxLayout(self)

        info = QLabel(
            "飞书长连接模式：PC 主动连接飞书，无需公网 IP 或 ngrok。\n"
            "事件将转发到已连接的 OpenKiwi 手机。仅支持企业自建应用。"
        )
        info.setWordWrap(True)
        info.setStyleSheet("color: #8B949E; font-size: 12px;")
        layout.addWidget(info)

        group = QGroupBox("应用凭证")
        group_layout = QVBoxLayout(group)

        row1 = QHBoxLayout()
        row1.addWidget(QLabel("App ID:"))
        self.app_id_input = QLineEdit()
        self.app_id_input.setPlaceholderText("从飞书开放平台获取")
        self.app_id_input.setEchoMode(QLineEdit.EchoMode.Normal)
        row1.addWidget(self.app_id_input)
        group_layout.addLayout(row1)

        row2 = QHBoxLayout()
        row2.addWidget(QLabel("App Secret:"))
        self.app_secret_input = QLineEdit()
        self.app_secret_input.setPlaceholderText("从飞书开放平台获取")
        self.app_secret_input.setEchoMode(QLineEdit.EchoMode.Password)
        row2.addWidget(self.app_secret_input)
        group_layout.addLayout(row2)

        btn_row = QHBoxLayout()
        self.start_btn = QPushButton("启动飞书长连接")
        self.start_btn.setObjectName("primary")
        self.start_btn.clicked.connect(self._on_toggle)
        btn_row.addWidget(self.start_btn)
        btn_row.addStretch()
        group_layout.addLayout(btn_row)

        layout.addWidget(group)

        self.status_label = QLabel("飞书长连接未启动")
        self.status_label.setStyleSheet("color: #8B949E;")
        layout.addWidget(self.status_label)

        layout.addStretch()
        self._is_running = False

    def _on_toggle(self):
        if self._is_running:
            self.stop_requested.emit()
        else:
            self.start_requested.emit()

    def set_running(self, running: bool):
        self._is_running = running
        self.start_btn.setEnabled(True)
        self.start_btn.setText("停止飞书长连接" if running else "启动飞书长连接")

    def set_start_mode(self, can_start: bool):
        self._is_running = not can_start
        self.start_btn.setEnabled(True)
        self.start_btn.setText("启动飞书长连接" if can_start else "停止飞书长连接")

    def set_status(self, text: str):
        self.status_label.setText(text)

    def get_app_id(self) -> str:
        return self.app_id_input.text().strip()

    def get_app_secret(self) -> str:
        return self.app_secret_input.text().strip()
