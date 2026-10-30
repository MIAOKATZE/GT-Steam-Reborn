#!/usr/bin/env python3
"""No dependencies or source rewriting: exact expression/guard audit for the R2 item-list fixes."""
from pathlib import Path
import re
from argparse import ArgumentParser
parser = ArgumentParser(description="Lexically verify all 30 formerly filtered required item lists retain their expressions/counts/order")
parser.add_argument('--original-dir', type=Path, required=True, help='Pre-R2 directory containing the four recipe Java files')
parser.add_argument('--source-root', type=Path, default=Path(__file__).resolve().parents[2], help='Current complete repository root')
args = parser.parse_args()
r=args.source_root;count=0
for name in ['HatchRecipes.java','MultiblockMachineRecipes.java','ProcessingMachineRecipes.java','NodeAndHubRecipes.java']:
 old=(args.original_dir/name).read_text();new=(r/'src/main/java/com/miaokatze/gtsr/loader/recipes'/name).read_text()
 assert 'filterNulls(' not in new,name
 matches=list(re.finditer(r'ItemStack\[\] (\w+) = filterNulls\(',old))
 newmatches=list(re.finditer(r'ItemStack\[\] (\w+) = new ItemStack\[\] \{',new))
 assert len(matches)==len(newmatches),(name,len(matches),len(newmatches))
 for a,b in zip(matches,newmatches):
  assert a.group(1)==b.group(1)
  start=a.end();end=start;depth=1
  while depth:
   if old[end]=='(':depth+=1
   if old[end]==')':depth-=1
   end+=1
  oldexpr=old[start:end-1];newend=new.index('};',b.end());newexpr=new[b.end():newend]
  normalize=lambda x: re.sub(r'\s+','',x)
  assert normalize(oldexpr)==normalize(newexpr),name
  use=new.index('.itemInputs('+b.group(1)+')',newend)
  assert 'hasNull('+b.group(1)+')' in re.sub(r'\s+','',new[newend:use]),(name,b.group(1))
  count+=1
 print('PASS exact ingredient expressions and guards:',name,len(matches))
assert count==30
print('PASS all 30 required input lists retain every original expression/count/order and reject missing inputs')
