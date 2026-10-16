#!/usr/bin/env python3
"""Turn a /gtsrtest ui JSON receipt into a local, searchable widget geometry inspector."""

import argparse
import json
from pathlib import Path


HTML = r'''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><title>GTSR UI 探针审查</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#101820;color:#e0e9ee;font:14px system-ui}
header{padding:20px;border-bottom:1px solid #344653}h1{font-size:20px;margin:0 0 8px}
.warning{color:#e8c483;line-height:1.6}.bar{display:flex;gap:12px;padding:14px;align-items:center}
input,select,button{background:#1c2b36;color:inherit;border:1px solid #506674;padding:8px;border-radius:5px}
input{width:260px}main{display:grid;grid-template-columns:300px 1fr 390px;height:calc(100vh - 175px)}
nav,aside{overflow:auto;padding:14px;border:1px solid #344653}nav button{display:block;width:100%;text-align:left;margin-bottom:6px;word-break:break-word}
nav button.active{border-color:#62dac2;background:#223e43}section{overflow:auto;padding:24px}
svg{display:block;overflow:visible;background:#1a2630;border:1px solid #6d8595;margin:12px 0}
rect{fill:#61d8c511;stroke:#61d8c5;stroke-width:.65;cursor:pointer}rect.unresolved{stroke:#c69e66;stroke-dasharray:3 2}
rect.selected{fill:#eacb7044;stroke:#fff0a0;stroke-width:2}pre{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.6}
.muted{color:#a0b6c4}label{white-space:nowrap}#summary{line-height:1.7}text{fill:#d8e9ee;font:7px system-ui;pointer-events:none}
</style>
<header><h1>GTSR UI 探针审查</h1><div class="warning">这是探针记录的控件几何与结构视图。未解析尺寸显示虚线。客户端字体、纹理、GL 画面和未运行的生命周期步骤仍未验收。</div></header>
<div class="bar"><input id="filter" placeholder="筛选机器 / 状态"><select id="zoom"><option value="1">1×</option><option value="2" selected>2×</option><option value="3">3×</option><option value="4">4×</option></select><label><input id="hidden" type="checkbox" style="width:auto">显示隐藏控件</label><span id="count" class="muted"></span></div>
<main><nav id="list"></nav><section><div id="summary"></div><svg id="canvas"></svg></section><aside><b>选中控件 / 运行证据</b><select id="node" style="width:100%;margin:12px 0" aria-label="控件树节点"></select><pre id="detail"></pre></aside></main>
<script id="receipt" type="application/json">__DATA__</script>
<script>
const report=JSON.parse(document.getElementById('receipt').textContent), results=report.results||[];
const $=id=>document.getElementById(id);let selected=results.findIndex(r=>r.details&&r.details.nodes), nodePath=null;
function list(){const q=$('filter').value.toLowerCase();$('list').replaceChildren();let count=0;
results.forEach((r,i)=>{if(!(r.id+' '+r.status).toLowerCase().includes(q))return;count++;const b=document.createElement('button');b.textContent=r.id+' · '+r.status;b.className=i===selected?'active':'';b.onclick=()=>{selected=i;nodePath=null;list();draw()};$('list').append(b)});$('count').textContent=count+' / '+results.length+' 项 · '+(report.gtsrVersion||'')}
function draw(){const r=results[selected];$('canvas').replaceChildren();if(!r){$('summary').textContent='没有 UI 探针结果';return}
const d=r.details||{},nodes=d.nodes||[],byPath=new Map(nodes.map(n=>[n.path,n]));
$('node').replaceChildren();const overview=document.createElement('option');overview.value='';overview.textContent='运行证据';$('node').append(overview);
nodes.forEach(n=>{const o=document.createElement('option');o.value=n.path;o.textContent=n.path+' · '+(n.widgetClass||n.type||n.class||'')+' · '+(n.width||0)+'×'+(n.height||0);$('node').append(o)});$('node').value=nodePath||'';
$('summary').textContent=r.id+' · '+r.status+' · '+(d.framework||'')+' · '+nodes.length+' 控件\n'+r.detail;
$('detail').textContent=JSON.stringify(nodePath?byPath.get(nodePath):{...r,details:{...d,nodes:undefined}},null,2);
function position(n,seen=new Set()){if(Number.isFinite(n.absoluteX)&&Number.isFinite(n.absoluteY))return[n.absoluteX,n.absoluteY];if(seen.has(n.path))return[0,0];seen.add(n.path);
let x=n.relativeX??n.x??0,y=n.relativeY??n.y??0;const parent=byPath.get(String(n.path).replace(/[./][^./]+$/,''));if(parent&&parent!==n){const p=position(parent,seen);x+=p[0];y+=p[1]}return[x,y]}
const root=nodes.find(n=>n.path==='0'),base=root?position(root):[0,0],w=Math.max(1,d.guiWidth||root?.width||480),h=Math.max(1,d.guiHeight||root?.height||320),scale=Number($('zoom').value),svg=$('canvas');
svg.setAttribute('viewBox',`0 0 ${w} ${h}`);svg.setAttribute('width',w*scale);svg.setAttribute('height',h*scale);
nodes.forEach(n=>{if(n.enabled===false&&!$('hidden').checked)return;const p=position(n),rect=document.createElementNS('http://www.w3.org/2000/svg','rect');
for(const[k,v]of Object.entries({x:p[0]-base[0],y:p[1]-base[1],width:Math.max(0,n.width||0),height:Math.max(0,n.height||0)}))rect.setAttribute(k,v);
rect.classList.toggle('unresolved',n.boundsEvidence!=='NATIVE_RESOLVED');rect.classList.toggle('selected',n.path===nodePath);
const title=document.createElementNS(svg.namespaceURI,'title');title.textContent=n.path+' '+(n.widgetClass||n.type||n.class||'');rect.append(title);
rect.onclick=e=>{e.stopPropagation();nodePath=n.path;draw()};svg.append(rect)});
}
$('filter').oninput=list;$('zoom').onchange=draw;$('hidden').onchange=draw;$('node').onchange=()=>{nodePath=$('node').value||null;draw()};list();draw();
</script></html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    data = json.loads(args.report.read_text(encoding='utf-8-sig'))
    if data.get('category') != 'UI' and not any(r.get('category') == 'UI' for r in data.get('results', [])):
        parser.error('Input must be a UI probe report, not a machine production report')
    output = args.output or args.report.with_suffix('.html')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(HTML.replace('__DATA__', json.dumps(data, ensure_ascii=False).replace('<', '\\u003c')),
                      encoding='utf-8')
    print(output.resolve())


if __name__ == '__main__':
    main()
