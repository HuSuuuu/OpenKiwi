"""Remote terminal panel for executing commands on the phone."""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPlainTextEdit, QLineEdit,
    QPushButton, QLabel
)
from PyQt6.QtCore import pyqtSignal
from PyQt6.QtGui import QFont, QTextCursor


class TerminalPanel(QWidget):
    command_sent = pyqtSignal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        header = QHBoxLayout()
        header.setContentsMargins(8, 6, 8, 6)
        header.addWidget(QLabel("远程终端"))
        header.addStretch()

        self.clear_btn = QPushButton("清除")
        self.clear_btn.setFixedHeight(28)
        self.clear_btn.clicked.connect(self._clear)
        header.addWidget(self.clear_btn)
        layout.addLayout(header)

        mono_font = QFont("Consolas", 11)
        mono_font.setStyleHint(QFont.StyleHint.Monospace)

        self.output = QPlainTextEdit()
        self.output.setReadOnly(True)
        self.output.setFont(mono_font)
        self.output.setStyleSheet(
            "background-color: #0D1117; color: #B0B0B0; border: none; padding: 8px;"
        )
        layout.addWidget(self.output)

        input_bar = QHBoxLayout()
        input_bar.setContentsMargins(8, 4, 8, 8)

        self.prompt_label = QLabel("$")
        self.prompt_label.setStyleSheet("color: #58A6FF; font-weight: bold;")
        input_bar.addWidget(self.prompt_label)

        self.cmd_input = QLineEdit()
        self.cmd_input.setFont(mono_font)
        self.cmd_input.setPlaceholderText("输入命令...")
        self.cmd_input.returnPressed.connect(self._on_send)
        input_bar.addWidget(self.cmd_input)

        self.run_btn = QPushButton("执行")
        self.run_btn.setObjectName("primary")
        self.run_btn.setFixedHeight(30)
        self.run_btn.clicked.connect(self._on_send)
        input_bar.addWidget(self.run_btn)

        layout.addLayout(input_bar)

    def _on_send(self):
        cmd = self.cmd_input.text().strip()
        if not cmd:
            return
        self.output.appendPlainText(f"$ {cmd}")
        self.command_sent.emit(cmd)
        self.cmd_input.clear()

    def append_output(self, text: str, is_error: bool = False):
        self.output.appendPlainText(text)
        cursor = self.output.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        self.output.setTextCursor(cursor)

    def _clear(self):
        self.output.clear()
