# Ore center item icon sources

Run from the repository root with JDK 17 or newer:

```powershell
java tools/ore-center/icons/GenerateIcons.java
java tools/ore-center/icons/GenerateIcons.java --check
```

`source-32/` contains the exact 54 pre-change 32×32 PNG assets. Keep these immutable: generation always reads this directory, never the production outputs, so running it repeatedly cannot halve the textures again.

The 51 module packages retain their source design and use a deterministic 2×2 area reduction with alpha-weighted RGB. The three module shells use code-authored, full-height three-face cube geometry in `GenerateIcons.java`, with the original gray-blue, silver, and indigo tier colors and gold I/II/III badges. Every output is a transparent 16×16 PNG. No external libraries, downloads, or AI raster generation are used.

`--check` compares every production pixel to a fresh in-memory generation, checks the exact 54-file inventories and all source/output dimensions and alpha channels, and refreshes the review artifacts. `review/verification.tsv` records source/output SHA-256 identities and transparent/partial/opaque pixel counts; the two PNG sheets show the sources beside the new icons with nearest-neighbor enlargement.
