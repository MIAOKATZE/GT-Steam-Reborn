"""Exercise the released offline viewer in Chrome without starting Minecraft."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/remaster-browser-check'
OUT.mkdir(parents=True, exist_ok=True)
errors = []
samples = ['fallen_foundry-r7-v0', 'subsided_factory-r7-v0',
           'fiction_expansion_project-r7-v2', 'forgotten_lake_court-r7-v0',
           'city_manor_ruin-r7-v0']
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path='C:/Program Files/Google/Chrome/Application/chrome.exe',
                               headless=True, args=['--allow-file-access-from-files',
                                                    '--disable-background-networking'])
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.on('pageerror', lambda error: errors.append(str(error)))
    page.goto((ROOT / 'plan/prosperity/index.html').as_uri(), wait_until='load')
    page.wait_for_function("document.getElementById('hero-stats').textContent.includes('体素')", timeout=120000)
    page.locator('[data-tab="structures"]').click()
    ids = page.evaluate('window.PROSPERITY_STRUCTURES.structures.map(m=>m.id)')
    assert len(ids) == 357, len(ids)
    available = [i for i in samples if i in ids]
    # Names are authored by the frozen plan; match category when an ID changes.
    for prefix in ['subsided_factory', 'fiction', 'forgotten_lake_court', 'city_']:
        if not any(i.startswith(prefix) for i in available):
            available.append(next(i for i in ids if i.startswith(prefix) and '-r7-' in i))
    results = []
    for model_id in available:
        page.locator('#search').fill('')
        page.locator('#category').select_option('')
        page.locator('#structure-list button').nth(ids.index(model_id)).click()
        page.wait_for_function("id=>document.getElementById('stats').dataset.modelId===id",
                               arg=model_id, timeout=120000)
        assert page.locator('#material-warning').count() == 0, model_id
        stats = page.locator('#stats').inner_text()
        page.locator('#projection').click()
        page.locator('#cut-axis').select_option('1')
        page.locator('#cut-axis').dispatch_event('input')
        page.locator('#cut').evaluate("e=>{e.value=Number(e.max)-2;e.dispatchEvent(new Event('input'));}")
        page.locator('#structure-canvas').screenshot(path=str(OUT / (model_id + '.png')))
        page.locator('#reset').click()
        results.append({'id': model_id, 'stats': stats})
    for tab in ['biomes', 'entities', 'items', 'blocks', 'chapters']:
        if page.locator('[data-tab="' + tab + '"]').count():
            page.locator('[data-tab="' + tab + '"]').click()
    assert not errors, errors
    report = {'viewer': 'Chrome offline file URI', 'models': len(ids), 'samples': results,
              'pageErrors': errors, 'minecraftRuntime': False}
    (OUT / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))
    browser.close()
