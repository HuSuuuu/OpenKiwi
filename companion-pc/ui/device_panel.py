"""Device information panel showing phone status."""

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QGroupBox,
    QProgressBar, QPushButton, QGridLayout
)
from PyQt6.QtCore import pyqtSignal, QTimer

from core.protocol import DeviceInfo


class DevicePanel(QWidget):
    refresh_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(12)

        header = QHBoxLayout()
        header.addWidget(QLabel("设备信息"))
        header.addStretch()
        refresh_btn = QPushButton("刷新")
        refresh_btn.clicked.connect(self.refresh_requested.emit)
        header.addWidget(refresh_btn)
        layout.addLayout(header)

        device_group = QGroupBox("设备")
        device_grid = QGridLayout(device_group)
        self.model_label = self._info_row(device_grid, 0, "型号")
        self.android_label = self._info_row(device_grid, 1, "Android")
        self.ip_label = self._info_row(device_grid, 2, "IP 地址")
        self.wifi_label = self._info_row(device_grid, 3, "Wi-Fi")
        layout.addWidget(device_group)

        battery_group = QGroupBox("电池")
        battery_layout = QVBoxLayout(battery_group)
        self.battery_bar = QProgressBar()
        self.battery_bar.setMaximum(100)
        battery_layout.addWidget(self.battery_bar)
        self.battery_label = QLabel("--")
        battery_layout.addWidget(self.battery_label)
        layout.addWidget(battery_group)

        storage_group = QGroupBox("存储")
        storage_layout = QVBoxLayout(storage_group)
        self.storage_bar = QProgressBar()
        self.storage_bar.setMaximum(100)
        storage_layout.addWidget(self.storage_bar)
        self.storage_label = QLabel("--")
        storage_layout.addWidget(self.storage_label)
        layout.addWidget(storage_group)

        ram_group = QGroupBox("内存")
        ram_layout = QVBoxLayout(ram_group)
        self.ram_bar = QProgressBar()
        self.ram_bar.setMaximum(100)
        ram_layout.addWidget(self.ram_bar)
        self.ram_label = QLabel("--")
        ram_layout.addWidget(self.ram_label)
        layout.addWidget(ram_group)

        layout.addStretch()

    def _info_row(self, grid: QGridLayout, row: int, label: str) -> QLabel:
        grid.addWidget(QLabel(label + ":"), row, 0)
        value = QLabel("--")
        value.setStyleSheet("color: #C9D1D9;")
        grid.addWidget(value, row, 1)
        return value

    def update_info(self, info: DeviceInfo):
        self.model_label.setText(info.device_model or "--")
        self.android_label.setText(info.android_version or "--")
        self.ip_label.setText(info.ip_address or "--")
        self.wifi_label.setText(info.wifi_ssid or "--")

        self.battery_bar.setValue(info.battery_level)
        charge_text = " (充电中)" if info.battery_charging else ""
        self.battery_label.setText(f"{info.battery_level}%{charge_text}")

        if info.storage_total_mb > 0:
            used = info.storage_total_mb - info.storage_free_mb
            pct = int(used * 100 / info.storage_total_mb)
            self.storage_bar.setValue(pct)
            self.storage_label.setText(
                f"{_mb_to_gb(used):.1f} / {_mb_to_gb(info.storage_total_mb):.1f} GB"
            )

        if info.ram_total_mb > 0:
            used = info.ram_total_mb - info.ram_free_mb
            pct = int(used * 100 / info.ram_total_mb)
            self.ram_bar.setValue(pct)
            self.ram_label.setText(
                f"{_mb_to_gb(used):.1f} / {_mb_to_gb(info.ram_total_mb):.1f} GB"
            )


def _mb_to_gb(mb: int) -> float:
    return mb / 1024.0
