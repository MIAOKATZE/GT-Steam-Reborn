package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtsr.common.dimension.prosperity.encounter.ForgottenLakeEncounterRegistry;
import com.miaokatze.gtsr.common.dimension.prosperity.encounter.TileEntitySealedChest;
import com.miaokatze.gtsr.common.dimension.prosperity.lore.HistoryProgress;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterBlocks;
import com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterRuntime;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;

/** Reading evidence is scoped to one generated altar. There is no client request that records a read. */
public final class SpacetimeAltarStory {

    public static final String TITLE = "lore.entry.structures.spacetime_altar.title";
    private static final Map<EntityPlayerMP, Session> SESSIONS = new WeakHashMap<>();

    private SpacetimeAltarStory() {}

    public static void resetServerState() {
        SESSIONS.clear();
    }

    /** Retire idle reading leases as well as leases submitted for activation. Never load chunks here. */
    public static void expireSessions() {
        SESSIONS.entrySet()
            .removeIf(entry -> {
                EntityPlayerMP player = entry.getKey();
                Session session = entry.getValue();
                World world = session.world.get();
                return session.player.get() != player
                    || !ServerPacketAuthority.isCurrentPlayer(player, session.connection.get())
                    || world == null
                    || player.worldObj != world
                    || session.expires < world.getTotalWorldTime()
                    || session.board.get() == null;
            });
    }

    public static boolean place(World world, int x, int y, int z, String id) {
        if (world.isRemote || world.provider.dimensionId != 0) return false;
        net.minecraft.block.Block support = RemasterRuntime.resolve("Thaumcraft:blockCosmeticSolid");
        if (!world.setBlock(x + 4, y + 2, z + 3, support, 7, 2) || !world.setBlock(x + 9, y + 2, z + 3, support, 7, 2)
            || !world.setBlock(x + 4, y + 3, z + 3, RemasterBlocks.get("gtsr:draft_pressure_console"), 0, 2)
            || !world.setBlock(x + 9, y + 3, z + 3, ForgottenLakeEncounterRegistry.sealedChest, 0, 2)) return false;
        TileSpacetimeAltarStory board = new TileSpacetimeAltarStory();
        world.setTileEntity(x + 4, y + 3, z + 3, board);
        board.bind(id);
        TileEntity tile = world.getTileEntity(x + 9, y + 3, z + 3);
        if (!(tile instanceof TileEntitySealedChest)) return false;
        ((TileEntitySealedChest) tile).initializeAltar(id);
        return true;
    }

    private static boolean playerValid(EntityPlayer player) {
        return player instanceof EntityPlayerMP && ServerPacketAuthority.isCurrentPlayer(player)
            && player.isEntityAlive()
            && !player.worldObj.isRemote
            && player.worldObj.provider.dimensionId == 0
            && !(player instanceof FakePlayer);
    }

    /** Does not force-load an absent core chunk, and rejects copied tiles and retired structures. */
    public static SpacetimeAltarIndex.Entry owner(World world, String id) {
        if (world == null || world.isRemote || world.provider.dimensionId != 0 || id == null || id.isEmpty())
            return null;
        SpacetimeAltarIndex.Entry entry = SpacetimeAltarIndex.get(world)
            .find(id);
        if (entry == null || !entry.valid
            || !world.getChunkProvider()
                .chunkExists(entry.x >> 4, entry.z >> 4)
            || world.getBlock(entry.x, entry.y, entry.z) != SpacetimeAltarBlocks.core) return null;
        TileEntity core = world.getTileEntity(entry.x, entry.y, entry.z);
        return core instanceof TileSpacetimeAltar && id.equals(((TileSpacetimeAltar) core).instanceId()) ? entry : null;
    }

    public static boolean inRange(EntityPlayer player, String id) {
        if (!playerValid(player)) return false;
        SpacetimeAltarIndex.Entry entry = owner(player.worldObj, id);
        return entry != null && Math.abs(player.posX - (entry.x + .5)) <= 7
            && Math.abs(player.posZ - (entry.z + .5)) <= 7
            && player.posY >= entry.y - 8
            && player.posY <= entry.y + 7;
    }

    public static void enter(EntityPlayerMP player, String id) {
        HistoryProgress.spacetimeAltarEntered(player, id);
    }

    public static void activated(EntityPlayerMP player, String id) {
        HistoryProgress.spacetimeAltarActivated(player, id);
    }

    public static boolean read(EntityPlayer player, TileSpacetimeAltarStory board) {
        if (!validBoard(player, board)) return false;
        EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        SpacetimeAltarIndex.get(player.worldObj)
            .markRead(board.instanceId());
        HistoryProgress.spacetimeAltarRead(serverPlayer, board);
        Session session = new Session(serverPlayer, board, player.worldObj.getTotalWorldTime() + 1200);
        // Entity.equals uses the numeric ID, which vanilla respawn reuses. Replace the weak key too.
        SESSIONS.remove(serverPlayer);
        SESSIONS.put(serverPlayer, session);
        SpacetimeAltarStoryNetwork.show(serverPlayer, board, session.nonce);
        player.addChatMessage(new ChatComponentTranslation("gtsr.altar.chest_unlocked"));
        return true;
    }

    public static boolean validBoard(EntityPlayer player, TileSpacetimeAltarStory board) {
        if (!playerValid(player) || board == null
            || board.getWorldObj() != player.worldObj
            || player.getDistanceSq(board.xCoord + .5, board.yCoord + .5, board.zCoord + .5) > 64) return false;
        SpacetimeAltarIndex.Entry entry = owner(player.worldObj, board.instanceId());
        return entry != null && board.xCoord == entry.x - 2
            && board.yCoord == entry.y - 5
            && board.zCoord == entry.z - 3
            && player.worldObj.getTileEntity(board.xCoord, board.yCoord, board.zCoord) == board
            && player.worldObj.getBlock(board.xCoord, board.yCoord, board.zCoord)
                == RemasterBlocks.get("gtsr:draft_pressure_console");
    }

    public static boolean chestReady(TileEntitySealedChest chest) {
        SpacetimeAltarIndex.Entry entry = owner(chest.getWorldObj(), chest.getAltarInstance());
        return entry != null && entry.storyRead
            && chest.xCoord == entry.x + 3
            && chest.yCoord == entry.y - 5
            && chest.zCoord == entry.z - 3
            && chest.getWorldObj()
                .getTileEntity(chest.xCoord, chest.yCoord, chest.zCoord) == chest
            && chest.getWorldObj()
                .getBlock(chest.xCoord, chest.yCoord, chest.zCoord) == ForgottenLakeEncounterRegistry.sealedChest;
    }

    public static boolean chestClick(EntityPlayer player, TileEntitySealedChest chest) {
        if (!playerValid(player) || player.worldObj != chest.getWorldObj()
            || player.getDistanceSq(chest.xCoord + .5, chest.yCoord + .5, chest.zCoord + .5) > 64) return false;
        if (chestReady(chest) && HistoryProgress.hasSpacetimeAltarRead(player, chest.getAltarInstance())) return true;
        player.addChatMessage(new ChatComponentTranslation("gtsr.altar.chest_requires_read"));
        return false;
    }

    public static void activateFromReading(EntityPlayerMP player, UUID nonce) {
        if (!playerValid(player)) return;
        Session session = SESSIONS.get(player);
        // An old queued player must neither spend inventory nor consume a replacement entity's lease.
        if (session == null || session.player.get() != player
            || session.connection.get() != player.playerNetServerHandler) return;
        SESSIONS.remove(player);
        TileSpacetimeAltarStory board = session.board.get();
        if (!session.nonce.equals(nonce) || session.world.get() != player.worldObj
            || session.expires < player.worldObj.getTotalWorldTime()
            || !validBoard(player, board)) return;
        SpacetimeAltarIndex.Entry entry = owner(player.worldObj, board.instanceId());
        if (entry != null)
            ((TileSpacetimeAltar) player.worldObj.getTileEntity(entry.x, entry.y, entry.z)).activate(player);
    }

    private static final class Session {

        final WeakReference<EntityPlayerMP> player;
        final WeakReference<NetHandlerPlayServer> connection;
        final WeakReference<TileSpacetimeAltarStory> board;
        final WeakReference<World> world;
        final long expires;
        final UUID nonce = UUID.randomUUID();

        Session(EntityPlayerMP player, TileSpacetimeAltarStory board, long expires) {
            this.player = new WeakReference<>(player);
            this.connection = new WeakReference<>(player.playerNetServerHandler);
            this.board = new WeakReference<>(board);
            this.world = new WeakReference<>(board.getWorldObj());
            this.expires = expires;
        }
    }
}
