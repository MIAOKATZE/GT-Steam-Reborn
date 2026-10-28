package com.miaokatze.gtsr.client.critical;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.common.critical.CriticalGeometry;
import com.miaokatze.gtsr.common.critical.CriticalMachineState;

/** Actual renderer mode, mechanical-pose and approved FX samplers; no GL invocation. */
public final class CriticalRuntimeAnimationProbe {
    public static int checks;
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    public static void sample(CriticalMachineState visual,double phase,String expectedMode,boolean effects) {
        String slug=CriticalGeometry.visualMachineSlug(visual);
        CriticalGeometry.Model model=CriticalGeometry.getModel(slug);
        String mode=CriticalControllerRenderer.machineMode(visual,true,model.meta.has("designFX"),slug);
        check(mode.equals(expectedMode),"production renderer mode follows actual working state: "+slug+" mode="+mode);
        check(visual.getVisualBatchPhase(0)==phase,"production phase sample remains stable: "+slug);
        if(slug.endsWith("solar")&&effects) {
            int moved=0;
            for(var entry:model.animations.entrySet()) {
                JsonObject group=entry.getValue().getAsJsonObject();
                if(!group.has("workSpin")&&!group.has("workMotion"))continue;
                double[] working=CriticalMechanicalPose.matrix(group,phase,7.25,mode),idle=CriticalMechanicalPose.matrix(group,phase,7.25,"idle");
                double delta=0;for(int i=0;i<16;i++)delta=Math.max(delta,Math.abs(working[i]-idle[i]));
                if(delta>1e-8)moved++;
            }
            check(moved>0,"solar workMotion/workSpin groups have a distinct real working pose");
            CriticalGeometry.Model base=CriticalGeometry.getModel(CriticalGeometry.visualBaseSlug(visual));
            CriticalGeometry.Model loom=CriticalGeometry.getModel(CriticalGeometry.visualLoomSlug(visual));
            JsonObject[] metas={base.meta,loom.meta,model.meta},animations={base.animations,loom.animations,model.animations};
            String[] slugs={CriticalGeometry.visualBaseSlug(visual),CriticalGeometry.visualLoomSlug(visual),slug};
            int[] active={0},idle={0};
            CriticalSolarFX.sample(metas,animations,slugs,phase,7.25,mode,1,null,(family,index,point,color,alpha,glow,glowAlpha,size)->{if(!family.equals("gravity")&&alpha>1e-8)active[0]++;});
            CriticalSolarFX.sample(metas,animations,slugs,phase,7.25,"idle",1,null,(family,index,point,color,alpha,glow,glowAlpha,size)->{if(!family.equals("gravity")&&alpha>1e-8)idle[0]++;});
            check(active[0]>idle[0],"solar work-window FX receive the actual nonzero batch phase");
        } else if(model.meta.has("designFX")&&effects) {
            String baseSlug=CriticalGeometry.visualBaseSlug(visual),loomSlug=CriticalGeometry.visualLoomSlug(visual);
            CriticalGeometry.Model base=CriticalGeometry.getModel(baseSlug),loom=CriticalGeometry.getModel(loomSlug);
            JsonObject[] metas={base.meta,loom.meta,model.meta},animations={base.animations,loom.animations,model.animations};
            String[] slugs={baseSlug,loomSlug,slug};int[] active={0},idle={0};
            CriticalDesignFX.sample(metas,animations,slugs,phase,7.25,mode,1,null,(family,source,index,point,color,alpha,glow,size)->{if(alpha>1e-8)active[0]++;});
            CriticalDesignFX.sample(metas,animations,slugs,phase,7.25,"idle",1,null,(family,source,index,point,color,alpha,glow,size)->{if(alpha>1e-8)idle[0]++;});
            check(active[0]>idle[0],"energy design FX receive their real working phase: "+slug);
        }
    }
    public static void continuity() {
        for(String slug:new String[]{"v2_battery","v2_turbine"}) {
            List<JsonObject> groups=new ArrayList<>();
            for(var entry:CriticalGeometry.getModel(slug).animations.entrySet())if(entry.getValue().getAsJsonObject().has("continuousMotion"))groups.add(entry.getValue().getAsJsonObject());
            check(!groups.isEmpty(),"approved continuous mechanism exists: "+slug);
            CriticalContinuousMotion.Context context=new CriticalContinuousMotion.Context();JsonObject group=groups.get(0);
            context.beginFrame(0,groups,"idle");context.beginFrame(1,groups,"working");double a=context.sample(group).theta;
            context.beginFrame(2,groups,"working");double b=context.sample(group).theta;
            context.beginFrame(3,groups,"idle");double c=context.sample(group).theta;
            context.beginFrame(4,groups,"working");double d=context.sample(group).theta;
            double direction=CriticalAnimation.number(group.getAsJsonObject("continuousMotion"),"direction",1);
            check((b-a)*direction>0&&(c-b)*direction>0&&(d-c)*direction>0,"work/pause/resume never resets continuous mechanism angle: "+slug);
        }
    }
}
