"""Self-authored diagnostic textures. Writes only to this project's run-smoke/fixtures."""
from pathlib import Path
import hashlib
import json
import math
import random
import zipfile
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'run-smoke' / 'fixtures'
COLORS = {'Main-Pack': (220, 75, 60), 'Pack-A': (45, 130, 240), 'Pack-B': (65, 205, 120)}

def file(pack, resource):
    path = ROOT / pack / 'assets' / 'minecraft' / resource
    path.parent.mkdir(parents=True, exist_ok=True)
    return path

def tile(pack, resource, color, size=(16, 16)):
    image = Image.new('RGBA', size, color + (255,))
    draw = ImageDraw.Draw(image)
    for y in range(0, size[1], 8):
        for x in range(0, size[0], 8):
            if (x // 8 + y // 8) % 2:
                draw.rectangle((x, y, x + 3, y + 3), fill=tuple(max(0, c - 45) for c in color) + (255,))
    image.save(file(pack, resource))

def sword(pack, color):
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.line((3, 12, 12, 3), fill=(30, 40, 45), width=4)
    draw.line((5, 10, 12, 3), fill=color + (255,), width=3)
    draw.line((3, 9, 7, 13), fill=(230, 185, 80), width=2)
    draw.line((2, 14, 4, 12), fill=(130, 90, 45), width=2)
    image.save(file(pack, 'textures/item/diamond_sword.png'))

def sky(pack, world, aurora=False):
    rng = random.Random(44 if aurora else 21)
    n = 128
    image = Image.new('RGBA', (n * 3, n * 2))
    for face in range(6):
        face_image = Image.new('RGBA', (n, n), (9, 13, 32, 255))
        draw = ImageDraw.Draw(face_image)
        if aurora:
            for x in range(n):
                center = int(45 + math.sin(x * .055 + face) * 16)
                for offset in range(-15, 16):
                    weight = 1 - abs(offset) / 16
                    draw.point((x, center + offset), fill=(int(25 * weight), int(60 + 170 * weight), int(85 + 100 * weight), 255))
        else:
            for x in range(n):
                center = int(50 + math.sin(x * .025 + face) * 15)
                for offset in range(-20, 21):
                    weight = 1 - abs(offset) / 21
                    draw.point((x, center + offset), fill=(int(20 + 65 * weight), int(20 + 55 * weight), int(40 + 110 * weight), 255))
        for _ in range(120):
            x, y = rng.randrange(n), rng.randrange(n)
            draw.point((x, y), fill=(200, 220, 255, 255))
        draw.text((4, 4), str(face), fill=(255, 200, 80))
        image.paste(face_image, (face % 3 * n, face // 3 * n))
    image.save(file(pack, f'optifine/sky/world{world}/sky0.png'))
    file(pack, f'optifine/sky/world{world}/sky0.properties').write_text(
        'source=./sky0.png\nblend=replace\nrotate=true\nspeed=0.2\naxis=0 1 0\nweather=clear rain thunder\n', encoding='ascii')

def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    for pack, color in COLORS.items():
        (ROOT / pack).mkdir(exist_ok=True)
        (ROOT / pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'min_format': [75, 0], 'max_format': [75, 0], 'description': 'TextureMixer diagnostic fixture'}}), encoding='utf-8')
        tile(pack, 'textures/block/diamond_block.png', color)
        tile(pack, 'textures/entity/equipment/humanoid/diamond.png', color, (64, 32))
        tile(pack, 'textures/entity/equipment/humanoid_leggings/diamond.png', color, (64, 32))
        tile(pack, 'textures/entity/chest/normal.png', color, (64, 64))
    sword('Main-Pack', COLORS['Main-Pack'])
    sword('Pack-A', COLORS['Pack-A'])
    tile('Main-Pack', 'textures/block/stone.png', COLORS['Main-Pack'])
    tile('Pack-B', 'textures/item/emerald.png', COLORS['Pack-B'])
    # Pack-B deliberately has no sword resource, including shared parents.
    for name in ['Empty-Pack', 'Worn-Only']:
        (ROOT / name / 'assets').mkdir(parents=True, exist_ok=True)
    tile('Worn-Only', 'textures/entity/equipment/humanoid/diamond.png', (190, 80, 225), (64, 32))
    sky('Sky-Galaxy', 0)
    sky('Sky-Aurora', 1, True)
    file('Invalid-Pack', 'items/diamond_sword.json').write_text('{"model":{"type":"minecraft:condition","on_true":{"type":"invalid:unknown"},"on_false":{"type":"minecraft:empty"}}}', encoding='ascii')
    # Standard, explicitly selected ZIPs. Empty candidates retain an assets/ directory entry.
    for name in ['Main-Pack', 'Pack-A', 'Pack-B', 'Empty-Pack', 'Sky-Galaxy', 'Sky-Aurora', 'Invalid-Pack']:
        with zipfile.ZipFile(ROOT / (name + '.zip'), 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('assets/', b'')
            for path in sorted((ROOT / name).rglob('*')):
                if path.is_file():
                    info = zipfile.ZipInfo(path.relative_to(ROOT / name).as_posix(), (2026, 11, 11, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    archive.writestr(info, path.read_bytes())
    manifest = {}
    for path in sorted(ROOT.rglob('*')):
        if path.is_file() and path.name != 'fixture-hashes.json':
            manifest[path.relative_to(ROOT).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    (ROOT / 'fixture-hashes.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(f'Created {len(manifest)} diagnostic files under {ROOT}')

if __name__ == '__main__':
    main()
