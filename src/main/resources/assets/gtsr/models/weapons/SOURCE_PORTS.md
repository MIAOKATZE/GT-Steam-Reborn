# GT-Outpost portable weapon adaptations

Source project: GT-Outpost by MiaoKatze, https://github.com/MIAOKATZE/GT-Outpost.
Local source used: E:/CodeGT/GT-Outpost.

The adapted Java effects in com.miaokatze.gtsr.client.weapons.outpost retain
their original attribution and use the project's AGPL-3.0-or-later license.
The full license is included in SOURCE_LICENSE.txt. The separate original
asset license is preserved without modification in SOURCE_ASSET_LICENSE.txt.

Direct source implementations used include OverrideHudRenderer.drawArcBand,
GatlingClientFxManager/MotorLoopSound, GatlingFxProfile, FxEntityWorldPass,
EntityMuzzleFlash, SmokePuffEntity, EntityVisualCasing/RenderVisualCasing,
ProjectileTrailFx, ExplosionEffectFactory and CurvedAimTrajectoryRenderer.
QLZ04 uses the round26 CombatEffectsClient/WeaponFxProfiles explosive impact
branch and its SupportBlastMesh, PixelBlastMesh and BlastFxGeometry renderers,
with actual blast radius 2.0. T20 uses only SMALL flash and smoke visuals.
Portable adaptations change resource names, player attachment, budgets and
muzzle flash size; they do not require GT-Outpost at runtime.

The new lm12_heat.obj, t20_heat.obj and qlz04_heat.obj meshes are derived from
the corresponding existing portable barrel geometry, preserving its vertices
and projecting a separate heat UV. The existing base OBJ and PNG files are
unchanged. textures/weapons/turret_heat_ramp.png is copied byte for byte from
GT-Outpost textures/fx/turret_heat_ramp.png. The heat rendering follows
TurretObjRenderer.renderRedHotPass and GatlingFxProfile heat colors.

Weapon sound assets, including gatling_motor.ogg, originate from GT-Outpost.
Detailed source paths, adaptation boundaries and checksums are recorded in
the project's plan/implementation/weapons-v1.20.89 evidence.
