#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["segno"]
# ///
"""Kusuri 局域网导出 —— 电脑端接收端。

手机在「设置 → 备份与导出 → 局域网导出(扫码)」里扫码(或手输),把 JSON 备份 / CSV 记录
推送到这里。契约见 docs/lan-export.md。

用法::

    uv run tools/kusuri-receive.py
    uv run tools/kusuri-receive.py --out ~/Documents/kusuri --port 50000

它只做三件事:亮出口令与二维码、校验口令、原子落盘。没有遥测,没有外联。
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import http.server
import json
import os
import secrets
import socket
import sys
import threading
import time
from pathlib import Path

#: Crockford base32:没有 I / L / O / U,手输时不易看错。
CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
CODE_LENGTH = 6

#: 单次接收上限(docs/lan-export.md §3.1)。
MAX_BYTES = 32 * 1024 * 1024

DEFAULT_PORT = 47821
PORT_FALLBACK_ATTEMPTS = 10

KINDS = ("json", "records")
DEFAULT_FILENAMES = {"json": "kusuri-backup.json", "records": "kusuri-records.csv"}
CONTENT_TYPES = {
    "json": "application/json; charset=utf-8",
    "records": "text/csv; charset=utf-8",
}

#: 口令错一次的惩罚:让六位口令的暴力枚举不可行(docs/lan-export.md §7)。
BAD_CODE_DELAY_SECONDS = 0.5

#: 单条连接的超时(s)。契约:连接 5s、读 15s。
CONNECTION_TIMEOUT_SECONDS = 15


def generate_code() -> str:
    return "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))


def normalize_code(raw: str) -> str:
    """手机端与终端显示之间做一次容错:去掉分隔符,统一大写。"""
    return "".join(ch for ch in raw.strip().upper() if ch.isalnum())


def outbound_ipv4() -> str | None:
    """拿到"出口网卡"的 IPv4。

    往 TEST-NET-1 连一个 UDP 地址(不会真的发包),内核会挑一张有默认路由的网卡;
    这样就不会把 docker0 / virbr0 之类的地址打给用户。
    """
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 9))
        return probe.getsockname()[0]
    except OSError:
        return None
    finally:
        probe.close()


def other_ipv4_candidates(primary: str | None) -> list[str]:
    """其它可能的本机 IPv4(多网卡时提示用户"换一个地址试试")。"""
    candidates: list[str] = []
    try:
        infos = socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
    except OSError:
        return candidates
    for info in infos:
        address = str(info[4][0])
        if address.startswith("127.") or address == primary or address in candidates:
            continue
        candidates.append(address)
    return candidates


def sanitize_filename(raw: str | None, kind: str) -> str:
    """只取文件名:剥掉路径(防 `../../.bashrc`),去掉控制字符,限制长度。"""
    if not raw:
        return DEFAULT_FILENAMES[kind]
    name = os.path.basename(raw.replace("\\", "/"))
    name = "".join(ch for ch in name if ch.isprintable() and ch not in ':/"<>|?*')
    name = name.strip().strip(".")
    if not name:
        return DEFAULT_FILENAMES[kind]
    if len(name) > 120:
        stem, dot, suffix = name.rpartition(".")
        name = (stem[: 120 - len(dot) - len(suffix)] + dot + suffix) if dot else name[:120]
    return name


def unique_path(directory: Path, name: str) -> Path:
    """重名不覆盖:依次追加 -1、-2…(docs/lan-export.md §3.2)。"""
    candidate = directory / name
    if not candidate.exists():
        return candidate
    stem, dot, suffix = name.rpartition(".")
    stem = stem if dot else name
    suffix = f"{dot}{suffix}" if dot else ""
    for index in range(1, 1000):
        candidate = directory / f"{stem}-{index}{suffix}"
        if not candidate.exists():
            return candidate
    raise RuntimeError(f"同名文件过多: {name}")


def format_bytes(size: int) -> str:
    if size < 1024:
        return f"{size} B"
    if size < 1024 * 1024:
        return f"{size / 1024:.1f} KB"
    return f"{size / (1024 * 1024):.1f} MB"


def render_terminal_qr(payload: str) -> str:
    """按模块矩阵自己画:每模块 2 字符宽 × 1 行高。

    不用 segno 自带的 terminal():它的 compact 模式一行塞两行码,模块被纵向压扁一半,
    扫码器经常认不出。这里用 ANSI 背景色画 2 字符方块,几何是方的,且深浅与终端主题无关。
    """
    import segno

    matrix = segno.make(payload, error="m").matrix
    width = len(matrix[0])
    quiet = 2
    dark = "\033[40m  "
    light = "\033[47m  "
    reset = "\033[0m"

    lines = [light * (width + 2 * quiet) + reset for _ in range(quiet)]
    for row in matrix:
        line = light * quiet
        for module in row:
            line += dark if module else light
        lines.append(line + light * quiet + reset)
    lines.extend(light * (width + 2 * quiet) + reset for _ in range(quiet))
    return "\n".join(lines)


def print_qr(payload: str, png_path: str | None) -> None:
    """画二维码。整块失败也只提示,绝不让"收不到推送"败在一个显示细节上。"""
    try:
        import segno

        if png_path:
            segno.make(payload, error="m").save(png_path, scale=8, border=4)
            print(f"  二维码已另存 {png_path}")
        if sys.stdout.isatty():
            print(render_terminal_qr(payload))
        else:
            # 输出被重定向/管道时画二维码没有意义(也扫不了),用 --png。
            print("  (输出不是终端,未画二维码;需要就用 --png <路径> 另存)")
    except Exception as error:  # noqa: BLE001 - 显示失败不该让服务起不来
        print(f"  (二维码显示失败:{error};可用 --png 另存,或手输上面的地址)")


class Receiver(http.server.ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address: tuple[str, int], code: str, out_dir: Path):
        super().__init__(address, UploadHandler)
        self.code = code
        self.out_dir = out_dir
        self.received = 0
        self.last_received_at: float | None = None


class UploadHandler(http.server.BaseHTTPRequestHandler):
    server_version = "KusuriReceive/1"
    protocol_version = "HTTP/1.1"
    timeout = CONNECTION_TIMEOUT_SECONDS

    server: Receiver  # 类型提示用;运行时由 socketserver 注入

    # 默认日志太吵,我们自己打印有意义的信息。
    def log_message(self, fmt: str, *args: object) -> None:  # noqa: A003
        pass

    def _respond(self, status: int, body: bytes, content_type: str = "application/json; charset=utf-8") -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            if body:
                self.wfile.write(body)
        except OSError:
            # 对端提前断开(手机取消发送、Wi-Fi 掉线)是常态:记一行,不炸 traceback。
            print("  · 对端已断开,应答没送到")
        self.close_connection = True

    def finish(self) -> None:
        try:
            super().finish()
        except OSError:
            pass

    def _respond_json(self, status: int, payload: dict[str, object]) -> None:
        self._respond(status, json.dumps(payload, ensure_ascii=False).encode("utf-8"))

    def _peer(self) -> str:
        return f"{self.client_address[0]}:{self.client_address[1]}"

    def do_GET(self) -> None:  # noqa: N802
        if self.path.split("?")[0] != "/":
            self._respond_json(404, {"ok": False, "error": "not_found"})
            return
        # 刻意不在这里显示会话口令:这个页面在局域网内任何人可访问,
        # 口令只出现在运行脚本的终端里(docs/lan-export.md §7)。
        received = self.server.received
        if self.server.last_received_at is None:
            last = "尚无"
        else:
            last = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(self.server.last_received_at))
        body = (
            "Kusuri 局域网导出接收端正在运行\n"
            f"已接收:{received} 个文件\n"
            f"最近接收:{last}\n"
            f"落盘目录:{self.server.out_dir}\n\n"
            "文件名与校验值只在运行脚本的终端里显示。\n"
        ).encode("utf-8")
        self._respond(200, body, content_type="text/plain; charset=utf-8")

    def do_POST(self) -> None:  # noqa: N802
        if self.path.split("?")[0] != "/upload":
            self._respond_json(404, {"ok": False, "error": "not_found"})
            return

        # 口令:先校验,再读体(口令错就不必把数据读进来)。
        given = normalize_code(self.headers.get("X-Kusuri-Code", ""))
        if not hmac.compare_digest(given, self.server.code):
            time.sleep(BAD_CODE_DELAY_SECONDS)
            print(f"  ✗ 口令不匹配(来自 {self._peer()})")
            self._respond_json(401, {"ok": False, "error": "bad_code"})
            return

        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            self._respond_json(400, {"ok": False, "error": "bad_request"})
            return

        kind = (self.headers.get("X-Kusuri-Kind") or "").strip().lower()
        if kind not in KINDS:
            self._respond_json(400, {"ok": False, "error": "bad_request"})
            return

        try:
            length = int(self.headers.get("Content-Length", ""))
        except ValueError:
            self._respond_json(400, {"ok": False, "error": "bad_request"})
            return
        if length < 0:
            self._respond_json(400, {"ok": False, "error": "bad_request"})
            return
        if length > MAX_BYTES:
            print(f"  ✗ 太大:{format_bytes(length)} > {format_bytes(MAX_BYTES)}")
            self._respond_json(413, {"ok": False, "error": "too_large"})
            return

        body = self._read_exactly(length)
        if body is None:
            self._respond_json(400, {"ok": False, "error": "bad_request"})
            return
        if not body:
            self._respond_json(422, {"ok": False, "error": "bad_payload"})
            return
        if kind == "json":
            try:
                json.loads(body.decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                print("  ✗ 声称 JSON 但解析失败")
                self._respond_json(422, {"ok": False, "error": "bad_payload"})
                return

        name = sanitize_filename(self.headers.get("X-Kusuri-Filename"), kind)
        digest = hashlib.sha256(body).hexdigest()
        try:
            destination = self._write_atomically(name, body)
        except OSError as error:
            print(f"  ✗ 写盘失败:{error}")
            self._respond_json(400, {"ok": False, "error": "bad_request"})
            return

        self.server.received += 1
        self.server.last_received_at = time.time()
        print(
            f"  ✓ {time.strftime('%H:%M:%S')} {kind} · {format_bytes(len(body))} · {destination}",
        )
        print(f"    sha256 {digest}")
        self._respond_json(
            200,
            {"ok": True, "saved": str(destination), "sha256": digest, "bytes": len(body)},
        )

    def _read_exactly(self, length: int) -> bytes | None:
        chunks: list[bytes] = []
        remaining = length
        try:
            while remaining > 0:
                chunk = self.rfile.read(min(remaining, 64 * 1024))
                if not chunk:
                    return None
                chunks.append(chunk)
                remaining -= len(chunk)
        except OSError:
            # 读超时或对端断开(契约:读 15s)。
            return None
        return b"".join(chunks)

    def _write_atomically(self, name: str, body: bytes) -> Path:
        """先写 .part 再改名:中途被打断不会留下半个文件(docs/lan-export.md §3.2)。

        并发推送时用唯一临时名,避免两个请求抢同一个 .part。
        """
        destination = unique_path(self.server.out_dir, name)
        temporary = destination.with_name(f"{destination.name}.{secrets.token_hex(4)}.part")
        try:
            with open(temporary, "wb") as handle:
                handle.write(body)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, destination)
        finally:
            if temporary.exists():
                temporary.unlink(missing_ok=True)
        return destination

    def _method_not_allowed(self) -> None:
        self._respond_json(405, {"ok": False, "error": "method_not_allowed"})

    do_HEAD = _method_not_allowed  # noqa: N815
    do_PUT = _method_not_allowed  # noqa: N815
    do_DELETE = _method_not_allowed  # noqa: N815
    do_PATCH = _method_not_allowed  # noqa: N815
    do_OPTIONS = _method_not_allowed  # noqa: N815


def bind_server(bind: str, port: int, code: str, out_dir: Path) -> tuple[Receiver, int]:
    """端口被占用时依次往后试几个,并返回实际端口。"""
    last_error: OSError | None = None
    for offset in range(PORT_FALLBACK_ATTEMPTS):
        try:
            server = Receiver((bind, port + offset), code, out_dir)
            return server, port + offset
        except OSError as error:
            last_error = error
    raise SystemExit(f"端口 {port}…{port + PORT_FALLBACK_ATTEMPTS - 1} 都被占用了:{last_error}")


def start_idle_watchdog(server: Receiver, idle_timeout: int) -> None:
    if idle_timeout <= 0:
        return

    def watch() -> None:
        while True:
            time.sleep(5)
            moment = server.last_received_at or server.started_at
            if time.time() - moment >= idle_timeout:
                print(f"\n空闲 {idle_timeout} 秒,自动退出。")
                server.shutdown()
                return

    threading.Thread(target=watch, daemon=True).start()


def main(argv: list[str] | None = None) -> int:
    # 输出被管道/重定向时 Python 默认块缓冲,启动信息与口令会卡在缓冲区里看不见。
    sys.stdout.reconfigure(line_buffering=True)
    sys.stderr.reconfigure(line_buffering=True)

    parser = argparse.ArgumentParser(
        description="Kusuri 局域网导出的电脑端接收端(只接收你自己的手机推送)。",
    )
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"监听端口(默认 {DEFAULT_PORT},占用时自动往后试)")
    parser.add_argument("--out", default="~/Downloads", help="落盘目录(默认 ~/Downloads)")
    parser.add_argument("--code", default=None, help="固定会话口令(默认随机六位)")
    parser.add_argument("--no-qr", action="store_true", help="不打印二维码")
    parser.add_argument("--png", default=None, help="把二维码另存为 PNG(终端扫不出来时的备选)")
    parser.add_argument("--idle-timeout", type=int, default=0, help="空闲多少秒后自动退出(默认 0 = 一直服务到 Ctrl-C)")
    parser.add_argument("--bind", default="0.0.0.0", help="绑定地址(默认 0.0.0.0)")
    args = parser.parse_args(argv)

    if not 1 <= args.port <= 65535:
        parser.error("端口必须在 1…65535 之间")

    out_dir = Path(os.path.expanduser(args.out))
    out_dir.mkdir(parents=True, exist_ok=True)

    code = normalize_code(args.code) if args.code else generate_code()
    if len(code) != CODE_LENGTH:
        parser.error(f"口令必须是 {CODE_LENGTH} 位(字母表 {CODE_ALPHABET})")

    server, port = bind_server(args.bind, args.port, code, out_dir)
    server.started_at = time.time()  # type: ignore[attr-defined]

    host = outbound_ipv4()
    if host is None:
        print("警告:拿不到本机 IPv4(没连网?),手输地址可能不可用。", file=sys.stderr)
        host = "0.0.0.0"

    uri = f"kusuri://lan-export/1?h={host}&p={port}&c={code}"
    short = f"{host.rsplit('.', 1)[-1]}#{code}" if host.count(".") == 3 else f"{host}#{code}"

    print(f"Kusuri 接收端 · 落盘 {out_dir}")
    if host is None:
        candidates = other_ipv4_candidates(primary=None)
        print("  拿不到本机 IPv4(没连网?);手输时试这些地址:", "、".join(candidates) or "无")
    else:
        print(f"  手输  {short}   或  {host}:{port}#{code}")
    if args.idle_timeout > 0:
        print(f"  空闲 {args.idle_timeout} 秒后自动退出")
    print()
    if not args.no_qr:
        print_qr(uri, args.png)
    print("等待推送…  Ctrl-C 退出")
    print()

    start_idle_watchdog(server, args.idle_timeout)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        print("\n收工。")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
