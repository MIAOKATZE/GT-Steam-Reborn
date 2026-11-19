// SPDX-License-Identifier: AGPL-3.0-only
// Export ore rules and routed graphs; the native controller resolves recipes at runtime.
const fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const root = process.cwd();
const project = path.join(root,'plan',fs.readdirSync('plan').find(name => fs.existsSync(path.join('plan',name,'graph-generator-presets.js'))));
const context = vm.createContext({ console, structuredClone, atob, TextDecoder, TextEncoder, Uint8Array, performance, setTimeout, clearTimeout });
context.window = context;
const names = ['ore-registry.js','ore-center-catalog.js','ore-locale.js','graph-outlets.js','graph-ore-feed.js','graph-rule-catalog.js','graph-generator-presets.js','graph-generator-graph.js','terminal-trial.js'];
const apps=fs.readFileSync(path.join(project,'app.js'),'utf8');
vm.runInContext(apps.split('  const reviewSlots =')[0]+'})();',context);
for (const file of names) vm.runInContext(fs.readFileSync(path.join(project,file),'utf8'),context,{filename:file});
const processes = {
  crusher:'CRUSHER', hammer:'HAMMER', washer:'WASHER', simpleWasher:'SIMPLE_WASHER', chemicalbath:'CHEMICAL_BATH',
  sifter:'SIFTER', centrifuge:'CENTRIFUGE', centrifugeSeparation:'SEPARATION', thermalCentrifuge:'THERMAL_CENTRIFUGE',
  electrolyzer:'ELECTROLYZER', dryer:'DRYER', smelting:'SMELTING',
};
const assert = require('node:assert/strict');
const groups = context.oreGeneratorPresetGroups;
const rawExclusion = groups.find(group => group.id === 'special-None').entries[0].expression;
const ruleValue = rule => rule.kind === 'item' ? 'item:' + rule.key : rule.name;
const result = groups.map(group => {
  const {graph,warnings}=context.oreGeneratorGraph.build(group);
  const result={id:group.id,label:group.label,category:group.category,description:group.description,
    expressions:group.entries.map(({expression,label})=>({expression,label})), warnings};
  if (!graph) return result;
  const names=new Map(graph.nodes.map((node,index)=>[node.id,index]));
  const target = value => value === context.oreOutlets.BUS ? -1 : names.get(value);
  assert.equal(names.get(graph.entry),0);
  result.rules=graph.entryRules.map(ruleValue);
  result.entries=graph.entryBranches.map(({target: node,rule})=>[names.get(node),ruleValue(rule)]);
  result.exclusions=graph.entryExclusions.length ? [
    rawExclusion,
    '^(?:crushed(?:Purified)?|dust(?:Impure|Pure))(?:' + graph.entryExclusions.join('|') + ')$',
  ] : [];
  result.nodes=graph.nodes.map((node,index)=>{
    const process=processes[node.module];
    assert.ok(process,node.module);
    const ports=context.oreOutlets.ports(node).slice();
    if (node.module === 'simpleWasher') ports.splice(1,0,{target:context.oreOutlets.BUS});
    const links=ports.map(port=>target(port.target));
    assert.ok(links.every(link=>Number.isInteger(link) && (link===-1 || link>index)),node.id);
    const filters=ports.flatMap((port,slot)=>port.filter ? [[slot,port.filter.mode==='allow',port.filter.rules.map(ruleValue)]] : []);
    return {process,links,filters};
  });
  return result;
});
const output = path.join(root,'src/main/resources/assets/gtsr/orecenter/generator-presets.json');
fs.writeFileSync(output, JSON.stringify(result,null,0) + '\n', 'utf8');
console.log('Exported',result.length,'generator groups; graphs:',result.filter(group=>group.nodes).length);
