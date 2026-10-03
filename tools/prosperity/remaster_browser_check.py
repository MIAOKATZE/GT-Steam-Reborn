"""Check the three refining Boss scenes and separate paused design introductions."""
import argparse
import json
from pathlib import Path
from playwright.sync_api import sync_playwright
from export_remaster_preview import rollout_policy

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/remaster-browser-check'


def validate_rollout_bundle(bundle):
    policy = rollout_policy()
    assert bundle['rollout'] == policy, 'Preview policy differs from runtime source'
    models = bundle['structures']
    assert [m.get('productionId') for m in models] == policy['activeIds'], 'Unexpected active scene selection'
    assert all(m.get('productionVariant') == policy['standardVariant']
               and m.get('reviewStatus') == 'refining' for m in models), 'Nonstandard or accepted scene'
    assert len({m['id'] for m in models}) == len(models), 'Duplicate scene'
    catalog = json.loads((ROOT / 'src/main/resources/assets/gtsr/remaster/catalog.json').read_text(encoding='utf8'))
    expected_paused = {d['id'] for d in catalog['structures']} - set(policy['activeIds'])
    paused = bundle['pausedDesigns']
    assert {d['id'] for d in paused} == expected_paused, 'Incomplete paused design introductions'
    assert len(paused) == len(expected_paused), 'Duplicate paused design introduction'
    assert all(d.get('name') and d.get('category') and d.get('status') == 'paused-needs-simplification'
               for d in paused), 'Paused design lacks introduction or status'
    snapshots = []
    for scene in models:
        for sample in scene.get('sampleSnapshots', []):
            assert scene['productionId'] == 'forgotten_lake_court', 'Actual tree snapshot belongs only to giant-tree scene'
            assert sample.get('productionId') == scene['productionId'], 'Unowned snapshot'
            assert sample.get('geometryMode') == 'production-royal-route-snapshot', 'Unexpected city or historical snapshot'
            assert sample.get('reviewStatus') == 'refining', 'Snapshot cannot imply acceptance'
            snapshots.append(sample)
    assert len(snapshots) == 1, 'Expected attached actual giant-tree route sample'
    return policy, models, paused, snapshots


def select_preview(page, models, model):
    """Use actual selector and sample buttons without adding a fourth scene."""
    page.locator('[data-tab="structures"]').click()
    page.locator('#search').fill('')
    page.locator('#category').select_option('')
    scene = next(m for m in models if m['productionId'] == model['productionId'])
    page.locator('#structure-list button').nth(models.index(scene)).click()
    page.wait_for_function("id=>document.getElementById('stats').dataset.modelId===id", arg=scene['id'], timeout=120000)
    if model['id'] != scene['id']:
        index = next(i for i, sample in enumerate(scene.get('sampleSnapshots', [])) if sample['id'] == model['id'])
        page.locator('#model-facts button').nth(index).click()
        page.wait_for_function("id=>document.getElementById('stats').dataset.modelId===id", arg=model['id'], timeout=120000)
    assert page.locator('#structure-list button').count() == len(models), 'Attached sample expanded scene picker'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=OUT)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    errors, results = [], []
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path='C:/Program Files/Google/Chrome/Application/chrome.exe',
                                   headless=True, args=['--allow-file-access-from-files', '--disable-background-networking'])
        page = browser.new_page(viewport={'width': 1440, 'height': 1000})
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.goto((ROOT / 'plan/prosperity/index.html').as_uri(), wait_until='load')
        page.wait_for_function("window.PROSPERITY_STRUCTURES && window.ProsperityPlayerView", timeout=120000)
        policy, models, paused, snapshots = validate_rollout_bundle(page.evaluate('window.PROSPERITY_STRUCTURES'))
        page.locator('[data-tab="structures"]').click()
        assert page.locator('#structure-list button').count() == len(models)
        assert '打磨中' in page.locator('#count').inner_text()
        for model in models + snapshots:
            select_preview(page, models, model)
            assert page.locator('#material-warning').count() == 0, model['id']
            views = model.get('playerReviewViews', [])
            assert views, model['id'] + ' missing local review views'
            page.evaluate("v=>window.ProsperityPlayerView.get(document.getElementById('structure-canvas')).setPlayerView(v.eye,v.target)", views[0])
            page.evaluate("()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))")
            page.locator('#structure-canvas').screenshot(path=str(out / (model['id'] + '.png')))
            results.append({'id': model['id'], 'productionId': model['productionId'], 'stats': page.locator('#stats').inner_text(),
                            'view': views[0], 'attachedSample': model in snapshots, 'minecraftRuntime': False})
            page.locator('#reset').click()
        page.locator('[data-tab="designs"]').click()
        assert page.locator('#structure-panel').is_hidden(), 'Paused designs expose active 3D controls'
        assert page.locator('#cards .card').count() == len(paused)
        intro = page.locator('#cards').inner_text()
        assert all(d['id'] in intro and d['name'] in intro for d in paused), 'Missing rendered design introductions'
        assert '待大幅简化减量' in intro
        for tab in ['biomes', 'entities', 'items', 'blocks', 'story']:
            page.locator('[data-tab="' + tab + '"]').click()
            if tab == 'items':
                assert '38份真实原件' not in page.locator('#cards').inner_text(), 'Paused complex engineering guide remains active'
        assert not errors, errors
        report = {'viewer': 'Chrome offline file URI', 'rollout': policy, 'activeStandardScenes': len(models),
                  'attachedSamples': len(snapshots), 'pausedDesignIntroductions': len(paused), 'samples': results,
                  'productionReviewViews': sum(len(m.get('playerReviewViews', [])) for m in models),
                  'snapshotReviewViews': sum(len(m.get('playerReviewViews', [])) for m in snapshots),
                  'pageErrors': errors, 'minecraftRuntime': False, 'designAccepted': False}
        (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')
        print(json.dumps(report, ensure_ascii=False))
        browser.close()


if __name__ == '__main__':
    main()
