"""Check the existing entity roster; this rollout must never add entity kinds."""
import json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
source=(ROOT/'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java').read_text(encoding='utf8')
codes=re.findall(r'\w+\("((?:dr|di|dc|do)-\d+)"',source)
assert len(codes)==39 and all(int(c[3:])<=15 for c in codes if c.startswith('di-'))
print(json.dumps({'enumKindsIncludingRetiredDO01':len(codes),'newEntities':0,'formalPreview':39}))
