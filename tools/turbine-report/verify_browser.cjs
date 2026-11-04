/* Exercise the real file:// page in installed headless Chrome; no npm packages. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {spawn} = require('node:child_process');

const root = path.resolve(__dirname, '../..');
const report = path.join(root, 'plan/临时计划/20261103-动力阵列与巨型轮机重构预览/performance.html');
const browser = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'gtsr-report-browser-'));
const artifactDirectory = process.argv[2] ? path.resolve(process.argv[2]) : path.join(temporary, 'artifacts');
fs.mkdirSync(artifactDirectory, {recursive: true});
const profile = path.join(temporary, 'profile');
const chrome = spawn(browser, ['--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--disable-background-networking', '--disable-component-update', '--disable-extensions', '--disable-sync',
  '--metrics-recording-only', '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'],
{stdio: 'ignore', windowsHide: true});
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let websocket;

async function main() {
  const portFile = path.join(profile, 'DevToolsActivePort');
  for (let attempt = 0; !fs.existsSync(portFile); attempt++) {
    if (attempt > 100) throw new Error('Chrome DevTools did not start');
    await pause(100);
  }
  const [port, endpoint] = fs.readFileSync(portFile, 'utf8').trim().split(/\r?\n/);
  websocket = new WebSocket(`ws://127.0.0.1:${port}${endpoint}`);
  await new Promise((resolve, reject) => { websocket.onopen = resolve; websocket.onerror = reject; });
  let nextId = 1;
  const pending = new Map();
  const exceptions = [];
  const externalRequests = [];
  websocket.onmessage = message => {
    const data = JSON.parse(message.data);
    if (data.id && pending.has(data.id)) {
      const {resolve, reject, timeout} = pending.get(data.id);
      clearTimeout(timeout); pending.delete(data.id);
      if (data.error) reject(new Error(JSON.stringify(data.error))); else resolve(data.result);
    }
    if (data.method === 'Runtime.exceptionThrown') exceptions.push(data.params.exceptionDetails);
    if (data.method === 'Network.requestWillBeSent' && /^https?:/.test(data.params.request.url)) externalRequests.push(data.params.request.url);
  };
  const send = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
    const id = nextId++;
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 10000);
    pending.set(id, {resolve, reject, timeout});
    websocket.send(JSON.stringify({id, method, params, ...(sessionId ? {sessionId} : {})}));
  });
  const {targetId} = await send('Target.createTarget', {url: 'about:blank'});
  const {sessionId} = await send('Target.attachToTarget', {targetId, flatten: true});
  const tab = (method, params) => send(method, params, sessionId);
  const evaluate = async expression => {
    const {result, exceptionDetails} = await tab('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    assert.equal(exceptionDetails, undefined, JSON.stringify(exceptionDetails));
    return result.value;
  };
  await tab('Runtime.enable'); await tab('Network.enable'); await tab('Page.enable');
  await tab('Emulation.setDeviceMetricsOverride', {width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false});
  await tab('Page.navigate', {url: pathToFileURL(report).href});
  for (let attempt = 0; attempt < 100; attempt++) {
    if (await evaluate("document.querySelectorAll('#heavy-table tbody tr').length === 9")) break;
    await pause(100);
  }
  assert.equal(await evaluate("document.querySelectorAll('#mega-table tbody tr').length"), 12);
  assert.equal(await evaluate("document.querySelectorAll('#kpa-tier-table tbody tr').length"), 13);
  assert.match(await evaluate("document.getElementById('mega-cards').textContent"), /240 EU\/t/);
  const baseline = await evaluate("document.getElementById('mega-cards').textContent");
  assert.match(baseline, /7\.5 A/);
  const heavyBaseline = await evaluate("JSON.parse(document.getElementById('report-data').textContent).heavy");
  assert.deepEqual(heavyBaseline.filter(row => row[5] === 100).map(row => [row[7], row[8], row[4]]), [[320, 125, 75], [1536, 150, 100], [2048, 200, 150]]);
  assert.deepEqual(heavyBaseline.filter(row => row[5] === 25).map(row => [row[7], row[8]]), [[64, 100], [256, 100], [256, 100]]);
  const expectedCounts = {tier: 12, material: 3, extra: 5, steam: 6, power: 5, mode: 3, chip: 2};
  for (const [axis, count] of Object.entries(expectedCounts)) {
    await evaluate(`document.getElementById('mega-axis').value=${JSON.stringify(axis)}; document.getElementById('mega-axis').dispatchEvent(new Event('change',{bubbles:true}));`);
    assert.equal(await evaluate("document.querySelectorAll('#mega-table tbody tr').length"), count, axis);
    assert.equal(await evaluate("document.querySelectorAll('#mega-table tbody tr.current').length"), 1, axis);
  }
  await evaluate("document.getElementById('mega-steam').value='5'; document.getElementById('mega-steam').dispatchEvent(new Event('change',{bubbles:true}));");
  assert.match(await evaluate("document.getElementById('mega-notice').textContent"), /不可运行/);
  assert.match(await evaluate("document.getElementById('mega-cards').textContent"), /未解锁/);
  await evaluate("document.getElementById('mega-chip').value='1'; document.getElementById('mega-chip').dispatchEvent(new Event('change',{bubbles:true}));");
  await evaluate("document.getElementById('preset-base').click(); document.getElementById('mega-chip').value='1'; document.getElementById('mega-chip').dispatchEvent(new Event('change',{bubbles:true}));");
  assert.match(await evaluate("document.getElementById('mega-notice').textContent"), /需额外 4 组/);
  await evaluate("document.getElementById('preset-max').click();");
  const maximum = await evaluate("document.getElementById('mega-cards').textContent");
  assert.match(await evaluate("document.getElementById('mega-notice').textContent"), /循环超限已激活/);
  assert.equal(await evaluate("document.querySelectorAll('#mega-table tbody tr.unavailable').length"), 0);
  const sourceSample = await evaluate(`JSON.parse(document.getElementById('report-data').textContent).mega.find(r=>r.slice(0,7).join('/')==='12/3/4/5/10/2/1')`);
  assert(BigInt(sourceSample[10]) > 2147483647n, 'High-tier output must preserve the long path');
  assert.match(maximum, new RegExp(BigInt(sourceSample[10]).toLocaleString('en-US')));
  await evaluate("document.getElementById('kpa-tier').value='3';document.getElementById('kpa-machine').value='2';document.getElementById('kpa-boost').value='1';document.getElementById('kpa-controls').dispatchEvent(new Event('change',{bubbles:true}));");
  const boosted = await evaluate("document.getElementById('kpa-cards').textContent");
  assert.match(boosted, /512 EU\/t/); assert.match(boosted, /2 L\/EU/); assert.match(boosted, /69/);
  await evaluate("document.getElementById('kpa-tier').value='1';document.getElementById('kpa-controls').dispatchEvent(new Event('change',{bubbles:true}));");
  assert.match(await evaluate("document.getElementById('kpa-cards').textContent"), /32 EU\/t/);
  for (const [input, expected] of [['0', '1'], ['99', '64'], ['1.7', '2']]) {
    await evaluate(`document.getElementById('kpa-count').value='${input}';document.getElementById('kpa-count').dispatchEvent(new Event('change',{bubbles:true}));`);
    assert.equal(await evaluate("document.getElementById('kpa-count').value"), expected);
  }
  const screenshot = async name => {
    const {data} = await tab('Page.captureScreenshot', {format: 'png'});
    fs.writeFileSync(path.join(artifactDirectory, name), Buffer.from(data, 'base64'));
  };
  await screenshot('desktop-max.png');
  await tab('Emulation.setDeviceMetricsOverride', {width: 390, height: 1000, deviceScaleFactor: 1, mobile: true});
  await evaluate("document.getElementById('preset-base').click();window.scrollTo(0,0)");
  await screenshot('mobile-base.png');
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false, 'Mobile page must not overflow horizontally');
  assert.deepEqual(exceptions, []);
  assert.deepEqual(externalRequests, []);
  const evidence = {status: 'PASS', browser: await send('Browser.getVersion'),
    checks: ['file:// load', 'all seven comparison axes', 'LV unavailable high-tier steam', 'chip group gate',
      'UMV long output', 'BigInt amp conversion', 'KPA boost and casing cap', 'KPA quantity bounds',
      '9 Heavy source samples', '390px mobile layout', 'zero runtime exceptions', 'zero HTTP(S) page requests'],
    baseline, maximum, boosted, sourceSample, screenshots: ['desktop-max.png', 'mobile-base.png']};
  fs.writeFileSync(path.join(artifactDirectory, 'evidence.json'), JSON.stringify(evidence, null, 2));
  console.log(`PASS: real headless Chrome file:// interactions; evidence: ${artifactDirectory}`);
  await send('Browser.close');
}

main().catch(error => { console.error(error); process.exitCode = 1; }).finally(() => {
  if (websocket) websocket.close();
  chrome.kill();
});
