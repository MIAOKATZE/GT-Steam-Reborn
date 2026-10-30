# GTSR packet / GUI authority regressions (R1)

Run offline with the cached official development dependencies:

```sh
python tools/tests/network/verify_packet_authority.py --workspace /path/to/audit-workspace
```

The workspace must contain `dependency-probe/gtsr-v2.0.43-dev.jar`, the cached
GT/MUI/Core dependencies, `review-monitoring/minecraft-mcp.jar`, the cached Forge
1614 binaries/mappings under `.gradle-build`, and the JDK 17 toolchain. This runner
never downloads anything or runs Gradle. Default output is `build/review-packet-authority`.

## Reproduced baseline → fixed

Against `57d082e9e748c8918ae3bb2c33df386c9a1cc99e`, all 14 scenarios fail with their
intended behavioral assertion; all pass with the patched production sources:

- Terminal, hub-binding, and wand handlers retained 10,000 requests from one
  connection. Admission is now 16/connection and 256/channel; one tick drains at
  most 64 entries, including invalid entries. A separate 55-check concurrent
  queue test verifies FIFO, identity ownership, global/per-owner limits and
  restored capacity after poll/clear.
- A disconnected player's terminal request or hub-binding request still reached
  dispatch/world access. A replaced handler on the same player also retained
  queued authority. Dispatch now requires the captured connection, its exact
  current player, an open native Netty channel, the exact online entity, and the
  current server world. Positive and negative native authority cases pass.
- A delayed old-channel terminal data callback arriving *after* reconnect used
  the new epoch and overwrote the new cache. The actual S2C handler now passes its
  originating connection through the Minecraft scheduler; both epoch and
  connection identity are checked before writing/opening.
- All four terminal envelope decoders accepted trailing bytes. They now require
  exact envelope length (including payload length before allocating).
- The actual History MUI session remained active after its connection closed
  while its held terminal and `openContainer` were retained. History and the six
  machine-terminal session families now capture/revalidate the connection before
  queued writes. The real History session's connected path remains valid.
- Absorb particles were sent to same-coordinate players in other dimensions.
  Dispatch now requires the exact source world.

Wand and binding queues expose `clearServerState()`; `GTSteamReborn.serverStopped`
wires both beside the existing terminal cleanup. The queue/session-clear test
passes. `Mui2ServerActions` remains lossless: it also owns already-committed chest
viewer cleanup obligations, which must not be silently dropped.

The existing `TerminalLifecycleRegression` was adapted to the new API/queue and
stronger actor validation. Its client, cluster, server, and cooldown modes also
pass (25 checks) in the detached overlay.

## Evidence and limits

The runner writes baseline/patched source copies, compiler logs, each scenario's
log, dependency classpath and a SHA-256 receipt. It compiles whole changed classes
against native Minecraft, locally remapped official Forge, native Netty, native
MUI, native GT/Core and the official GTSR development jar. Existing MCP naming
variants for the scheduler, TileEntity world getter, and font field are normalized
only in the detached source copies, with no behavior substitution.

Unsafe constructs isolated player/world/container state; native MUI manager
getters use field-initialized manager instances. The History test exercises the
exact `Session.active()` gate, not an opened/rendered UI. FX outbound transport is
a recording wrapper. No full Gradle/package build, side-transformer or Mixins,
GL/resource rendering, real game, or TCP session is claimed.

For the next independent rounds: per-GUI rejected-request ACK/liveness, legacy
terminal access/range semantics, client render/cache resource lifecycle, and
optional client classloading still warrant deeper coverage. The new admission
caps must never be generalized to inventory or lease-release obligations.

## Independent second round: local factories, navigation admission, weapon reload

```sh
python tools/tests/network/verify_gui_lifecycle.py --workspace /path/to/audit-workspace
```

This second runner compiles whole current classes offline (plus the unchanged
remote factory/data classes), then reproduces **13 intended baseline failures →
13 repaired scenarios**. Two additional control scenarios bring the patched run
to **100 assertions**. `--baseline-source /path/to/pre-R2/source-root` can instead
compare against a preserved first-round source snapshot; otherwise the same
original baseline commit above is used. The receipt includes source, fixture,
runner and dependency hashes.

- **C2S factory pre-lookup authority:** Native ModularUI2 `.92` registers
  `OpenGuiPacket` in both directions. Its server entry decodes a named factory's
  coordinates and calls `GuiManager.open`, which constructs the panel before
  testing interaction predicates. Five local factory families resolved distant
  target tiles before rejecting; six also consulted near but unloaded chunks.
  Local actor/range/height/loaded-chunk checks now precede all initial tile
  resolution. Existing 8-block inventory/Critical and 64-block handheld ranges
  are retained. Tests execute native packet write/read/server dispatch into the
  real factories; a delegating boundary stops **after** the real denied panel is
  constructed and **before** native container/game installation.
- **Remote negative controls:** The exact current remote descriptor encoder and
  decoder strip a non-null server-only grant sentinel. Missing/replayed grants,
  reused epochs and cross-dimension far coordinates cannot resolve a tile or
  pass the actual remote factory; native C2S dispatch reaches only the denied
  panel. Local hubs do not trust a remote subtype without its grant. Appending a
  flag cannot make a local cluster descriptor remote; invalid remote kinds and
  trailing bytes are rejected. The sentinel is deliberately **not** claimed to
  be a valid grant or a remote-opening happy path. Remote ownership policy is
  unchanged.
- **Entanglement navigation/apply race:** Navigation has no client awaiting
  flag. Sharing one server pending flag silently lost an immediately following
  apply/reset request and its response obligation. Navigation and writes now
  have independent admission flags: at most two queued tasks per session, even
  under a 10,000-iteration flood. Both flags release after inactive work. The
  real client rejection decoder separately clears `awaiting`; a live server
  business/ACK round trip is **not** claimed.
- **Weapon mesh reload:** The renderer now registers a resource reload listener,
  releases all owned display-list IDs and clears both OBJ and compiled-geometry
  caches. A recording deletion consumer verifies every cached ID is released
  exactly once and repeated cleanup is idempotent. Actual GL is not exercised.

The queue remains lossless for accepted cleanup obligations: this work does not
cap or drop `Mui2ServerActions`. Existing R1 packet/connection regressions and
concurrent queue tests were re-run successfully against the second-round state.

**Additional fixture limits:** the cached vanilla MCP jar lacks Forge-patched
`CreativeTabs(String)`, preventing full native GT machine initialization for the
server ACK exercise. Cached MUI client signatures also mention absent lwjgl3ify
input APIs (link-only types supplied; never invoked). Three missing fastutil map
constructors use explicit `LinkedHashMap` field fixtures; no specialized map
operations are tested. These are narrowly scoped detached boundary tests, not a
full build, dedicated-server side-transformer check, game/GUI success, renderer
preview, or TCP test. Native stale-manager reachability and shutdown cancellation
of already-accepted viewer-release work remain separate follow-up targets.

## Independent third round: retained MUI managers and remote connection grants

```sh
python tools/tests/network/verify_gui_authority.py --workspace /path/to/audit-workspace
```

The third runner compiles whole production classes and reproduces **six intended
baseline failures**, then passes **35 assertions in ten patched scenarios**.
The two production files repaired in this round were unchanged by R1/R2, so the
original baseline above is also their pre-R3 state.

- **Critical button authority and thread boundary:** native decoded MUI
  `PacketSyncHandler` packets can still reach a manager after its non-disposing
  close. Critical button commands previously ran immediately, including for
  closed managers, disconnected channels and replaced connection handlers. They
  now enqueue only an already-decoded fixed button command, with one pending
  slot shared by all buttons in that GUI. Server END revalidates the captured
  connection, captured container, current manager/player/container relationship,
  live container predicate and Critical permission. The slot releases even when
  authority has expired. No packet buffer or mutable MouseData is deferred.
- **Close/reopen interval:** a manager can retain and reopen its original
  container. Its native `onClose()` does not invoke panel close listeners; the
  verified native open callback therefore advances an interval generation.
  Queued commands cannot follow a manager into a replacement container or revive
  after close/reopen. A fresh command in the new interval still works. Primary
  button filtering, START-versus-END dispatch, cross-button flood admission,
  target predicate revocation and pending-slot release are tested.
- **Authoritative power mode:** execution consults `multiblock.isKindSelector()`
  instead of cached `loomController` display sync. Whole-class compilation and
  an explicit source guard cover this branch; no native controller runtime test
  is claimed for the power toggle itself.
- **Remote grant connection lifetime:** the real `Grant.valid` and
  `RemoteTerminalGuiData.canUseRemote` previously accepted an otherwise valid
  server-issued grant after disconnect or issuing-handler replacement. A grant
  now captures and revalidates its issuing connection before target lookup.
  Positive live grants, exact online entity, copied terminal stacks, ownership
  changes, removed targets and obsolete worlds are covered. Target ownership,
  loaded-target resolution and item identity policy remain unchanged.
- **Cleanup obligations stay lossless:** `Mui2ServerActions` is unchanged. The
  regression drains all 10,000 accepted generic cleanup callbacks; request
  admission limits must not be imposed on committed viewer-release work.
  Server-stop cancellation is not changed or claimed as tested here.

The old jar-only `MachineTerminalRemoteContract` now supplies a native connected
player/server fixture to satisfy grant liveness. It compiles in the detached
classpath, but its full GT lifecycle runtime is not part of the new passing run.

**Evidence and fixture boundaries:** native MC/Forge/Netty/MUI packet decoding,
retained-manager routing, open callbacks and queue draining execute against
whole production source overlays. Remote target/cache-node objects are explicit
ASM-generated API fixtures, implementing only the methods actually called by the
real grant/status path; they are not native GT tile lifecycle tests. The cached
Minecraft artifact lacks Forge's `Entity.getEntityData()` and
`EntityPlayer.PERSISTED_NBT_TAG`; a detached link-only API addition supplies these
unused signatures. Its method throws if invoked, so no history persistence is
simulated. The R2 fastutil constructor and unused lwjgl3ify API fixtures remain
explicit. All sources, fixtures, dependency identities and results are hashed in
`verification.json`. No Gradle/package build, side transformer, Mixin startup,
real game, rendered GUI, GL or TCP success is claimed.
