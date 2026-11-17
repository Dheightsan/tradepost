# usage: python rcon.py "command" "another command"
import os
import socket
import struct
import sys


def packet(request_id: int, kind: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read(sock: socket.socket) -> tuple[int, str]:
    size = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < size:
        data += sock.recv(size - len(data))
    request_id = struct.unpack("<i", data[:4])[0]
    return request_id, data[8:-2].decode("utf-8", errors="replace")


def main() -> int:
    port = int(os.environ.get("RCON_PORT", "25598"))
    password = os.environ.get("RCON_PASSWORD", "localtest")
    with socket.create_connection(("127.0.0.1", port), timeout=10) as sock:
        sock.sendall(packet(1, 3, password))
        if read(sock)[0] == -1:
            print("RCON login failed", file=sys.stderr)
            return 1
        for i, command in enumerate(sys.argv[1:], start=2):
            sock.sendall(packet(i, 2, command))
            print(f"> {command}\n{read(sock)[1]}")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
