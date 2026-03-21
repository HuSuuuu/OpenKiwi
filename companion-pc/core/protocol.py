"""Communication protocol definitions for OpenKiwi Companion."""

import json
from dataclasses import dataclass, field, asdict
from enum import Enum
from typing import Optional


class MessageType(str, Enum):
    CHAT = "chat"
    CHAT_STREAM = "chat_stream"
    CHAT_END = "chat_end"
    TERMINAL = "terminal"
    TERMINAL_OUTPUT = "terminal_output"
    FILE_UPLOAD = "file_upload"
    FILE_DOWNLOAD = "file_download"
    FILE_DATA = "file_data"
    DEVICE_INFO = "device_info"
    DEVICE_INFO_RESPONSE = "device_info_response"
    CODE_EXECUTE = "code_execute"
    CODE_RESULT = "code_result"
    PING = "ping"
    PONG = "pong"
    ERROR = "error"
    SESSIONS = "sessions"
    SESSION_LIST = "session_list"


@dataclass
class WsMessage:
    type: str
    content: str = ""
    session_id: str = ""
    extra: dict = field(default_factory=dict)

    def to_json(self) -> str:
        d = {"type": self.type, "content": self.content, "sessionId": self.session_id}
        d.update(self.extra)
        return json.dumps(d, ensure_ascii=False)

    @classmethod
    def from_json(cls, data: str) -> "WsMessage":
        d = json.loads(data)
        return cls(
            type=d.get("type", ""),
            content=d.get("content", ""),
            session_id=d.get("sessionId", ""),
            extra={k: v for k, v in d.items() if k not in ("type", "content", "sessionId")},
        )


@dataclass
class DeviceInfo:
    battery_level: int = 0
    battery_charging: bool = False
    storage_free_mb: int = 0
    storage_total_mb: int = 0
    ram_free_mb: int = 0
    ram_total_mb: int = 0
    wifi_ssid: str = ""
    ip_address: str = ""
    android_version: str = ""
    device_model: str = ""

    @classmethod
    def from_dict(cls, d: dict) -> "DeviceInfo":
        return cls(**{k: v for k, v in d.items() if k in cls.__dataclass_fields__})
