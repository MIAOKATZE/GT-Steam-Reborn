# Static correctness review — 2026-10-30

Base: `57d082e9e748c8918ae3bb2c33df386c9a1cc99e` (v2.0.43).

This change set is a correctness review, not a balance redesign. It combines two
multi-angle review/fix rounds and a fresh independent third round. The original
review environment could not resolve dependencies and did not verify a full build
or client/server gameplay. Dated maintainer validation below records the later
supported-environment results. Focused tests are not whole-mod integration acceptance.

## Changes by subsystem

- **Machines and transfers:** use accepted/paid quantities rather than requested
  quantities; retain undelivered output; validate live controller/hatch ownership;
  prevent inherited and custom output paths from paying or exporting twice.
- **Critical machines:** support the intended Critical ME hatch in chemical inputs,
  propagate native payment failures, cap arithmetic before debit, and require all
  TCDS ingredients before successful operation. Construction checks must apply to
  offline owners as well as online owners.
- **Saved world state:** preserve paused/unknown records; quarantine malformed
  identities and state without silently rewriting them as valid empty state.
  Bound spatial arithmetic and stale travel/altar requests.
- **Biome lookup:** bind spatial sources to the requesting world/seed instead of a
  process-global last-bound manager. Client seed-zero managers must not overwrite
  server lookup authority. GTSR-only sampling stages use bounded private scratch
  instead of vanilla IntCache; no global transform is installed. Clean seeded
  planner fingerprints are unchanged. Native underfilled odd windows are compared
  with fully filled native envelope crops, not claimed as direct-native parity.
- **Encounters and weapons:** preserve ray endpoints across native mutable tracing;
  bound encounter inputs and generated output; retain pending spawn obligations.
  Choose an eligible nearby survival player rather than rejecting the encounter
  because an ineligible player is nearer. Multi-pack weapon refunds keep per-source
  paid receipts and do not synthesize regenerating mimic vessels.
- **Packets and GUIs:** bound untrusted admission, validate exact connection/player
  lifetimes, reject malformed envelopes, and revalidate before queued actions.
  Cleanup obligations are not discarded through admission throttling.
- **Recipes and BetterQuesting:** retain all mandatory ingredients and skip incomplete
  recipes rather than silently discounting them, including mandatory fluids.
  Preserve zero-count circuit selectors. Preflight typed quest/progress and
  prerequisite identities plus native definition parsing before mutation. Replay
  disk progress only for definitions actually recreated by this injection, in
  upstream aggregate/per-player order; do not roll back existing live claims or
  resets, or rewrite foreign quests/progress.

No confirmed shared Core defect or general root cause for intermittent recipe
loss was established. Existing GTSR item-order namespace extraction was already
correct and was not replaced with the different GTIT implementation.

## Evidence and boundaries

Tests use cached official development dependencies, including GT5U 5.09.54.205,
BetterQuesting 3.8.87, and the GTSR v2.0.43 development jar. Some runners compile
whole changed production classes against native Minecraft/Forge/GT/BQ APIs;
others extract a method or use explicit world, transport, registry, or renderer
seams. Per-runner READMEs distinguish these cases. Mechanical mapping adapters in
detached test copies are not production behavior changes.

Native GT ME recipe-processing completion is **not idempotent**. Do not add an
extra `endRecipeProcessing` call when an outer owner already closes the window.
Source-pattern assertions alone are not evidence that a transaction paid once.

The review did not certify Forge side transformations, a real AE grid, third-party
claim/protection events, actual TCP transport, GL rendering, complete optional-mod
combinations, or full quest-pack boot/reload. Native vanilla spawn protection is
not equivalent to support for every claims plugin.

## Local acceptance before merging

1. Build with the project's normal supported dependency cache and run the focused
   regression runners documented under `tools/tests`, `tools/prosperity`, and
   `tools/dim1`.
2. In a disposable copy of an existing save, exercise machine interruption, partial
   output, unavailable ME power, reload and hatch reassignment. Check conservation.
3. Test dedicated and integrated servers, reconnect/respawn while a GUI or travel
   request is pending, and close/reopen critical terminals before queued dispatch.
4. Compare unchanged-seed terrain/planner output, travel between dimensions, and
   ensure corrupt or unknown saved records remain preserved for recovery.
5. Reload BQ with owned and foreign quests, already-claimed progress, and malformed
   records. Confirm foreign content is unchanged and valid rewards are not replayed.

The original PR was kept in draft for maintainer review of these environment checks.

## Maintainer validation — 2026-10-31

The production changes were reviewed by subsystem against the actual GT205,
ModularUI2 2.3.92 and BetterQuesting 3.8.87 APIs. Two full-build blockers were
repaired: Java 8 requires `collect(Collectors.toList())`, and this project's mapped
NBTTagList exposes `func_150303_d()`. The project's formatter also corrected 41
changed Java files. The normal Gradle build then passed compilation, Spotless,
Checkstyle and reobfuscation. There are no Gradle test sources.

Fresh focused runs passed machine conservation (56), construction permission seams
(14 scenarios / 1,046 checks), native permission (7), BQ (93), critical consumption
(23), TCDS (31), hub transfers (25), and required recipe item/fluid captures. The
biome runs covered 30,787 sampling checks, 8,192 unchanged planner points and 772
consumer checks. The actual request queue passed 55 checks; four production packet
classes passed 12 envelope checks. These counts remain scoped assertions.

Detached test fixtures needed local MCP naming and Forge world initialization
adaptations; all adaptations stayed under ignored build output. Original fixture
failures are retained alongside the adapted runs. No production behavior was
changed to satisfy those fixtures. This review established neither a shared Core
defect nor a general cause of intermittent pack recipe disappearance.

A disposable RC2 dedicated server reached TestLab READY with the packaged
candidate and Core 0.1.27. Its machine catalog then hit an inherited test-lab error:
CRITICAL_INPUT_HATCH_ME was parsed as CriticalMachineKind.INPUT_HATCH_ME. Neither
catalog nor enum was changed by this PR. The blocked resource scenarios are not
reported as passes. After correcting an invalid scenario entry ID, eight related
production MUI2 builders passed on the server, followed by successful task/supply
cleanup. All three owned server runs exited normally without forced termination.
These FakePlayer builder probes do not open real player containers or validate
client layout; screen-lifecycle layout errors remain recorded as that boundary.

The complete client, real AE grid, claims integrations, old-save recovery and full
BQ pack reload remain outside this acceptance. No GitHub CI check pass is claimed.
The maintainer's merge decision uses the production review, successful packaged
build, bounded native regressions and explicitly scoped server evidence.

## Focused reproduction entry points

- `tools/tests/README-machine-critical-regressions.md`: ordinary machine
  conservation (56 checks), critical permission seams (14 scenarios / 1,046
  checks, including 1,000 repeated cache-reuse checks), and 7 native vanilla
  spawn/operator permission checks. These totals are not independent gameplay
  scenario counts.
- `tools/tests/network/README.md`: packet admission, GUI factories, reconnect and
  deferred command lifetime. Third-round tests reproduce 6 pre-fix failures and
  pass 35 patched assertions; earlier packet/GUI suites are retained.
- `tools/dim1/README-biome-authority.md`: world/seed separation, allocation lifetime,
  seeded layer parity and external IntCache isolation.
- `tools/prosperity/README-world-lifecycle.md` and
  `tools/prosperity/README-world-round2.md`: persistent/unknown state, typed record
  validation and original travel lifetimes. Later third-round coverage is
  documented in `tools/prosperity/README-world-round3.md`.
- `tools/tests/compat/README.md`: native BQ definition/progress/identity and
  recipe required-item/fluid probes; the single entry point is
  `tools/tests/run_compat_regressions.py`.

Older repository tooltip checks are not a green acceptance baseline: the
unchanged `machine_tooltip_layering_contract.py` expects color literals absent
from the unchanged production tooltip class, while the old tooltip catalog
fixture lacks APIs used by that class. These pre-existing test-fixture failures
were not used to justify unrelated production tooltip changes.

The scoped biome layers prioritize isolation and determinism, not a blanket speed
claim. Local warmed timings were mixed: representative planner point queries
were faster, while a large 256×256 fine window cost about 1.31× the native baseline.
These are sampler timings, not gameplay benchmarks.

Weapon/encounter reproduction: `tools/prosperity/README-encounter-round3.md`
includes actual reload/update transaction tests with explicitly isolated projectile
and effect endpoints. Legacy free top-ups migrate paid-source identity before
changing the return target. Invalid receipt containers remain opaque and block
resource-changing transactions rather than accepting a debit without a persistent
refund receipt. Missing mimic vessels do not get reconstructed.
