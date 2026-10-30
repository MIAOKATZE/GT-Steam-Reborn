package com.miaokatze.gtsr.common.terminal;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiPalette;
import com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.TerminalUiStyle;
import com.miaokatze.gtsr.common.gui.mui2.TerminalViewport;
import com.miaokatze.gtsr.common.network.ServerPacketAuthority;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Item right-click list. Index requests refer only to this server-created list, never client NBT or coordinates. */
public final class MachineHistoryFactory implements UIFactory<MachineHistoryFactory.Data> {

    private static final MachineHistoryFactory INSTANCE = new MachineHistoryFactory();
    private static boolean registered;

    private MachineHistoryFactory() {}

    public static void register() {
        if (registered) return;
        registered = true;
        GuiManager.registerFactory(INSTANCE);
        RemoteTerminalFactory.register();
        Mui2ServerActions.register();
    }

    public static void open(EntityPlayer player) {
        open(player, data -> GuiManager.open(INSTANCE, data, (EntityPlayerMP) player));
    }

    static void open(EntityPlayer player, Consumer<Data> opener) {
        if (player instanceof EntityPlayerMP server && !(player instanceof FakePlayer)
            && MachineTerminalHistory.held(player)) {
            MachineTerminalHistory.terminalId(server);
            ItemStack requestedTerminal = server.getHeldItem()
                .copy();
            int requestedSlot = server.inventory.currentItem;
            Object previousContainer = server.openContainer;
            NetHandlerPlayServer connection = server.playerNetServerHandler;
            // C08 copies the held stack after onItemRightClick and still queries the old inventory slots.
            // Bind the session only after that packet has finished; later stack replacements still revoke it.
            Mui2ServerActions.enqueue(() -> {
                if (!ServerPacketAuthority.isCurrentPlayer(server, connection)
                    || server.openContainer != previousContainer
                    || server.inventory.currentItem != requestedSlot
                    || !MachineTerminalHistory.held(server)
                    || !ItemStack.areItemStacksEqual(requestedTerminal, server.getHeldItem())) return;
                opener.accept(new Data(server, MachineTerminalHistory.read(server), true));
            });
        }
    }

    @Override
    public String getFactoryName() {
        return "gtsr:machine_terminal_history";
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, Data data) {
        return data.valid(player);
    }

    @Override
    public ModularPanel createPanel(Data data, PanelSyncManager manager, UISettings settings) {
        if (!data.valid(data.getPlayer())) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("machine_history_denied");
        }
        settings.canInteractWith(player -> data.valid(player));
        Session session = new Session(data);
        manager.syncValue("machine_history", session);
        ParentWidget<?> content = new ParentWidget<>().size(384, 234);
        content.child(
            IKey.lang("gtsr.terminal.history.title")
                .asWidget()
                .pos(0, 4)
                .size(384, 14)
                .color(GtsrGuiPalette.TEXT_TITLE));
        content.child(
            IKey.lang("gtsr.terminal.history.hint")
                .asWidget()
                .pos(0, 20)
                .size(384, 12)
                .scale(.75f)
                .color(GtsrGuiPalette.TEXT_MUTED));
        for (int row = 0; row < 6; row++) {
            final int offset = row;
            ButtonWidget<?> button = new ButtonWidget<>().name("machine_history_row_" + row)
                .pos(0, 36 + row * 27)
                .size(352, 25)
                .background(TerminalUiStyle.CARD)
                .hoverBackground(TerminalUiStyle.ROW_HOVER)
                .setEnabledIf(
                    widget -> session.index(offset) < data.entries.size() && !session.deleted(session.index(offset)))
                .onMousePressed(mouse -> {
                    if (mouse == 0) session.request(0, session.index(offset));
                    return true;
                });
            ParentWidget<?> cells = new ParentWidget<>().size(352, 25);
            cells.child(
                IKey.dynamic(() -> com.miaokatze.gtsr.common.gui.mui2.hub.HubClientText.clip(session.name(offset), 340))
                    .asWidget()
                    .name("machine_history_name_" + row)
                    .pos(5, 1)
                    .size(342, 11)
                    .color(GtsrGuiPalette.TEXT_BODY));
            cells.child(
                IKey.dynamic(() -> session.description(offset))
                    .asWidget()
                    .name("machine_history_position_" + row)
                    .pos(5, 13)
                    .size(342, 10)
                    .scale(.7f)
                    .color(GtsrGuiPalette.TEXT_MUTED));
            button.child(cells);
            button.addTooltipLine(IKey.dynamic(() -> session.name(offset)))
                .addTooltipLine(IKey.dynamic(() -> session.description(offset)))
                .tooltipAutoUpdate(true);
            content.child(button);
            content.child(
                TerminalUiStyle.button("gtsr.terminal.history.delete")
                    .name("machine_history_delete_" + row)
                    .pos(355, 36 + row * 27)
                    .size(29, 25)
                    .overlay(IKey.str("x"))
                    .setEnabledIf(
                        widget -> session.index(offset) < data.entries.size()
                            && !session.deleted(session.index(offset)))
                    .onMousePressed(mouse -> {
                        if (mouse == 0) session.request(1, session.index(offset));
                        return true;
                    })
                    .addTooltipLine(IKey.lang("gtsr.terminal.history.delete")));
        }
        content.child(
            IKey.lang("gtsr.terminal.history.empty")
                .asWidget()
                .pos(8, 50)
                .size(368, 16)
                .color(GtsrGuiPalette.TEXT_MUTED)
                .setEnabledIf(widget -> session.remaining() == 0));
        content.child(
            TerminalUiStyle.button("gtsr.mui2.hub.previous")
                .name("machine_history_previous")
                .pos(0, 202)
                .size(60, 18)
                .overlay(
                    IKey.str("<")
                        .color(() -> session.page > 0 ? GtsrGuiPalette.TEXT_BODY : GtsrGuiPalette.TEXT_MUTED))
                .background(
                    new DynamicDrawable(() -> session.page > 0 ? TerminalUiStyle.BUTTON : TerminalUiStyle.DISABLED))
                .hoverBackground(
                    new DynamicDrawable(() -> session.page > 0 ? TerminalUiStyle.HOVER : TerminalUiStyle.DISABLED))
                .onMousePressed(mouse -> {
                    if (mouse == 0 && session.page > 0) session.page--;
                    return true;
                }));
        content.child(
            IKey.dynamic(() -> (session.page + 1) + " / " + session.pages())
                .asWidget()
                .pos(70, 204)
                .size(240, 14)
                .color(GtsrGuiPalette.TEXT_LABEL));
        content.child(
            TerminalUiStyle.button("gtsr.mui2.hub.next")
                .name("machine_history_next")
                .pos(324, 202)
                .size(60, 18)
                .overlay(
                    IKey.str(">")
                        .color(
                            () -> session.page + 1 < session.pages() ? GtsrGuiPalette.TEXT_BODY
                                : GtsrGuiPalette.TEXT_MUTED))
                .background(
                    new DynamicDrawable(
                        () -> session.page + 1 < session.pages() ? TerminalUiStyle.BUTTON : TerminalUiStyle.DISABLED))
                .hoverBackground(
                    new DynamicDrawable(
                        () -> session.page + 1 < session.pages() ? TerminalUiStyle.HOVER : TerminalUiStyle.DISABLED))
                .onMousePressed(mouse -> {
                    if (mouse == 0 && session.page + 1 < session.pages()) session.page++;
                    return true;
                }));
        content.child(
            IKey.dynamicKey(() -> IKey.lang("gtsr.terminal.history." + session.receipt))
                .asWidget()
                .pos(0, 222)
                .size(384, 11)
                .scale(.7f)
                .color(GtsrGuiPalette.TEXT_ACCENT));
        return ModularPanel.defaultPanel("machine_terminal_history", 400, 240)
            .background(TerminalUiStyle.HUB_PANEL)
            .child(
                TerminalViewport.wrapExact("machine_history_viewport", content, 384, 234)
                    .pos(8, 0)
                    .widthRelOffset(1f, -16)
                    .heightRelOffset(1f, -6));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(Data data, ModularPanel panel) {
        return new BoundedTerminalScreen("gtsr", panel, 400, 240);
    }

    @Override
    public void writeGuiData(Data data, PacketBuffer buffer) {
        buffer.writeByte(data.entries.size());
        for (MachineTerminalHistory.Entry entry : data.entries) {
            buffer.writeInt(entry.dimension);
            buffer.writeInt(entry.x);
            buffer.writeInt(entry.y);
            buffer.writeInt(entry.z);
            ByteBufUtils.writeUTF8String(buffer, entry.name);
        }
    }

    @Override
    public Data readGuiData(EntityPlayer player, PacketBuffer buffer) {
        if (buffer.readableBytes() > 16384) throw new IllegalArgumentException("Oversized terminal descriptor");
        int count = buffer.readUnsignedByte();
        if (count > MachineTerminalHistory.LIMIT) throw new IllegalArgumentException("Too many terminal entries");
        java.util.ArrayList<MachineTerminalHistory.Entry> entries = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int dimension = buffer.readInt(), x = buffer.readInt(), y = buffer.readInt(), z = buffer.readInt();
            String name = ByteBufUtils.readUTF8String(buffer);
            if (name.length() > 96) throw new IllegalArgumentException("Terminal name too long");
            entries.add(new MachineTerminalHistory.Entry(dimension, x, y, z, 0, null, "", "", name));
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing terminal descriptor");
        return new Data(player, entries, false);
    }

    public static final class Data extends GuiData {

        final List<MachineTerminalHistory.Entry> entries;
        private final ItemStack terminal;
        private final String terminalId;
        private final int slot;
        private final boolean issued;
        private final NetHandlerPlayServer connection;

        Data(EntityPlayer player, List<MachineTerminalHistory.Entry> entries, boolean issued) {
            super(player);
            this.entries = Collections.unmodifiableList(new java.util.ArrayList<>(entries));
            terminal = player.getHeldItem();
            slot = player.inventory.currentItem;
            this.issued = issued;
            this.connection = ServerPacketAuthority.connection(player);
            terminalId = issued ? MachineTerminalHistory.terminalId(player) : "";
        }

        boolean valid(EntityPlayer player) {
            return player == getPlayer() && !player.isDead
                && !(player instanceof FakePlayer)
                && (player.worldObj.isRemote || issued && ServerPacketAuthority.isCurrentPlayer(player, connection)
                    && MachineTerminalHistory.held(player)
                    && player.inventory.currentItem == slot
                    && player.getHeldItem() == terminal
                    && terminalId.equals(MachineTerminalHistory.terminalId(player)));
        }
    }

    private static final class Session extends SyncHandler<Session> {

        final Data data;
        final AtomicBoolean pending = new AtomicBoolean();
        volatile String[] statuses;
        volatile String receipt = "ready";
        final boolean[] deleted;
        long lastPoll = Long.MIN_VALUE, lastAction = Long.MIN_VALUE;
        int page;

        Session(Data data) {
            this.data = data;
            statuses = new String[data.entries.size()];
            Arrays.fill(statuses, "offline");
            deleted = new boolean[data.entries.size()];
            allowC2S();
        }

        int remaining() {
            int count = 0;
            for (String status : statuses) if (!"deleted".equals(status)) count++;
            return count;
        }

        int index(int row) {
            page = Math.min(page, pages() - 1);
            int visible = 0, requested = page * 6 + row;
            String[] snapshot = statuses;
            for (int original = 0; original < snapshot.length; original++) {
                if ("deleted".equals(snapshot[original])) continue;
                if (visible++ == requested) return original;
            }
            return data.entries.size();
        }

        int pages() {
            return Math.max(1, (remaining() + 5) / 6);
        }

        boolean deleted(int index) {
            return index >= 0 && index < statuses.length && "deleted".equals(statuses[index]);
        }

        String name(int row) {
            int index = index(row);
            return index >= data.entries.size() ? ""
                : IKey.lang(data.entries.get(index).name)
                    .getFormatted();
        }

        String description(int row) {
            int index = index(row);
            if (index >= data.entries.size()) return "";
            MachineTerminalHistory.Entry entry = data.entries.get(index);
            return entry.dimension + " | "
                + entry.x
                + ", "
                + entry.y
                + ", "
                + entry.z
                + " | "
                + IKey.lang("gtsr.terminal.history.status." + statuses[index])
                    .getFormatted();
        }

        boolean active() {
            return isValid() && !getSyncManager().isClient()
                && data.valid(data.getPlayer())
                && data.getPlayer().openContainer == getSyncManager().getContainer();
        }

        void request(int action, int index) {
            if (index < 0 || index >= data.entries.size()) return;
            syncToServer(1, buffer -> {
                buffer.writeByte(action);
                buffer.writeByte(index);
            });
        }

        @Override
        public void detectAndSendChanges(boolean init) {
            long now = TerminalServerSessions.currentServerTick();
            if (!active() || !init && lastPoll != Long.MIN_VALUE && now - lastPoll < 20) return;
            lastPoll = now;
            String[] next = new String[data.entries.size()];
            for (int i = 0; i < next.length; i++)
                next[i] = deleted[i] ? "deleted" : MachineTerminalHistory.status(data.getPlayer(), data.entries.get(i));
            if (!init && Arrays.equals(statuses, next)) return;
            statuses = next;
            syncToClient(0, buffer -> {
                buffer.writeByte(next.length);
                for (String value : next) ByteBufUtils.writeUTF8String(buffer, value);
            });
        }

        @Override
        public void readOnServer(int id, PacketBuffer buffer) throws IOException {
            if (id != 1 || buffer.readableBytes() != 2) return;
            int action = buffer.readUnsignedByte(), index = buffer.readUnsignedByte();
            if (action > 1 || index >= data.entries.size() || !pending.compareAndSet(false, true)) return;
            Mui2ServerActions.enqueue(() -> {
                try {
                    long now = TerminalServerSessions.currentServerTick();
                    if (!active() || deleted[index] || lastAction != Long.MIN_VALUE && now - lastAction < 5) return;
                    lastAction = now;
                    MachineTerminalHistory.Entry entry = data.entries.get(index);
                    if (action == 1) {
                        MachineTerminalHistory.delete(data.getPlayer(), entry);
                        deleted[index] = true;
                        lastPoll = Long.MIN_VALUE;
                    } else if (!RemoteTerminalFactory.open((EntityPlayerMP) data.getPlayer(), entry)) {
                        syncToClient(2, out -> ByteBufUtils.writeUTF8String(out, "unavailable"));
                        lastPoll = Long.MIN_VALUE;
                    }
                } finally {
                    pending.set(false);
                }
            });
        }

        @Override
        public void readOnClient(int id, PacketBuffer buffer) throws IOException {
            if (buffer.readableBytes() > 1024) return;
            if (id == 0) {
                int size = buffer.readUnsignedByte();
                if (size != data.entries.size()) return;
                String[] next = new String[size];
                for (int i = 0; i < size; i++) {
                    next[i] = ByteBufUtils.readUTF8String(buffer);
                    if (!Arrays.asList("ready", "offline", "replaced", "unsupported", "denied", "unbound", "deleted")
                        .contains(next[i])) return;
                }
                if (!buffer.isReadable()) statuses = next;
            } else if (id == 2) {
                String next = ByteBufUtils.readUTF8String(buffer);
                if (!buffer.isReadable() && "unavailable".equals(next)) receipt = next;
            }
        }
    }
}
