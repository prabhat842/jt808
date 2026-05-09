#!/usr/bin/env python3
import asyncio
import contextlib
import signal
import time


JT808_HOST = "127.0.0.1"
JT808_PORT = 7611
JT1078_HOST = "127.0.0.1"
JT1078_PORT = 1078


def bcd_decode(data: bytes) -> str:
    chars = []
    for item in data:
        chars.append(str((item >> 4) & 0x0F))
        chars.append(str(item & 0x0F))
    return "".join(chars)


def bcd_encode(digits: str, length: int) -> bytes:
    digits = "".join(ch for ch in digits if ch.isdigit())[-length * 2 :].rjust(length * 2, "0")
    return bytes((int(digits[i]) << 4) | int(digits[i + 1]) for i in range(0, len(digits), 2))


def escape(data: bytes) -> bytes:
    out = bytearray()
    for item in data:
        if item == 0x7D:
            out.extend((0x7D, 0x01))
        elif item == 0x7E:
            out.extend((0x7D, 0x02))
        else:
            out.append(item)
    return bytes(out)


def unescape(data: bytes) -> bytes:
    out = bytearray()
    index = 0
    while index < len(data):
        item = data[index]
        if item == 0x7D and index + 1 < len(data):
            nxt = data[index + 1]
            if nxt == 0x01:
                out.append(0x7D)
            elif nxt == 0x02:
                out.append(0x7E)
            index += 2
        else:
            out.append(item)
            index += 1
    return bytes(out)


def checksum(data: bytes) -> int:
    value = 0
    for item in data:
        value ^= item
    return value & 0xFF


def frame(message_id: int, terminal_id: str, sequence: int, body: bytes) -> bytes:
    packet = bytearray()
    packet.extend(message_id.to_bytes(2, "big"))
    packet.extend((0x4000 | len(body)).to_bytes(2, "big"))
    packet.append(1)
    packet.extend(bcd_encode(terminal_id, 10))
    packet.extend(sequence.to_bytes(2, "big"))
    packet.extend(body)
    packet.append(checksum(packet))
    return b"\x7e" + escape(packet) + b"\x7e"


def parse(packet: bytes):
    packet = unescape(packet)
    if not packet or checksum(packet[:-1]) != packet[-1]:
        return None
    message_id = int.from_bytes(packet[0:2], "big")
    props = int.from_bytes(packet[2:4], "big")
    versioned = bool(props & 0x4000)
    body_length = props & 0x03FF
    offset = 4
    if versioned:
        offset += 1
        terminal_id = bcd_decode(packet[offset : offset + 10])
        offset += 10
    else:
        terminal_id = bcd_decode(packet[offset : offset + 6])
        offset += 6
    sequence = int.from_bytes(packet[offset : offset + 2], "big")
    offset += 2
    body = packet[offset : offset + body_length]
    return message_id, terminal_id, sequence, body


async def read_frame(reader: asyncio.StreamReader):
    data = bytearray()
    while True:
        item = await reader.readexactly(1)
        if item == b"\x7e":
            if data:
                return bytes(data)
        else:
            data.extend(item)


async def handle_jt808(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    peer = writer.get_extra_info("peername")
    try:
        while True:
            parsed = parse(await read_frame(reader))
            if not parsed:
                continue
            message_id, terminal_id, sequence, _body = parsed
            if message_id == 0x0100:
                body = sequence.to_bytes(2, "big") + b"\x00" + b"mock-token"
                writer.write(frame(0x8100, terminal_id, 1, body))
            else:
                body = sequence.to_bytes(2, "big") + message_id.to_bytes(2, "big") + b"\x00"
                writer.write(frame(0x8001, terminal_id, 1, body))
            await writer.drain()
    except (asyncio.IncompleteReadError, ConnectionResetError):
        pass
    finally:
        writer.close()
        with contextlib.suppress(Exception):
            await writer.wait_closed()
        print(f"jt808 closed {peer}")


async def handle_jt1078(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    peer = writer.get_extra_info("peername")
    total = 0
    try:
        while True:
            data = await reader.read(8192)
            if not data:
                break
            total += len(data)
    except ConnectionResetError:
        pass
    finally:
        writer.close()
        with contextlib.suppress(Exception):
            await writer.wait_closed()
        print(f"jt1078 closed {peer} bytes={total}")


async def main():
    jt808 = await asyncio.start_server(handle_jt808, JT808_HOST, JT808_PORT)
    jt1078 = await asyncio.start_server(handle_jt1078, JT1078_HOST, JT1078_PORT)
    print(f"mock JT808 listening on {JT808_HOST}:{JT808_PORT}")
    print(f"mock JT1078 listening on {JT1078_HOST}:{JT1078_PORT}")
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, stop.set)
    started = time.time()
    while not stop.is_set():
        await asyncio.sleep(1)
        if time.time() - started > 300:
            stop.set()
    jt808.close()
    jt1078.close()
    await jt808.wait_closed()
    await jt1078.wait_closed()


if __name__ == "__main__":
    asyncio.run(main())
