package com.miaokatze.gtsr.client.weapons.outpost;

/**
 * Adapted from GT-Outpost by MiaoKatze, AGPL-3.0-or-later.
 * Original algorithms retained; portable networking/pose replaces turret context.
 */
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.util.ResourceLocation;

public final class MotorLoopSound implements ITickableSound {

    /**
     * 停转淡出时长（tick；4t=0.2s，任务包建议带 0.15~0.25s 内定档，R3 写死；
     * 非 FX Profile 调参——如需调整改此常量一处）
     */
    static final int MOTOR_FADEOUT_TICKS = 4;

    /** 淡出每 tick 音量减量（beginFadeOut 时按当前音量均分；volume 非负故恒非负） */
    private float fadeStep;
    /** 淡出剩余 tick 数（-1=未在淡出；0..N-1=淡出进行中） */
    private int fadeRemaining = -1;

    private final ResourceLocation sound;

    public MotorLoopSound() {
        this(GatlingFxProfile.SND_MOTOR);
    }

    public MotorLoopSound(String key) {
        sound = new ResourceLocation(key);
    }

    private double x, y, z;
    private float volume, pitch;
    private boolean done;

    /** 每 tick 由 manager 写入：终端基座中心 + Profile 映射 pitch/volume（含距离衰减） */
    public void tick(net.minecraft.entity.player.EntityPlayer te, float pitch, float volume) {
        this.x = te.posX;
        this.y = te.posY;
        this.z = te.posZ;
        this.pitch = pitch;
        this.volume = volume;
        this.fadeRemaining = -1; // 重新起转/继续高转：取消未完成淡出，音量恢复 Profile 映射
    }

    /** 停播请求（出界/条目摘除/登出换维等硬停场景；下一拍 update 生效） */
    public void requestStop() {
        this.done = true;
    }

    /** R3：停转淡出请求（幂等；done/已在淡出时为空操作） */
    public void beginFadeOut() {
        if (this.done || this.fadeRemaining >= 0) {
            return;
        }
        this.fadeRemaining = MOTOR_FADEOUT_TICKS;
        this.fadeStep = this.volume / MOTOR_FADEOUT_TICKS;
    }

    @Override
    public void update() {
        // R3：淡出斜坡逐 tick 推进（SoundManager.updateAllSounds 每 tick 调用本方法）；
        // 音量到 0 即置 done，下一拍 stopSound 移除——非 requestStop 的硬切断路径
        if (this.fadeRemaining > 0) {
            this.fadeRemaining--;
            this.volume = Math.max(0.0F, this.volume - this.fadeStep);
            if (this.fadeRemaining == 0 || this.volume <= 1.0E-4F) {
                this.volume = 0.0F;
                this.done = true;
            }
        }
    }

    @Override
    public boolean isDonePlaying() {
        return this.done;
    }

    @Override
    public ResourceLocation getPositionedSoundLocation() {
        return this.sound;
    }

    @Override
    public boolean canRepeat() {
        return true;
    }

    @Override
    public int getRepeatDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        return this.volume;
    }

    @Override
    public float getPitch() {
        return this.pitch;
    }

    @Override
    public float getXPosF() {
        return (float) this.x;
    }

    @Override
    public float getYPosF() {
        return (float) this.y;
    }

    @Override
    public float getZPosF() {
        return (float) this.z;
    }

    @Override
    public ISound.AttenuationType getAttenuationType() {
        return ISound.AttenuationType.NONE;
    }
}
