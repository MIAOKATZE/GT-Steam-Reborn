# Encounter and weapon transaction regressions (round 3)

## Repairs

- Cyclic spawners activate if **any eligible living, noncreative, non-FakePlayer** is inside the original strict 16-block radius. An ineligible nearer bystander no longer masks the survival player. Timer, recharge, quota and RNG ordering are unchanged.
- QLZ paid reloads retain per-source receipts and detached custom NBT. Topping up from several packs no longer silently discards rounds when the final pack cannot absorb the entire refund. Ordinary overflow is returned as an exactly sized consumable; a detached pack receives a new routing identity on use, not a copied live identity.
- A missing **mimic** pack is never reconstructed: the paid rounds remain loaded and the switch is rejected until its original vessel is available (or the rounds are fired). A present, already-regenerated mimic retains the prior saturating restoration policy. This is **not exact round conservation across regeneration**; no new regenerating asset is created.
- Refunds advance only by actual inventory acceptance or verified ownership of the returned drop in the world's loaded list / Forge capture list. Native `Entity.entityDropItem` returns a non-null object even if world admission fails, so non-null alone is insufficient. An unpaid remainder stays in gun NBT, prevents type/mode relabeling, and can be retried after save/reload without duplication.
- Existing magazines lacking receipts migrate their aggregate paid count and final-pack identity, including an all-free economy top-up before the final-pack pointer changes. No zero-paid receipt is appended. Missing historical custom NBT cannot be recovered. No uncertain legacy amount is converted into a new mimic vessel.
- A present but unreadable receipt outer/element type is preserved opaquely and blocks reload debit, shot migration and refund. The constant-time raw subtype check accepts legitimate empty END/COMPOUND lists; an emptied String-typed list is still rejected because native `removeTag` does not reset its subtype.

Ordinary `AmmoItem` has no container-item return and removes itself at zero remaining rounds. The repository's only ordinary-to-mimic recipe requires a full (damage 0) pack; no independent empty-container refund/recycling value was found. These checks do not constrain third-party recipes or arbitrary event handlers.

## Offline execution

```sh
python3 tools/prosperity/verify_encounter_round3.py --workspace /path/to/audit-workspace
python3 tools/prosperity/verify_encounter_bounds.py --current-world --out /path/to/r2-rerun
python3 tools/tests/resource_boundary_contract.py
```

The first two scripts use already cached native MC, Forge, GT and source-matching GTSR jars and local JDK 17; they make no downloads and do not invoke Gradle. The R3 baseline is the prior audit's R2 snapshot where present, otherwise commit `57d082e9e748c8918ae3bb2c33df386c9a1cc99e`. `--out` selects a separate artifact directory. Verification JSON records production/test hashes, compiler output and each expected-red / passing case. Two explicitly labeled pre-integration variants replay the previous all-free-top-up early return and missing schema preflight to prove those later regressions fail; they are not mislabeled as the repository baseline.

### Exercised boundaries

- Complete modified `RemasterRuntime` and `WeaponController` compile against cached native APIs (mechanical MCP-name adaptations are listed in the runner).
- Actual Runtime timer activation and eligibility scan use packaged authored node data, native NBT/storage, and allocated native WorldServer/player instances. Tests cover creative/dead/FakePlayer masking, strict radius endpoints and exact subsequent RNG sequence; they do not execute spawning, entity AI or recharge gameplay.
- Native `ItemStack`, `InventoryPlayer`, NBT list/compound copying and ItemStack save/read execute for refund, source provenance, legacy migration, paid-only refunds, failed/partial delivery, retry, route identity and mimic behavior. A 1,000-iteration sequence verifies that outstanding receipt count stays at twelve, not historical reload count.
- Full actual `finishReload` and `update` execute in a separate transaction run. Only projectile construction, physical effect pose and network effect transport are replaced with explicit fixtures. Actual controller debit, source removal, shot-admission rollback, accepted-shot receipt consumption and save/read/refund are not extracted or replaced. Player economy RNG sequence is checked.
- The existing resource-boundary suite retains its singularity partial-insertion assertions and adds rejected-drop remainder/retry and missing-mimic checks.
- `WeaponAuditCommand`'s synthetic fixture resets and overflow expectations match the receipt contract. Its complete source compiles with two compile-only Forge API signatures and a native bridge visibility adapter. **The guarded server command was not run.**

### Limits

No complete Gradle build, Forge startup, multiplayer session, rendering or gameplay acceptance is claimed. The inherited Maven Central permission denial was not retried or bypassed. API overlays add only documented missing Forge fields/signatures; their throwing methods are never executed. Drop-admission tests model accepted, captured and rejected returns; arbitrary third-party handlers that mutate/replace ownership beyond these APIs are not simulated. Existing loot-hook allocation/NBT-size limits remain as documented in `README-encounter-bounds.md`.
