package audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.dimension.testlab.CriticalPowerLiveProbe;
import com.miaokatze.miaokatzecore.api.recipe.RecipeRegistration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Opt-in test mod for an owned dedicated-server fixture. Never included in the production jar. */
@Mod(modid="gtsr_power_live_probe",name="GTSR owned power live audit",version="1",
    dependencies="required-after:gtsr;required-after:miaokatzecore",acceptableRemoteVersions="*")
public final class PowerLiveProbeMod {

    private Path receipt;
    private int ticks, finishedAt;
    private String status;
    private boolean coreCompleteSeen;
    private boolean recipeDone;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        RecipeNonCellLiveObserver.register();
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        Path cwd=Paths.get("").toAbsolutePath().normalize();
        receipt=cwd.resolve(System.getProperty("gtsr.power.receipt",
            "gtsr-power-live/critical-power-live.json")).normalize();
        if (!receipt.startsWith(cwd)) throw new IllegalArgumentException("Power audit receipt must stay inside owned server cwd");
        if (Files.exists(receipt)) {
            status="FAIL";
            System.out.println("[GTSR-POWER-LIVE] refusing existing receipt="+receipt);
        }
        FMLCommonHandler.instance().bus().register(this);
        System.out.println("[GTSR-POWER-LIVE] INITIALIZED awaiting Core completion receipt="+receipt);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase!=TickEvent.Phase.END) return;
        ticks++;
        if (status!=null) {
            if (ticks-finishedAt==1 || (ticks-finishedAt)%20==0) completedMarker();
            return;
        }
        try {
            if (!RecipeRegistration.isComplete()) {
                if (ticks>=600) throw new IllegalStateException("Core RecipeRegistration did not complete in600 server END ticks");
                return;
            }
            if (!coreCompleteSeen) {
                coreCompleteSeen=true;
                System.out.println("[GTSR-POWER-LIVE] CORE_COMPLETE tick="+ticks);
            }
            if (!recipeDone) recipeDone=RecipeNonCellLiveObserver.finish(receipt.resolveSibling("noncell-recipes-live.json"));
            if (!CriticalPowerLiveProbe.onTick(FMLCommonHandler.instance().getMinecraftServerInstance(),receipt) || !recipeDone) return;
            JsonObject result=new JsonParser().parse(new String(Files.readAllBytes(receipt),StandardCharsets.UTF_8))
                .getAsJsonObject();
            status=result.get("status").getAsString();
            result.addProperty("recipeAuditStatus",RecipeNonCellLiveObserver.status());
            if (!"PASS".equals(RecipeNonCellLiveObserver.status())) status="FAIL";
            result.addProperty("status",status);
            if (!"PASS".equals(status) && !"FAIL".equals(status))
                throw new IllegalStateException("Unexpected helper status="+status);
            result.addProperty("coreCompleteBeforeProbe",coreCompleteSeen);
            result.addProperty("coreSource",RecipeRegistration.class.getProtectionDomain().getCodeSource().getLocation().toString());
            Files.write(receipt,new GsonBuilder().setPrettyPrinting().create().toJson(result)
                .getBytes(StandardCharsets.UTF_8));
            finishedAt=ticks;
            completedMarker();
        } catch (Throwable failure) {
            failure.printStackTrace();
            status="FAIL";finishedAt=ticks;
            JsonObject result=new JsonObject();result.addProperty("status",status);
            result.addProperty("coreCompleteSeen",coreCompleteSeen);result.addProperty("ticks",ticks);
            result.addProperty("error",failure.toString());
            try {
                Files.createDirectories(receipt.getParent());
                Files.write(receipt,new GsonBuilder().setPrettyPrinting().create().toJson(result)
                    .getBytes(StandardCharsets.UTF_8));
            } catch (Exception writeFailure) { writeFailure.printStackTrace(); }
            completedMarker();
        }
    }

    private void completedMarker() {
        System.out.println("[GTSR-POWER-LIVE] COMPLETE status="+status+" receipt="+receipt);
    }
}
