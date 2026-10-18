package blockrenderer6343.client.world;

/** No world access occurs in hatch/ofBlock catalog queries; avoid the client-only SRG world initializer. */
public abstract class DummyWorld extends net.minecraft.world.World {
    public static final DummyWorld INSTANCE = null;

    private DummyWorld() {
        super(null, "unused", (net.minecraft.world.WorldProvider) null, null, null);
    }
}
