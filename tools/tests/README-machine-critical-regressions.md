# Machine conservation and construction permissions

These are **bounded regression tests, not a Forge/game integration suite**. They make no
network requests and do not launch a game. Supply a local JDK and, where required, a
classpath from the mapped 1.7.10 development environment.

## Ordinary machines

```sh
python3 tools/tests/test_machine_conservation_r3.py \
  --javac "$JAVA_HOME/bin/javac" --classpath "$MACHINE_TEST_CP"
```

Expected: **56 checks, zero failures**. `MACHINE_TEST_CP` must provide real mapped
Minecraft Item/ItemStack/NBT, Forge FluidStack/FML and their runtime dependencies;
include Forge's language resource directory for detached FML registry bootstrap.
JDK 25 was used for the recorded run. GT, AE and world endpoints in this harness are
explicit fixtures, while the selected production method bodies execute unchanged.

Coverage: actual paid singularity fuel and one native-window close; physical/manual
slots; disabled/unknown stocking buses; bounded mode persistence; 30 L/s fractional
water including reload, tier changes and failed payment; partial ore output ownership,
tagged and large-count persistence, retry without duplicate prefixes; unload/removal
detachment without severing a newly assigned controller.

To reproduce the pre-fix comparison, add `--source-root /path/to/pre-r3-checkout`.
The recorded pre-R3 source fails **36 of the same 56 checks**.

Retained earlier suites: `test_machine_conservation_r2.py`,
`test_cluster_payment_receipts.py`, `test_hub_transfer_contracts.py`,
`singularity_mode_contract.py`, `working_fluid_payment_contract.py`,
`hub_pending_output_contract.py`, `drilling_fluid_owner_contract.py`, and
`resource_boundary_contract.py`. Check their command-line options separately;
the cluster suite requires Minecraft's `getItemDamage` naming alias rather than
the alternate `getMetadata` mapping. Do not change production code for this
detached-artifact naming difference.

## Critical construction permission seams

```sh
python3 tools/tests/critical_permission_regression.py \
  --java-home "$JAVA_HOME" --out build/critical-permission
```

Expected: **14 scenarios, 1,046 checks, zero failures**; 1,000 checks deliberately
repeat access on one controller to verify actor reuse. These are not 1,046
independent integration scenarios. The complete production access helper and
selected placement/supply/removal/payment-checkpoint methods execute against
finite world, player and ledger fixtures. JDK 17 was used.

To test old State code while retaining the new helper for harness compilation:

```sh
python3 tools/tests/critical_permission_regression.py \
  --java-home "$JAVA_HOME" --out build/critical-permission-before \
  --source-dir /path/to/pre-r3-checkout/src/main/java \
  --helper-dir src/main/java
```

The recorded pre-R3 State fails **12 scenarios**. Denied paid construction retains
its checkpoint; restoring permission must not pay twice. Other cases cover online,
offline and other-dimension owners, world/owner changes, malformed owner IDs,
client calls, and controller-local actor ownership.

## Native vanilla permission boundary

```sh
python3 tools/tests/run_critical_permission_native.py \
  --java-home "$JAVA_HOME" --classpath "$NATIVE_PERMISSION_CP" \
  --out build/critical-permission-native
```

Expected: **7 checks**. `NATIVE_PERMISSION_CP` needs real mapped Minecraft,
Forge/FakePlayer, authlib and runtime dependencies. The complete production helper
is compiled alongside the test. Actual vanilla WorldServer/DedicatedServer
spawn-protection and UUID operator-list lookup execute. Actors and server objects
are preallocated test fixtures: **FakePlayer construction, complete server
startup, Forge claim integrations and real game placement do not execute**.

The permission policy is `World.canMineBlock`, not a claim-system bypass promise.
Unknown owner identities fail closed. The helper deliberately avoids Forge 1.7's
profile-only global FakePlayerFactory cache and uses an exact-world, exact-owner,
controller-local actor instead.

Whole-source checks in the review also compiled the six changed machine classes
and complete CriticalMachineState/helper against cached native APIs. The detached
Minecraft artifact needed documented naming aliases and Forge API overlays; the
State compile used an explicitly throwing, **unexecuted** Block light-query shim.
Those checks are not a full Forge build. Full Gradle resolution, live AE-grid
failure injection, claim-mod integration and cross-save crash atomicity remain
unverified.
