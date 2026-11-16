#!/usr/bin/env python3
"""Compile custom owner/framework Java against explicitly supplied target contracts."""
import argparse
import os
from pathlib import Path
import subprocess

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sources', type=Path, required=True)
    p.add_argument('--classpath', required=True, help='API37 android.jar plus genuine target framework contracts')
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--javac', default='javac')
    p.add_argument('--d8', help='optional externally supplied SDK d8 executable')
    p.add_argument('--android-jar', type=Path)
    a = p.parse_args()
    sources = sorted(a.sources.rglob('*.java')); assert sources
    assert not a.out.exists(); a.out.mkdir(parents=True)
    classes = a.out / 'classes'; classes.mkdir()
    subprocess.run([a.javac, '-source', '8', '-target', '8', '-encoding', 'UTF-8',
                    '-proc:none', '-implicit:none', '-classpath', a.classpath,
                    '-d', str(classes), *map(str, sources)], check=True)
    if a.d8:
        assert a.android_jar and a.android_jar.is_file()
        dex = a.out / 'dex'; dex.mkdir()
        dependencies = [v for item in a.classpath.split(os.pathsep) for v in ('--classpath', item)]
        subprocess.run([a.d8, '--release', '--min-api', '28', '--lib', str(a.android_jar),
                        *dependencies, '--output', str(dex),
                        *map(str, sorted(classes.rglob('*.class')))], check=True)
    print('Compile-only output; contracts must never be packaged as production classes.')

if __name__ == '__main__': main()
