"""USB/ADB bridge for connecting to OpenKiwi via USB Type-C."""

import asyncio
import logging
import shutil
import subprocess
from typing import Optional

logger = logging.getLogger(__name__)


class USBBridge:
    """Manages ADB port forwarding to connect to OpenKiwi via USB."""

    def __init__(self, local_port: int = 8765, remote_port: int = 8765):
        self.local_port = local_port
        self.remote_port = remote_port
        self._forwarding_active = False

    @staticmethod
    def is_adb_available() -> bool:
        return shutil.which("adb") is not None

    async def get_devices(self) -> list[str]:
        """List connected ADB devices."""
        if not self.is_adb_available():
            return []
        try:
            proc = await asyncio.create_subprocess_exec(
                "adb", "devices",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, _ = await proc.communicate()
            lines = stdout.decode().strip().split("\n")[1:]
            devices = []
            for line in lines:
                parts = line.strip().split("\t")
                if len(parts) >= 2 and parts[1] == "device":
                    devices.append(parts[0])
            return devices
        except Exception as e:
            logger.error(f"Failed to list devices: {e}")
            return []

    async def setup_forward(self, device_serial: Optional[str] = None) -> bool:
        """Set up ADB port forwarding."""
        if not self.is_adb_available():
            logger.error("ADB not found in PATH")
            return False

        cmd = ["adb"]
        if device_serial:
            cmd.extend(["-s", device_serial])
        cmd.extend(["forward", f"tcp:{self.local_port}", f"tcp:{self.remote_port}"])

        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            _, stderr = await proc.communicate()
            if proc.returncode == 0:
                self._forwarding_active = True
                logger.info(f"ADB forward: tcp:{self.local_port} -> tcp:{self.remote_port}")
                return True
            else:
                logger.error(f"ADB forward failed: {stderr.decode()}")
                return False
        except Exception as e:
            logger.error(f"ADB forward error: {e}")
            return False

    async def remove_forward(self) -> bool:
        """Remove ADB port forwarding."""
        if not self._forwarding_active:
            return True
        try:
            proc = await asyncio.create_subprocess_exec(
                "adb", "forward", "--remove", f"tcp:{self.local_port}",
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            await proc.communicate()
            self._forwarding_active = False
            return True
        except Exception:
            return False

    @property
    def is_forwarding(self) -> bool:
        return self._forwarding_active

    def get_local_address(self) -> tuple[str, int]:
        """Returns (host, port) to connect to after forwarding."""
        return ("127.0.0.1", self.local_port)
