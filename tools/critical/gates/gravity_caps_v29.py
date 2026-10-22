"""V1.21.29 gravity end caps: preserve the CTM rails and all side artwork.

Run --apply once to amend released bytes; default verifies assets without writes
outside build/. Animation uses the existing native terrain-atlas sprite path.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import io
import json
import sys
import math
import subprocess

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from material_palette import expand_palette, normalize_palette

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'build/critical-v2-runtime-v12129/material'
TEXTURES = ROOT / 'src/main/resources/assets/gtsr/textures/blocks/critical/v2'
MATERIALS = ROOT / 'src/main/resources/assets/gtsr/critical/materials.json'
MANIFEST = ROOT / 'src/main/resources/assets/gtsr/critical/source-sha256.json'
BASELINE = 'd77d25912a0c76df3dff47555b5345688144e9d0'
FRAMES, TICKS, N = 12, 8, 32


def rel(p):
    return p.relative_to(ROOT).as_posix()


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def released(p):
    return subprocess.check_output(['git', 'show', BASELINE + ':' + rel(p)], cwd=ROOT)


def load(p):
    value = json.loads(p.read_text(encoding='utf-8'))
    return expand_palette(value) if p == MATERIALS else value


def write(p, value, crlf=False):
    if p == MATERIALS and 'schemaVersion' in json.loads(p.read_text(encoding='utf-8')):
        value = normalize_palette(value)
    text = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes((text.replace('\n', '\r\n') if crlf else text).encode('utf-8'))


def artwork(original, face, frame):
    """Nearest-neighbour pixel art, no smoothing; centre radius fits old rails."""
    im = original.convert('RGBA').copy()
    em = Image.new('RGBA', (N, N))
    phase = frame * math.tau / FRAMES
    pulse = .90 + .10 * math.cos(phase)
    for y in range(6, 26):
        for x in range(6, 26):
            dx, dy = x - 15.5, y - 15.5
            r = math.hypot(dx, dy)
            # Recess and stepped bezel are albedo; only the circular field glows.
            grain = ((x // 2 + y // 2) % 3) - 1
            color = (66 + grain, 73 + grain, 90 + grain, 255)
            glow = None
            if r <= 9.8:
                if r > 8.6:
                    color = (109, 120, 143, 255) if dy < 0 else (39, 46, 64, 255)
                elif face == 'top':
                    color = (24, 29, 51, 255)
                    if r >= 6.7:
                        # Cold-white outer ring over a low-saturation violet-blue carrier.
                        color = (162, 171, 204, 255) if r > 7.6 else (79, 82, 131, 255)
                        glow = (202, 211, 238, 232) if r > 7.6 else (118, 126, 182, 200)
                    elif r <= 2.3:
                        color = (75, 82, 126, 255)
                        glow = (130, 146, 200, 184)
                else:
                    color = (29, 48, 73, 255)
                    if r >= 7.0:
                        color = (134, 172, 191, 255)
                        glow = (173, 215, 230, 228)
                    elif r >= 4.8:
                        color = (63, 88, 135, 255)
                        glow = (95, 126, 188, 192)
                    else:
                        color = (65, 107, 142, 255) if r > 2.8 else (113, 155, 174, 255)
                        glow = (105, 164, 192, 184) if r > 2.8 else (163, 203, 217, 208)
            im.putpixel((x, y), color)
            if glow:
                # A gentle moving segment; mask coverage stays circular every frame.
                angle = math.atan2(dy, dx)
                highlight = 1.0 + .08 * max(0, math.cos(angle - phase))
                rgb = tuple(min(255, round(c * pulse * highlight)) for c in glow[:3])
                em.putpixel((x, y), (*rgb, glow[3]))
    return im, em


def sprite(path):
    return 'gtsr:critical/v2/' + path.relative_to(TEXTURES).with_suffix('').as_posix()


def amend():
    materials = load(MATERIALS)
    original_materials = expand_palette(json.loads(released(MATERIALS)))
    assert materials == original_materials, 'Materials changed since released baseline; review before apply.'
    gravity = materials['v2_gravity_modulator']
    owned = []
    def save(p, im):
        im.save(p)
        owned.append(p)
    def pair(albedo, face):
        original = Image.open(io.BytesIO(released(albedo)))
        color, first = artwork(original, face, 0)
        save(albedo, color)
        stem = albedo.with_suffix('')
        emission = Path(str(stem) + '_emission.png')
        frozen = Path(str(stem) + '_emission_frozen.png')
        strip = Image.new('RGBA', (N, N * FRAMES))
        for frame in range(FRAMES):
            strip.paste(artwork(original, face, frame)[1], (0, frame * N))
        save(emission, strip)
        save(frozen, first)
        meta = Path(str(emission) + '.mcmeta')
        write(meta, {'animation': {'frametime': TICKS, 'frames': list(range(FRAMES)),
                                  'interpolate': False}}, crlf=face == 'bottom')
        if face == 'top':
            owned.append(meta)
        return {'albedo': sprite(albedo), 'emission': sprite(emission),
                'frozenAlbedo': sprite(albedo), 'frozenEmission': sprite(frozen)}
    gravity['sprites']['top'] = pair(TEXTURES / 'gravity_modulator__top.png', 'top')
    for face in ('top', 'bottom'):
        for mask in range(16):
            path = TEXTURES / f'ctm/gravity_modulator/{face}/mask_{mask:02d}.png'
            gravity['ctm']['sprites'][face][str(mask)] = pair(path, face)
    gravity['sprites']['bottom'] = copy.deepcopy(gravity['ctm']['sprites']['bottom']['0'])
    gravity['animationFaces'] = ['px', 'nx', 'py', 'ny', 'pz', 'nz']
    gravity['ctm']['faceFrameCounts']['top'] = FRAMES
    gravity['runtimeRevision'] = 'V2-R4 + v1.21.29 circular gravity caps'
    write(MATERIALS, materials, crlf=True)
    owned.append(MATERIALS)
    manifest = load(MANIFEST)
    for p in owned:
        manifest['files'][rel(p)] = sha(p)
    manifest['authorities']['v2']['directionalFaceRegistration'] = (
        'gravity py cold-white violet-blue ring and ny cyan-blue circular field; '
        'independent live twelve-frame terrain-atlas emission and frame-zero frozen emission; '
        'original side sprites, CTM rails, downward FX and native lightValue retained')
    write(MANIFEST, manifest, crlf=True)
    owned.extend((MANIFEST, Path(__file__).resolve()))
    write(OUT / 'owned-paths.json', {'ownedPaths': sorted(map(rel, owned)), 'planMirrors': []})


def resolve(name):
    return ROOT / 'src/main/resources/assets/gtsr/textures/blocks' / (name.split(':', 1)[1] + '.png')


def verify():
    materials = load(MATERIALS)
    baseline = expand_palette(json.loads(released(MATERIALS)))
    gravity = materials['v2_gravity_modulator']
    prior = baseline['v2_gravity_modulator']
    assert all(v == materials[k] for k, v in baseline.items() if k != 'v2_gravity_modulator')
    immutable = ['lightValue', 'lightOpacity', 'lightStatePolicy', 'emissionStrength',
                 'directionalEmission', 'lightSourceRole', 'frameCount', 'ticksPerFrame', 'faceSprites']
    assert all(gravity[k] == prior[k] for k in immutable)
    assert gravity['sprites']['side'] == prior['sprites']['side']
    assert gravity['ctm']['sprites']['side'] == prior['ctm']['sprites']['side']
    baseline_manifest = json.loads(released(MANIFEST))
    unchanged = []
    for p in TEXTURES.rglob('*'):
        if p.is_file() and ('/gravity_modulator/side/' in rel(p)
                            or p.name.startswith('gravity_modulator') and '__top' not in p.name):
            # Released manifest records source bytes, including CRLF animation
            # metadata; older Git blobs may have normalized its line endings.
            assert sha(p) == baseline_manifest['files'][rel(p)], rel(p)
            unchanged.append(rel(p))
    for p in (ROOT / 'src/main/resources/assets/gtsr/critical/models').glob('*.json'):
        assert sha(p) == baseline_manifest['files'][rel(p)], rel(p)
        unchanged.append(rel(p))
    cap_checks = []
    for face in ('top', 'bottom'):
        for mask in range(16):
            pair = gravity['ctm']['sprites'][face][str(mask)]
            im = Image.open(resolve(pair['albedo'])).convert('RGBA')
            em = Image.open(resolve(pair['emission'])).convert('RGBA')
            frozen = Image.open(resolve(pair['frozenEmission'])).convert('RGBA')
            original = Image.open(io.BytesIO(released(resolve(pair['albedo'])))).convert('RGBA')
            assert im.size == frozen.size == (N, N) and em.size == (N, N * FRAMES)
            assert frozen.tobytes() == em.crop((0, 0, N, N)).tobytes()
            assert pair['frozenAlbedo'] == pair['albedo']
            meta = load(Path(str(resolve(pair['emission'])) + '.mcmeta'))['animation']
            assert meta == {'frametime': TICKS, 'frames': list(range(FRAMES)), 'interpolate': False}
            frame_hashes = set()
            counts = []
            for frame in range(FRAMES):
                crop = em.crop((0, frame * N, N, (frame + 1) * N))
                frame_hashes.add(hashlib.sha256(crop.tobytes()).hexdigest())
                count = 0
                for y in range(N):
                    for x in range(N):
                        pixel = crop.getpixel((x, y))
                        if pixel[3]:
                            assert math.hypot(x - 15.5, y - 15.5) <= 8.6
                            count += 1
                        if x < 6 or x >= 26 or y < 6 or y >= 26:
                            assert im.getpixel((x, y)) == original.getpixel((x, y))
                assert count > 80
                counts.append(count)
            assert len(frame_hashes) > 1
            cap_checks.append({'face': face, 'mask': mask, 'litPixelsPerFrame': counts,
                               'distinctFrameHashes': len(frame_hashes), 'frozenIsFrameZero': True,
                               'ctmOuterRailsPreserved': True})
    names = []
    for side in ('ny', 'py', 'nz', 'pz', 'nx', 'px'):
        group = gravity['ctm']['faceLookup'][side]
        names.append(gravity['ctm']['sprites'][group]['0']['albedo'])
    assert len(set(names[:3])) == 3 and len(set(names[2:])) == 1
    manifest = load(MANIFEST)
    owned = load(OUT / 'owned-paths.json')['ownedPaths']
    sha_checks = {}
    for path in owned:
        if path.startswith('src/') and path != rel(MANIFEST):
            assert manifest['files'][path] == sha(ROOT / path), path
            sha_checks[path] = sha(ROOT / path)
    make_preview(gravity)
    write(OUT / 'checks.json', {'status': 'PASS', 'baseline': BASELINE,
          'nativeTileSize': N, 'frameCount': FRAMES, 'ticksPerFrame': TICKS,
          'realEmissionCircleMask': True, 'capChecks': cap_checks,
          'getIconResolutionOrderNyPyNzPzNxPx': names,
          'terrainAtlasRegistration': 'existing CriticalMaterialTextures.registerPair; no extra atlas or palette',
          'nativeLightValue': gravity['lightValue'], 'nativeLightPolicyUnchanged': True,
          'sideAndGeometryByteIdentical': unchanged, 'assetSha256': sha_checks,
          'noAngelicaFallback': 'same existing native emission sprite/render path; runtime probe owned by renderer agent',
          'runtimeJavaProbe': 'not executed by asset gate'})
    print(f'PASS: {len(cap_checks)} caps; circular animated masks, frozen frame zero, CTM rails and sides preserved.')


def make_preview(gravity):
    sheet = Image.new('RGB', (920, 390), (24, 29, 39))
    draw = ImageDraw.Draw(sheet)
    draw.text((16, 12), 'Gravity Modulator v1.21.29 - native 32px / nearest neighbour', fill=(220, 229, 239))
    for col, face in enumerate(('side', 'top', 'bottom')):
        pair = gravity['ctm']['sprites'][face]['0']
        im = Image.open(resolve(pair['albedo'])).convert('RGBA')
        em = Image.open(resolve(pair['emission'])).convert('RGBA').crop((0, 0, N, N))
        x = 16 + col * 302
        draw.text((x, 40), face.upper(), fill=(207, 219, 231))
        sheet.paste(im.resize((192, 192), Image.Resampling.NEAREST), (x, 64))
        black = Image.new('RGBA', (N, N), (0, 0, 0, 255))
        black.alpha_composite(em)
        sheet.paste(black.resize((96, 96), Image.Resampling.NEAREST), (x, 278))
        draw.text((x + 106, 286), 'Actual emission', fill=(191, 204, 219))
        draw.text((x + 106, 308), '12 frames / 8 ticks', fill=(191, 204, 219))
        draw.text((x + 106, 330), 'frame 0 shown', fill=(191, 204, 219))
    sheet.save(OUT.parent / 'material-preview.png')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.apply:
        amend()
    verify()
