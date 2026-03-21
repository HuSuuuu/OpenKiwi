"""Feishu long-connection (WebSocket) client. Forwards events to OpenKiwi Android."""

import json
import logging
import threading
from typing import Callable, Optional

logger = logging.getLogger(__name__)


class FeishuWsForwarder:
    """Runs Feishu WebSocket client in a background thread, forwards events to Android."""

    def __init__(
        self,
        app_id: str,
        app_secret: str,
        android_base_url: str,
        on_status: Optional[Callable[[str], None]] = None,
    ):
        self.app_id = app_id
        self.app_secret = app_secret
        self.android_base_url = android_base_url.rstrip("/")
        self.on_status = on_status
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()

    def _emit_status(self, msg: str):
        if self.on_status:
            try:
                self.on_status(msg)
            except Exception:
                pass

    def _forward_to_android(self, payload: dict):
        url = f"{self.android_base_url}/api/feishu/event"
        try:
            import requests
            resp = requests.post(url, json=payload, timeout=5)
            if resp.status_code != 200:
                logger.warning(f"Feishu forward to Android failed: {resp.status_code} {resp.text[:200]}")
        except Exception as e:
            logger.error(f"Feishu forward error: {e}")

    def _build_webhook_payload(self, event_type: str, event_data: dict) -> dict:
        """Convert lark event to webhook-compatible format for Android."""
        return {
            "type": "event_callback",
            "header": {"event_type": event_type},
            "event": event_data,
        }

    def _run_ws_client(self):
        try:
            import lark_oapi as lark
        except ImportError:
            self._emit_status("飞书: 未安装 lark-oapi，请运行 pip install lark-oapi")
            return

        def _to_dict(obj):
            """Extract dict from lark event object."""
            if hasattr(obj, "to_dict"):
                return obj.to_dict()
            if hasattr(obj, "__dict__"):
                return {k: _to_dict(v) for k, v in obj.__dict__.items() if not k.startswith("_")}
            return obj

        def do_p2_im_message_receive_v1(data) -> None:
            try:
                raw = lark.JSON.marshal(data) if hasattr(lark, "JSON") else _to_dict(data)
                if not isinstance(raw, dict):
                    raw = {}
                event_data = raw.get("event") or raw.get("message") or raw
                if isinstance(event_data, dict) and "message" not in event_data and "message_id" in raw:
                    event_data = {"message": raw, "sender": raw.get("sender", {})}
                elif not isinstance(event_data, dict):
                    event_data = {}
                payload = self._build_webhook_payload("im.message.receive_v1", event_data)
                self._forward_to_android(payload)
            except Exception as e:
                logger.exception(f"Feishu event handle error: {e}")

        event_handler = (
            lark.EventDispatcherHandler.builder("", "")
            .register_p2_im_message_receive_v1(do_p2_im_message_receive_v1)
            .build()
        )

        try:
            self._emit_status("飞书: 正在连接...")
            cli = lark.ws.Client(
                self.app_id,
                self.app_secret,
                event_handler=event_handler,
                log_level=lark.LogLevel.WARN,
            )
            self._emit_status("飞书: 长连接已建立")
            cli.start()
        except Exception as e:
            self._emit_status(f"飞书: 连接失败 - {e}")
            logger.exception("Feishu WS client error")
        finally:
            self._emit_status("飞书: 长连接已断开")

    def start(self) -> bool:
        if not self.app_id or not self.app_secret:
            self._emit_status("飞书: 请配置 App ID 和 App Secret")
            return False
        if not self.android_base_url or "http" not in self.android_base_url:
            self._emit_status("飞书: 请先连接 OpenKiwi 手机")
            return False
        if self._thread and self._thread.is_alive():
            return True
        self._stop.clear()
        self._thread = threading.Thread(target=self._run_ws_client, daemon=True)
        self._thread.start()
        return True

    def stop(self):
        self._stop.set()
        # lark cli.start() is blocking, we cannot interrupt it from here
        # User needs to close the app or we'd need to use a different approach
        self._emit_status("飞书: 将在下次重连时停止")
