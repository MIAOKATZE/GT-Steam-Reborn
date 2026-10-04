"""Update archive rollout facts without rewriting its app or renderer."""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
catalog=json.loads((ROOT/'src/main/resources/assets/gtsr/remaster/catalog.json').read_text(encoding='utf8'))
ids=[e['id'] for e in catalog['structures']]
path=ROOT/'plan/prosperity/data/remaster.js'
text=path.read_text(encoding='utf8').replace('c.meta.sourceVersion="1.20.71"','c.meta.sourceVersion="1.20.72"')
marker='/* v72 rollout facts */'
if marker in text:text=text.split(marker)[0]
text+='\n'+marker+'\n(function(c){c.meta.runtimeRollout='+json.dumps({'activeIds':ids,'newAuthored':61,'originalScenes':3,'cityDesignOnly':True},ensure_ascii=False,separators=(',',':'))+';for(const s of c.structures||[])s.description=(s.description||[]).filter(t=>!t.includes("其他结构仅留设计介绍"));})(window.PROSPERITY_CATALOG);\n'
path.write_text(text,encoding='utf8')
path=ROOT/'plan/prosperity/structures/remaster-index.js'
text=path.read_text(encoding='utf8')
text=re.sub(r'b\.rollout=\{.*?\};', 'b.rollout='+json.dumps({'activeIds':ids,'standardVariant':0,'status':'implemented-v72','source':'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/remaster/RemasterRollout.java','futureSource':'plan/prosperity/structures/future','cityDesignOnly':True},separators=(',',':'))+';',text,count=1)
path.write_text(text,encoding='utf8')
print('Archive rollout facts: 64 active / 61 new / city design-only')
