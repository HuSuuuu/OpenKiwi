"""
Code execution helper for Chaquopy.
Runs user code in a proper Python frame with stdout/stderr capture.
"""
import sys
import io
import traceback


def run_code(code):
    """Execute code string, return dict with stdout, stderr, exit_code."""
    stdout_buf = io.StringIO()
    stderr_buf = io.StringIO()

    old_stdout = sys.stdout
    old_stderr = sys.stderr
    sys.stdout = stdout_buf
    sys.stderr = stderr_buf

    exit_code = 0
    try:
        exec(code, {"__name__": "__main__", "__builtins__": __builtins__})
    except SystemExit as e:
        exit_code = e.code if isinstance(e.code, int) else 1
    except Exception:
        exit_code = 1
        traceback.print_exc(file=stderr_buf)
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr

    return {
        "stdout": stdout_buf.getvalue(),
        "stderr": stderr_buf.getvalue(),
        "exit_code": exit_code,
    }
