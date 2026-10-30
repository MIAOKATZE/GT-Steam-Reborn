# Offline world/travel round-two regressions

Run against existing local dependencies only:

```sh
python tools/prosperity/verify_world_round2.py \
  --workspace /path/to/audit-workspace \
  --out /tmp/gtsr-world-round2
```

The workspace layout matches `verify_world_lifecycle.py`: cached MCP Minecraft,
the source-matching GTSR dev jar, Java 8/17 toolchains, and Minecraft libraries.
`--classpath` can supply the cached library classpath explicitly. No dependencies
are fetched, and Gradle, Minecraft, Forge startup and remote operations are not run.

The default comparison uses local git revision
`57d082e9e748c8918ae3bb2c33df386c9a1cc99e`. To isolate changes after round one,
pass `--baseline-dir /path/to/gtsr-r1-snapshot`; files absent from that sparse
overlay fall back to `--baseline-ref`. Each baseline must compile and produce a
behavioral assertion failure; missing APIs/linkage failures do not count as red.
The runner records per-stage logs and source/test/dependency hashes in
`verification.json`.

## Checks and boundaries

- **Saved state — 1,079 assertions.** Complete production ledger/site and
  rollout/catalog/prefab/future planner source; native Minecraft NBT and packaged
  descriptors. Malformed identity, state and anchor records remain opaque and
  unavailable to runtime indexing. Readable frozen identities cannot be
  re-registered or win through input-order duplicate records. Three roundtrips
  preserve their raw NBT. Native chunk-overflow flags cannot become generated
  owners; 1,024 legal-coordinate cases retain old footprint results. The old
  source fails 26 checks, including an actual Minecraft `ReportedException` from
  a string-valued state. The only World fixture supplies Forge's storage field;
  the copied source maps `func_150296_c()` to cached MCP `getKeySet()`.
- **Altar reading — 23 assertions.** Complete production reading logic and
  `ServerPacketAuthority`, actual native player/entity equality, connections,
  server and online-player-list checks. Reused numeric entity IDs, stale/closed/
  reassigned connections and removed online players cannot inherit or consume
  another lifetime's lease. Same-player one-use, expiry and wrong-nonce behavior
  remain intact. The old source fails eight checks. World block lookups, authored
  board/core/chest registration, history/network effects and vis payment are
  explicit fixtures; no real altar activation/payment or packet dispatch is claimed.
- **Recall discovery — 88 assertions.** Complete production recall source and
  native player/NBT/stack classes. Injected cold-chunk and collision-provider
  exceptions produce an ordinary failure before energy simulation, item mutation
  or movement. Missing/wrong-type saved mark fields cannot load default-coordinate
  chunks. Unsafe landing remains unsafe, and fatal `Error` propagates. The old
  source fails 40 checks. IC2 and dimension lookup are fixtures. Forge-only Block
  and transfer API signatures are compile adapters removed before runtime; the
  unavailable successful landing/transfer method bodies are never exercised.
- **Round-one persistence — 23 assertions** remains green with the current data
  validation, including inactive variants, paused prefabs and older layouts.

This is not a whole-mod build, world-save disk test, successful dimension transfer,
energy debit/refund proof, real cold generation, full multiplayer test or exhaustive
review of every worldgen path. Quarantine intentionally does not reinterpret or
repair unreadable progress. A record with no trustworthy canonical identity is
retained but cannot reserve a guessed identity or unknown geometric footprint.
