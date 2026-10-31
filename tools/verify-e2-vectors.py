#!/usr/bin/env python3
"""Rebuild portable Windows Core vectors and compare exact Android test-resource bytes.

Controlled inputs only; no network listener, provider, native-security experiment,
fixture rewriting, Git mutation, credential issuance or live telemetry.
Run the explicit project restore separately before this no-restore verification.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--windows-root', required=True, type=Path)
    parser.add_argument('--dotnet', default='dotnet')
    args = parser.parse_args()
    root = args.windows_root.resolve()
    project = root / 'tests/PeripheralBatteryCompanion.InteropVectors/PeripheralBatteryCompanion.InteropVectors.csproj'
    if not project.is_file():
        raise SystemExit('E2 portable producer project missing; review the Windows E2 branch first.')
    expected = Path(__file__).resolve().parents[1] / 'app/src/testInterop/resources/interop/e2'
    environment = os.environ.copy()
    environment['DOTNET_GENERATE_ASPNET_CERTIFICATE'] = 'false'
    environment['DOTNET_CLI_TELEMETRY_OPTOUT'] = '1'
    with tempfile.TemporaryDirectory(prefix='pbc-e2-') as temporary:
        emitted = Path(temporary) / 'emitted'
        subprocess.run([
            args.dotnet, 'run', '--project', str(project), '--configuration', 'Release',
            '--no-restore', '--', '--output', str(emitted),
        ], cwd=root, env=environment, check=True)
        names = sorted(p.name for p in emitted.iterdir())
        if names != sorted(p.name for p in expected.iterdir()):
            raise SystemExit('E2 file membership differs; no Android resources were modified.')
        differing = [name for name in names if (emitted / name).read_bytes() != (expected / name).read_bytes()]
        if differing:
            raise SystemExit('E2 byte mismatch: ' + ', '.join(differing))
        print(f'E2 exact-byte regeneration PASS: {len(names)} files; Android resources unchanged.')


if __name__ == '__main__':
    main()
