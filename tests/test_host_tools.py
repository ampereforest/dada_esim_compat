#!/usr/bin/env python3
"""Synthetic host-only tests; no firmware, device, root or policy tool required."""
import ast
from contextlib import contextmanager
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
@contextmanager
def retained_tempdir(label):
    """Preserve successful and failed fixtures; workspace cleanup is not authorized."""
    yield tempfile.mkdtemp(prefix=label)

def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m

class Tools(unittest.TestCase):
    def test_syntax_and_xml(self):
        for p in ROOT.rglob('*.py'): ast.parse(p.read_text(), filename=str(p))
        for p in ROOT.rglob('*.xml'): ET.parse(p)
        for p in ROOT.rglob('*.sh'):
            shell = 'bash' if p.read_text().startswith('#!/usr/bin/env bash') else 'sh'
            subprocess.run([shell, '-n', str(p)], check=True)

    def test_pack_verify_and_refuse_overwrite(self):
        m = module('exact_super')
        with retained_tempdir('dada-host-pack-') as tmp:
            d = Path(tmp); original = d / 'original'; original.write_bytes(b'P' * 512 + b'X' * 3584)
            image = d / 'payload'; image.write_bytes(b'custom-source-test')
            layout = {'device_size': 4096, 'prefix_bytes': 512,
                      'prefix_sha256': hashlib.sha256(b'P' * 512).hexdigest(),
                      'partitions': [{'name': 'test_a', 'offset': 1024, 'size': 1024}]}
            images = {'test_a': image}; output = d / 'out'
            self.assertTrue(m.pack(output, original, layout, images)['prefix_identical'])
            with self.assertRaises(AssertionError): m.pack(output, original, layout, images)
            with output.open('r+b') as f: f.seek(3000); f.write(b'!')
            with self.assertRaises(ValueError): m.verify(output, original, layout, images)
            layout['prefix_sha256'] = '0' * 64
            with self.assertRaises(AssertionError): m.prepare(layout, original, images)

    def test_source_guard_negative_controls(self):
        m = module('source_guard')
        with retained_tempdir('dada-host-guard-') as tmp:
            d = Path(tmp); f = d / 'README.md'; f.write_text('Safe custom documentation\n')
            self.assertEqual(m.check(d)['files'], 1)
            f.write_bytes(b'bad\0payload')
            with self.assertRaises(ValueError): m.check(d)
            f.write_text('BEGIN ' + 'PRIVATE KEY')
            with self.assertRaises(ValueError): m.check(d, denied=['PRIVATE KEY'])
            f.write_text('x' * 1_000_000)
            with self.assertRaises(ValueError): m.check(d)

    def test_native_synthetic_endpoint(self):
        with retained_tempdir('dada-host-native-') as tmp:
            out = Path(tmp) / 'fixture'
            subprocess.run(['cc', '-DN3_ROM', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
                            str(ROOT / 'integration/native/endpoint_tests.c'), '-o', str(out)], check=True)
            result = subprocess.run([str(out)], capture_output=True, text=True, check=True, timeout=15)
            self.assertIn('PASS 35', result.stdout)

if __name__ == '__main__': unittest.main()
