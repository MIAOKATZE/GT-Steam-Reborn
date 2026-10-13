package cpw.mods.fml.common;

/** Detached test gate: supply only the static side query needed by GT's tooltip superclass. No FML bootstrap. */
public final class FMLCommonHandler {
    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();
    public static FMLCommonHandler instance() { return INSTANCE; }
    public cpw.mods.fml.relauncher.Side getEffectiveSide() { return cpw.mods.fml.relauncher.Side.SERVER; }
    public cpw.mods.fml.relauncher.Side getSide() { return cpw.mods.fml.relauncher.Side.SERVER; }
}
