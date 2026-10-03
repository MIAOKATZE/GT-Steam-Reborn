"""Capture eye-height evidence from real exported voxels; no Minecraft physics/AI claims."""
import argparse
import html
import hashlib
import gzip
import json
from pathlib import Path
from playwright.sync_api import sync_playwright
from remaster_browser_check import validate_rollout_bundle, select_preview

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/remaster-pov-check'


def main():
    global OUT
    parser = argparse.ArgumentParser()
    parser.add_argument('--all-variants', action='store_true', help='Unsupported after containment; only runtime standard scenes are active.')
    parser.add_argument('--limit', type=int, default=0)
    parser.add_argument('--resume', action='store_true', help='Keep completed model evidence and capture remaining models.')
    parser.add_argument('--out', type=Path, default=OUT, help='Capture directory; use a new directory to retain historical evidence.')
    args = parser.parse_args()
    if args.all_variants:
        parser.error('--all-variants is unavailable: contained rollout has only standard scenes')
    if args.limit < 0:
        parser.error('--limit must be nonnegative')
    OUT = args.out.resolve()
    OUT.mkdir(parents=True, exist_ok=True)
    errors, results, findings = [], [], []
    if args.resume and (OUT / 'report.json').exists():
        previous = json.loads((OUT / 'report.json').read_text(encoding='utf8'))
        results, findings = previous['samples'], previous['findings']
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path='C:/Program Files/Google/Chrome/Application/chrome.exe',
                                   headless=True, args=['--allow-file-access-from-files',
                                                        '--disable-background-networking'])
        page = browser.new_page(viewport={'width': 1280, 'height': 960})
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto((ROOT / 'plan/prosperity/index.html').as_uri(), wait_until='load')
        page.wait_for_function("window.PROSPERITY_STRUCTURES && window.ProsperityPlayerView", timeout=120000)
        page.locator('[data-tab="structures"]').click()
        policy, models, paused, snapshots = validate_rollout_bundle(page.evaluate('window.PROSPERITY_STRUCTURES'))
        args.rollout = policy
        args.pausedDesignIntroductions = len(paused)
        selected = models + snapshots
        if args.limit:
            selected = selected[:args.limit]
        args.selectedModelIds = [m['id'] for m in selected]
        args.selectedProductionIds = sorted({m['productionId'] for m in selected})
        args.activeStandardScenes = len(models)
        args.attachedSamples = len(snapshots)
        viewer_hash = hashlib.sha256()
        viewer_paths = [ROOT / 'plan/prosperity' / relative for relative in
                        ('renderer.js', 'player-view.js', 'app.js', 'index.html', 'styles.css', 'style.css', 'structures/remaster-index.js')]
        viewer_paths += sorted((ROOT / 'plan/prosperity/textures').rglob('*'))
        viewer_paths += [ROOT / relative for relative in
                         ('tools/prosperity/export_remaster_preview.py', 'tools/prosperity/remaster_pov_check.py',
                          'tools/prosperity/remaster_browser_check.py', 'plan/prosperity/tools/catalog_export.py')]
        viewer_paths += sorted((ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity').rglob('*.java'))
        viewer_paths += sorted((ROOT / 'src/main/resources/assets/gtsr/textures/blocks').rglob('*.png'))
        viewer_paths += sorted((ROOT / 'build/resources/patchedMc/assets/minecraft/textures/blocks').rglob('*.png'))
        for path in viewer_paths:
            relative = path.relative_to(ROOT).as_posix()
            if path.is_file():
                viewer_hash.update(relative.encode('utf8'))
                viewer_hash.update(path.read_bytes())
        def fingerprint(model):
            digest = hashlib.sha256(viewer_hash.digest())
            digest.update(json.dumps(model, sort_keys=True, ensure_ascii=False,
                                    separators=(',', ':')).encode('utf8'))
            digest.update((ROOT / 'plan/prosperity' / model['src']).read_bytes())
            source = model.get('source', {})
            if source.get('file'):
                source_path = ROOT / source['file']
                source_bytes = source_path.read_bytes()
                digest.update(source['file'].encode('utf8'))
                digest.update(source_bytes)
                if source_path.suffix == '.gz':
                    raw = gzip.decompress(source_bytes)
                    assert hashlib.sha256(raw).hexdigest() == source['sha256'], model['id'] + ' source manifest SHA'
                    manifest = json.loads(raw)
                    for chunk in manifest['slices']:
                        chunk_path = ROOT / 'src/main/resources/assets/gtsr/remaster' / chunk['file']
                        chunk_bytes = chunk_path.read_bytes()
                        assert hashlib.sha256(gzip.decompress(chunk_bytes)).hexdigest() == chunk['sha256'], chunk['file']
                        digest.update(chunk['file'].encode('utf8'))
                        digest.update(chunk_bytes)
            if source.get('authorFile'):
                author_bytes = (ROOT / source['authorFile']).read_bytes()
                assert hashlib.sha256(author_bytes).hexdigest() == source['authorSha256'], model['id'] + ' actual author SHA'
                digest.update(author_bytes)
            if source.get('captureFile'):
                digest.update((ROOT / source['captureFile']).read_bytes())
            return digest.hexdigest()
        current = {m['id']: fingerprint(m) for m in selected}
        current_views = {m['id']: {v['id'] for v in m.get('playerReviewViews', [])} for m in selected}
        # A resumed screenshot is evidence only for the exact exported model and view set.
        results = [r for r in results if r.get('modelFingerprint') == current.get(r['id'])
                   and {v['id'] for v in r['views']} == current_views.get(r['id'])
                   and all((OUT / v['screenshot']).exists() for v in r['views'])]
        completed = {r['id'] for r in results}
        findings = [f for f in findings if f['model'] in completed]
        captures = 0
        for model in selected:
            model_id = model['id']
            if model_id in completed:
                continue
            # Releasing the page also releases model script caches and WebGL allocations.
            # Long all-variant runs otherwise retain every previously loaded voxel array.
            if captures and captures % 8 == 0:
                page.close()
                page = browser.new_page(viewport={'width': 1280, 'height': 960})
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.goto((ROOT / 'plan/prosperity/index.html').as_uri(), wait_until='load')
                page.wait_for_function("window.PROSPERITY_STRUCTURES && window.ProsperityPlayerView", timeout=120000)
                page.locator('[data-tab="structures"]').click()
            views = model.get('playerReviewViews', [])
            if not views:
                findings.append({'model': model_id, 'issue': 'missing-eye-height-views'})
                continue
            select_preview(page, models, model)
            assert page.locator('#material-warning').count() == 0, model_id
            record = {'id': model_id, 'productionId': model.get('productionId'),
                      'attachedSample': model in snapshots,
                      'modelFingerprint': current[model_id],
                      'geometryMode': model.get('geometryMode'),
                      'interactionApproachCoverage': model.get('interactionApproachCoverage'),
                      'interactionApproaches': model.get('interactionApproaches', []),
                      'productionInteractionApproaches': model.get('productionInteractionApproaches', []),
                      'reviewCoverage': model.get('reviewCoverage'),
                      'absentViews': model.get('playerReviewAbsentViews', []), 'views': []}
            for index, view in enumerate(views):
                page.evaluate("v=>window.ProsperityPlayerView.get(document.getElementById('structure-canvas')).setPlayerView(v.eye,v.target)", view)
                page.evaluate("()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))")
                camera = page.evaluate("()=>window.ProsperityPlayerView.get(document.getElementById('structure-canvas')).playerCamera")
                assert camera['eye'] == view['eye'], model_id + ' camera'
                assert not page.evaluate("()=>window.ProsperityPlayerView.get(document.getElementById('structure-canvas')).ortho"), model_id
                filename = f'{model_id}-{index:02d}.png'
                page.locator('#structure-canvas').screenshot(path=str(OUT / filename))
                evidence = {'id': view['id'], 'label': view['label'], 'eye': view['eye'],
                            'target': view['target'], 'screenshot': filename,
                            'floor': view.get('floor'), 'headClearance': view.get('headClearance'),
                            'lineOfSight': view.get('lineOfSight'), 'targetId': view.get('targetId'),
                            'role': view.get('role'), 'sourceSha256': view.get('sourceSha256'),
                            'productionDerived': view.get('productionDerived', False),
                            'targetBlock': view.get('targetBlock')}
                if view.get('lineOfSight') == 'occluded':
                    findings.append({'model': model_id, 'view': view['id'], 'targetId': view.get('targetId'),
                                     'screenshot': filename, 'issue': 'authored-target-occluded-review-required'})
                # Low information frames identify inspection candidates; they do not prove a design fault.
                try:
                    from PIL import Image, ImageStat
                    with Image.open(OUT / filename) as image:
                        crop = image.convert('RGB').crop((image.width//4, image.height//4,
                                                         image.width*3//4, image.height*3//4)).resize((64, 64))
                        evidence['centralRgbStddev'] = [round(s, 3) for s in ImageStat.Stat(crop).stddev]
                        if max(evidence['centralRgbStddev']) < 8:
                            findings.append({'model': model_id, 'view': view['id'], 'screenshot': filename,
                                             'issue': 'low-information-center-review-required'})
                except ImportError:
                    evidence['imageMetric'] = 'Pillow unavailable'
                record['views'].append(evidence)
            results.append(record)
            captures += 1
            # Preserve progress if a large GPU asset later fails or times out.
            write_report(results, findings, errors, args)
            print(f'captured {model_id}: {len(views)} local views', flush=True)
        browser.close()
    assert not errors, errors
    assert {r['productionId'] for r in results} == set(args.selectedProductionIds), 'Incomplete selected scene evidence'
    assert {r['id'] for r in results} == set(args.selectedModelIds), 'Incomplete current model evidence'
    report = write_report(results, findings, errors, args)
    print(json.dumps({k: report[k] for k in ('models', 'screenshots', 'uniqueProductionIds')}
                     | {'reviewCandidates': len(report['findings'])}, ensure_ascii=False))


def write_report(results, findings, errors, args):
    report = {'viewer': 'Chrome offline file URI / perspective eye-height',
              'minecraftRuntime': False, 'standardScenesOnly': True,
              'rollout': args.rollout, 'activeStandardScenes': args.activeStandardScenes,
              'attachedSamples': args.attachedSamples, 'pausedDesignIntroductions': args.pausedDesignIntroductions,
              'selectedModelIds': args.selectedModelIds, 'partialCapture': bool(args.limit),
              'models': len(results), 'screenshots': sum(len(r['views']) for r in results),
              'uniqueProductionIds': len({r['productionId'] for r in results if r['productionId']}),
              'productionControlScreenshots': sum(v.get('role') == 'site-controller'
                                                   for r in results if not r.get('attachedSample') for v in r['views']),
              'snapshotControlScreenshots': sum(v.get('role') == 'site-controller'
                                                 for r in results if r.get('attachedSample') for v in r['views']),
              'productionSurfaceEntranceScreenshots': sum(v.get('role') == 'surface-entrance'
                                                           for r in results if not r.get('attachedSample') for v in r['views']),
              'samples': results, 'findings': findings, 'pageErrors': errors,
              'limits': ['Screenshots verify exported geometry and camera pose only.',
                         'Blankness/damage/visible guidance require visual review of the cited frame.',
                         'Interactability, loot outcomes, enemy AI, natural terrain/tree integration require production runtime evidence.'],
              'designAccepted': False}
    (OUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
    flagged = {(f['model'], f.get('view')) for f in findings}
    sections = []
    for model in results:
        figures = []
        for view in model['views']:
            label = ('待人工复查 · ' if (model['id'], view['id']) in flagged else '') + view['label']
            figures.append('<figure><a href="' + html.escape(view['screenshot']) + '"><img loading="lazy" src="'
                           + html.escape(view['screenshot']) + '"></a><figcaption>' + html.escape(label)
                           + '<br>' + html.escape(view['id']) + ' / eye=' + html.escape(str(view['eye'])) + '</figcaption></figure>')
        sections.append('<details><summary>' + html.escape(model['id']) + ' / ' + str(len(model['views']))
                        + '局部镜头</summary><div>' + ''.join(figures) + '</div></details>')
    (OUT / 'gallery.html').write_text('<!doctype html><meta charset="utf-8"><title>人物视角审查证据</title>'
                                     '<style>body{background:#152125;color:#eee;font:16px sans-serif;padding:20px}'
                                     'summary{padding:12px;cursor:pointer}details div{display:flex;flex-wrap:wrap}'
                                     'figure{width:420px;margin:8px}img{width:100%}figcaption{font-size:13px}</style>'
                                     '<h1>人物视角审查证据</h1><p>离线真实体素；未模拟敌人、AI、交互、自然岩层、水体或树体接合。'
                                     '截图完成与材质通过不等于设计通过。</p>' + ''.join(sections), encoding='utf8')
    return report


if __name__ == '__main__':
    main()
