"""Read native coordinates and original PNG ink. Reports measurements; visual acceptance remains explicit."""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image,ImageDraw
from analyze_v2011_alignment import ink_bounds,native_background_origin

COLORS={'edge':(83,100,111),'gold':(234,200,121),'plasma':(210,164,235)}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);a=p.parse_args()
    run=a.run.resolve();assert run.parent.parent.name=='v2012-ui-qa'
    check=run/'checks.json'
    if not check.exists():check=run/'checks-live.json'
    checks=json.loads(check.read_text(encoding='utf-8'))
    rows={r['name']:r for r in json.loads((run/'plan.json').read_text(encoding='utf-8'))}
    output=run.parent.parent/'alignment'/run.name;output.mkdir(parents=True,exist_ok=True)
    results=[];animation={}
    for event in checks.get('scriptEvents',[]):
        if 'galleryCapture' not in event:continue
        name=event['galleryCapture'];row=rows.get(name,{})
        if event.get('v2012MachineTooltip'):
            proof=event['v2012MachineTooltip'];results.append(dict(case=name,tooltip=proof,visualAcceptance='REQUIRES_RAW_PNG_REVIEW'));continue
        observed=event.get('nativeNeiObservation')
        if not observed:continue
        raw=run/'screenshots'/(name+'.png');image=Image.open(raw).convert('RGB')
        scale=image.width//(event['displayWidth']//row.get('scale',2));assert scale==row.get('scale',2)
        origin=native_background_origin(image,event['geometry'],scale)
        overlay=image.copy();pen=ImageDraw.Draw(overlay)

        def rect(x,y,w,h):return [origin[0]+int(x*scale),origin[1]+int(y*scale),origin[0]+int((x+w)*scale),origin[1]+int((y+h)*scale)]
        def pixel_points(rgb,x0,y0,x1,y1):
            # Sample GUI pixel centers from the original PNG, never a declared draw command.
            return [(x,y) for y in range(y0,y1) for x in range(x0,x1)
                    if image.getpixel((origin[0]+x*scale,origin[1]+y*scale))[:3]==rgb]
        def bbox(points):return None if not points else [min(x for x,y in points),min(y for x,y in points),max(x for x,y in points),max(y for x,y in points)]

        result=dict(case=name,rawSHA256=hashlib.sha256(raw.read_bytes()).hexdigest(),recipePixelOrigin=origin,
                    nativeSlotFrames=observed['nativeSlotFrames'],labels=[],visualAcceptance='REQUIRES_RAW_PNG_REVIEW')
        obstacles=[(f['x'],f['y'],f['width'],f['height']) for f in observed['nativeSlotFrames']]
        obstacles += [(label['x'],label['y'],label['actualFontWidth'],observed['fontHeight']) for label in observed['sourceDeclaredLabelOriginsWithActualFontMetrics']]
        buttons=[]
        for button in observed['actualNativeRecipeButtons']:
            # Native buttons are RecipeWidget-local; read actual displayed widget locations, then anchor
            # against the raw PNG card origin. No RecipePageManager relayout/query is invoked.
            box=[button['x']+button['widgetX']-origin[0]/scale,button['y']+button['widgetY']-origin[1]/scale,button['width'],button['height']]
            collisions=[list(other) for other in obstacles if box[0]<other[0]+other[2] and box[0]+box[2]>other[0] and box[1]<other[1]+other[3] and box[1]+box[3]>other[1]]
            buttons.append(dict(buttonClass=button['class'],actualRectRelativeToRawCard=box,overlappingNativeSlotOrDeclaredLabelBoxes=collisions))
            pen.rectangle(rect(*box),outline='#ff4040',width=1)
        result['nativeButtonGeometry']=buttons
        for frame in observed['nativeSlotFrames']:pen.rectangle(rect(frame['x'],frame['y'],frame['width'],frame['height']),outline='#40d060',width=1)
        for label in observed['sourceDeclaredLabelOriginsWithActualFontMetrics']:
            bounds=ink_bounds(image,rect(label['x'],label['y'],label['actualFontWidth']+1,observed['fontHeight']+1),tuple(bytes.fromhex(label['color'])))
            result['labels'].append(dict(text=label['resolvedText'],origin=[label['x'],label['y']],actualFontWidth=label['actualFontWidth'],pixelInkBounds=bounds,
                                         withinCardWidth=label['x']+label['actualFontWidth']<=170))
        flow=observed.get('sourceFlowActualRuntimeValues')
        if flow:
            fluid=row['operation']!='solar_dust';in_group='fluidInputsInventory' if fluid else 'itemInputsInventory';out_group='fluidOutputsInventory' if fluid else 'itemOutputsInventory'
            ins=[f for f in observed['nativeSlotFrames'] if f.get('group')==in_group]
            outs=[f for f in observed['nativeSlotFrames'] if f.get('group')==out_group]
            right=max(f['x']+f['width'] for f in ins);left=min(f['x'] for f in outs) if outs else 144
            center=(right+left)/2;cy=ins[0]['y']+ins[0]['height']/2
            operation=row['operation'];points=[];inferred=None;method=''
            if operation in ('solar_water','solar_salt'):
                y=int(cy+(2 if operation=='solar_water' else -20));points=pixel_points(COLORS['edge'],right+1,y,left,y+1)
                bounds=bbox(points);inferred=(bounds[0]+bounds[2]+1)/2 if bounds else None;method='Inactive boiler branch actual EDGE top border: 26 GUI pixels, midpoint'
            elif operation=='turbine':
                points=pixel_points(COLORS['edge'],right+1,44,left,91);bounds=bbox(points);inferred=(bounds[0]+bounds[2])/2 if bounds else None;method='Actual turbine outer EDGE ring inclusive left/right extrema'
            elif operation=='solar_dust':
                points=pixel_points(COLORS['gold'],right+1,int(cy-16),left,int(cy-15));bounds=bbox(points);inferred=(bounds[0]+bounds[2])/2+8 if bounds else None;method='Actual irradiation upper vertical ray; sun glyph is eight pixels left of process center'
            elif operation=='sun':
                points=pixel_points(COLORS['plasma'],right+1,int(cy-16),left,int(cy-3));bounds=bbox(points);inferred=(bounds[0]+bounds[2])/2 if bounds else None;method='Actual stellar outer PLASMA upper arc inclusive extrema; excludes output chevron at centerY-3 and lower incoming stream'
            else:
                points=pixel_points(COLORS['edge'],right+1,44,left,83);bounds=bbox(points);inferred=(bounds[0]+bounds[2])/2 if bounds else None;method='Actual inactive chemical 2x2 window EDGE frames inclusive extrema'
            result['horizontal']=dict(nativeInputRight=right,nativeOutputLeft=left,outputEvidence='native output slot frames' if outs else 'production EU output label anchor; this recipe has no output slot',
                                      independentSlotGapCenterX=center,nativeInputCenterY=cy,sourceFlow=flow,measuredGlyphInkBounds=bbox(points),measurementMethod=method,
                                      actualInkInferredProcessCenterX=inferred,inkMatchesNativeGapCenter=inferred==center,sourceMatchesNativeGap=flow['inputRight']==right and flow['outputLeft']==left and flow['centerX']==center,
                                      verticalAligned=flow['centerY']==cy)
            if points:
                bounds=bbox(points);pen.rectangle(rect(bounds[0],bounds[1],bounds[2]-bounds[0]+1,bounds[3]-bounds[1]+1),outline='#ff60d0',width=1)
            pen.line(rect(center,44,0,47),fill='#ff9940',width=1)
            diagram=image.crop(rect(right+1,44,left-right-2,47));result['diagramCropSHA256']=hashlib.sha256(diagram.tobytes()).hexdigest()
            diagram.save(output/(name+'.diagram.png'));animation[name]=(result['diagramCropSHA256'],observed['drawTick'])
        else:
            result['constructionFourBoxes']=[dict(x=x,y=31,width=37,height=13,topInkGUI=len(pixel_points(COLORS['gold'] if i<=int(row['operation'][6:])-2 else COLORS['edge'],x,31,x+37,32))) for i,x in enumerate([8,47,86,125])]
        overlay.save(output/(name+'.overlay.png'));results.append(result)
    pairs=[]
    for first,second,expected in [('v2012.turbine','v2012.turbine.later',False),('v2012.turbine.reduced.a','v2012.turbine.reduced.b',True)]:
        if first in animation and second in animation:
            pairs.append(dict(first=first,second=second,drawTicks=[animation[first][1],animation[second][1]],sameDiagramPixels=animation[first][0]==animation[second][0],expectedSameDiagramPixels=expected))
    (output/'geometry-and-ink.json').write_text(json.dumps(dict(scope='Original PNG exact-color ink plus independently observed native slot geometry. No overall PASS is inferred; inspect raw PNGs for interactions, hierarchy, typography and native favorite/overlay buttons.',cases=results,animationPairs=pairs),ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(cases=len(results),output=str(output/'geometry-and-ink.json')),ensure_ascii=False))


if __name__=='__main__':main()
