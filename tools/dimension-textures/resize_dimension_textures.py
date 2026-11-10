"""Explicitly authorized Pillow frame downsampling. Protects monsters, frame timelines and PNG colour chunks."""
from pathlib import Path
from io import BytesIO
import argparse
import hashlib
import json
import math
import struct
import zipfile
from collections import Counter
from collections import defaultdict
from itertools import combinations
from PIL import Image, ImageDraw, ImageFont, ImageChops

REPO = Path(__file__).resolve().parents[2]
WORKSPACE = REPO.parent
ASSETS = REPO / 'src/main/resources/assets/gtsr'
TEXTURES = ASSETS / 'textures'
OUT = WORKSPACE / '.artifacts/gtsr-dimension-trim/blocks-items'
ARCHIVE = WORKSPACE / '.artifacts/core-global-release/artifacts/gtsr/gtsr-v2.1.30.jar'
BASELINE = OUT / 'before.json'
COLOUR_CHUNKS = {b'sRGB', b'gAMA', b'cHRM', b'iCCP', b'sBIT'}
ALIASES = {
    'riveted_hatch': 'rust_riveted_plate', 'boiler_casing': 'rust_riveted_plate',
    'pressure_reservoir': 'rust_riveted_plate', 'patina_trim': 'cast_iron_pillar',
    'silent_bell_base': 'slag_masonry', 'sootstone_tiles': 'slag_masonry',
    'salvage_workbench': 'rootwood_archive',
}
SHATTERED = ['corestone', 'monolith', 'ash_top', 'ash_top_side', 'ash_base',
    'slag_top', 'slag_top_side', 'slag_base', 'glass_top', 'glass_top_side', 'glass_base',
    'tar_top', 'tar_top_side', 'tar_base', 'spike_cluster', 'ash_log_side', 'ash_log_top', 'ash_thorn']
PRUNED = {'blocks/shattered_' + name + '.png' for name in SHATTERED} | {
    'blocks/ruins_' + name + '.png' for name in ALIASES}

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def chunks(data):
    require(data[:8] == b'\x89PNG\r\n\x1a\n', 'PNG signature')
    offset, result = 8, []
    while offset < len(data):
        size = struct.unpack_from('>I', data, offset)[0]
        raw = data[offset:offset + size + 12]
        require(len(raw) == size + 12, 'Complete PNG chunk')
        result.append((raw[4:8], raw)); offset += len(raw)
    return result

def item_paths():
    lore = REPO / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/lore'
    import re
    text = (lore / 'LoreRegistry.java').read_text(encoding='utf-8')
    initial = re.findall(r'"([a-z_]+)"', text.split('for (String id : new String[] {', 1)[1].split('})', 1)[0])
    text = (lore / 'HistoryProgress.java').read_text(encoding='utf-8')
    new = re.findall(r'"([a-z_]+)"', text.split('NEW_RELICS = {', 1)[1].split('};', 1)[0])
    result = ['items/lore/' + name + '.png' for name in ['history'] + initial + new]
    result += ['items/spacetime_anchor_beacon.png', 'items/spacetime_anchor_beacon_needle.png', 'items/AbyssalObsessionBucket.png']
    require(len(result) == len(set(result)) == 39, '39 actual registered dimension-item textures')
    return result

def source_paths():
    return sorted([p.relative_to(TEXTURES).as_posix() for p in (TEXTURES / 'blocks').rglob('*.png')]) + item_paths()

def monster_guard():
    roots = [ASSETS / 'liminal', TEXTURES / 'entity']
    return {p.relative_to(REPO).as_posix(): sha(p.read_bytes()) for root in roots if root.exists() for p in root.rglob('*.png')}

def animation(path, raw, metadata):
    image = Image.open(BytesIO(raw)); image.load()
    section = metadata.get('animation') if metadata is not None else None
    if section is None:
        require(image.width == image.height, 'Static sprite must be square: ' + path)
        return image, image.width, image.height, 1
    fw = section.get('width', image.width)
    fh = section.get('height', fw)
    require(fw == fh and image.width == fw and image.height % fh == 0, 'Square vertical frames: ' + path)
    count = image.height // fh
    require(fw in (16, 32), 'Reviewed source frame sizes only: ' + path)
    for frame in section.get('frames', []):
        index = frame if isinstance(frame, int) else frame['index']
        require(0 <= index < count, 'Frame index remains inside strip: ' + path)
    return image, fw, fh, count

def prepare():
    if BASELINE.exists():
        return load(BASELINE)
    rows = []
    paths = source_paths()
    require(sum(p.startswith('blocks/') for p in paths) == 787, '787 source block PNGs')
    with zipfile.ZipFile(ARCHIVE) as archive:
        for path in paths:
            file = TEXTURES / path; raw = file.read_bytes()
            asset = 'assets/gtsr/textures/' + path
            require(sha(archive.read(asset)) == sha(raw), 'Existing retained JAR is exact original PNG: ' + path)
            meta_path = file.with_suffix('.png.mcmeta')
            meta = load(meta_path) if meta_path.exists() else None
            image, fw, fh, count = animation(path, raw, meta)
            rows.append(dict(path=path, kind='block' if path.startswith('blocks/') else 'item',
                sha256=sha(raw), bytes=len(raw), mode=image.mode, width=image.width, height=image.height,
                frame_width=fw, frame_height=fh, frames=count, metadata=meta,
                metadata_sha256=sha(meta_path.read_bytes()) if meta is not None else None,
                colour_chunks=[sha(chunk) for kind, chunk in chunks(raw) if kind in COLOUR_CHUNKS]))
    data = dict(status='BASELINED_BEFORE_TEXTURE_EDITS', pngs=rows,
        original_png_archive=str(ARCHIVE), original_png_archive_sha256=sha(ARCHIVE.read_bytes()),
        protected_monster_pngs=monster_guard(), approved_pruned_paths=sorted(PRUNED),
        aliases=ALIASES, source_count=len(rows), block_count=787, item_count=39)
    save(BASELINE, data)
    return data

def light(pixel):
    return max(pixel[:3]) * pixel[3]

def convert(path, raw, metadata):
    image, fw, fh, count = animation(path, raw, metadata)
    if fw == 16:
        return raw, [], count
    target = Image.new(image.mode, (16, 16 * count))
    if image.mode == 'P':
        target.putpalette(image.getpalette())
    ctm = '/ctm/' in path
    emission = '_emission' in path
    details = []
    for frame in range(count):
        source = image.crop((0, frame * fh, fw, (frame + 1) * fh))
        rgba = source.convert('RGBA'); pixels = rgba.load(); result = Image.new(image.mode, (16, 16))
        colour_counts = Counter(rgba.getdata())
        rare_limit = max(2, fw * fh // 64)
        if image.mode == 'P':
            result.putpalette(image.getpalette())
        calibrated = 0
        for y in range(16):
            for x in range(16):
                sx, sy = x * 2 + 1, y * 2 + 1
                # Preserve physical CTM tile boundaries, rather than skipping top/left one-pixel borders.
                xs = [0] if ctm and x == 0 else [31] if ctm and x == 15 else [x * 2, x * 2 + 1]
                ys = [0] if ctm and y == 0 else [31] if ctm and y == 15 else [y * 2, y * 2 + 1]
                sx = min(xs, key=lambda value: abs(value - sx)); sy = min(ys, key=lambda value: abs(value - sy))
                selected = pixels[sx, sy]
                # Select existing source colours/alpha only. Thin transparent silhouettes and emission survive.
                if emission and light(selected) == 0:
                    best = max(((xx, yy) for yy in ys for xx in xs), key=lambda p: (light(pixels[p]) if emission else pixels[p][3], -abs(p[0]-sx)-abs(p[1]-sy)))
                    if best != (sx, sy):
                        sx, sy = best; calibrated += 1
                elif selected[3] > 0 and colour_counts[selected] > rare_limit and not (
                    ctm and '/loom_spool/' in path and (x in (0, 15) or y in (0, 15))):
                    # Retain rare, contrasting single-pixel inlays/glyphs without growing opaque silhouettes.
                    accents = [(xx, yy) for yy in ys for xx in xs if pixels[xx, yy][3] > 0
                        and colour_counts[pixels[xx, yy]] <= rare_limit
                        and sum((pixels[xx, yy][c] - selected[c]) ** 2 for c in range(3)) >= 24 ** 2]
                    if accents:
                        sx, sy = max(accents, key=lambda p: sum((pixels[p][c] - selected[c]) ** 2 for c in range(3)))
                        calibrated += 1
                result.putpixel((x, y), source.getpixel((sx, sy)))
        before = list(rgba.getdata()); after = list(result.convert('RGBA').getdata())
        old_alpha = sum(p[3] > 0 for p in before); new_alpha = sum(p[3] > 0 for p in after)
        old_light = sum(light(p) > 0 for p in before); new_light = sum(light(p) > 0 for p in after)
        salvage = None
        if old_alpha and not new_alpha or emission and old_light and not new_light:
            sy, sx = divmod(max(range(fw * fh), key=lambda i: light(before[i]) if emission else before[i][3]), fw)
            tx, ty = sx // 2, sy // 2
            if ctm:
                tx = max(1, min(14, tx)) if sx not in (0, 31) else tx
                ty = max(1, min(14, ty)) if sy not in (0, 31) else ty
            result.putpixel((tx, ty), source.getpixel((sx, sy))); salvage = [sx, sy, tx, ty]
            after = list(result.convert('RGBA').getdata()); new_alpha = sum(p[3] > 0 for p in after); new_light = sum(light(p) > 0 for p in after)
        require(not old_alpha or new_alpha, 'Nonempty alpha frame retained: ' + path)
        require(not emission or not old_light or new_light, 'Nonempty emissive frame retained: ' + path)
        require(set(after).issubset(set(before)), 'No invented colours or alpha: ' + path)
        details.append(dict(frame=frame, before_alpha_pixels=old_alpha, after_alpha_pixels=new_alpha,
            before_emission_pixels=old_light if emission else None, after_emission_pixels=new_light if emission else None,
            calibrated_pixels=calibrated, salvage=salvage, ctm_boundary_sampling=ctm))
        target.paste(result, (0, frame * 16))
    encoded = BytesIO(); target.save(encoded, format='PNG', compress_level=9)
    generated = chunks(encoded.getvalue()); old_chunks = chunks(raw)
    old_header = next(chunk for kind, chunk in old_chunks if kind == b'IHDR')
    new_header = next(chunk for kind, chunk in generated if kind == b'IHDR')
    require(old_header[16:21] == new_header[16:21], 'PNG bit depth / colour / compression / filtering semantics unchanged: ' + path)
    # Keep every original ancillary, colour-profile, palette and transparency chunk byte-for-byte.
    # Only IHDR dimensions and image data change; this is not a generic metadata strip.
    output = bytearray(raw[:8]); inserted = False
    for kind, chunk in old_chunks:
        if kind == b'IHDR':
            output.extend(new_header)
        elif kind == b'IDAT':
            if not inserted:
                for new_kind, new_chunk in generated:
                    if new_kind == b'IDAT': output.extend(new_chunk)
                inserted = True
        else:
            output.extend(chunk)
    result_raw = bytes(output)
    check = Image.open(BytesIO(result_raw)); check.load()
    require(check.size == target.size and list(check.convert('RGBA').getdata()) == list(target.convert('RGBA').getdata()), 'Saved PNG exact resized samples')
    return result_raw, details, count

def source_bytes(baseline, path):
    with zipfile.ZipFile(baseline['original_png_archive']) as archive:
        return archive.read('assets/gtsr/textures/' + path)

def previews(baseline):
    def sheet(paths, name, tiled=False):
        row_height = 154; canvas = Image.new('RGB', (760, 35 + row_height * len(paths)), '#313137'); draw = ImageDraw.Draw(canvas)
        draw.text((10, 8), 'Before (source)                         After 16px/frame                    Difference', fill='white')
        for index, path in enumerate(paths):
            row = next(r for r in baseline['pngs'] if r['path'] == path)
            before = Image.open(BytesIO(source_bytes(baseline, path))).convert('RGBA').crop((0,0,row['frame_width'],row['frame_height']))
            after_raw, _, _ = convert(path, source_bytes(baseline, path), row['metadata'])
            after = Image.open(BytesIO(after_raw)).convert('RGBA').crop((0,0,16,16))
            if tiled:
                def tile(image):
                    out=Image.new('RGBA',(image.width*2,image.height*2));
                    for x in range(2):
                        for y in range(2):out.paste(image,(x*image.width,y*image.height))
                    return out
                before, after = tile(before), tile(after)
            before = before.resize((128,128),Image.Resampling.NEAREST); after=after.resize((128,128),Image.Resampling.NEAREST)
            diff=ImageChops.difference(before.convert('RGB'),after.convert('RGB')).convert('RGBA')
            for x,image in [(10,before),(270,after),(530,diff)]:
                bg=Image.new('RGBA',(128,128),'#72727b');bg.alpha_composite(image);canvas.paste(bg.convert('RGB'),(x,35+index*row_height))
            draw.text((10,165+index*row_height),path.replace('blocks/critical/v2/ctm/','ctm/')[-100:],fill='white')
        OUT.mkdir(parents=True,exist_ok=True);canvas.save(OUT/name)
    ctm = [r['path'] for r in baseline['pngs'] if '/ctm/' in r['path'] and '/top/mask_15.png' in r['path']][:6]
    sheet(ctm,'ctm-seams-before-after.png',True)
    loom = ['blocks/critical/v2/ctm/loom_spool/side/mask_' + mask + suffix + '.png'
        for suffix in ('', '_frozen') for mask in ('01', '04', '05')]
    sheet(loom, 'ctm-loom-spool-before-after.png', True)
    sample = ['blocks/prosperity_tuft_rust.png','blocks/prosperity_rust_log_side.png',
        'blocks/critical/magnetic_coil_emission.png','items/spacetime_anchor_beacon.png',
        'items/spacetime_anchor_beacon_needle.png','items/lore/history.png']
    sheet([p for p in sample if (TEXTURES/p).exists()],'alpha-glow-items-before-after.png')

def repair_ctm(baseline):
    """Revisit only the six reviewed spool masks; preserve all other accepted pixels and timelines."""
    result=load(OUT/'apply-result.json')
    previous=OUT/'apply-before-ctm-fix.json'
    if not previous.exists():previous.write_bytes((OUT/'apply-result.json').read_bytes())
    changed=[]
    for row in result['pngs']:
        if '/ctm/loom_spool/side/' not in row['path'] or not any(
            row['path'].endswith('mask_'+mask+suffix+'.png') for mask in ('01','04','05') for suffix in ('','_frozen')):
            continue
        original=next(x for x in baseline['pngs'] if x['path']==row['path'])
        file=TEXTURES/row['path']
        require(sha(file.read_bytes())==row['after_sha256'],'Review candidate changed before edge repair')
        raw,details,count=convert(row['path'],source_bytes(baseline,row['path']),original['metadata'])
        if sha(raw)!=row['after_sha256']:
            file.write_bytes(raw);changed.append(row['path'])
        row.update(after_sha256=sha(raw),bytes_after=len(raw),frame_details=details)
    result['result_bytes']=sum(x['bytes_after'] for x in result['pngs'])
    result['ctm_edge_calibration']={'family':'loom_spool','reviewed_masks':['01','04','05'],
        'changed_pngs':changed,'policy':'No whole-frame rare-colour ranking on connected tile boundaries'}
    save(OUT/'apply-result.json',result)
    previews(baseline)
    return result['ctm_edge_calibration']

def verify_ctm(baseline):
    import re
    families=defaultdict(dict)
    def edges(raw, size):
        image=Image.open(BytesIO(raw)).convert('RGBA'); signatures=[[],[],[],[]]
        for frame in range(image.height//size):
            top=frame*size
            signatures[0].extend(image.getpixel((x,top)) for x in range(size))
            signatures[1].extend(image.getpixel((size-1,top+y)) for y in range(size))
            signatures[2].extend(image.getpixel((x,top+size-1)) for x in range(size))
            signatures[3].extend(image.getpixel((0,top+y)) for y in range(size))
        return [tuple(s) for s in signatures]
    for row in baseline['pngs']:
        if '/ctm/' not in row['path']:continue
        path=Path(row['path']);match=re.fullmatch(r'mask_(\d+)(.*)\.png',path.name)
        require(match is not None,'Known CTM mask filename')
        families[(path.parent.as_posix(),match[2])][int(match[1])]=(
            edges(source_bytes(baseline,row['path']),row['frame_width']),edges((TEXTURES/row['path']).read_bytes(),16))
    checks=main=added=lost=coarsened=0
    for masks in families.values():
        for a,b in combinations(masks.values(),2):
            for side in range(4):
                checks+=1
                if a[0][side]==b[0][side] and a[1][side]!=b[1][side]:added+=1
                if a[0][side]!=b[0][side] and a[1][side]==b[1][side]:coarsened+=1
        for mask,a in masks.items():
            for side,bit in enumerate((1,2,4,8)):
                if mask^bit not in masks:continue
                b=masks[mask^bit]
                if a[0][side]!=b[0][side]:
                    main+=1
                    if a[1][side]==b[1][side]:lost+=1
    require(checks==10680 and main==1544,'Full reviewed CTM relation and main-bit coverage')
    require(added==0 and lost==0,'No new CTM seams or lost principal connection edges')
    return dict(families=len(families),all_frame_edge_relation_checks=checks,main_connection_bit_checks=main,
        new_seams=added,lost_main_connections=lost,corner_detail_signatures_coarsened=coarsened)

def apply(baseline):
    require(sha(ARCHIVE.read_bytes()) == baseline['original_png_archive_sha256'], 'Original archive unchanged')
    require(monster_guard() == baseline['protected_monster_pngs'], 'Monster PNGs remain original')
    rows=[]
    # Prepare every output and reject unexpected source drift before touching any source asset.
    pending=[]
    for row in baseline['pngs']:
        path=TEXTURES/row['path']; require(path.exists() and sha(path.read_bytes())==row['sha256'], 'Source PNG changed before apply: '+row['path'])
        raw, details, count=convert(row['path'],path.read_bytes(),row['metadata']); metadata=row['metadata']
        if metadata is not None:
            require(sha(path.with_suffix('.png.mcmeta').read_bytes())==row['metadata_sha256'],'Source animation metadata changed')
            metadata=json.loads(json.dumps(metadata));metadata['animation']['width']=16;metadata['animation']['height']=16
        pending.append((path,raw,metadata));rows.append(dict(path=row['path'],kind=row['kind'],before_sha256=row['sha256'],after_sha256=sha(raw),frames=count,frame_details=details,bytes_before=row['bytes'],bytes_after=len(raw),ctm='/ctm/' in row['path'],emission='_emission' in row['path']))
    for path,raw,metadata in pending:
        path.write_bytes(raw)
        if metadata is not None:path.with_suffix('.png.mcmeta').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    require(monster_guard()==baseline['protected_monster_pngs'],'Monster PNG SHA protection after apply')
    result=dict(status='ALL_REVIEWED_FRAMES_DOWNSAMPLED',source_block_pngs=787,dimension_item_pngs=39,
        target_frame_size=[16,16],pngs=rows,protected_monster_pngs_unchanged=True,
        image_count=len(rows),source_bytes=sum(r['bytes_before'] for r in rows),result_bytes=sum(r['bytes_after'] for r in rows),
        png_colour_metadata_preserved=True,rgba_samples_only_from_original=True)
    save(OUT/'apply-result.json',result)
    return result

def verify(baseline):
    actual=[];deleted=[]
    for row in baseline['pngs']:
        path=TEXTURES/row['path']
        if not path.exists():
            require(row['path'] in PRUNED,'Only explicitly approved palette PNGs may disappear');deleted.append(row['path']);continue
        metadata=load(path.with_suffix('.png.mcmeta')) if row['metadata'] is not None else None
        image,fw,fh,count=animation(row['path'],path.read_bytes(),metadata)
        require(fw==fh==16 and count==row['frames'],'16px and original frame count: '+row['path'])
        before_meta=json.loads(json.dumps(row['metadata'])) if row['metadata'] is not None else None
        if before_meta is not None:
            before_meta['animation']['width']=16;before_meta['animation']['height']=16
            require(metadata==before_meta,'Only explicit animation width/height adapted: '+row['path'])
        require([sha(chunk) for kind,chunk in chunks(path.read_bytes()) if kind in COLOUR_CHUNKS]==row['colour_chunks'],'Exact PNG colour chunks: '+row['path'])
        actual.append(dict(path=row['path'],kind=row['kind'],frame_width=fw,frames=count,sha256=sha(path.read_bytes())))
    require(monster_guard()==baseline['protected_monster_pngs'],'Protected monster PNG SHA')
    result=dict(status='PASS',verified_pngs=len(actual),blocks=sum(x['kind']=='block' for x in actual),
        dimension_items=sum(x['kind']=='item' for x in actual),original_source_pngs=len(baseline['pngs']),
        original_block_pngs=787,approved_pruned_pngs=sorted(deleted),protected_monster_pngs_unchanged=True,
        frames_and_timelines_preserved=True,png_colour_chunks_preserved=True,ctm=verify_ctm(baseline),images=actual)
    save(OUT/'verify-result.json',result);return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['prepare','apply','repair-ctm','verify']);args=parser.parse_args()
    baseline=prepare()
    if args.action=='prepare':previews(baseline);print(json.dumps({'status':baseline['status'],'blocks':787,'items':39,'out':str(OUT)}))
    elif args.action=='apply':result=apply(baseline);print(json.dumps({k:result[k] for k in ['status','image_count','source_bytes','result_bytes']}))
    elif args.action=='repair-ctm':print(json.dumps(repair_ctm(baseline)))
    else:result=verify(baseline);print(json.dumps({k:result[k] for k in ['status','verified_pngs','blocks','dimension_items','approved_pruned_pngs']}))
