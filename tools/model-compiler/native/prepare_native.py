"""Compile an isolated Case and source reference pack. Never install, prepare a world or launch Minecraft."""
from pathlib import Path
import argparse
import hashlib
import gzip
import json
import struct
import subprocess
import sys
import zipfile
sys.dont_write_bytecode = True

REPO = Path(__file__).resolve().parents[3]
WORKSPACE = REPO.parent
OUT = WORKSPACE / '.artifacts/gtsr-dimension-trim'
RUNTIME = WORKSPACE / '.artifacts/core-global-followup/runtime'
RELEASE = WORKSPACE / '.artifacts/core-global-release'

def require(ok, message):
    if not ok:
        raise RuntimeError(message)

def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def rename_legacy(data):
    """Change every self-name/descriptive UTF8 constant, including lambda handles/descriptors; no opcode changes."""
    require(data[:4] == b'\xca\xfe\xba\xbe', 'Legacy reference is a classfile')
    count = struct.unpack_from('>H', data, 8)[0]
    at, index, result, replacements = 10, 1, bytearray(data[:10]), 0
    old = b'com/miaokatze/gtsr/client/encounter/RigAsset'
    new = b'com/miaokatze/gtsr/client/encounter/RigAssetLegacyReference'
    lengths = {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index < count:
        tag = data[at]; at += 1; result.append(tag)
        if tag == 1:
            size = struct.unpack_from('>H', data, at)[0]; at += 2
            value = data[at:at + size]; at += size
            replacements += value.count(old)
            value = value.replace(old, new)
            result.extend(struct.pack('>H', len(value))); result.extend(value)
        else:
            require(tag in lengths, 'Unknown class constant tag')
            size = lengths[tag]; result.extend(data[at:at + size]); at += size
            if tag in (5, 6):
                index += 1
        index += 1
    result.extend(data[at:])
    require(replacements > 0, 'Legacy self reference was renamed')
    return bytes(result), replacements

def metadata(path, modid):
    with zipfile.ZipFile(path) as archive:
        value = json.loads(archive.read('mcmod.info').decode('utf-8-sig'))
    rows = value if isinstance(value, list) else value.get('modList', [value])
    row = next(r for r in rows if r.get('modid') == modid)
    require(isinstance(row['version'], str) and row['version'], 'Explicit FML metadata version')
    return row['version']

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--gtsr-jar', type=Path, required=True)
    parser.add_argument('--legacy-jar', type=Path, default=RELEASE/'artifacts/gtsr/gtsr-v2.1.30.jar')
    parser.add_argument('--spawn-clearance-reference', type=Path, default=OUT/'spawn-clearance/prefab-spawn-clearance.json')
    args = parser.parse_args()
    product = args.gtsr_jar.resolve(strict=True); legacy_jar = args.legacy_jar.resolve(strict=True)
    clearance_reference = args.spawn_clearance_reference.resolve(strict=True)
    clearance = read(clearance_reference)
    require(clearance['nodes'] and len(clearance['nodes']) == clearance['summary']['total'], 'Complete frozen prefab spawn-clearance reference')
    for relative, digest in clearance['inputFileSha256'].items():
        source = (REPO/relative).resolve(strict=True); source.relative_to(REPO)
        require(sha(source) == digest, 'Frozen collision-reference source identity: ' + relative)
    observed_declared_mismatches = []
    for relative, hashes in clearance['resources'].items():
        resource = (REPO/'src/main/resources/assets/gtsr/remaster'/relative).resolve(strict=True)
        resource.relative_to(REPO/'src/main/resources/assets/gtsr/remaster')
        require(sha(resource) == hashes['gzipSha256'], 'Frozen collision-reference gzip identity: ' + relative)
        raw_sha = hashlib.sha256(gzip.decompress(resource.read_bytes())).hexdigest()
        require(raw_sha == hashes['rawSha256'], 'Frozen collision-reference decompressed identity: ' + relative)
        if raw_sha != hashes['declaredRawSha256']:
            observed_declared_mismatches.append(dict(file=relative,actual=raw_sha,declared=hashes['declaredRawSha256']))
    require(observed_declared_mismatches == clearance['preexistingDeclaredHashMismatches'], 'Only the explicitly recorded baseline catalog hash mismatch is accepted')
    base = read(RUNTIME/'qa-build/build-receipt.json')
    require(sha(base['jar']) == base['sha256'], 'Frozen base QA digest')
    for path, digest in base['sources'].items():
        require(sha(path) == digest, 'Frozen base QA source changed')
    lwjgl = read(RUNTIME/'qa-gtmb-owner/compile-receipt.json')['classpath'][0]
    require(sha(lwjgl['path']) == lwjgl['sha256'], 'LWJGL2 compile API digest')
    dependencies = [Path(base['jar']), Path(base['forge']['path']), Path(base['gson']['path']), Path(lwjgl['path'])]
    sources = [Path(__file__).with_name('RigMeshNativeCase.java'), Path(__file__).with_name('DimensionTrimNativeCase.java'), Path(__file__).with_name('RigMeshCaseMod.java'), RUNTIME/'extensions/ExtensionSupport.java']
    golden = REPO/'src/test/resources/encounter/rigmesh-golden.tsv'
    rows = [line.split('\t') for line in golden.read_text(encoding='utf-8').splitlines() if line]
    require(len({r[0] for r in rows}) == len(rows), 'Independent reference codes are unique')
    active_manifest = REPO/'src/main/resources/assets/gtsr/liminal/active-models.txt'
    active = [line.strip() for line in active_manifest.read_text(encoding='utf-8').splitlines()
              if line.strip() and not line.lstrip().startswith('#')]
    require(active and len(active) == len(set(active)), 'Nonempty unique active-model manifest')
    require(set(active) <= {r[0] for r in rows}, 'Each active model has a frozen source oracle')
    active_rows = [r for r in rows if r[0] in active]
    originals = {r[0]: REPO/'src/main/resources/assets/gtsr/liminal'/r[0]/'models'/(r[0]+'.obj') for r in rows}
    for row in rows:
        require(sha(originals[row[0]]) == row[1], 'Golden source identity: ' + row[0])
    clip_counts = {code: len(read(REPO/'src/main/resources/assets/gtsr/liminal'/code/'models/rig_animation.json')['animations']) for code in active}
    require(all(clip_counts.values()), 'Each active model has authored clips')
    with zipfile.ZipFile(product) as archive:
        obj = [n for n in archive.namelist() if n.startswith('assets/gtsr/liminal/') and n.endswith('.obj')]
        compiled = [n for n in archive.namelist() if n.startswith('assets/gtsr/liminal/') and n.endswith('.rigmesh')]
        expected = {'assets/gtsr/liminal/'+code+'/models/'+code+'.rigmesh' for code in active}
        require(not obj and set(compiled) == expected, 'Actual runtime: no source OBJ and exact manifest mesh closure')
        require(archive.read('assets/gtsr/liminal/active-models.txt') == active_manifest.read_bytes(), 'Runtime manifest equals source authority')
    with zipfile.ZipFile(legacy_jar) as archive:
        legacy_bytes = archive.read('com/miaokatze/gtsr/client/encounter/RigAsset.class')
    reference, replacement_count = rename_legacy(legacy_bytes)
    identity = dict(sources={str(p):sha(p) for p in sources}, dependencies={str(p):sha(p) for p in dependencies},
                    legacy_jar=str(legacy_jar), legacy_jar_sha256=sha(legacy_jar),
                    current_jar=str(product), current_jar_sha256=sha(product), golden_sha256=sha(golden),
                    active_manifest_sha256=sha(active_manifest), active_models=active, clip_counts=clip_counts,
                    spawn_clearance_reference=str(clearance_reference),spawn_clearance_sha256=sha(clearance_reference))
    fingerprint = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
    out = OUT/'native'/fingerprint[:16]; classes = out/'classes'; classes.mkdir(parents=True, exist_ok=True)
    options = ['--release','8','-Xlint:-options','-encoding','UTF-8','-proc:none','-cp',';'.join(map(str,dependencies)),'-d',str(classes),*map(str,sources)]
    argfile = out/'javac.args'; argfile.write_text('\n'.join(json.dumps(a, ensure_ascii=False) for a in options)+'\n',encoding='utf-8')
    javac = Path(base['java']).with_name('javac.exe')
    process = subprocess.run([str(javac),'@'+str(argfile)],capture_output=True)
    (out/'compile.log').write_bytes(process.stdout+process.stderr)
    require(process.returncode == 0, 'Native Case javac failed: '+str(out/'compile.log'))
    jar = out/'gtsr-rigmesh-qa.jar'
    def put(archive, name, content):
        info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,content)
    with zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(classes.rglob('*.class')):
            require(struct.unpack_from('>H',path.read_bytes(),6)[0] == 52, 'Case Java8 bytecode')
            put(archive,path.relative_to(classes).as_posix(),path.read_bytes())
        put(archive,'com/miaokatze/gtsr/client/encounter/RigAssetLegacyReference.class',reference)
        put(archive,'rigmesh-reference/golden.tsv',golden.read_bytes())
        put(archive,'rigmesh-reference/active-models.txt',active_manifest.read_bytes())
        put(archive,'rigmesh-reference/clip-counts.json',json.dumps(clip_counts,sort_keys=True).encode())
        put(archive,'dimension-trim-reference/prefab-spawn-clearance.json',clearance_reference.read_bytes())
        for code,path in originals.items():
            put(archive,'rigmesh-reference/'+code+'.obj',path.read_bytes())
        put(archive,'mcmod.info',json.dumps([dict(modid='gtsr_rigmesh_qa',name='Owned rigmesh framebuffer comparison',version='1')]).encode())
    formal = read(RELEASE/'formal-tags-and-artifacts.json')
    ids = {'core':'miaokatzecore','gtit':'gtit','gtswn':'gtswn','gtsr':'gtsr','gtmb':'gtmb'}
    products = []
    for row in formal['projects']:
        runtime = next(a for a in row['artifacts'] if a['role']=='runtime')
        path = product if row['project']=='gtsr' else Path(runtime['retained_path'])
        modid = ids[row['project']]
        products.append(dict(modid=modid,version=metadata(path,modid),path=str(path),sha256=sha(path)))
    manifest = dict(products=products,extras=[],case_jars=[dict(modid='gtsr_rigmesh_qa',version='1',path=str(jar),sha256=sha(jar))],
                    client_cases=['coreglobal.followup.qa.RigMeshNativeCase','coreglobal.followup.qa.DimensionTrimNativeCase'],server_cases=[])
    save(out/'manifest.json',manifest)
    save(out/'control.json',dict(holdUntilStop=False,configOverrides=[],console=dict(stopAfter=False,commands=[])))
    receipt = dict(status='NATIVE_CASE_COMPILED_NOT_LAUNCHED',jar=str(jar),sha256=sha(jar),manifest=str(out/'manifest.json'),control=str(out/'control.json'),
                   identity=identity,legacy_original_class_sha256=hashlib.sha256(legacy_bytes).hexdigest(),
                   legacy_renamed_class_sha256=hashlib.sha256(reference).hexdigest(),constant_pool_self_name_replacements=replacement_count,
                   class_instruction_bytes_unchanged=True,reference_source_models=len(rows),active_models=len(active_rows),
                   authored_clips=sum(clip_counts.values()),expected_frame_pairs=sum(clip_counts.values())*2,
                   collision_reference_nodes=len(clearance['nodes']),collision_reference_resources_verified=len(clearance['resources']),
                   preexisting_declared_hash_mismatches=observed_declared_mismatches,
                   framebuffer=dict(width=192,height=192,view='controlled fixed orthographic',lighting=False,depth_test=False),
                   base_qa_unchanged=sha(base['jar'])==base['sha256'],instance_mutation=False,game_launch=False,compile_exit=process.returncode)
    save(out/'build-receipt.json',receipt);save(OUT/'native-current.json',receipt)
    print(json.dumps(receipt,ensure_ascii=False,indent=2))

if __name__=='__main__':
    main()
