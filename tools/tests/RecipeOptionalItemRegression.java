import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.registry.GameRegistry;
import com.miaokatze.gtsr.loader.recipes.RecipeLoaderUtils;
public final class RecipeOptionalItemRegression {
 public static void main(String[] args) {
  Item missing=GameRegistry.findItem("GoodGenerator", "compactFusionCoil");
  if(missing!=null)throw new AssertionError("Expected absent optional registry");
  ItemStack wrapped=new ItemStack(missing,32,2);
  if(wrapped==null||wrapped.getItem()!=null)throw new AssertionError("Native ItemStack constructor shape");
  if(!RecipeLoaderUtils.hasNull(wrapped))throw new AssertionError("Missing optional GoodGenerator item bypassed guard");
  System.out.println("PASS actual Forge absent GoodGenerator registry item is rejected before registration");
 }
}
