#!/usr/bin/env python3
"""Create-only host EROFS recipe; full mounted metadata comparison remains mandatory."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import uuid

def digest(p):
    h = hashlib.sha256()
    with p.open('rb') as f:
        while b := f.read(4 * 1024 * 1024): h.update(b)
    return h.hexdigest()

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for k in ('tree', 'fs-config', 'file-contexts', 'mkfs', 'fsck', 'tool-pins', 'out'):
        p.add_argument('--' + k, type=Path, required=True)
    p.add_argument('--uuid', required=True)
    p.add_argument('--timestamp', type=int, required=True)
    p.add_argument('--mount-point', choices=('/', '/product', '/system_ext'), required=True)
    p.add_argument('--extent-bytes', type=int, required=True)
    p.add_argument('--dedupe', action='store_true', help='retain only when selected recipe already enables it')
    p.add_argument('--compress-hints', type=Path, help='optional authenticated partition-specific fit recipe')
    a = p.parse_args(); uuid.UUID(a.uuid)
    assert a.timestamp >= 0 and a.extent_bytes > 0 and a.tree.is_dir()
    pins = json.loads(a.tool_pins.read_text())
    for tool in (a.mkfs, a.fsck): assert digest(tool) == pins[tool.name]
    assert a.fs_config.is_file() and a.file_contexts.is_file()
    # Dedicated new output directory; retain failures, never remove or overwrite inputs.
    assert not a.out.exists(); a.out.mkdir(parents=True)
    image = a.out / 'partition.img'
    args = [str(a.mkfs), '-b4096', '-zlz4hc,level=12', '-C65536', '-x2',
            '-T' + str(a.timestamp), '--mkfs-time', '--preserve-mtime', '-U' + a.uuid,
            '--mount-point=' + a.mount_point, '--fs-config-file=' + str(a.fs_config),
            '--file-contexts=' + str(a.file_contexts)]
    if a.dedupe: args.append('-Ededupe')
    if a.compress_hints: args.append('--compress-hints=' + str(a.compress_hints))
    args += [str(image), str(a.tree)]
    with (a.out / 'mkfs.stdout').open('xb') as o, (a.out / 'mkfs.stderr').open('xb') as e:
        subprocess.run(args, stdout=o, stderr=e, check=True)
    assert image.stat().st_size <= a.extent_bytes
    with (a.out / 'fsck.stdout').open('xb') as o, (a.out / 'fsck.stderr').open('xb') as e:
        subprocess.run([str(a.fsck), '--extract', str(image)], stdout=o, stderr=e, check=True)
    (a.out / 'host-recipe.json').write_text(json.dumps({'status': 'FSCK_ONLY_FULL_METADATA_COMPARISON_REQUIRED',
        'argv': args, 'bytes': image.stat().st_size, 'sha256': digest(image)}, indent=2) + '\n')
    print('fsck passed; independently compare ALL mounted content/inodes/xattrs/links before accepting.')

if __name__ == '__main__': main()
