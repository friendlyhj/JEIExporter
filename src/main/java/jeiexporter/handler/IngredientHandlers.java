package jeiexporter.handler;

import jeiexporter.jei.JEIConfig;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;

import java.util.HashMap;
import java.util.Map;

/**
 * @author youyihj
 */
public class IngredientHandlers {
    private static final Map<IIngredientType<?>, IIngredientHandler<?>> HANDLERS = new HashMap<>();

    static {
        ItemHandler itemHandler = new ItemHandler();
        registerHandler(VanillaTypes.ITEM, itemHandler);
        registerHandler(VanillaTypes.FLUID, new FluidHandler());
        registerHandler(JEIConfig.ORE_DICT_ENTRY, new OreDictHandler(itemHandler));
    }

    public static <T> void registerHandler(IIngredientType<T> type, IIngredientHandler<T> handler) {
        HANDLERS.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    public static <T> IIngredientHandler<T> getHandler(IIngredientType<T> type) {
        return (IIngredientHandler<T>) HANDLERS.computeIfAbsent(type, CustomIngredientHandler::new);
    }

    public static <T> IIngredientHandler<T> getHandlerByIngredient(T ingredient) {
        return getHandler(JEIConfig.getIngredientRegistry().getIngredientType(ingredient));
    }
}
