# Encounter loot and cyclic-spawner bounds

Offline regression runner (no downloads, full Gradle build, server or client launch):

```sh
python3 tools/prosperity/verify_encounter_bounds.py --workspace /path/to/audit-workspace
```

The workspace supplies cached Minecraft MCP, Forge MCP, GT5U MCP and matching GTSR v2.0.43 dev jars, dependencies, ASM 9.8 and JDK 17. `--out` selects an evidence directory. `--baseline` selects the comparison Git revision. `--current-world` additionally compiles the current RemasterData/Site and support sources, rather than the baseline support sources used by default to isolate these repairs. The runner records source hashes and all compile/test logs in `verification.json` and adjacent files.

## Assertions

- An `Integer.MAX_VALUE` external stack is rejected before unbounded `ItemStack`/NBT copies. A counted native NBT subclass interrupts the baseline after 60 copies, without triggering an OOM.
- Capture retains at most 53 normalized ordinary-slot stacks; repeated emissions beyond capacity stop only that candidate. The next valid dungeon candidate succeeds, ordinary target overflow still skips, and invalid max-stack sizes still abort.
- Normal split/merge/NBT/16-stack-limit semantics and valid native-generator RNG consumption remain unchanged. Repeated native slot selections do not discard emissions.
- Actual `RemasterRuntime.death` consumes only admitted, authored, server-side identities once. Completed legacy pairs compact; pending unloaded survivors, unrelated keys, opaque keys and cumulative serial/death counters survive. A detached native-NBT save/read and 10,000 further deaths demonstrate that completed kills do not grow permanent identity flags.

## Exact evidence boundary

The complete modified production sources compile, with mechanical MCP name substitutions listed in the runner. The loot tests execute native Minecraft ItemStack, NBT and `WeightedRandomChestContent` behavior, plus actual GTSR normalization/capture/candidate code. The inventory is a detached dynamic proxy. Full GT registry initialization and the complete tier reward planner are not executed.

Spawner tests execute actual production Runtime.death, ledger/storage methods, authored packaged spawner metadata and native Minecraft NBT/MapStorage. The world is an Unsafe-allocated native WorldServer. Its vanilla class receives only the Forge `perWorldStorage` field needed for storage access. The native Entity class receives a **throwing, never-executed** `getEntityData` API seam solely to compile the complete Runtime source. These are not a complete Forge runtime, tick-loop/spawn-admission simulation or gameplay acceptance test.

## Deliberate remaining boundaries

This repair bounds **GTSR-owned** splitting and intermediate captures. Forge's patched `WeightedRandomChestContent.generateChestContent` calls `ChestGenHooks.generateStacks`, which can allocate an entire emission array before invoking the capture inventory. Custom third-party generators may also allocate or run arbitrary work before callbacks. Those upstream allocations, large individual NBT payloads and arbitrary mod behavior are **not** bounded by this patch. The cached Minecraft generator exercised here is the native vanilla implementation; the Forge emission-array path is inspected in the cached patch, not executed. Native generation/RNG behavior has not been replaced or specialized.

Spawner identity storage is proportional to outstanding admissions, not all historical kills. Pending/unloaded survivors are deliberately never evicted merely to impose a hard limit. Cumulative `serial`/`deadCount`, reward gates, recharge timing, XP and friendly-fire/singularity policy remain unchanged.
