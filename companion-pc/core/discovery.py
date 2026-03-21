"""mDNS / Zeroconf: discover OpenKiwi phones advertising _openkiwi._tcp."""

from __future__ import annotations

import time
from typing import List, Tuple

try:
    from zeroconf import ServiceBrowser, ServiceListener, Zeroconf
except ImportError:  # pragma: no cover
    Zeroconf = None  # type: ignore


def discover_openkiwi_phones(timeout: float = 3.0) -> List[Tuple[str, int]]:
    """
    Returns list of (ip, port) for OpenKiwi companion WebSocket on the phone.
    """
    if Zeroconf is None:
        return []

    class _Collector(ServiceListener):
        def __init__(self) -> None:
            self.items: List[Tuple[str, int]] = []

        def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
            info = zc.get_service_info(type_, name)
            if not info:
                return
            for addr in info.parsed_addresses():
                self.items.append((addr, int(info.port)))

        def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
            pass

        def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
            pass

    zc = Zeroconf()
    col = _Collector()
    browser = ServiceBrowser(zc, "_openkiwi._tcp.local.", col)
    time.sleep(timeout)
    browser.cancel()
    zc.close()

    seen: set[tuple[str, int]] = set()
    out: List[Tuple[str, int]] = []
    for h, p in col.items:
        key = (h, p)
        if key not in seen:
            seen.add(key)
            out.append((h, p))
    return out
