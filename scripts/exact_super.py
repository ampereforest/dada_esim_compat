#!/usr/bin/env python3
"""Pack/verify exact raw super using authenticated, unchanged single-extent LP layout.

No LP regeneration, policy parsing, mounting, device access, flashing or deletion.
LP semantic validation with pinned lpdump for slots 0..2 is a separate mandatory gate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path

CHUNK = 4 * 1024 * 1024

def hash_range(path, offset, size):
    h = hashlib.sha256()
    with path.open('rb') as f:
        f.seek(offset)
        while size:
            b = f.read(min(size, CHUNK))
            if not b: raise ValueError('short input')
            h.update(b); size -= len(b)
    return h.hexdigest()

def zero_range(f, offset, size):
    f.seek(offset)
    while size:
        b = f.read(min(size, CHUNK))
        if not b or any(b): raise ValueError('nonzero padding/unallocated range')
        size -= len(b)

def prepare(layout, original, images):
    size = layout['device_size']; prefix = layout['prefix_bytes']
    assert isinstance(size, int) and isinstance(prefix, int) and 0 < prefix < size
    assert original.is_file() and original.stat().st_size == size
    assert hash_range(original, 0, prefix) == layout['prefix_sha256']
    rows = sorted(layout['partitions'], key=lambda r: r['offset'])
    assert len({r['name'] for r in rows}) == len(rows)
    assert set(images) == {r['name'] for r in rows}, 'all populated partitions must be explicit'
    end = prefix; pins = {}
    for r in rows:
        start, length = r['offset'], r['size']
        assert isinstance(start, int) and isinstance(length, int) and length > 0
        assert start >= end and start + length <= size and start % 512 == length % 512 == 0
        src = images[r['name']]
        assert src.is_file() and not src.is_symlink() and 0 < src.stat().st_size <= length
        with src.open('rb') as f: assert f.read(4) != b'\x3a\xff\x26\xed', 'raw images only'
        pins[r['name']] = {'bytes': src.stat().st_size, 'sha256': hash_range(src, 0, src.stat().st_size)}
        end = start + length
    return rows, pins

def verify(output, original, layout, images):
    rows, pins = prepare(layout, original, images)
    assert output.is_file() and output.stat().st_size == layout['device_size']
    assert hash_range(output, 0, layout['prefix_bytes']) == layout['prefix_sha256']
    end = layout['prefix_bytes']
    with output.open('rb') as f:
        for r in rows:
            zero_range(f, end, r['offset'] - end)
            pin = pins[r['name']]
            assert hash_range(output, r['offset'], pin['bytes']) == pin['sha256']
            zero_range(f, r['offset'] + pin['bytes'], r['size'] - pin['bytes'])
            end = r['offset'] + r['size']
        zero_range(f, end, layout['device_size'] - end)
    return {'status': 'PASS_RAW_BYTES_NOT_LP_SEMANTIC_OR_BOOT_ACCEPTANCE',
            'bytes': output.stat().st_size, 'sha256': hash_range(output, 0, output.stat().st_size),
            'payloads': pins, 'prefix_identical': True, 'padding_and_unallocated_zero': True}

def pack(output, original, layout, images):
    rows, pins = prepare(layout, original, images)
    assert not output.exists() and output.parent.is_dir()
    available = os.statvfs(output.parent)
    assert available.f_bavail * available.f_frsize >= layout['device_size'] + CHUNK
    fd = os.open(output, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        os.posix_fallocate(fd, 0, layout['device_size'])
        def write(data, offset):
            v = memoryview(data)
            while v:
                n = os.pwrite(fd, v, offset)
                if n <= 0: raise ValueError('short write')
                offset += n; v = v[n:]
        with original.open('rb') as f: write(f.read(layout['prefix_bytes']), 0)
        for r in rows:
            offset = r['offset']; h = hashlib.sha256(); count = 0
            with images[r['name']].open('rb') as f:
                while b := f.read(CHUNK):
                    count += len(b); assert count <= pins[r['name']]['bytes']
                    h.update(b); write(b, offset); offset += len(b)
            assert count == pins[r['name']]['bytes'] and h.hexdigest() == pins[r['name']]['sha256']
        os.fsync(fd)
    finally:
        os.close(fd)  # Failure bytes remain for diagnosis; no automatic cleanup.
    return verify(output, original, layout, images)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('pack', 'verify'))
    p.add_argument('--layout', type=Path, required=True)
    p.add_argument('--original', type=Path, required=True)
    p.add_argument('--image', action='append', required=True, metavar='PARTITION=RAW_PATH')
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args(); images = {}
    for item in a.image:
        name, path = item.split('=', 1); assert name not in images
        images[name] = Path(path)
    method = pack if a.mode == 'pack' else verify
    print(json.dumps(method(a.output, a.original, json.loads(a.layout.read_text()), images), indent=2))

if __name__ == '__main__': main()
