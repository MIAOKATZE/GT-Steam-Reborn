"""Read native screenshot geometry and pixel ink; generated overlays are not raw evidence."""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image, ImageDraw


def ink_bounds(image, rect, rgb):
    x0, y0, x1, y1 = rect
    found = [(x, y) for y in range(y0, min(y1, image.height)) for x in range(x0, min(x1, image.width))
             if max(abs(a-b) for a, b in zip(image.getpixel((x, y))[:3], rgb)) <= 2]
    return None if not found else [min(p[0] for p in found), min(p[1] for p in found),
                                  max(p[0] for p in found)+1, max(p[1] for p in found)+1]


def native_background_origin(image, geometry, scale):
    # Native recipe button initialization can temporarily relocate its RecipeWidget.
    # Anchor against the raw PNG's production background top border, never those temporary coordinates.
    start_x, start_y = geometry["x"] * scale, geometry["y"] * scale
    end_x = start_x + geometry["width"] * scale
    for y in range(start_y, min(image.height, start_y + geometry["height"] * scale)):
        run_start = None
        for x in range(start_x, min(image.width, end_x)):
            match = image.getpixel((x, y))[:3] == (23, 33, 42)
            if match and run_start is None:
                run_start = x
            if not match and run_start is not None:
                if x - run_start == 170 * scale:
                    return [run_start, y]
                run_start = None
        if run_start is not None and min(image.width, end_x) - run_start == 170 * scale:
            return [run_start, y]
    raise ValueError("No actual 170px production background border inside native GUI")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    args = parser.parse_args()
    run = args.run.resolve()
    assert run.parent.parent.name == "v2011-ui-qa"
    source = run / "checks.json"
    if not source.exists():
        source = run / "checks-live.json"
    checks = json.loads(source.read_text(encoding="utf-8"))
    launch = json.loads((run / "launch.json").read_text(encoding="utf-8"))
    scale = launch["guiScale"]
    assert scale == 2, "This bounded plan uses an observed scale2 client"
    output = run.parent.parent / "alignment" / run.name
    output.mkdir(parents=True, exist_ok=True)
    results = []
    for event in checks.get("scriptEvents", []):
        observed = event.get("nativeNeiObservation")
        if not observed or "nativeSlotFrames" not in observed:
            continue
        name = event["galleryCapture"]
        raw = run / "screenshots" / (name + ".png")
        image = Image.open(raw).convert("RGB")
        overlay = image.copy()
        pen = ImageDraw.Draw(overlay)
        origin = native_background_origin(image, event["geometry"], scale)

        def rect(x, y, w, h):
            return [origin[0]+x*scale, origin[1]+y*scale, origin[0]+(x+w)*scale, origin[1]+(y+h)*scale]

        item = dict(case=name, rawSHA256=hashlib.sha256(raw.read_bytes()).hexdigest(), recipePixelOrigin=origin,
                    originEvidence="Exact native production background top border in raw PNG (170 GUI pixels wide)",
                    postButtonQueryRecipePosition=[observed["recipeX"], observed["recipeY"]],
                    nativeStackCount=len(observed["nativePositionedStacks"]), labels=[],
                    nativeButtons=observed["actualNativeRecipeButtons"],
                    allStacksOnePixelInset=all(s["nativeOnePixelInset"] for s in observed["nativePositionedStacks"]))
        button_rects = []
        for button in observed["actualNativeRecipeButtons"]:
            # GuiRecipe.getRecipePosition includes handler YShift; native buttons are widget-local.
            # The recorded temporary widget and recipe positions expose that shift independently.
            shift_x = observed["recipeX"] + event["geometry"]["x"] - button["widgetX"]
            shift_y = observed["recipeY"] + event["geometry"]["y"] - button["widgetY"]
            box = [button["x"]-shift_x, button["y"]-shift_y, button["width"], button["height"]]
            button_rects.append(dict(buttonClass=button["class"], handlerShift=[shift_x, shift_y],
                                     nativeRectRelativeToRecipe=box))
            pen.rectangle(rect(*box), outline="#ff4040", width=1)
        item["nativeButtonRectsRelativeToRecipe"] = button_rects
        for frame in observed["nativeSlotFrames"]:
            pen.rectangle(rect(frame["x"], frame["y"], frame["width"], frame["height"]), outline="#40d060", width=1)
        for stack in observed["nativePositionedStacks"]:
            pen.rectangle(rect(stack["x"], stack["y"], stack["width"], stack["height"]), outline="#40d0ff", width=1)
        for label in observed["sourceDeclaredLabelOriginsWithActualFontMetrics"]:
            rgb = tuple(bytes.fromhex(label["color"]))
            bounds = ink_bounds(image, rect(label["x"], label["y"], label["actualFontWidth"]+1, observed["fontHeight"]+1), rgb)
            result = dict(text=label["resolvedText"], declaredDrawOrigin=[label["x"], label["y"]],
                          measuredNativeFontWidth=label["actualFontWidth"], pixelInkBounds=bounds)
            if bounds:
                result["bottomInkYRelativeToRecipe"] = (bounds[3]-origin[1])/scale
                result["leftInkXRelativeToRecipe"] = (bounds[0]-origin[0])/scale
                pen.line((origin[0]+label["x"]*scale, bounds[3], origin[0]+(label["x"]+label["actualFontWidth"])*scale, bounds[3]), fill="#ff60d0", width=1)
            item["labels"].append(result)
        obstacles = [(x["x"], x["y"], x["width"], x["height"]) for x in observed["nativeSlotFrames"]]
        obstacles += [(x["x"], x["y"], x["actualFontWidth"], observed["fontHeight"]) for x in observed["sourceDeclaredLabelOriginsWithActualFontMetrics"]]
        item["nativeButtonsClearOfSlotsAndLabelBoxes"] = all(not (a[0] < b[0]+b[2] and a[0]+a[2] > b[0] and a[1] < b[1]+b[3] and a[1]+a[3] > b[1])
                                                                 for button in button_rects for a in [button["nativeRectRelativeToRecipe"]] for b in obstacles)
        if "declaredArrowCenterY" in observed:
            center = observed["declaredArrowCenterY"]
            bounds = ink_bounds(image, rect(126, center-5, 9, 10), (234, 200, 121))
            actual = None if bounds is None else ((bounds[1]+bounds[3])/2-origin[1])/scale
            relevant = [s["actualSlotFrame"]["y"]+s["actualSlotFrame"]["height"]/2
                        for s in observed["nativePositionedStacks"] if s["input"] and s["fluid"] == (center == 73)]
            item["arrow"] = dict(declaredCenterY=center, actualGoldHeadCenterY=actual,
                                 nativeInputSlotCentersY=relevant,
                                 aligned=actual == center and all(c == center for c in relevant))
            pen.line(rect(76, center, 60, 0), fill="#ff9940", width=1)
        overlay.save(output / (name + ".overlay.png"))
        results.append(item)
    result = dict(scope="Raw PNG exact-color text/arrow ink plus independent native widget coordinates. Bottom ink is a glyph-edge observation, not a claimed typographic baseline. Overlays are derived inspection aids; inspect raw PNG for native button clearance.", cases=results)
    (output / "geometry-and-ink.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(dict(cases=len(results), output=str(output / "geometry-and-ink.json")), ensure_ascii=False))


if __name__ == "__main__":
    main()
