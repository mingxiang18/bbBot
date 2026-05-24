#!/usr/bin/env python3
"""bb-sandbox exec 服务。

接收 bot 的 HTTP 请求，在 bubblewrap 隔离环境里跑一条 bash 命令，回 stdout/stderr/exitCode。
工作目录绑定到该用户在共享 NFS 上的文件空间（/agent-files/<safe(userId)>），
所以 shell 里读写的就是 bot 侧 file_read/file_write 看到的同一份文件。

安全：本服务是 RCE-by-design endpoint，必须靠 NetworkPolicy 限定只有 bot pod 能访问，
可选 SANDBOX_TOKEN 头校验。代码本身经 bwrap 禁网 + 独立命名空间隔离。
"""
import json
import os
import re
import shutil
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

AGENT_FILES_ROOT = os.environ.get("AGENT_FILES_ROOT", "/agent-files")
SANDBOX_TOKEN = os.environ.get("SANDBOX_TOKEN", "")
LISTEN_PORT = int(os.environ.get("SANDBOX_PORT", "8080"))
# 服务端硬上限：请求里的 timeoutMs 再大也不超过它
MAX_TIMEOUT_MS = int(os.environ.get("SANDBOX_MAX_TIMEOUT_MS", "120000"))
OUTPUT_CAP = 64 * 1024  # stdout/stderr 各截断 64KB

SANDBOX_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"


def safe(user_id):
    """复刻 Java AgentFileSpace.safe()：Java 的 \\w 是 ASCII，故这里用显式 ASCII 集合。"""
    if not user_id:
        return "_anonymous"
    return re.sub(r"[^A-Za-z0-9_\-]", "_", user_id)


def resolve_user_dir(user_id):
    """解析用户目录并做越权校验；返回绝对路径或抛 ValueError。"""
    root = os.path.realpath(AGENT_FILES_ROOT)
    user_dir = os.path.realpath(os.path.join(root, safe(user_id)))
    if user_dir != root and not user_dir.startswith(root + os.sep):
        raise ValueError("path escapes agent files root")
    os.makedirs(user_dir, exist_ok=True)
    return user_dir


def build_bwrap_cmd(user_dir, command, network_enabled):
    cmd = ["bwrap"]
    for p in ("/usr", "/bin", "/lib", "/lib64", "/etc", "/sbin"):
        if os.path.exists(p):
            cmd += ["--ro-bind", p, p]
    # 在容器里跑 bwrap 不能 `--proc`（挂全新 procfs 会被内核 "mount too revealing" 拒绝，
    # 因为容器运行时把 /proc 的部分路径 masked/locked 了）。改 bind 容器已 masked 的 /proc，
    # 沿用其安全掩码；相应地不再 --unshare-pid（否则 /proc/self 与新 pid ns 不一致）。
    cmd += ["--dev", "/dev", "--tmpfs", "/tmp", "--bind", "/proc", "/proc"]
    cmd += ["--bind", user_dir, "/work", "--chdir", "/work"]
    if not network_enabled:
        cmd += ["--unshare-net"]
    cmd += [
        "--die-with-parent",
        "--unshare-user", "--unshare-uts", "--unshare-ipc",
        "--clearenv",
        "--setenv", "PATH", SANDBOX_PATH,
        "--setenv", "HOME", "/work",
        "--setenv", "LANG", "C.UTF-8",
        "bash", "-c", command,
    ]
    return cmd


def run_command(user_id, command, timeout_ms, network_enabled):
    user_dir = resolve_user_dir(user_id)
    timeout_s = max(1, min(timeout_ms, MAX_TIMEOUT_MS) / 1000.0)
    cmd = build_bwrap_cmd(user_dir, command, network_enabled)
    start = time.monotonic()
    timed_out = False
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            timeout=timeout_s,
        )
        exit_code = proc.returncode
        stdout = proc.stdout
        stderr = proc.stderr
    except subprocess.TimeoutExpired as e:
        timed_out = True
        exit_code = -1
        stdout = e.stdout or b""
        stderr = e.stderr or b""
    duration_ms = int((time.monotonic() - start) * 1000)
    return {
        "exitCode": exit_code,
        "stdout": _decode_cap(stdout),
        "stderr": _decode_cap(stderr),
        "timedOut": timed_out,
        "durationMs": duration_ms,
    }


def _decode_cap(b):
    if not b:
        return ""
    if len(b) > OUTPUT_CAP:
        b = b[:OUTPUT_CAP]
    return b.decode("utf-8", errors="replace")


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/healthz":
            self._send(200, {"status": "ok"})
        else:
            self._send(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/exec":
            self._send(404, {"error": "not_found"})
            return
        if SANDBOX_TOKEN and self.headers.get("X-Sandbox-Token", "") != SANDBOX_TOKEN:
            self._send(401, {"error": "unauthorized"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            req = json.loads(self.rfile.read(length) or b"{}")
        except Exception as e:
            self._send(400, {"error": "bad_request", "message": str(e)})
            return

        command = req.get("command")
        user_id = req.get("userId")
        if not command:
            self._send(400, {"error": "command_required"})
            return
        try:
            result = run_command(
                user_id=user_id,
                command=command,
                timeout_ms=int(req.get("timeoutMs", 15000)),
                network_enabled=bool(req.get("networkEnabled", False)),
            )
        except ValueError as e:
            self._send(400, {"error": "path_not_allowed", "message": str(e)})
            return
        except Exception as e:
            self._send(500, {"error": "exec_failed", "message": str(e)})
            return
        self._send(200, result)

    def log_message(self, fmt, *args):
        # 静音默认 access log（避免把命令打进日志），错误另行处理
        pass


def main():
    if shutil.which("bwrap") is None:
        raise SystemExit("bwrap not found in image")
    server = ThreadingHTTPServer(("0.0.0.0", LISTEN_PORT), Handler)
    print(f"bb-sandbox exec server listening on :{LISTEN_PORT}, root={AGENT_FILES_ROOT}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
