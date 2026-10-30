# BetterQuesting / recipe compatibility regressions

These tests are local and **never download dependencies or invoke Gradle**. They exercise complete
production source files against explicit, caller-supplied jars. They are not a full mod build or a
transformed Forge client/server test.

## Run

Use Python 3.9+ and JDK 17+ (the injector contains modern switch syntax). Supply a development
classpath whose Minecraft, Forge, GT and BQ symbols use consistent MCP names. For example:

```sh
python tools/tests/run_compat_regressions.py \
  --java-home /path/to/jdk17 \
  --classpath-file /path/to/native-classpath.txt \
  --work-dir /tmp/gtsr-compat-tests \
  --suite all
```

`native-classpath.txt` is one platform-separated classpath (`:` on Unix, `;` on Windows), without shell
quotes. `--classpath` accepts the same string directly. Entries must be existing jar files; there is
no network fallback. Logs, generated seam sources, fresh compiled classes, temporary quest resources,
and SHA-256 classpath manifests are retained in the output directory. Each subprocess has a bounded
60-second timeout; native BQ I/O executors also shut down in a `finally` block.

The verified dependency set was Minecraft 1.7.10, Forge 10.13.4.1614, BetterQuesting **3.8.87-GTNH**,
GT5U **5.09.54.205**, ModularUI 1.3.4, ModularUI2 2.3.92, NEI 2.8.145, Core 0.1.27, and the existing
GTSR 2.0.43 development jar for untouched project symbols, plus their cached supporting libraries
(Gson 2.2.4, Guava, log4j, trove4j, fastutil, Netty, authlib, commons-lang3/IO, etc.). The audit's MC,
Forge, GT and BQ jars had mechanically mapped method/field names; no database, parser, progress,
recipe-builder-parent, or fluid-filter implementation was rewritten. Supply your own legally obtained
development jars; binary artifacts are not shipped with these tests.

Individual suites:

- `bq`: public injector with native BQ quest/database/NBT/progress implementations and **registered
  native** BQ Standard completion-reward parser. Also checks optional BQ absence by removing jars
  containing `betterquesting.api.questing.IQuest` from a second JVM's classpath.
- `inputs`: actual Minecraft `ItemStack`, actual Forge absent `GameRegistry.findItem`, complete
  production `RecipeLoaderUtils`, **no fixtures**. Zero-count integrated-circuit selectors stay valid.
- `recipes`: entire production `HatchRecipes` and `MultiblockMachineRecipes` with capture seams;
  proves missing required items/fluids do not yield discounted registrations, and valid amounts remain.
- `frontend`: actual GT205 frontend constructor, UI/NEI builders and MUI sizing types; nine fluid grids.
- `overlay`: compile the eight compat/recipe production files against native jars, **without fixtures**.

To test a previous revision with today's tests, use `--source-root /path/to/old/checkout`. This must be
a complete source root for the selected suite; tests/fixtures are always taken from the current
checkout. For the R2→R3 comparison, BQ had **13/93 failures before, 93/93 after**, and the required-fluid
capture had **3/6 failures before, 6/6 after**. Earlier-round checks continue passing. A failure is a
nonzero exit; expected failures from a baseline must not be called successful validation.

The pre-R2 item-cost invariant is also available without Java dependencies:

```sh
python tools/tests/verify_required_recipe_inputs.py \
  --original-dir /path/to/pre-r2/src/main/java/com/miaokatze/gtsr/loader/recipes
```

This lexical check verifies that all 30 formerly filtered ingredient lists retain exactly the same
expressions/counts/order after whitespace normalization, and that a null guard precedes registration.
It is not a substitute for the native/capture tests.

## Exact test boundaries

`compat/fixtures.py` and `recipe_fixtures()` in the runner contain every seam, and the runner emits
the actual sources it compiled:

- **BQ:** logger/version/config values, mod-loaded detector, world-directory setting, dirty-save and
  three network boundaries, and an empty-list OreDictionary bootstrap. Actual BQ registries, standard
  factories, quest instances, lines, databases, converters, file I/O and progress merging execute.
  The ore seam avoids an untransformed Minecraft `Item.delegate` linkage; real material-cell
  resolution and real network delivery are **not** tested.
- **Recipe capture:** item/material/ore/GT enumeration lookups, crafting API, GT builder/map capture,
  and FML active-mod identity are seams. Actual MC stacks, actual Forge `FluidRegistry`/`FluidStack`,
  and actual GT205 `ArrayExt.removeNullFluids` execute; the fixture GTValues supplies its canonical
  empty-array constant. The full native `GTRecipeBuilder` is **not** executed in this suite. Bytecode
  inspection confirms its `fluidInputs(FluidStack...)` delegates to that filtering method. This is a
  controlled missing-registry scenario, not proof that a normal complete pack lacks those fluids.
- **Frontend:** texture globals, GUIColorOverride and a dormant GTRecipe shape are link-only seams;
  parent construction and sizing calculations are native. This is not an in-game screenshot test.

## R3 contracts and limits

1. Entire-pack detached definition deserialization runs before any live quest write. A malformed
   native `bq_standard:questcompletion` target used to throw after replacing a live quest's properties
   and tasks; a late failing quest could leave earlier refreshes and newly created lines behind.
2. UUID prerequisite lists must contain complete, exactly typed identities. Native BQ silently
   drops partial/empty/non-compound prerequisite entries; preflight rejects these instead of unlocking
   a damaged quest. Valid external UUID references and native legacy integer-array prerequisites remain
   accepted. Only the known prerequisite schema is checked, not arbitrary custom item-tag names.
3. Disk progress recovery targets **only quests newly created by this injection**. Existing quests
   already have authoritative in-memory progress. Replaying older disk records must not revoke a
   new claim or undo a reset, including for GTSR-owned UUIDs. Newly recreated quests still recover
   legacy `QuestProgress.json` before the newer per-player files, with exact identity preflight.
4. R2 recovery tests used `resetUser` on a retained definition as a stand-in for the absent-definition
   default-reload window. That stand-in incorrectly demanded replay into a live quest. R3 explicitly
   removes the definition, and separately exercises native database snapshot/reload/restore for both
   retained and missing definitions. Malformed selected-file tests still execute on recreated quests.
5. Native default-config smoke tests cover 13 task and six reward families, checking global quest
   definitions/progress/line snapshots before and after detached parsing. Static inspection of those
   supplied readers found local state updates and registry reads, not live database writes. This is
   **not universal third-party plugin transactional safety**: arbitrary extension parser side effects,
   nondeterminism, or downstream progress-parser failures do not have general rollback support.
6. Three raw fluid lookups (32,000 L indalloy140 for each of two controllers; 1,000 L oganesson for the
   turbine chip) now skip the recipe when absent. Names, amounts, item costs, duration and EU/t for
   valid recipes are unchanged. Other required-material/optional-mod behavior was not redesigned.

Full Gradle dependency resolution and transformed gameplay remain separate validation requirements.
