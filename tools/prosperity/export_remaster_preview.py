"""Export the production revision-seven chunk assets into the existing offline field archive."""
import base64
import gzip
import hashlib
import json
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/gtsr'
DEST = ROOT / 'plan/prosperity'


def dump(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8', newline='\n')


def main():
    package = ASSETS / 'remaster'
    catalog = json.loads((package / 'catalog.json').read_text(encoding='utf-8'))
    block_data = json.loads((package / 'blocks.json').read_text(encoding='utf-8'))
    legacy = json.loads((DEST / 'data/catalog.json').read_text(encoding='utf-8'))
    names = {e['id']: e.get('name', e['id']) for e in legacy['structures']}
    structures = {e['id']: e for e in catalog['structures']}
    models = []
    for descriptor in catalog['prefabs']:
        stem = f"{descriptor['id']}-r7-v{descriptor['variant']}"
        manifest = json.loads(gzip.decompress((package / descriptor['file']).read_bytes()))
        palette = manifest['palette']
        runs = []
        for entry in manifest['slices']:
            data = json.loads(gzip.decompress((package / entry['file']).read_bytes()))
            runs.extend(r for r in data if palette[r[4]] != 'minecraft:air#0')
        runs.sort(key=lambda r: (r[1], r[2], r[0]))
        model_data = {'palette': palette, 'runs': runs}
        dump(DEST / f'structures/remaster/{stem}.js',
             'window.PROSPERITY_STRUCTURES_DATA=window.PROSPERITY_STRUCTURES_DATA||{};'
             f'window.PROSPERITY_STRUCTURES_DATA[{json.dumps(stem)}]='
             + json.dumps(model_data, ensure_ascii=False, separators=(',', ':')) + ';\n')
        entry = structures[descriptor['id']]
        description = [f"第七轮生产预制，方案 {descriptor['variant'] + 1}；各方案互斥。",
                       '从游戏使用的区块切片解码；此视图为离线体素，不代表实机验收。']
        if descriptor['id'] == 'fiction_expansion_project':
            description[0] = ['工程遗构', '地面构架完成', '高空扩大器闭合'][descriptor['variant']] + '；这是施工阶段。'
        models.append({'id': stem, 'name': names.get(descriptor['id'], descriptor['id']) + f" · 第七轮 {descriptor['variant'] + 1}",
                       'category': entry['category'], 'src': f'structures/remaster/{stem}.js',
                       'size': descriptor['extent'], 'origin': descriptor['min'],
                       'count': descriptor['solidCount'], 'variant': 'authored', 'description': description,
                       'source': {'file': 'src/main/resources/assets/gtsr/remaster/' + descriptor['file'],
                                  'label': '生产预制 SHA256 ' + descriptor['sha256']}})
    dump(DEST / 'structures/remaster-index.js',
         '(function(w){const previous=w.PROSPERITY_STRUCTURES.structures;'
         'for(const m of previous){m.name += " · 旧版";m.description=(m.description||[]).concat(["v1/v2历史布局，供旧存档对照。"]);}'
         'w.PROSPERITY_STRUCTURES.structures='
         + json.dumps(models, ensure_ascii=False, separators=(',', ':')) + '.concat(previous);})(window);\n')

    # The frozen authored boxes are also the input used to create RemasterBlocks.
    plan = ROOT / 'plan' / '\u4e34\u65f6\u8ba1\u5212' / 'preview'
    script = """const fs=require('fs'),vm=require('vm');const w={REMASTER_TEXTURES:{}};
const sandbox={window:w};vm.createContext(sandbox);
for(const p of process.argv.slice(1))vm.runInContext(fs.readFileSync(p,'utf8'),sandbox);
process.stdout.write(JSON.stringify(w.REMASTER_TEXTURES));"""
    result = subprocess.run(['node', '-e', script, *[str(plan / f'blocks{i}.js') for i in range(4, 8)]],
                            check=True, capture_output=True, text=True, encoding='utf-8')
    textures = json.loads(result.stdout)
    production_hashes = {hashlib.sha256(p.read_bytes()).hexdigest()
                         for p in (ASSETS / 'textures/blocks').glob('remaster_*.png')}
    for key, tex in textures.items():
        for image in [tex.get('texture'), *tex.get('faces', {}).values()]:
            if image and image.startswith('data:image/png;base64,'):
                digest = hashlib.sha256(base64.b64decode(image.split(',', 1)[1])).hexdigest()
                if digest not in production_hashes:
                    raise ValueError('Preview texture is not registered in production: ' + key)
        texture_bytes = base64.b64decode(tex['texture'].split(',', 1)[1])
        digest = hashlib.sha256(texture_bytes).hexdigest()
        tex['source'] = {'path': 'src/main/resources/assets/gtsr/textures/blocks/remaster_' + digest[:16] + '.png',
                         'sha256': digest}
    legacy_text = (DEST / 'textures/textures.js').read_text(encoding='utf-8')
    previous = json.loads(legacy_text[legacy_text.index('=') + 1:legacy_text.rindex(';')])
    for key, tex in previous.items():
        source = tex.get('source', {}).get('path', '').replace('\\', '/')
        if 'src/main/resources/' in source:
            path = ROOT / ('src/main/resources/' + source.split('src/main/resources/', 1)[1])
            if path.exists():
                blob = path.read_bytes()
                replacement = dict(tex)
                replacement['texture'] = 'data:image/png;base64,' + base64.b64encode(blob).decode('ascii')
                replacement['source'] = {'path': path.relative_to(ROOT).as_posix(),
                                         'sha256': hashlib.sha256(blob).hexdigest()}
                textures.setdefault(key, replacement)
    for block_id in ('ruin_debris_rivet_plate', 'ruined_casing_rusted'):
        path = ASSETS / 'textures/blocks' / (block_id + '.png')
        blob = path.read_bytes()
        textures['gtsr:' + block_id + '#0'] = {
            'block': 'gtsr:' + block_id, 'meta': 0, 'render': 'cube', 'faces': {},
            'texture': 'data:image/png;base64,' + base64.b64encode(blob).decode('ascii'),
            'source': {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(blob).hexdigest()}}
    dump(DEST / 'textures/remaster.js', 'Object.assign(window.PROSPERITY_TEXTURES,'
         + json.dumps(textures, ensure_ascii=False, separators=(',', ':')) + ');\n')
    blocks = [{'id': b['id'], 'name': b['name'], 'group': 'architecture', 'category': 'block',
               'description': [b.get('purpose', '')], 'registryName': 'gtsr:' + b['id'],
               'texture': 'gtsr:' + b['id'] + '#0', 'function': b.get('purpose', ''),
               'locations': '第七轮预制与功能工序', 'sources': [{'file':
                   'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterBlocks.java',
                   'label': '生产注册'}]} for b in block_data['blocks']]
    engineering = json.loads(gzip.decompress((ASSETS / 'remaster' /
        next(p['file'] for p in catalog['prefabs'] if p['id'] == 'fiction_expansion_project' and p['variant'] == 0)).read_bytes()))
    witnesses = engineering['metadata']['longPuzzle']['requiredTestimonies']
    items = [{'id': 'remaster_witness_' + code, 'name': '身份原件 · ' + code,
              'group': 'relic', 'category': 'item', 'registryName': 'gtsr:RemasterWitness7',
              'description': ['独立子类型 ' + str(i) + '；由对应遗址核发，保存身份、站点、事件与版本出处。'],
              'obtaining': '对应遗址的专用箱或实体死亡事件；普通探索箱不产出。',
              'uses': '完成七阶段工程后投入对应身份台座；38份真实原件共同解锁终局验收。',
              'sources': [{'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterWitness.java',
                           'label': '服务端出处核验及独立子类型'}]} for i, code in enumerate(witnesses)]
    terrain = [{'id': 'remaster_massif', 'name': '工厂高位岩体与缓坡', 'group': 'terrain_variant',
                'description': ['依据地下工厂实际最深体素设置至少180的生成锚点；先生成岩体，再按预制显式空气开出内部空间。',
                                '结构周边192格平滑过渡至原地形，避免工厂底部被截断。'],
                'sources': [{'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterTerrain.java', 'label': '生产地质分支'}]},
               {'id': 'remaster_terrace', 'name': '铸造厂台地与遗址整地', 'group': 'terrain_variant',
                'description': ['铸造厂采用96格过渡台地；其他遗址及新城市采用24格整地过渡。巨树王庭保留自然木叶。'],
                'sources': [{'file': 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterTerrain.java', 'label': '生产地质分支'}]}]
    dump(DEST / 'data/remaster.js', '(function(c){c.blocks.push('
         + ','.join(json.dumps(b, ensure_ascii=False) for b in blocks) + ');c.items.push('
         + ','.join(json.dumps(b, ensure_ascii=False) for b in items) + ');c.terrainVariants.push('
         + ','.join(json.dumps(b, ensure_ascii=False) for b in terrain) + ');'
         'c.meta.counts.blocks=c.blocks.length;c.meta.counts.items=c.items.length;'
         'c.meta.counts.terrainVariants=c.terrainVariants.length;'
         '})(window.PROSPERITY_CATALOG);\n')
    print(f'passed: {len(models)} production prefab previews / {len(blocks)} blocks / registered textures')


if __name__ == '__main__':
    main()
