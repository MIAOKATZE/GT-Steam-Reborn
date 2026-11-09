"""Bounded source-chain audit for the nineteen removed self-target GTNL transforms.

This catches unqualified/static-import candidate omissions; it does not assert live formation.
"""
from pathlib import Path
import argparse, hashlib, json, re
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'src/main/java/com/miaokatze/gtsr'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    checks=[];sources={}
    def read(name):
        p=JAVA/name;text=p.read_text(encoding='utf-8');sources[name]={'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),
            'staticImports':re.findall(r'^import static (.*);',text,re.M),
            'structureCandidates':re.findall(r'\.atLeast\(([^)]*)\)',text)}
        return re.sub(r'\s+','',text)
    def check(ok,label):
        checks.append({'label':label,'passed':bool(ok)})
        if not ok:raise AssertionError(label)
    a=read('common/machine/MTEAmmoniaPlant.java')
    check(a.count('.atLeast(IndependentAmmoniaInputElement.INSTANCE,SteamOutputBus,OutputHatch)')==2,
        'ammonia_both_unqualified_structure_input_candidates_use_optional_adapter')
    e=read('compat/gtnl/IndependentAmmoniaInputElement.java')
    for x in ['candidates(HatchElement.InputHatch.mteClasses(),true)','PipelessSteamSupport.add(machine,tile,casing)',
        'HatchElement.InputHatch.count(machine)+PipelessSteamSupport.count(machine)']:
        check(x in e,'ammonia_adapter_'+x)
    for name,constant in [('MTEGearSteamCompressor','PressureSteamInput'),('MTEKineticProcessingArray','PressureSteamInput'),
        ('MTESiemensMartinFurnace','PressureSteamInput'),('MTEMegaSteamTurbineArray','PressureSteamInput')]:
        s=read('common/machine/'+name+'.java')
        for x in ['PipelessSteamSupport.candidates(mteClasses,this=='+constant+')','PipelessSteamSupport.add(this,aTileEntity,aBaseCasingIndex)',
            'PipelessSteamSupport.count(t)','PipelessSteamSupport.updateTextures(this,']:
            check(x in s,name+'_'+x)
    s=read('common/machine/base/MTEGTSRMultiBlockBase.java')
    for x in ['implementsIGTSRProgressProvider,IAdditionalSteamSourceHolder','additionalSteamSources=newArrayList<>()',
        'super.clearHatches();additionalSteamSources.clear();','IndependentSteamSupport.accepts(this)&&PipelessSteamSupport.add(this,tile,casing)']:
        check(x in s,'independent_instance_owner_'+x)
    s=read('common/machine/cluster/ClusterStructureDef.java')
    check('PipelessSteamSupport.candidates(classes,this==PressureSteamInput)' in s,'cluster_named_pressure_candidate')
    check('returnt.addInputHatchToMachineList(te,' in s,'cluster_adder_reaches_own_base')
    check('controller.pressureSteamHatchCount()+PipelessSteamSupport.count(controller)' in s,'cluster_pressure_count_includes_optional')
    s=read('common/machine/base/MTESingularityMachineBase.java')
    for x in ['PipelessSteamSupport.candidates(mteClasses,this==SteamInput)','PipelessSteamSupport.add(this,aTileEntity,aBaseCasingIndex)',
        'all.addAll(additionalSteamSources)','super.clearHatches();additionalSteamSources.clear();']:
        check(x in s,'singularity_'+x)
    for name in ['MTESteamSingularityEntangler','MTECriticalSingularityCompressor','MTEDenseStateManipulator','MTECrustMatterAggregator']:
        s=read('common/machine/'+name+'.java');check('PipelessSteamSupport.clear(this)' in s,name+'_failed_recheck_clears_optional')
    s=read('common/api/enums/GTSRHatchElement.java')
    check('PipelessSteamSupport.candidates(mteClassesSupplier.get(),this==SteamInputHatch||this==PressureSteamInputHatch)' in s,'shared_named_input_candidates')
    check(s.count('PipelessSteamSupport.count(t)')==2,'shared_both_input_counts')
    for name in ['MTEAirCompressor','MTEAtmosphericCentrifuge','MTELargeSteamFurnace','MTEVeinSteamPyrolyzer','MTECrustSteamBorer','MTESteamFluidDrill','MTESingularityDrillingHub']:
        s=read('common/machine/'+name+'.java');check('PipelessSteamSupport.count(this)' in s,name+'_explicit_structure_source_query')
        check('PipelessSteamSupport.updateTextures(this,getCasingTextureID())' in s,name+'_texture_tail')
    s=read('compat/gtnl/PipelessSteamSupport.java')
    check(s.count('GTNLMixinSelection.pipelessEnabled()')>=6,'ordinary_hooks_share_startup_P_gate')
    s=read('common/machine/MTEMegaSteamTurbineArray.java')
    start=s.index('privatebooleandepleteSteamByType(');end=s.index('privatelong',start) if 'privatelong' in s[start:] else len(s)
    payment=s[start:end]
    check(payment.index('depleteFluidAcross(mInputHatches')<payment.index('for(MTEHatchPressureSteamInput')
        <payment.index('for(MTEOverpressureTurbineInputHatch')<payment.index('PipelessSteamSupport.getStored(this)'),
        'turbine_native_ordinary_pressure_overpressure_then_optional_remainder')
    cfg=json.loads((ROOT/'src/main/resources/mixins.gtsr.json').read_text(encoding='utf-8'))
    check({x for x in cfg['mixins'] if x.startswith('gtnl.')}=={'gtnl.GTNLNativeSteamMachineMixin','gtnl.SharedSteamSourceMixin','gtnl.SteamMultiMachineBaseGTNLMixin'},'only_three_external_GTNL_transforms_remain')
    receipt={'status':'PASS','checks':checks,'sources':sources,'boundary':'Source-chain/static alias audit only. Actual canonical formation and natural tick proof requires followup_ammonia_natural[_gtnl] runtime reports.'}
    args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS GTNL full-family source chains checks='+str(len(checks)))
if __name__=='__main__':main()
