package com.miaokatze.gtsr.common.weapons;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
/** Projectile/effect transport seam only; the complete controller owns payment and spawn result handling. */
public final class EntityWeaponProjectile extends Entity {
    public EntityWeaponProjectile(World w, EntityLivingBase p, WeaponKind k, float damage, float penetration, int fire) { super(w); }
    protected void entityInit() {}
    protected void readEntityFromNBT(NBTTagCompound n) {}
    protected void writeEntityToNBT(NBTTagCompound n) {}
    public void freezeEnchantments(ItemStack s) {}
    public void setMode(int m) {}
    public void setCritical(boolean b) {}
    public boolean critical() { return false; }
    public void detonate() { setDead(); }
}
