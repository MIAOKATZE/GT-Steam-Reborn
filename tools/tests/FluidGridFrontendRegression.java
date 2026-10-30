package gregtech.api.recipe;

import com.miaokatze.gtsr.api.recipe.FluidGridFrontend;

/** Exercises the real GT205 parent constructor calling the GTSR override. */
public final class FluidGridFrontendRegression {
    public static void main(String[] args) {
        int checks=0, failures=0;
        for (int count : new int[]{1,3,4,6,7,9,10,12,15}) {
            FluidGridFrontend front = new FluidGridFrontend(
                BasicUIProperties.builder().maxItemInputs(0).maxItemOutputs(0).maxFluidInputs(1).maxFluidOutputs(count),
                NEIRecipeProperties.builder());
            int expected=82+Math.max((count+2)/3-2,0)*18;
            int actual=front.getNEIProperties().recipeBackgroundSize.height;
            checks++;
            if (actual != expected) failures++;
            System.out.println((actual==expected?"PASS ":"FAIL ")+count+" fluid outputs height="+actual+" expected="+expected);
        }
        System.out.println("GT205 native frontend checks="+checks+" failures="+failures);
        if(failures!=0)throw new AssertionError(failures+" frontend regressions");
    }
}
