# World-scoped biome authority regression

## Defect and repair

`GTSRWorldChunkManager` previously installed a `Source` in a process-global authority keyed only by dimension. An integrated client's seed-zero manager could replace the server's source; a replacement save could also change another still-reachable world's source. Spawn lookup, planner weights/burial and Shattered fog then sampled whichever manager was constructed last.

The manager now owns a scoped authority. `forWorld(world)` reads that exact world's current manager and never falls back to a dimension-only source. Allocation metadata remains shared; manager/source/world references do not enter the static registry. Consequently there is no world map to clear on unload or server stop. Legacy `bind` remains deprecated for isolated historical tools only. A source-routing regression rejects production calls to it and reviews all six runtime world-bearing spatial-consumer files.

Pure planners now carry their existing world seed explicitly through weight, micro-strength and anchor-burial lookup. Their authority cache holds at most four seed/selector-ID chains per ledger, not worlds or managers. The chunk-center identity convention, XOR domain salt, selector membership, micro formula and random streams are unchanged. Shattered rendering still has its own client source; this change does not introduce seed synchronization or switch fog to chunk-byte lookup.

Round 3 closes an additional native shared-pool defect. Holding `IntCache.class` around GTSR evaluation alone was insufficient: vanilla `WorldChunkManager` can already hold live pooled arrays before GTSR resets the pool. A deterministic test borrows a native array, calls GTSR once and detects its contents being overwritten; a separate concurrent native-WCM probe also reproduced corruption before the fix.

The chain now uses GTSR-private sampling stages and a per-chain monitor, retaining native `GenLayer` seed initialization and selection methods. Zoom, smoothing and nearest-jittered-corner rules, salts, RNG call order, ties and selector IDs stay equivalent. The selector also uses private allocation. No GTSR sampling path borrows or resets native `IntCache`; no global transformer, mixin, Hodgepodge setting or external dependency is changed. Windows are copied to exact logical lengths before unlocking. Each stage retains at most 4,096 ints, and Voronoi retains at most 8,192 jitter doubles; larger arrays are temporary, with no static/world retention.

Native Voronoi also underfills some odd, unaligned windows. The private stage includes their entire rectangle. Such cases are checked against a fully filled native multiple-of-four envelope cropped to the requested window, **not** falsely reported as equal to native stale-tail output. Normal chunk planes and single-point/planner identities retain their existing fingerprints.

### Runtime and source boundaries

The inspected official [Hodgepodge configuration](https://github.com/GTNewHorizons/Hodgepodge/blob/7e2386c785e056b38dadc0931c7cbfc5658a4072/src/main/java/com/mitchej123/hodgepodge/config/SpeedupsConfig.java) makes `fastIntCache` experimental and disabled by default; the inspected [GTNH pack configuration](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/blob/ed9ee8377267a785dcf4502b2001270da750bfb5/config/hodgepodge.cfg) explicitly sets it to `false`. Hodgepodge's [mixin selection](https://github.com/GTNewHorizons/Hodgepodge/blob/7e2386c785e056b38dadc0931c7cbfc5658a4072/src/main/java/com/mitchej123/hodgepodge/mixins/Mixins.java) gates the explicit-release cache replacement behind that option. Thus an upstream patch cannot be assumed active. This is pinned-source evidence, not an inspection of every user's loaded/transformed mod stack.

The private stages are newly structured implementations of these sampling rules, calling inherited native seed/selection operations rather than including decompiled vanilla classes. Local native source was consulted to verify contracts; this is not a clean-room claim. The repository's license is unchanged. No Minecraft source, jar, transformed class or upstream Hodgepodge source is included in this change.

Allocation replacement and `recordNoSlot` now remove the prior biome instance from the reverse index. Otherwise an old manager/source could still resolve a superseded instance under the replacement's ID, or a withdrawn instance with `NO_SLOT`. This preserves the documented last-record-wins ledger contract without changing normal one-time allocation or seeded generation.

## Run

Use existing jars only; the runner performs no downloads, Gradle tasks or game startup:

```sh
python tools/dim1/verify_biome_authority.py \
  --classpath "$CACHED_MCP_AND_MOD_JARS" \
  --java /path/to/java8/bin/java --javac /path/to/java8/bin/javac \
  --baseline-ref 57d082e9e748c8918ae3bb2c33df386c9a1cc99e \
  --baseline-snapshot /path/to/review-state/gtsr-r1-snapshot \
  --output /tmp/gtsr-biome-authority
```

The snapshot is a sparse overlay; other baseline files are read from the supplied local git revision. For candidate-only checks omit both baseline arguments. The baseline run also requires the deterministic borrowed-pool and odd-window failures; candidate-only executes the complete pool, parity and concurrency suite. The classpath needs MCP-named Minecraft and Forge, matching GTSR dev and GT5U jars, and their existing dependencies (including Log4j, Guava and commons-lang3). Source/javadoc jars are unnecessary.

## Evidence

- Frozen R1: construction of a seed-zero second manager redirects **789/1,024** server-coordinate lookups. Candidate passes **1,556** scope/cache/degradation/isolated-chain concurrency assertions.
- Clean-server coarse/fine/micro fingerprint is identical: `2695093788726760236`.
- Production planner code matches frozen R1 over **8,192** points and two seeds, covering machine/ruin intents, anchor identities and all three burial families. Fingerprints: seed `19` → `7448778721409024538`; seed `-88372631` → `-2304174081120013901`. Candidate remains identical after another client manager is constructed.
- Actual `GTSRChunkProviderBase.getPossibleCreatures` and Shattered provider identity bodies pass **772** checks: standalone client source before a server exists, correct-world spawn lists, client/source isolation, and the source-independent SHORT/EMPTY surface guard.
- Round 3 pool-isolation and native-parity regression: **30,787** checks and **22,427,263** compared cells (including native-reference self-checks), zoom levels 0–8, seven seeds including `Long.MIN_VALUE`/`Long.MAX_VALUE`, five ID rosters, negative coordinates, odd windows and large 1,025×769 / 513×515 requests. **1,890** fine windows match direct native calls; **947** underfilled-native cases use native-envelope crops, with another **625** targeted 3×3 window checks. **20,000** concurrent operations cover same-chain coarse/fine readers, another chain, native vanilla WCM, and a separate hostile-pool-reset phase. Native array sentinels and global cache accounting remain unchanged; returned arrays cannot poison private scratch.
- Round 3 allocation-lifecycle regression: frozen R2 fails on the superseded-instance identity; candidate passes **218** checks, including replacement/withdrawal, old scoped sources, nearest lookup, explicit-seed cache behavior, idempotent same-instance records and bounded reverse-index retention.
- Re-running `tools/prosperity/verify_world_lifecycle.py` preserves all four original baseline failures and candidate successes. Its biome baseline now explicitly compiles the original chain; otherwise the new owned-window contract would mask the older stale-pool-tail failure.

## Fixture and mapping limits

The tests use native vanilla `World`, biome, `GenLayer` RNG, reference Zoom/Smooth/Voronoi and `IntCache` classes. The candidate uses its production private sampling stages, not stubs. Native `WorldServer` objects are allocated without constructors and serve as minimal world carriers (one marked `isRemote=true`); no save, networking, ticking, dimension transfer or rendering runs. Spawn assertions use registered vanilla fixture biomes with their native spawn lists, not a claim of a complete custom-creature gameplay smoke. Only the mod main logger is replaced in core tests.

Generated source adapters compensate for the cached vanilla jar versus Forge's development API: raw biome-list signatures; `func_147416_a`/`findClosestStructure`; `field_150604_aj`/`topBlockMetadata`; two Forge-added provider `@Override` annotations; and the erased `CommandBase.compareTo` bridge. Command compilation also uses a throw-if-executed signature shim for Forge's three-argument teleporter transfer method. None of these adapters changes repository production sources. The shim is never executed.

This is native focused source/consumer verification, **not** a full Gradle build, Forge client/server launch, visual fog check or claim that all archived historical-era tools still pass. Old tools that assumed manager construction automatically binds the global registry must opt into their explicit legacy fixture binding or use the scoped API.

## Local sampling cost (not a gameplay benchmark)

A Java 8, single-thread, seven-paired-trial warmed median comparison with the original monitor-only native chain found candidate/original ratios: coarse/fine 1×1 `0.70× / 0.67×`, 16×16 `1.14× / 0.85×`, 32×32 `1.12× / 1.08×`, and 256×256 `1.10× / 1.31×`. Small planner queries did not regress in this fixture; large fine windows can cost more. These timings do not establish FPS, chunk throughput, allocation pressure or performance in a transformed GTNH client/server.
