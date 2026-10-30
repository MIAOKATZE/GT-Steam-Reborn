# World / travel: aggressive third-round regressions

`verify_world_round3.py` runs seven offline suites against a frozen prior-round
source overlay and the current sources. It downloads nothing, invokes no Gradle,
and starts neither Minecraft nor Forge.

```sh
python tools/prosperity/verify_world_round3.py \
  --workspace "$WORKSPACE" \
  --baseline-dir "$WORKSPACE/review-state/gtsr-r2-snapshot" \
  --out /tmp/gtsr-world-round3
```

Without an overlay, baseline paths come from local git revision
`57d082e9e748c8918ae3bb2c33df386c9a1cc99e` (override with `--baseline-ref`). Missing
paths in a sparse overlay fall back to that ref. `--suite` selects `data`,
`navigation`, `altar`, `anchor`, `stop`, `search`, or `portal`. Java 17 and existing
cached Minecraft/GTSR/Gson/Guava/log4j/Netty/authlib/commons-lang3 jars are required;
the expected workspace layout is explicit in the runner. Baseline compilation and
runtime linkage must succeed before a behavioral assertion failure counts as RED.
The final receipt records source/test/runner, baseline-source, and jar SHA-256s.

## Protected contracts

- **Persisted progress is not a fresh world.** An unreadable `sites` container
  survives NBT roundtrips and blocks fresh site registration. Known malformed
  nested progress is frozen, not silently defaulted to unclaimed/unspawned state.
  Multiple records with the same identity are preserved and quarantined rather
  than choosing the first, possibly unclaimed, copy. Unknown extension tags remain
  opaque. In particular, spawner member flags are typed only when their suffix is
  the canonical nonnegative integer serial used by the runtime; future string
  markers, signs, leading zeroes, whitespace and overflow suffixes are preserved.
- **Navigation belongs to one exact actor and connection.** Captured packet
  handlers may not follow a respawn/reconnect to another actor. Beacon session and
  navigator query stamps replace the actual weak key, since vanilla entity
  equality aliases numeric IDs across respawn. Disconnected/removed actors cannot
  start or finish locate work. Session references cannot retain an old world or
  player through a strong handler/world cycle.
- **Reading leases expire while idle.** Board/world as well as player/connection
  references are weak; server ticks retire expired/disconnected sessions without
  requiring another click. Activation requests are keyed by connection and capture
  the exact player, so an old equal-ID map key cannot discard a replacement's valid
  nonce. The R2 one-use, range, board and current-player checks still apply.
- **Alt intent does not change owners while waiting.** Both queued and waiting
  intents retain the player captured at receipt; a rebound handler cannot combine
  an old packet with a replacement's new vanilla right-click. Retiring the old
  request does not consume the replacement's authorization. Queue drain is bounded.
- **Stopping a server clears all transient owners.** The existing
  `SpacetimeTravel.resetServerState()` hook clears anchor uses/waiters, beacon
  sessions/queue, navigator jobs/query/gift witnesses, reading sessions/activation
  queue and altar-search cache, besides recall cooldown/transfer guards. Admission
  and queue reset update accounting together. This does not clear saved progress.
- **Portal core contact is an exact-entity lease.** Native numeric-ID equality must
  not pin a respawn replacement to the previous entity's core position or authorize
  transfer before its own core contact. Stage/contact maps and the retained set
  use identity semantics; source cleanup still leaves actors in another world alone.

## What is native, and what is a seam

The suites compile the complete changed source files. Native Minecraft NBT,
`Entity.equals`, players, handlers, NetworkManager, server/configuration lists,
vectors and boxes are used where relevant. Unsafe only bypasses irrelevant native
constructors. Shared `ServerPacketAuthority` is the actual source. MCP aliases for
NBT keys and item owner/thrower setters are mechanical compile adaptations.

FML registration/transport, target searches, item registration, board/world/block
observations, activation payment and portal machine/failed-transfer effects are
explicit fixtures. Portal-only Forge `Entity.getEntityData()` calls are mapped to
an explicit identity-keyed NBT sink because the cached vanilla bytecode lacks that
Forge extension; entity equality and collision geometry are not replaced.
`ByteBufUtils` reading presentation serialization is **not** exercised. Altar
search compiles the full service against cached jars but tests only its reset.
Travel's stop fan-out uses counted effect sinks; each dependent cleanup is tested
separately. Weak-reference shape and deterministic retirement are checked, not
nondeterministic garbage-collection timing. Actual compass drop spawning is not
exercised.

These are not disk-save, real packet, successful cross-dimension transfer,
energy/vis payment, Forge lifecycle, packaged side-stripping, or full-build tests.
Run the R1 and R2 runners as compatibility checks as well; their separate limits
remain applicable. The real `GTSteamReborn.serverStopped` hookup is a source-level
integration check, not a running-server claim.
