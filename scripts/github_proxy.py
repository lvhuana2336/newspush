#!/usr/bin/env python3
"""Minimal concurrent HTTP CONNECT proxy for GitHub access.

The local hosts file maps github.com to 127.0.0.1 (written by an accelerator
tool). This proxy resolves the real GitHub IPs itself, so git can use it via
`http.proxy` while TLS stays end-to-end between git and GitHub.
"""
import argparse
import asyncio
import sys


OVERRIDES = {
    "github.com": [
        "140.82.113.3",
        "20.27.177.113",
        "140.82.112.3",
        "20.26.156.215",
        "20.200.245.247",
    ],
    "api.github.com": ["20.205.243.168"],
    "uploads.github.com": ["20.205.243.161"],
}


async def pipe(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.CancelledError):
        pass
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def handle(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        header = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), timeout=10)
    except Exception:
        writer.close()
        return

    first_line = header.split(b"\r\n", 1)[0].decode("latin-1", "ignore")
    parts = first_line.split()
    if len(parts) < 2 or parts[0] != "CONNECT":
        writer.write(b"HTTP/1.1 405 Only CONNECT\r\nConnection: close\r\n\r\n")
        await writer.drain()
        writer.close()
        return

    host, _, port_text = parts[1].partition(":")
    try:
        port = int(port_text or "443")
    except ValueError:
        port = 443

    upstream_reader = None
    upstream_writer = None
    candidates = OVERRIDES.get(host)
    if candidates:
        for ip in candidates:
            try:
                upstream_reader, upstream_writer = await asyncio.wait_for(
                    asyncio.open_connection(ip, port), timeout=8
                )
                break
            except Exception:
                continue
    else:
        try:
            upstream_reader, upstream_writer = await asyncio.wait_for(
                asyncio.open_connection(host, port), timeout=10
            )
        except Exception:
            upstream_writer = None

    if upstream_writer is None or upstream_reader is None:
        writer.write(b"HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n")
        await writer.drain()
        writer.close()
        return

    writer.write(b"HTTP/1.1 200 Connection established\r\n\r\n")
    await writer.drain()
    print("tunnel " + host + ":" + str(port), flush=True)
    await asyncio.gather(
        pipe(reader, upstream_writer),
        pipe(upstream_reader, writer),
    )


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8443)
    args = parser.parse_args()
    server = await asyncio.start_server(handle, "127.0.0.1", args.port)
    print("github connect proxy on 127.0.0.1:" + str(args.port), flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        sys.exit(0)
