# Offline world/lifecycle regressions

Run `verify_world_lifecycle.py` with Java 8 and an explicit **local cached** classpath:

```sh
python tools/prosperity/verify_world_lifecycle.py \
  --javac "$JAVA8_HOME/bin/javac" --java "$JAVA8_HOME/bin/java" \
  --classpath "$MCP_MC_JAR:$GTSR_DEV_JAR:$GSON_JAR:$OTHER_MC_LIBRARIES" \
  --output /tmp/gtsr-world-lifecycle \
  --baseline-ref 57d082e9e748c8918ae3bb2c33df386c9a1cc99e
```

Required libraries: MCP-named Minecraft 1.7.10, source-matching GTSR dev jar, Gson
2.2.4, Guava 17, log4j API/core, Netty, authlib, and commons-lang3. The script does
not download dependencies or invoke Gradle. Omit `--baseline-ref` to test only the
working tree. With the baseline option it requires an assertion failure for each
old source, followed by a passing current-source run. Compilation/linkage failures
are not accepted as baseline regression evidence. Sources, fixtures, compile logs,
and test logs are preserved under `--output`.

## Exact scope

- **Persistence:** actual `RemasterData`, `RemasterSite`, rollout/catalog/prefab and
  future planner source, packaged descriptors, and Minecraft NBT. Only `World`'s
  `perWorldStorage` API surface is stubbed. The local source copy maps
  `func_150296_c()` to the cached MCP bytecode's `getKeySet()` name. Proves paused,
  unknown-prefab, nonstandard-variant and old-layout records survive three exact
  NBT roundtrips without becoming runtime/index owners.
- **Anchor intent:** actual queue, use authorization, packet parsing, and drain;
  actual Minecraft player/stack/connection classes and Netty buffers. Player
  construction is bypassed using Unsafe and explicit unique entity IDs (respawn
  deliberately reuses the old numeric ID). FML registration/transport and
  `SpacetimeTravel` eligibility/mark effects are explicit fixtures. Proves the
  session tick limiter and vanilla-use lease do not cross player replacement.
- **Biome viability:** actual manager/definition/selector/chain source and vanilla
  `GenLayer`/`IntCache`. Logging is a stub; two generic list parameter signatures in
  the local source copy are erased to match vanilla bytecode. Proves spare pooled
  array capacity cannot reject a valid logical biome query.
- **Travel reset:** actual `SpacetimeTravel` source and its static transient state;
  unavailable Forge/IC2 API surfaces are explicit compile/link fixtures. Proves the
  stop-reset clears cooldown and transfer state, is idempotent, and admits a new
  world-time deadline. Its explicit reset sinks also check fan-out to anchor, beacon,
  navigator, reading/activation, and altar-search cleanup; the complete dependent
  implementations are checked separately by `verify_world_round3.py`. Does **not**
  execute recall, transfer, energy debit, landing,
  or the FML stop event. The `GTSteamReborn.serverStopped` hook must be checked
  separately.

These focused checks are not a full mod build, Forge/server/client startup,
chunk-provider integration, or a gameplay pass.
