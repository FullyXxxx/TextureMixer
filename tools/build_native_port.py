"""Build and audit TextureMixer for the unobfuscated Java 25 editions."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
SOURCE = PROJECT / "src"
STAGING = PROJECT / "build/port-staging"
REPORTS = PROJECT / "build/reports/port-matrix"
FABRIC_MAVEN = "https://maven.fabricmc.net/"
TARGETS = {
    "26.1": ("0.145.1+26.1", "0.19.3"),
    "26.1.1": ("0.145.4+26.1.1", "0.19.3"),
    "26.1.2": ("0.155.3+26.1.2", "0.19.3"),
    "26.2": ("0.161.0+26.2", "0.19.5"),
    "26.3": ("0.166.2+26.3", "0.20.1"),
}


def fetch(path: Path, url: str, sha1: str | None = None) -> Path:
    if path.is_file() and (sha1 is None or hashlib.sha1(path.read_bytes()).hexdigest() == sha1):
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=45) as response:
        data = response.read()
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        raise RuntimeError(f"SHA-1 mismatch for {url}")
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as pending:
        pending.write(data)
    Path(pending.name).replace(path)
    return path


def game_files(version: str) -> tuple[dict, Path]:
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    loom = gradle_home / "caches/fabric-loom" / version
    if not (loom / "mojang_minecraft_info.json").is_file() or not (loom / "minecraft-client.jar").is_file():
        loom = STAGING / "game" / version
    metadata_path = loom / "mojang_minecraft_info.json"
    if not metadata_path.exists():
        manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        with urllib.request.urlopen(manifest_url, timeout=45) as response:
            manifest = json.load(response)
        entry = next(info for info in manifest["versions"] if info["id"] == version)
        fetch(metadata_path, entry["url"], entry.get("sha1"))
    metadata = json.loads(metadata_path.read_bytes())
    if metadata["id"] != version:
        raise ValueError(f"Unexpected game metadata in {metadata_path}")
    client = metadata["downloads"]["client"]
    return metadata, fetch(loom / "minecraft-client.jar", client["url"], client["sha1"])


def maven_path(coordinate: str) -> str:
    group, artifact, version = coordinate.split(":")
    return f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.jar"


def windows_library(component: dict) -> bool:
    if re.search(r":natives-(?!windows(?:$|:))", component["name"]):
        return False
    rules = component.get("rules")
    if not rules:
        return True
    supported = False
    for rule in rules:
        platform = rule.get("os", {})
        if not platform or platform.get("name") == "windows":
            supported = rule["action"] == "allow"
    return supported


def dependencies(version: str, launcher: Path | None, metadata: dict, game: Path):
    api, loader = TARGETS[version]
    cache = STAGING / f"libraries-{version}"
    url = f"https://meta.fabricmc.net/v2/versions/loader/{version}/{loader}/profile/json"
    profile = json.loads(fetch(cache / "fabric-profile.json", url).read_bytes())

    def library(path, url, sha1=None):
        local = launcher / "libraries" / path if launcher else None
        if local and local.is_file() and (sha1 is None or hashlib.sha1(local.read_bytes()).hexdigest() == sha1):
            return local
        return fetch(cache / path, url, sha1)

    runtime = [game]
    for component in metadata["libraries"]:
        artifact = component["downloads"].get("artifact")
        if artifact and windows_library(component):
            runtime.append(library(artifact["path"], artifact["url"], artifact["sha1"]))
    for component in profile["libraries"]:
        path = maven_path(component["name"])
        runtime.append(library(path, component["url"] + path))
    runtime = list(dict.fromkeys(runtime))
    api_path = maven_path(f"net.fabricmc.fabric-api:fabric-api:{api}")
    api_jar = library(api_path, FABRIC_MAVEN + api_path)
    menu = fetch(STAGING / "libraries-26.1.2/modmenu-18.0.2.jar",
                 "https://cdn.modrinth.com/data/mOgUt4GM/versions/ZJ6YTrMY/modmenu-18.0.2.jar")
    compile_path = runtime + [api_jar, menu]
    nested_jars = [api_jar, *(jar for jar in runtime if jar.name.startswith("fabric-loader-"))]
    for jar in nested_jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith("META-INF/jars/") and name.endswith(".jar"):
                    path = cache / "expanded" / Path(name).name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    content = archive.read(name)
                    if not path.exists() or path.read_bytes() != content:
                        path.write_bytes(content)
                    compile_path.append(path)
    return runtime, compile_path, api_jar, profile["mainClass"]


def sources_for(version: str):
    main = SOURCE / "main/java"
    pkg = Path("com/fullyx/texturemixer")
    baseline = json.loads((SOURCE / "mc261/native-sources.json").read_bytes())
    files = {str(file.relative_to(main)): file for file in (main / pkg / "core").glob("*.java")}
    files.update({str(Path(file)): main / file for file in baseline["sharedSources"]})
    extras = list(baseline["extraMixins"])
    removed = set()
    variants = ["mc261"]
    if version in ("26.2", "26.3"):
        variants.append("mc262")
    if version == "26.3":
        variants.append("mc263")
    for name in variants:
        root = SOURCE / name
        java = root / "java"
        files.update({str(file.relative_to(java)): file for file in java.rglob("*.java")})
        config = root / "native-mixins.json"
        if config.is_file():
            changes = json.loads(config.read_bytes())
            extras.extend(changes.get("add", []))
            removed.update(changes.get("remove", []))
    java_files = sorted(file for file in files.values()
                        if not (file.parent.name == "mc12111" and file.stem in removed))
    return java_files, list(dict.fromkeys(extras)), removed


def jdk_tool(jdk: Path, name: str) -> Path:
    return jdk / "bin" / (name + (".exe" if os.name == "nt" else ""))


def compile_java(jdk: Path, files, classpath, output: Path, log: Path):
    output.mkdir(parents=True)
    args_file = output.parent / (output.name + ".args")

    def quote(value):
        return '"' + str(value).replace("\\", "/") + '"'

    arguments = ["--release", "25", "-proc:none", "-encoding", "UTF-8", "-classpath",
                 quote(os.pathsep.join(map(str, classpath))), "-d", quote(output)]
    arguments.extend(quote(path) for path in files)
    args_file.write_text("\n".join(arguments), "utf-8")
    with log.open("w", encoding="utf-8") as sink:
        result = subprocess.run([jdk_tool(jdk, "javac"), "-J-Dfile.encoding=UTF-8", "@" + str(args_file)],
                                stdout=sink, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Compilation failed. {log}")


def metadata_for(version: str, release: str, extras, removed):
    api, loader = TARGETS[version]
    resources = SOURCE / "main/resources"
    mod = (resources / "fabric.mod.json").read_text("utf-8")
    fields = dict(version=f"{release}+{version}", minecraft=version, loader=loader, fabricApi=api, javaVersion=25)
    for name, value in fields.items():
        mod = mod.replace("${" + name + "}", str(value))
    mixins = (resources / "texturemixer.mixins.json").read_text("utf-8")
    substitutions = {
        "blockAtlasMixin": ["PreviewBlockAtlasMixin"],
        "atlasManagerMixin": ["AtlasSpriteMixin"],
        "deferredGuiMixins": ["DrawContextAccess", "EnchantGuiMixin", "EnchantItemGuiMixin", "EnchantSubmissionMixin"],
        "pipelineMixins": ["RenderPipelinesAccess", "DryColorMixin"],
        "itemModelMixins": ["ItemAssetsMixin", "SpecialItemMixin", "PreviewNestedItemMixin"],
    }
    for name, entries in substitutions.items():
        values = "".join(json.dumps(entry) + "," for entry in entries)
        mixins = mixins.replace("${" + name + "}", values)
    config = json.loads(mixins)
    config["compatibilityLevel"] = "JAVA_25"
    config["client"] = list(dict.fromkeys(name for name in [*config["client"], *extras] if name not in removed))
    return json.loads(mod), config


def package(target: Path, classes: Path, resources, metadata, mixins=None):
    excluded = {"fabric.mod.json", "texturemixer.mixins.json", "assets/texturemixer/shaders/core/sky_legacy.json",
                "assets/texturemixer/shaders/core/sky_immediate.vsh"}
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for root in [classes, *resources]:
            for file in sorted(root.rglob("*")):
                if file.is_file():
                    name = file.relative_to(root).as_posix()
                    if name not in excluded:
                        archive.write(file, name)
        archive.writestr("fabric.mod.json", json.dumps(metadata, indent=2) + "\n")
        if mixins is not None:
            archive.writestr("texturemixer.mixins.json", json.dumps(mixins, indent=2) + "\n")
            for notice in ("LICENSE", "THIRD-PARTY-NOTICES.md"):
                archive.write(PROJECT / notice, "META-INF/" + notice)
    with zipfile.ZipFile(target) as archive:
        assert archive.testzip() is None
        assert len(set(archive.namelist())) == len(archive.namelist())
        if mixins is not None:
            initial_class = "com/fullyx/texturemixer/TextureMixerClient.class"
            assert int.from_bytes(archive.read(initial_class)[6:8], "big") == 69
            assert not any("/audit/" in name or "/smoke/" in name for name in archive.namelist())
            for name in mixins["client"]:
                class_file = mixins["package"].replace(".", "/") + "/" + name + ".class"
                assert class_file in archive.namelist(), name


def audit(args, version, metadata, jdk, runtime, compile_path, api_jar, main_class, jar, classes, temporary):
    relative = Path("run-version-audit") / version / str(time.time_ns())
    run_dir = PROJECT / relative
    mods = run_dir / "mods"
    mods.mkdir(parents=True)
    if args.mipmap_levels is not None:
        (run_dir / "options.txt").write_text(f"mipmapLevels:{args.mipmap_levels}\n", "utf-8")
    shutil.copy2(jar, mods / jar.name)
    shutil.copy2(api_jar, mods / api_jar.name)
    if args.modmenu:
        shutil.copy2(args.modmenu, mods / args.modmenu.name)
    audit_source = SOURCE / ("versionAuditNative" if version.startswith("26.1") else "versionAudit26Recent") / "java"
    report_dir = REPORTS / version / "features"
    if args.audit_label:
        report_dir /= args.audit_label
    report_dir.mkdir(parents=True, exist_ok=True)
    audit_classes = temporary / "audit"
    compile_java(jdk, sorted(audit_source.rglob("*.java")), compile_path + [classes], audit_classes,
                 report_dir / "compile-audit.log")
    audit_manifest = json.loads((SOURCE / "versionAudit/resources/fabric.mod.json").read_bytes())
    package(mods / "texturemixer-audit.jar", audit_classes, [], audit_manifest)
    command = [jdk_tool(jdk, "java"), "-Xmx2G", "-XX:ActiveProcessorCount=8",
               "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true",
               "-Dtexturemixer.versionWorldAudit=true", "-Dtexturemixer.versionFeatureAudit=true",
               "-cp", os.pathsep.join(map(str, runtime)), main_class,
               "--username", "MixerAudit", "--version", version, "--gameDir", run_dir, "--assetsDir", args.assets_dir,
               "--assetIndex", str(metadata["assetIndex"]["id"]), "--accessToken", "0", "--width", "1280", "--height", "720"]
    if args.backend:
        command.extend(["--graphicsBackend", args.backend])
    with (report_dir / "console.log").open("w", encoding="utf-8") as log:
        result = subprocess.run(command, cwd=run_dir, stdout=log, stderr=subprocess.STDOUT, timeout=180)
    evidence = run_dir / "version-audit.txt"
    if evidence.exists():
        shutil.copy2(evidence, report_dir / evidence.name)
    if result.returncode or not evidence.exists():
        raise RuntimeError(f"Client exited with {result.returncode}. {report_dir / 'console.log'}")
    message = evidence.read_text("utf-8")
    markers = ("Worn preview pixels", "sky pixels", "Water and Lava preview pixels", "Water and Lava world pixels")
    if not message.startswith(f"PASS: {version} ") or not all(marker in message for marker in markers):
        raise RuntimeError(message.splitlines()[0])
    fluid_shots = ('water-vanilla-grid', 'water-custom-source', 'water-custom-confirmation', 'lava-vanilla-grid', 'lava-custom-source', 'lava-custom-confirmation', 'world-water', 'world-lava')
    for name in ("custom-sword", "custom-hand", "custom-block", "custom-worn", "world-sky") + fluid_shots:
        shutil.copy2(run_dir / "screenshots" / f"port-audit-{name}.png", report_dir / (name + ".png"))
    print(message.strip(), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", choices=TARGETS)
    parser.add_argument("--release", default=os.environ.get("TEXTUREMIXER_RELEASE_VERSION", "1.0.0"))
    parser.add_argument("--output", type=Path, default=Path(os.environ.get("TEXTUREMIXER_ARTIFACT_DIR", PROJECT / "build/libs")))
    parser.add_argument("--launcher", type=Path, default=os.environ.get("TEXTUREMIXER_LAUNCHER"),
                        help="Optional Minecraft directory for reusing installed libraries and assets")
    parser.add_argument("--jdk", type=Path, default=os.environ.get("TEXTUREMIXER_JDK25") or os.environ.get("JAVA_HOME"),
                        help="JDK 25 installation directory (defaults to JAVA_HOME)")
    parser.add_argument("--assets-dir", type=Path)
    parser.add_argument("--audit", action="store_true")
    parser.add_argument("--audit-label", help="Save an additional test configuration under its own report folder")
    parser.add_argument("--modmenu", type=Path)
    parser.add_argument("--backend", choices=("opengl", "vulkan"))
    parser.add_argument("--mipmap-levels", type=int, choices=range(5), help="Override mipmapping in the isolated audit client")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?", args.release):
        parser.error("Expected a semantic release version")
    if args.audit_label and not re.fullmatch(r"[A-Za-z0-9-]+", args.audit_label):
        parser.error("Expected an audit label using letters, digits or hyphens")
    version = args.target
    if args.audit:
        if os.name != "nt":
            parser.error("The native client audit currently supports Windows.")
        configured_assets = os.environ.get("TEXTUREMIXER_ASSETS_DIR")
        args.assets_dir = args.assets_dir or (Path(configured_assets) if configured_assets else None)
        if args.assets_dir is None and args.launcher is not None:
            args.assets_dir = args.launcher / "assets"
        if args.assets_dir is None or not (args.assets_dir / "indexes").is_dir():
            parser.error("--audit requires --assets-dir pointing to launcher's Minecraft assets.")
        args.assets_dir = args.assets_dir.resolve()
    javac = shutil.which("javac")
    jdk = args.jdk or (Path(javac).resolve().parent.parent if javac else None)
    if jdk is None or not jdk_tool(jdk, "javac").is_file():
        parser.error("Set JAVA_HOME or pass --jdk with a JDK 25 installation.")
    REPORTS.mkdir(parents=True, exist_ok=True)
    metadata, game = game_files(version)
    if args.audit:
        asset_index = args.assets_dir / "indexes" / (str(metadata["assetIndex"]["id"]) + ".json")
        if not asset_index.is_file():
            parser.error(f"The assets directory is missing the Minecraft {version} asset index: {asset_index.name}")
    runtime, compile_path, api_jar, main_class = dependencies(version, args.launcher, metadata, game)
    sources, extras, removed = sources_for(version)
    mod, mixins = metadata_for(version, args.release, extras, removed)
    with tempfile.TemporaryDirectory(dir=STAGING, prefix=f"native-{version}-") as folder:
        temporary = Path(folder)
        classes = temporary / "classes"
        compile_java(jdk, sources, compile_path, classes, REPORTS / f"compile-{version}-release.log")
        jar = temporary / f"TextureMixer-{args.release}+{version}.jar"
        resources = [SOURCE / "main/resources"]
        if version == "26.3":
            resources.append(SOURCE / "mc263/resources")
        package(jar, classes, resources, mod, mixins)
        if args.audit:
            audit(args, version, metadata, jdk, runtime, compile_path, api_jar, main_class, jar, classes, temporary)
        args.output.mkdir(parents=True, exist_ok=True)
        destination = args.output / jar.name
        shutil.copy2(jar, destination)
        digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        destination.with_suffix(".jar.sha256").write_text(f"{digest}  {destination.name}\n", "ascii")
        print(destination.resolve(), flush=True)


if __name__ == "__main__":
    main()
