"""Chat panel for communicating with the OpenKiwi Agent."""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QTextEdit, QLineEdit,
    QPushButton, QLabel, QScrollArea, QFrame
)
from PyQt6.QtCore import Qt, pyqtSignal
from PyQt6.QtGui import QKeyEvent


class ChatPanel(QWidget):
    message_sent = pyqtSignal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()
        self._streaming_text = ""

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        self.chat_display = QTextEdit()
        self.chat_display.setReadOnly(True)
        self.chat_display.setPlaceholderText("发送消息开始对话...")
        layout.addWidget(self.chat_display)

        input_bar = QFrame()
        input_bar.setStyleSheet("border-top: 1px solid #30363D;")
        input_layout = QHBoxLayout(input_bar)
        input_layout.setContentsMargins(8, 8, 8, 8)

        self.input_field = ChatInput()
        self.input_field.setPlaceholderText("输入消息... (Enter 发送, Shift+Enter 换行)")
        self.input_field.setMaximumHeight(80)
        self.input_field.send_requested.connect(self._on_send)
        input_layout.addWidget(self.input_field)

        self.send_btn = QPushButton("发送")
        self.send_btn.setObjectName("primary")
        self.send_btn.setFixedSize(60, 36)
        self.send_btn.clicked.connect(self._on_send)
        input_layout.addWidget(self.send_btn)

        layout.addWidget(input_bar)

    def _on_send(self):
        text = self.input_field.toPlainText().strip()
        if not text:
            return
        self.add_user_message(text)
        self.message_sent.emit(text)
        self.input_field.clear()

    def add_user_message(self, text: str):
        self.chat_display.append(
            f'<div style="color:#58A6FF;margin:4px 0"><b>你:</b></div>'
            f'<div style="color:#C9D1D9;margin:0 0 8px 12px">{_escape(text)}</div>'
        )
        self._scroll_bottom()

    def add_assistant_message(self, text: str):
        self.chat_display.append(
            f'<div style="color:#3FB950;margin:4px 0"><b>Agent:</b></div>'
            f'<div style="color:#C9D1D9;margin:0 0 8px 12px">{_escape(text)}</div>'
        )
        self._scroll_bottom()

    def begin_streaming(self):
        self._streaming_text = ""
        self.chat_display.append(
            '<div style="color:#3FB950;margin:4px 0"><b>Agent:</b></div>'
            '<div id="streaming" style="color:#C9D1D9;margin:0 0 8px 12px">'
        )

    def append_streaming(self, chunk: str):
        self._streaming_text += chunk
        cursor = self.chat_display.textCursor()
        cursor.movePosition(cursor.MoveOperation.End)
        cursor.insertText(chunk)
        self._scroll_bottom()

    def end_streaming(self):
        cursor = self.chat_display.textCursor()
        cursor.movePosition(cursor.MoveOperation.End)
        cursor.insertText("</div>")
        self._streaming_text = ""

    def add_system_message(self, text: str):
        self.chat_display.append(
            f'<div style="color:#8B949E;font-style:italic;margin:4px 0">{_escape(text)}</div>'
        )
        self._scroll_bottom()

    def _scroll_bottom(self):
        sb = self.chat_display.verticalScrollBar()
        sb.setValue(sb.maximum())


class ChatInput(QTextEdit):
    send_requested = pyqtSignal()

    def keyPressEvent(self, event: QKeyEvent):
        if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
            if event.modifiers() & Qt.KeyboardModifier.ShiftModifier:
                super().keyPressEvent(event)
            else:
                self.send_requested.emit()
        else:
            super().keyPressEvent(event)


def _escape(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")
    )
