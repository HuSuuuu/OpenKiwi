"""Remote code execution engine for OpenKiwi Companion PC."""

import subprocess
import sys
import os
import tempfile
import time
import logging
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class CodeResult:
    exit_code: int
    stdout: str
    stderr: str
    execution_time_ms: int
    language: str
    truncated: bool = False

    def to_dict(self) -> dict:
        return {
            "exit_code": self.exit_code,
            "stdout": self.stdout,
            "stderr": self.stderr,
            "execution_time_ms": self.execution_time_ms,
            "language": self.language,
            "truncated": self.truncated,
        }


MAX_OUTPUT = 100_000
MAX_TIMEOUT = 120


def execute_code(code: str, language: str, timeout_seconds: int = 30) -> CodeResult:
    timeout_seconds = min(timeout_seconds, MAX_TIMEOUT)
    language = language.lower().strip()

    if language in ("python", "python3", "py"):
        return _run_python(code, timeout_seconds)
    elif language in ("shell", "sh", "bash", "cmd", "powershell", "ps"):
        return _run_shell(code, language, timeout_seconds)
    elif language in ("javascript", "js", "node"):
        return _run_node(code, timeout_seconds)
    else:
        return CodeResult(
            exit_code=-1,
            stdout="",
            stderr=f"Unsupported language: {language}. Supported: python, shell, bash, powershell, javascript",
            execution_time_ms=0,
            language=language,
        )


def _run_python(code: str, timeout: int) -> CodeResult:
    with tempfile.NamedTemporaryFile(mode="w", suffix=".py", delete=False, encoding="utf-8") as f:
        f.write(code)
        script_path = f.name
    try:
        return _execute_process([sys.executable, script_path], timeout, "python")
    finally:
        os.unlink(script_path)


def _run_shell(code: str, language: str, timeout: int) -> CodeResult:
    if sys.platform == "win32":
        if language in ("powershell", "ps"):
            with tempfile.NamedTemporaryFile(mode="w", suffix=".ps1", delete=False, encoding="utf-8") as f:
                f.write(code)
                script_path = f.name
            try:
                return _execute_process(
                    ["powershell", "-ExecutionPolicy", "Bypass", "-File", script_path],
                    timeout, "powershell"
                )
            finally:
                os.unlink(script_path)
        else:
            with tempfile.NamedTemporaryFile(mode="w", suffix=".bat", delete=False, encoding="utf-8") as f:
                f.write(code)
                script_path = f.name
            try:
                return _execute_process(["cmd", "/c", script_path], timeout, "cmd")
            finally:
                os.unlink(script_path)
    else:
        shell = "bash" if language == "bash" else "sh"
        return _execute_process([shell, "-c", code], timeout, language)


def _run_node(code: str, timeout: int) -> CodeResult:
    with tempfile.NamedTemporaryFile(mode="w", suffix=".js", delete=False, encoding="utf-8") as f:
        f.write(code)
        script_path = f.name
    try:
        return _execute_process(["node", script_path], timeout, "javascript")
    except FileNotFoundError:
        return CodeResult(
            exit_code=-1,
            stdout="",
            stderr="Node.js not found. Please install Node.js on the companion PC.",
            execution_time_ms=0,
            language="javascript",
        )
    finally:
        os.unlink(script_path)


def _execute_process(cmd: list, timeout: int, language: str) -> CodeResult:
    start = time.time()
    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            cwd=tempfile.gettempdir(),
        )
        stdout_bytes, stderr_bytes = proc.communicate(timeout=timeout)
        elapsed = int((time.time() - start) * 1000)

        stdout = stdout_bytes.decode("utf-8", errors="replace")
        stderr = stderr_bytes.decode("utf-8", errors="replace")
        truncated = len(stdout) > MAX_OUTPUT or len(stderr) > MAX_OUTPUT

        return CodeResult(
            exit_code=proc.returncode,
            stdout=stdout[:MAX_OUTPUT],
            stderr=stderr[:MAX_OUTPUT],
            execution_time_ms=elapsed,
            language=language,
            truncated=truncated,
        )
    except subprocess.TimeoutExpired:
        proc.kill()
        elapsed = int((time.time() - start) * 1000)
        return CodeResult(
            exit_code=-1,
            stdout="",
            stderr=f"Execution timed out after {timeout}s",
            execution_time_ms=elapsed,
            language=language,
        )
    except FileNotFoundError:
        return CodeResult(
            exit_code=-1,
            stdout="",
            stderr=f"Runtime not found for {language}. Ensure it is installed and in PATH.",
            execution_time_ms=0,
            language=language,
        )
    except Exception as e:
        elapsed = int((time.time() - start) * 1000)
        return CodeResult(
            exit_code=-1,
            stdout="",
            stderr=f"Execution error: {str(e)}",
            execution_time_ms=elapsed,
            language=language,
        )
