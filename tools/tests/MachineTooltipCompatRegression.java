/**
 * Builds the descriptions of all 78 production-registered GTSR machines against a supplied upstream GT runtime.
 * For the 48 ISecondaryDescribable machines, both primary and secondary descriptions are read directly;
 * the remaining 30 machines use getDescription(). This exercises tooltip construction without the keyboard
 * selection in getCurrentDescription(). It does not run MachineTooltipsLoader, localization registration,
 * client rendering, or a Minecraft client/server launch. Reuses the Issue37 isolated registration bootstrap.
 */
public final class MachineTooltipCompatRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("MachineTooltipCompatRuntime", args[0]);
    }
}

class MachineTooltipCompatRuntime {
    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        com.miaokatze.gtsr.loader.MachineLoader.initMachines();
        int machines = 0, primary = 0, secondary = 0, direct = 0, failures = 0;
        for (int id = 0; id < gregtech.api.GregTechAPI.METATILEENTITIES.length; id++) {
            var registered = gregtech.api.GregTechAPI.METATILEENTITIES[id];
            if (registered == null || !registered.getClass().getName().startsWith("com.miaokatze.gtsr.")) continue;
            var mte = registered.getBaseMetaTileEntity().getMetaTileEntity();
            machines++;
            System.out.println("BEGIN id=" + id + " type=" + mte.getClass().getName());
            try {
                if (mte instanceof gregtech.api.interfaces.ISecondaryDescribable s) {
                    String[] description = s.getSecondaryDescription();
                    secondary++;
                    System.out.println("SECONDARY length=" + (description == null ? -1 : description.length));
                    description = s.getPrimaryDescription();
                    primary++;
                    System.out.println("PRIMARY length=" + (description == null ? -1 : description.length));
                } else {
                    String[] description = mte.getDescription();
                    direct++;
                    System.out.println("DESCRIPTION length=" + (description == null ? -1 : description.length));
                }
            } catch (Throwable t) {
                failures++;
                System.out.println("FAIL id=" + id + " type=" + mte.getClass().getName());
                t.printStackTrace(System.out);
            }
        }
        System.out.println("RESULT tooltips version=" + version + " machines=" + machines + " primary=" + primary
            + " secondary=" + secondary + " direct=" + direct + " failures=" + failures);
        if (machines != 78 || primary != 48 || secondary != 48 || direct != 30 || failures != 0) {
            throw new AssertionError("tooltip construction failed or registered machine coverage changed");
        }
    }
}
