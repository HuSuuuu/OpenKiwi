"""
OpenKiwi Python utility module - runs locally on the Android device.
Provides helper functions for the AI agent.
"""
import sys
import os
import json
import math
import re
import hashlib
import base64
import datetime
import urllib.parse
import csv
import io


def env_info():
    """Return Python environment information."""
    return {
        "python_version": sys.version,
        "platform": sys.platform,
        "executable": sys.executable or "embedded",
        "home": os.environ.get("HOME", ""),
        "cwd": os.getcwd(),
        "modules": sorted(list(sys.modules.keys()))[:50],
    }


def read_file(path):
    """Read a file and return its content."""
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def write_file(path, content):
    """Write content to a file."""
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    return f"Written {len(content)} bytes to {path}"


def parse_json(text):
    """Parse JSON string."""
    return json.loads(text)


def to_json(obj, indent=2):
    """Serialize object to JSON string."""
    return json.dumps(obj, ensure_ascii=False, indent=indent)


def csv_to_list(text, delimiter=","):
    """Parse CSV text into a list of dicts."""
    reader = csv.DictReader(io.StringIO(text), delimiter=delimiter)
    return list(reader)


def url_encode(params):
    """URL-encode a dict of parameters."""
    return urllib.parse.urlencode(params)


def base64_encode(data):
    """Base64-encode a string."""
    if isinstance(data, str):
        data = data.encode("utf-8")
    return base64.b64encode(data).decode("ascii")


def base64_decode(data):
    """Base64-decode a string."""
    return base64.b64decode(data).decode("utf-8")


def md5(text):
    """Calculate MD5 hash."""
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def sha256(text):
    """Calculate SHA-256 hash."""
    return hashlib.sha256(text.encode("utf-8")).hexdigest()
