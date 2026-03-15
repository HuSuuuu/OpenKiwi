"""File transfer panel for uploading/downloading files between PC and phone."""

import os
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QFileDialog, QProgressBar, QListWidget, QListWidgetItem, QGroupBox
)
from PyQt6.QtCore import pyqtSignal


class FilePanel(QWidget):
    upload_requested = pyqtSignal(str, str)   # local_path, remote_path
    download_requested = pyqtSignal(str, str) # remote_path, local_path

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(12)

        upload_group = QGroupBox("上传文件 (PC → 手机)")
        upload_layout = QVBoxLayout(upload_group)

        select_row = QHBoxLayout()
        self.selected_file_label = QLabel("未选择文件")
        self.selected_file_label.setStyleSheet("color: #8B949E;")
        select_row.addWidget(self.selected_file_label)

        self.select_btn = QPushButton("选择文件")
        self.select_btn.clicked.connect(self._select_file)
        select_row.addWidget(self.select_btn)
        upload_layout.addLayout(select_row)

        self.upload_btn = QPushButton("上传")
        self.upload_btn.setObjectName("primary")
        self.upload_btn.setEnabled(False)
        self.upload_btn.clicked.connect(self._on_upload)
        upload_layout.addWidget(self.upload_btn)

        layout.addWidget(upload_group)

        download_group = QGroupBox("下载文件 (手机 → PC)")
        download_layout = QVBoxLayout(download_group)

        remote_row = QHBoxLayout()
        remote_row.addWidget(QLabel("远程路径:"))
        from PyQt6.QtWidgets import QLineEdit
        self.remote_path_input = QLineEdit()
        self.remote_path_input.setPlaceholderText("/sdcard/Download/file.txt")
        remote_row.addWidget(self.remote_path_input)
        download_layout.addLayout(remote_row)

        self.download_btn = QPushButton("下载")
        self.download_btn.setObjectName("primary")
        self.download_btn.clicked.connect(self._on_download)
        download_layout.addWidget(self.download_btn)

        layout.addWidget(download_group)

        self.progress = QProgressBar()
        self.progress.setVisible(False)
        layout.addWidget(self.progress)

        log_label = QLabel("传输记录")
        log_label.setStyleSheet("font-weight: bold; margin-top: 8px;")
        layout.addWidget(log_label)

        self.transfer_log = QListWidget()
        layout.addWidget(self.transfer_log)

        self._selected_local_path = ""

    def _select_file(self):
        path, _ = QFileDialog.getOpenFileName(self, "选择文件")
        if path:
            self._selected_local_path = path
            name = os.path.basename(path)
            size = os.path.getsize(path)
            self.selected_file_label.setText(f"{name} ({_format_size(size)})")
            self.selected_file_label.setStyleSheet("color: #C9D1D9;")
            self.upload_btn.setEnabled(True)

    def _on_upload(self):
        if self._selected_local_path:
            remote = f"/sdcard/Download/{os.path.basename(self._selected_local_path)}"
            self.upload_requested.emit(self._selected_local_path, remote)
            self.add_log(f"↑ 上传: {os.path.basename(self._selected_local_path)}")

    def _on_download(self):
        remote = self.remote_path_input.text().strip()
        if not remote:
            return
        local, _ = QFileDialog.getSaveFileName(
            self, "保存到", os.path.basename(remote)
        )
        if local:
            self.download_requested.emit(remote, local)
            self.add_log(f"↓ 下载: {os.path.basename(remote)}")

    def add_log(self, text: str):
        self.transfer_log.insertItem(0, QListWidgetItem(text))

    def set_progress(self, value: int):
        self.progress.setVisible(value < 100)
        self.progress.setValue(value)


def _format_size(size: int) -> str:
    for unit in ["B", "KB", "MB", "GB"]:
        if size < 1024:
            return f"{size:.1f} {unit}"
        size /= 1024
    return f"{size:.1f} TB"
