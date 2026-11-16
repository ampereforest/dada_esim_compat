#!/usr/bin/env python3
"""Fail closed on oversized, non-text, secret-like or unreviewed publication files."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ALLOWED = {'.md', '.py', '.sh', '.c', '.h', '.java', '.kt', '.smali', '.patch',
           '.cil', '.rc', '.xml', '.json', '.additions'}
EXACT = {'.gitignore', '.gitattributes', 'NOTICE', 'LICENSE'}
RULES = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b'),
    re.compile(r'LPA:1\$[^\s<>]+\$[^\s<>]+'),
    re.compile(r'(?<![A-Za-z0-9])(?:89\d{16,30}|\d{15}|\+\d{10,15})(?![A-Za-z0-9])'),
    re.compile('/run/' + r'media/|/root/' + r'phase3|/ho' + r'me/[^\s<>]+'),
]

def files(root, indexed):
    if indexed:
        names = subprocess.check_output(['git', '-C', str(root), 'ls-files', '-z']).split(b'\0')
        return [root / n.decode('utf-8') for n in names if n]
    return [p for p in root.rglob('*') if '.git' not in p.relative_to(root).parts
            and (p.is_file() or p.is_symlink())]

def check(root, indexed=False, denied=()):
    pins = {}; total = 0
    for p in sorted(files(root, indexed)):
        name = p.relative_to(root).as_posix()
        if p.is_symlink() or not p.is_file(): raise ValueError('non-regular: ' + name)
        if p.suffix not in ALLOWED and p.name not in EXACT: raise ValueError('extension: ' + name)
        if any(x in p.relative_to(root).parts for x in ('upstream', 'toolchain', 'secrets', 'signing_keys')):
            raise ValueError('excluded directory: ' + name)
        data = (subprocess.check_output(['git', '-C', str(root), 'show', ':' + name])
                if indexed else p.read_bytes())
        if indexed and data != p.read_bytes(): raise ValueError('index/worktree mismatch: ' + name)
        total += len(data)
        if len(data) >= 1_000_000 or b'\0' in data: raise ValueError('binary/large file: ' + name)
        text = data.decode('utf-8')
        if any(r.search(text) for r in RULES) or any(x and x.casefold() in text.casefold() for x in denied):
            # Never echo the private match.
            raise ValueError('privacy check: ' + name)
        pins[name] = hashlib.sha256(data).hexdigest()
    if total >= 10_000_000: raise ValueError('source tree must be strictly below 10MB')
    return {'status': 'PASS_SOURCE_ONLY_PRIVACY_SIZE', 'files': len(pins), 'bytes': total, 'sha256': pins}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--index', action='store_true')
    parser.add_argument('--deny-file', type=Path, help='private newline list OUTSIDE repository; never copied or echoed')
    args = parser.parse_args(); root = args.root.resolve()
    denied = ()
    if args.deny_file:
        private = args.deny_file.resolve()
        if private.is_relative_to(root): raise ValueError('private deny list must remain outside repository')
        denied = private.read_text().splitlines()
    print(json.dumps(check(root, args.index, denied), indent=2))

if __name__ == '__main__': main()
