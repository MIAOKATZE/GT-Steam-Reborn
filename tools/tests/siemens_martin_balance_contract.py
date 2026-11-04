"""Execute exact Siemens registration/parallel/speed source at isolated recipe API boundaries."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/v2112-siemens-contract"


def body(source, signature):
    start = source.index("{", source.index(signature))
    depth = 1
    end = start + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


HARNESS = r'''
import java.util.*;
class ItemStack {
    final String id; final int amount;
    ItemStack(String i,int a){id=i;amount=a;} ItemStack copy(){return new ItemStack(id,amount);}
}
class Material {
    final String id;Material(String i){id=i;}
    ItemStack getGems(int n){return new ItemStack("gem"+id,n);}
    ItemStack getDust(int n){return new ItemStack("dust"+id,n);}
    ItemStack getIngots(int n){return new ItemStack("ingot"+id,n);}
}
class Materials {
    static Material Iron=new Material("Iron"),Coal=new Material("Coal"),Charcoal=new Material("Charcoal"),
        Steel=new Material("Steel"),Ash=new Material("Ash"),Carbon=new Material("Carbon");
}
class OreDictionary {static List<ItemStack> getOres(String s){return List.of(new ItemStack(s,1));}}
class GTValues {static final RA RA=new RA();}
class RA {Builder stdBuilder(){return new Builder();}}
class Builder {
    ItemStack[] inputs,outputs;int duration;int[] chances;
    Builder itemInputs(ItemStack... i){inputs=i;return this;}
    Builder itemOutputs(ItemStack... i){outputs=i;return this;}
    Builder outputChances(int... c){chances=c;return this;}
    Builder duration(int d){duration=d;return this;}
    Builder eut(int e){if(e!=0)throw new AssertionError("heat recipe EU");return this;}
    void addTo(List<Builder> map){map.add(this);}
}
class SiemensMartinBalanceContract {
    static final int SECONDS=20;
    static final List<Builder> siemensMartinRecipes=new ArrayList<>();
    double mFurnaceTemperature;
    // CONSTANTS
    static void registerSiemensMartinRecipes() // REGISTRATION
    public int getMaxParallelRecipes() // PARALLEL
    double speed() // SPEED
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args) {
        registerSiemensMartinRecipes();
        check(siemensMartinRecipes.size()==12,"twelve dedicated recipes including all eight original recipes");
        for(String iron:List.of("ingotIron","dustIron")) {
            for(String reducing:List.of("gemCoal","dustCoal","gemCharcoal","dustCharcoal","fuelCoke","dustCarbon")) {
                Builder recipe=siemensMartinRecipes.stream().filter(r->r.inputs[0].id.equals(iron)&&r.inputs[1].id.equals(reducing)).findFirst().orElseThrow();
                check(recipe.inputs[0].amount==2,"same iron quantity");
                check(recipe.inputs[1].amount==(reducing.equals("fuelCoke")||reducing.equals("dustCarbon")?1:2),"1:1 reducing agent quantity");
                check(recipe.outputs[0].id.equals("ingotSteel")&&recipe.outputs[0].amount==2,"same steel yield");
                check(recipe.outputs[1].id.equals("dustAsh")&&recipe.outputs[1].amount==1,"same ash yield");
                int seconds=reducing.equals("fuelCoke")?1200:reducing.equals("dustCarbon")?800:1600;
                check(recipe.duration==seconds*SECONDS,"recipe map duration retained; machine halves exactly once");
                check((reducing.equals("fuelCoke")||reducing.equals("dustCarbon"))||recipe.chances==null,"new alternatives preserve coal dust outputs");
            }
        }
        SiemensMartinBalanceContract machine=new SiemensMartinBalanceContract();
        double[] temperatures={.99,1,1.5,2,3};int[] parallel={0,128,192,256,256};
        for(int i=0;i<temperatures.length;i++) {machine.mFurnaceTemperature=temperatures[i];check(machine.getMaxParallelRecipes()==parallel[i],"parallel at "+temperatures[i]);}
        for(double temperature:new double[]{1,1.25,1.5,1.75,2}) {
            machine.mFurnaceTemperature=temperature;
            double old=.75*(1-Math.min(.5,(temperature-1)*100*.005));
            check(Math.abs(machine.speed()/old-.5)<1e-12,"duration halved at "+temperature);
        }
        System.out.println("PASS 12 exact-source recipes, 1:1 charcoal alternatives, parallel 0/128/192/256, duration half across overheat curve; GT processing/game=NOT_RUN");
    }
}
'''


def main():
    recipes = (ROOT / "src/main/java/com/miaokatze/gtsr/loader/recipes/ProcessingMachineRecipes.java").read_text(encoding="utf-8")
    machine = (ROOT / "src/main/java/com/miaokatze/gtsr/common/machine/MTESiemensMartinFurnace.java").read_text(encoding="utf-8")
    names = ("BASE_PARALLEL", "MAX_PARALLEL", "MAX_OVERHEAT", "MAX_RECIPE_TIME_REDUCTION", "RECIPE_TIME_REDUCTION_PER_PERCENT", "RECIPE_TIME_BASE_FACTOR")
    constants = "\n".join(re.search(r"private static final [^\n]+\b"+name+r"\s*=.*?;", machine).group() for name in names)
    source = HARNESS.replace("// CONSTANTS", constants)
    source = source.replace("// REGISTRATION", body(recipes, "public static void registerSiemensMartinRecipes()"))
    source = source.replace("// PARALLEL", body(machine, "public int getMaxParallelRecipes()"))
    source = source.replace("// SPEED", body(machine, ".setSpeedBonusSupplier(() ->"))
    OUT.mkdir(parents=True, exist_ok=True)
    java = OUT / "SiemensMartinBalanceContract.java"
    java.write_text(source, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(OUT), str(java)], check=True)
    subprocess.run(["java", "-cp", str(OUT), "SiemensMartinBalanceContract"], check=True)


if __name__ == "__main__":
    main()
