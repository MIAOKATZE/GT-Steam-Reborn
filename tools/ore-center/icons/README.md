# Ore center item icon sources

Run from the repository root with JDK 17 or newer:

```powershell
java tools/ore-center/icons/GenerateIcons.java
java tools/ore-center/icons/GenerateIcons.java --check
java tools/ore-center/icons/GenerateIcons.java --preview
```

`source-32/` contains the exact 54 pre-change 32×32 PNG assets. Keep these immutable: generation always reads this directory, never the production outputs, so running it repeatedly cannot halve the textures again.

The 51 module packages use code-authored native 16px geometry that retains the original stepped base, upper module case, and 17 color bands. Independent white 3×5 pixel glyphs with a dark shadow render the original two-letter codes in a readable 7×5 area. The gold I/II/III tier strokes are five pixels tall. Each package uses binary alpha (0/255) and at most 10 opaque colors to keep its edges crisp.

The three module shells use full-height three-face cube geometry in `GenerateIcons.java`, with the original gray-blue, silver, and indigo tier colors and gold I/II/III badges. Their existing PNG bytes are preserved: generation only writes the 51 packages and verifies each shell against a fixed SHA-256. Every production output is a transparent 16×16 PNG. No external libraries, downloads, or AI raster generation are used.

`--check` compares every production pixel to a fresh in-memory generation, checks the exact 54-file inventories and all source/output dimensions and alpha channels, enforces package alpha and palette limits, and refreshes the review artifacts. `review/verification.tsv` records source/output SHA-256 identities, transparent/partial/opaque pixel counts, and opaque color counts. `review/all-before-after.png` shows the original 32px sources beside the current 16px outputs; `review/shell-before-after.png` shows the old 32px shells above the unchanged cube shells.

`--preview` writes only `review/packages-preview.png`, comparing the current production packages above newly generated candidates below. The retained preview was captured before the crisp package update: its upper rows show the first, blurry 16px area reductions and its lower rows show the approved native pixel replacements. Do not regenerate this historical comparison unless deliberately replacing that review record. All sheets use nearest-neighbor enlargement.
