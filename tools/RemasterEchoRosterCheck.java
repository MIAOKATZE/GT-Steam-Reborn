import com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoKind;

/** Save/wire compatibility and exact authorized roster, independent of a Minecraft client. */
public final class RemasterEchoRosterCheck {
    public static void main(String[] args) {
        String[] legacy = { "dr-01", "dr-03", "dr-05", "dr-07", "dr-10", "dr-11", "dr-12", "dr-15",
            "dr-20", "di-02", "di-05", "di-08", "di-10", "di-13", "dc-02", "dc-08", "do-01" };
        for (int i = 0; i < legacy.length; i++) {
            if (!EchoKind.byWireId(i).code.equals(legacy[i])) throw new AssertionError("old ordinal " + i);
            if (EchoKind.byCode(legacy[i]).ordinal() != i) throw new AssertionError("stable code " + i);
        }
        for (int i = 1; i <= 20; i++) EchoKind.byCode(String.format("dr-%02d", i));
        for (int i = 1; i <= 15; i++) EchoKind.byCode(String.format("di-%02d", i));
        if (EchoKind.values().length != 39) throw new AssertionError("authorized roster count");
        if (EchoKind.DO02.style != EchoKind.BattleStyle.CONTROLLED) throw new AssertionError("controlled DO02");
        for (String denied : new String[] {"do-03", "do-04", "do-05", "dc-01", "dc-03"}) {
            try { EchoKind.byCode(denied); throw new AssertionError(denied); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: 17 stable ordinals; 20 DR + 15 DI; DO02 controlled; no extra DC/DO");
    }
}
