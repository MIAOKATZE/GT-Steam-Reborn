package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiDrawing;
import com.miaokatze.gtsr.client.gui.terminal.GtsrGuiTextures;
import com.miaokatze.gtsr.common.critical.ContainerCriticalController;
import com.miaokatze.gtsr.common.critical.CriticalConfiguration;
import com.miaokatze.gtsr.common.critical.recipe.CriticalConstructionCosts;
import com.miaokatze.gtsr.common.critical.recipe.CriticalRecipes;
import com.miaokatze.gtsr.common.critical.recipe.RecipeMatch;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Amber GTSR terminal. Inventory uses vanilla slots; every command executes through its server window. */
@SideOnly(Side.CLIENT)
public final class GuiCriticalController extends GuiContainer {

    private final ContainerCriticalController container;
    private int recipeIndex;

    public GuiCriticalController(ContainerCriticalController container) {
        super(container);
        this.container = container;
        xSize = ContainerCriticalController.WIDTH;
        ySize = ContainerCriticalController.HEIGHT;
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("gtsr.critical." + key);
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        button(1, 10, 28, 66, "");
        button(2, 80, 28, 258, "");
        for (int i = 0; i < 3; i++) {
            button(4 + i * 2, 91 + i * 83, 53, 22, "−");
            button(3 + i * 2, 115 + i * 83, 53, 22, "+");
        }
        button(9, 10, 99, 104, tr("build"));
        button(10, 122, 99, 104, tr("dismantle"));
        button(11, 234, 99, 104, "");
        button(12, 10, 229, 74, tr("repair"));
        button(13, 264, 229, 74, tr("recipes"));
        updateScreen();
    }

    private void button(int id, int x, int y, int width, String label) {
        buttonList.add(new TerminalButton(id, guiLeft + x, guiTop + y, width, label));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 13) {
            recipeIndex++;
            return;
        }
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        CriticalConfiguration config = container.configuration();
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.id == 1) button.displayString = config.tier.name();
            if (button.id == 2) button.displayString = tr("machine." + config.kind.key);
            if (button.id == 11) button.displayString = tr(container.enabled() ? "stop" : "start");
            if (button.id >= 1 && button.id <= 8) button.enabled = container.statusCode() == 0;
            if (button.id == 3 || button.id == 5 || button.id == 7) {
                button.enabled &= config.parallel + config.speed + config.economy < 8;
            }
            if (button.id == 4) button.enabled &= config.parallel > 0;
            if (button.id == 6) button.enabled &= config.speed > 0;
            if (button.id == 8) button.enabled &= config.economy > 0;
            if (button.id == 12) button.enabled = container.statusCode() != 0;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GtsrGuiDrawing.drawNineSlice(GtsrGuiTextures.LIST_PANEL, 4, guiLeft, guiTop, xSize, ySize, zLevel);
        drawRect(guiLeft + 4, guiTop + 4, guiLeft + xSize - 4, guiTop + 23, 0xFF3B2D1C);
        for (Object value : inventorySlots.inventorySlots) {
            net.minecraft.inventory.Slot slot = (net.minecraft.inventory.Slot) value;
            GtsrGuiDrawing.drawNineSlice(
                GtsrGuiTextures.SLOT_FRAME,
                4,
                guiLeft + slot.xDisplayPosition - 1,
                guiTop + slot.yDisplayPosition - 1,
                18,
                18,
                zLevel);
        }
        if (container.batchDuration() > 0) {
            int width = (int) Math.min(328L, 328L * container.batchProgress() / container.batchDuration());
            drawRect(guiLeft + 10, guiTop + 219, guiLeft + 338, guiTop + 224, 0xFF30261A);
            drawRect(guiLeft + 10, guiTop + 219, guiLeft + 10 + width, guiTop + 224, 0xFFD6A44C);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        CriticalConfiguration config = container.configuration();
        fontRendererObj.drawString(tr("title"), 10, 10, 0xE5BD76);
        fontRendererObj.drawString(tr("parallel") + " " + config.parallel, 10, 59, 0xD5C4A3);
        fontRendererObj.drawString(tr("speed") + " " + config.speed, 145, 59, 0xD5C4A3);
        fontRendererObj.drawString(tr("economy") + " " + config.economy, 228, 59, 0xD5C4A3);
        fontRendererObj.drawString(
            tr("status." + container.statusCode()) + " · " + tr("reason." + container.reasonCode()),
            10,
            81,
            0xE5BD76);
        fontRendererObj.drawString(tr("input"), 10, 126, 0xD5C4A3);
        fontRendererObj.drawString(tr("output"), 178, 126, 0xD5C4A3);
        fontRendererObj.drawString("EU " + container.energy() + " / " + container.capacity(), 10, 181, 0xD5C4A3);
        fontRendererObj.drawString(
            tr("construction") + " "
                + container.stage()
                + " · "
                + container.jobProgress()
                + " / "
                + container.jobTotal(),
            10,
            193,
            0xD5C4A3);
        fontRendererObj.drawString(
            tr("batch") + " " + container.batchProgress() + " / " + container.batchDuration() + " t",
            10,
            205,
            0xD5C4A3);
        fontRendererObj.drawString(tr("inventory"), 94, 230, 0xD5C4A3);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (mouseX < button.xPosition || mouseY < button.yPosition
                || mouseX >= button.xPosition + button.width
                || mouseY >= button.yPosition + button.height) continue;
            List<String> text = new ArrayList<>();
            if (button.id == 9 || button.id == 12) {
                int stage = Math.min(4, container.stage());
                int count = Math.max(1, container.jobTotal());
                text.add(tr("construction") + " " + stage);
                for (ItemStack item : CriticalConstructionCosts.forStage(container.configuration(), stage, count)) {
                    text.add(item.getDisplayName() + " × " + item.stackSize);
                }
                text.add(
                    tr("construction_power") + " " + container.configuration().tier.constructionVoltage() + " EU/t");
                text.add(tr("cost_hint"));
            } else if (button.id == 13) {
                List<RecipeMatch> recipes = CriticalRecipes.list(container.configuration());
                if (!recipes.isEmpty()) {
                    RecipeMatch recipe = recipes.get(recipeIndex % recipes.size());
                    text.add(tr("recipes") + " " + (recipeIndex % recipes.size() + 1) + " / " + recipes.size());
                    text.add(tr("input"));
                    append(text, recipe.itemInputs, recipe.fluidInputs);
                    text.add(tr("output"));
                    append(text, recipe.itemOutputs, recipe.fluidOutputs);
                    text.add(recipe.euPerTick + " EU/t · " + recipe.durationTicks + " t");
                    if (recipe.generatedEu > 0) text.add(tr("generated") + " " + recipe.generatedEu + " EU");
                    text.add(tr("recipe_hint"));
                }
            }
            if (!text.isEmpty()) drawHoveringText(text, mouseX, mouseY, fontRendererObj);
        }
    }

    private static void append(List<String> text, ItemStack[] items, FluidStack[] fluids) {
        for (ItemStack item : items) text.add("  " + item.getDisplayName() + " × " + item.stackSize);
        for (FluidStack fluid : fluids) text.add("  " + fluid.getLocalizedName() + " × " + fluid.amount + " mB");
        if (items.length == 0 && fluids.length == 0) text.add("  —");
    }

    private static final class TerminalButton extends GuiButton {

        TerminalButton(int id, int x, int y, int width, String label) {
            super(id, x, y, width, 18, label);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            boolean hover = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width
                && mouseY < yPosition + height;
            GtsrGuiDrawing.drawNineSlice(
                !enabled ? GtsrGuiTextures.BUTTON_DISABLED
                    : hover ? GtsrGuiTextures.BUTTON_HOVER : GtsrGuiTextures.BUTTON_NORMAL,
                4,
                xPosition,
                yPosition,
                width,
                height,
                0);
            drawCenteredString(
                minecraft.fontRenderer,
                displayString,
                xPosition + width / 2,
                yPosition + (height - 8) / 2,
                enabled ? 0xE5BD76 : 0x807568);
        }
    }
}
