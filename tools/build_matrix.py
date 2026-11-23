"""Build the Yarn-based TextureMixer targets from a source checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from zipfile import ZipFile

PROJECT = Path(__file__).resolve().parents[1]
RELEASE = next(
    line.split("=", 1)[1].strip()
    for line in (PROJECT / "gradle.properties").read_text("utf-8").splitlines()
    if line.startswith("mod_version=")
)
TARGETS = {
    '1.21.11': ('1.21.11+build.6', '0.141.6+1.21.11'),
    '1.21.10': ('1.21.10+build.3', '0.138.4+1.21.10'),
    '1.21.9': ('1.21.9+build.1', '0.134.1+1.21.9'),
    '1.21.8': ('1.21.8+build.1', '0.136.1+1.21.8'),
    '1.21.7': ('1.21.7+build.8', '0.129.0+1.21.7'),
    '1.21.6': ('1.21.6+build.1', '0.128.2+1.21.6'),
    '1.21.5': ('1.21.5+build.1', '0.128.2+1.21.5'),
    '1.21.4': ('1.21.4+build.8', '0.119.4+1.21.4'),
    '1.21.3': ('1.21.3+build.2', '0.114.1+1.21.3'),
    '1.21.2': ('1.21.2+build.1', '0.106.1+1.21.2'),
    '1.21.1': ('1.21.1+build.3', '0.116.18+1.21.1'),
    '1.21': ('1.21+build.9', '0.102.0+1.21'),
    '1.20.6': ('1.20.6+build.3', '0.100.8+1.20.6'),
    '1.20.5': ('1.20.5+build.1', '0.97.8+1.20.5'),
    '1.20.4': ('1.20.4+build.3', '0.97.3+1.20.4'),
    '1.20.3': ('1.20.3+build.1', '0.91.1+1.20.3'),
    '1.20.2': ('1.20.2+build.4', '0.91.6+1.20.2'),
    '1.20.1': ('1.20.1+build.10', '0.92.12+1.20.1'),
    '1.20': ('1.20+build.1', '0.83.0+1.20'),
}


def build(version, output, audit):
    yarn, api = TARGETS[version]
    wrapper = PROJECT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    build_dir = PROJECT / "build/matrix" / version
    command = [
        str(wrapper), "build", "--console=plain",
        f"-Pminecraft_version={version}", f"-Pyarn_mappings={yarn}",
        f"-Pfabric_api_version={api}", f"-PmatrixBuildDirectory={build_dir}",
    ]
    if audit:
        run_dir = f"run-version-audit/{version}/{time.time_ns()}"
        command.extend([
            "runVersionAuditClient", "-PversionAudit", "-PversionFeatureAudit",
            "-PversionWorldAudit", f"-PversionAuditRunDirectory={run_dir}",
        ])
    subprocess.run(command, cwd=PROJECT, check=True)
    if audit:
        report = PROJECT / run_dir / "version-audit.txt"
        result = report.read_text("utf-8")
        if not result.startswith(f"PASS: {version} ") or "Water and Lava world pixels" not in result:
            raise RuntimeError(f"Client audit failed. See {report}")
        print(result.strip())
    jar = build_dir / "libs" / f"TextureMixer-{RELEASE}+{version}.jar"
    with ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if (metadata["depends"]["minecraft"] != version or metadata["license"] != "MIT"
                or archive.testzip() is not None or "META-INF/LICENSE" not in archive.namelist()):
            raise RuntimeError(f"Invalid release JAR: {jar}")
    destination = output / jar.name
    shutil.copy2(jar, destination)
    digest = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix(".jar.sha256").write_text(f"{digest}  {jar.name}\n", "ascii")
    print(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("versions", nargs="*", metavar="MINECRAFT_VERSION",
                        help="Game versions to build (defaults to all 1.20.x and 1.21.x targets)")
    parser.add_argument("--audit", action="store_true", help="Run the isolated graphical client audit")
    parser.add_argument("--output", type=Path, default=PROJECT / "build/libs")
    args = parser.parse_args()
    for version in args.versions:
        if version not in TARGETS:
            parser.error(f"Unsupported version {version!r}. Available versions: {', '.join(TARGETS)}")
    args.output.mkdir(parents=True, exist_ok=True)
    for version in args.versions or TARGETS:
        build(version, args.output.resolve(), args.audit)


if __name__ == "__main__":
    main()
