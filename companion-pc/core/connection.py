"""Connection manager for WebSocket communication with OpenKiwi Agent."""

import asyncio
import base64
import json
import logging
import uuid
from enum import Enum
from pathlib import Path
from typing import Optional, Callable

from websockets.asyncio.client import connect as ws_connect
from websockets.exceptions import ConnectionClosed

from .protocol import WsMessage, MessageType

logger = logging.getLogger(__name__)


class ConnectionState(str, Enum):
    DISCONNECTED = "disconnected"
    CONNECTING = "connecting"
    CONNECTED = "connected"
    ERROR = "error"


class ConnectionManager:
    def __init__(self):
        self._ws = None
        self._state = ConnectionState.DISCONNECTED
        self._host = ""
        self._port = 8765
        self._on_message: Optional[Callable[[WsMessage], None]] = None
        self._on_state_change: Optional[Callable[[ConnectionState], None]] = None
        self._receive_task: Optional[asyncio.Task] = None
        self._file_futures: dict[str, asyncio.Future] = {}

    @property
    def state(self) -> ConnectionState:
        return self._state

    @property
    def address(self) -> str:
        return f"{self._host}:{self._port}"

    @property
    def android_http_url(self) -> str:
        """Base HTTP URL for forwarding Feishu events to Android."""
        if self._host and self._port:
            return f"http://{self._host}:{self._port}"
        return ""

    def set_callbacks(
        self,
        on_message: Callable[[WsMessage], None],
        on_state_change: Callable[[ConnectionState], None],
    ):
        self._on_message = on_message
        self._on_state_change = on_state_change

    def _set_state(self, state: ConnectionState):
        self._state = state
        if self._on_state_change:
            self._on_state_change(state)

    async def connect(self, host: str, port: int = 8765):
        if self._state == ConnectionState.CONNECTED:
            await self.disconnect()

        self._host = host
        self._port = port
        self._set_state(ConnectionState.CONNECTING)

        try:
            uri = f"ws://{host}:{port}"
            self._ws = await ws_connect(
                uri,
                ping_interval=20,
                ping_timeout=30,
                close_timeout=5,
            )
            self._set_state(ConnectionState.CONNECTED)
            logger.info(f"Connected to {uri}")

            self._receive_task = asyncio.create_task(self._receive_loop())

        except Exception as e:
            logger.error(f"Connection failed: {e}")
            self._set_state(ConnectionState.ERROR)
            raise

    async def disconnect(self):
        if self._receive_task:
            self._receive_task.cancel()
            self._receive_task = None
        if self._ws:
            try:
                await self._ws.close()
            except Exception:
                pass
            self._ws = None
        self._set_state(ConnectionState.DISCONNECTED)

    async def send(self, message: WsMessage):
        if not self._ws or self._state != ConnectionState.CONNECTED:
            raise ConnectionError("Not connected")
        await self._ws.send(message.to_json())

    async def send_chat(self, content: str, session_id: str = ""):
        await self.send(WsMessage(
            type=MessageType.CHAT,
            content=content,
            session_id=session_id,
        ))

    async def send_terminal(self, command: str):
        await self.send(WsMessage(
            type=MessageType.TERMINAL,
            content=command,
        ))

    async def request_device_info(self):
        await self.send(WsMessage(type=MessageType.DEVICE_INFO))

    async def request_sessions(self):
        await self.send(WsMessage(type=MessageType.SESSIONS))

    async def _receive_loop(self):
        try:
            async for raw in self._ws:
                try:
                    msg = WsMessage.from_json(raw)
                    if msg.type in (MessageType.FILE_DATA, "file_data"):
                        self._dispatch_file_data(msg.content)
                    if self._on_message:
                        self._on_message(msg)
                except json.JSONDecodeError:
                    logger.warning(f"Invalid JSON: {str(raw)[:100]}")
        except ConnectionClosed:
            logger.info("WebSocket connection closed")
        except asyncio.CancelledError:
            return
        except Exception as e:
            logger.error(f"Receive error: {e}")
        finally:
            if self._state == ConnectionState.CONNECTED:
                self._set_state(ConnectionState.DISCONNECTED)

    def _dispatch_file_data(self, content: str):
        try:
            payload = json.loads(content)
        except json.JSONDecodeError:
            logger.warning("file_data: invalid JSON")
            return
        rid = payload.get("request_id")
        if not rid:
            return
        fut = self._file_futures.pop(rid, None)
        if fut and not fut.done():
            fut.set_result(payload)

    async def send_file_upload(self, local_path: str, remote_name: str = "") -> dict:
        if not self._ws or self._state != ConnectionState.CONNECTED:
            return {"ok": False, "error": "not_connected"}
        p = Path(local_path)
        if not p.is_file():
            return {"ok": False, "error": "local_file_missing"}
        data = p.read_bytes()
        if len(data) > 900_000:
            return {"ok": False, "error": "file_too_large_pc"}
        rid = str(uuid.uuid4())
        loop = asyncio.get_running_loop()
        fut = loop.create_future()
        self._file_futures[rid] = fut
        name = remote_name.strip() or p.name
        await self.send(
            WsMessage(
                type=MessageType.FILE_UPLOAD,
                content="",
                session_id="",
                extra={
                    "filename": name,
                    "data": base64.b64encode(data).decode("ascii"),
                    "request_id": rid,
                },
            )
        )
        try:
            return await asyncio.wait_for(fut, timeout=120.0)
        except asyncio.TimeoutError:
            return {"ok": False, "error": "timeout"}
        finally:
            self._file_futures.pop(rid, None)

    async def request_file_download(self, android_path: str) -> dict:
        if not self._ws or self._state != ConnectionState.CONNECTED:
            return {"ok": False, "error": "not_connected"}
        rid = str(uuid.uuid4())
        loop = asyncio.get_running_loop()
        fut = loop.create_future()
        self._file_futures[rid] = fut
        await self.send(
            WsMessage(
                type=MessageType.FILE_DOWNLOAD,
                content="",
                session_id="",
                extra={"path": android_path.strip(), "request_id": rid},
            )
        )
        try:
            return await asyncio.wait_for(fut, timeout=120.0)
        except asyncio.TimeoutError:
            return {"ok": False, "error": "timeout"}
        finally:
            self._file_futures.pop(rid, None)
