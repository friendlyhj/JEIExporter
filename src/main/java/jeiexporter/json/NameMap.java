package jeiexporter.json;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jeiexporter.config.ConfigHandler;
import jeiexporter.handler.IIngredientHandler;
import jeiexporter.handler.IngredientHandlers;
import jeiexporter.jei.CategoryRebuilder;
import jeiexporter.jei.JEIConfig;
import jeiexporter.render.Loading;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import org.apache.commons.io.FileUtils;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author youyihj
 */
public class NameMap {
    private static final Map<IIngredientType<?>, Map<String, ?>> ingredients = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> void add(T ingredient) {
        Map<String, T> map = (Map<String, T>) ingredients.computeIfAbsent(JEIConfig.getIngredientRegistry().getIngredientType(ingredient), it -> new HashMap<>());
        IIngredientHandler<T> handler = IngredientHandlers.getHandlerByIngredient(ingredient);
        map.put(handler.getInternalId(ingredient), ingredient);
    }

    public static void clear() {
        ingredients.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void exportNames() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Map<String, Map<String, Map<String, String>>> names = new HashMap<>();
        Map<String, Map<String, String>> categoryNames = new HashMap<>();
        Map<String, Object> usedNames = new HashMap<>();
        Set<String> sameNames = new HashSet<>();
        int index = 0;
        int nameSteps = ConfigHandler.exportedLanguage.length * ingredients.size();
        Language mcLanguage = minecraft.getLanguageManager().getCurrentLanguage();
        List<Language> languages = Lists.newArrayList(mcLanguage);
        Arrays.stream(ConfigHandler.exportedLanguage)
                .filter(name -> !name.equals(mcLanguage.getLanguageCode()))
                .map(name -> minecraft.getLanguageManager().getLanguage(name))
                .forEach(languages::add);
        for (Language language : languages) {
            boolean isCurrentLanguage = language.equals(mcLanguage);
            if (!isCurrentLanguage) {
                toggleLanguage(language);
            }
            for (Map.Entry<IIngredientType<?>, Map<String, ?>> iIngredientTypeMapEntry : ingredients.entrySet()) {
                if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) break;
                index++;
                Map<String, ?> ingredientMap = iIngredientTypeMapEntry.getValue();
                IIngredientType<?> type = iIngredientTypeMapEntry.getKey();
                IIngredientHandler handler = IngredientHandlers.getHandler(type);
                Map<String, Map<String, String>> ingredientInfo = names.computeIfAbsent(handler.getType(), (it) -> new HashMap<>());
                Set<? extends Map.Entry<String, ?>> entries = ingredientMap.entrySet();
                int size = entries.size();
                int i = 0;
                for (Map.Entry<String, ?> entry : entries) {
                    i++;
                    int finalI = i;
                    int finalIndex = index;
                    Loading.render(() -> new Loading.Context(
                            "Exporting ingredient names for " + language.getLanguageCode(),
                            String.format("Exporting %s (%s/%s)", type.getIngredientClass().getCanonicalName(), finalI, size),
                            (finalI * 1F) / size,
                            String.format("%s/%s", finalIndex, nameSteps),
                            (finalIndex * 1F) / nameSteps)
                    );
                    Map<String, String> entryMap = ingredientInfo.computeIfAbsent(entry.getKey(), it -> new HashMap<>());
                    Object ingredient = entry.getValue();
                    String displayName = handler.getDisplayName(ingredient);
                    if (usedNames.containsKey(displayName)) {
                        sameNames.add(displayName);
                        List<String> tooltip = handler.getTooltip(minecraft, ingredient);
                        if (!tooltip.isEmpty()) {
                            entryMap.put(language.getLanguageCode() + "_tooltip", String.join("\\n", tooltip));
                        }
                    } else {
                        usedNames.put(displayName, ingredient);
                    }
                    entryMap.put(language.getLanguageCode(), displayName);
                    if (isCurrentLanguage) {
                        String tag = handler.getTag(ingredient);
                        if (!tag.isEmpty()) {
                            entryMap.put("tag", tag);
                        }
                    }
                }
            }
            CategoryRebuilder categoryRebuilder = new CategoryRebuilder();
            categoryRebuilder.rebuildCategory();
            List<IRecipeCategory> categories = categoryRebuilder.getCategories();
            for (IRecipeCategory<?> category : categories) {
                categoryNames.computeIfAbsent(category.getUid(), it -> new HashMap<>()).put(language.getLanguageCode(), category.getTitle());
            }
        }
        toggleLanguage(mcLanguage);
        for (String sameName : sameNames) {
            Object firstIngredient = usedNames.get(sameName);
            if (firstIngredient != null) {
                IIngredientHandler<Object> handler = IngredientHandlers.getHandlerByIngredient(firstIngredient);
                List<String> tooltip = handler.getTooltip(minecraft, firstIngredient);
                if (!tooltip.isEmpty()) {
                    names.get(handler.getType()).get(handler.getInternalId(firstIngredient)).put(mcLanguage.getLanguageCode() + "_tooltip", String.join("\\n", tooltip));
                }
            }
        }
        try {
            asJson(new File("exports/nameMap.json"), names);
            asJson(new File("exports/categoryTitles.json"), categoryNames);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void asJson(File location, Map<?, ?> map) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        FileUtils.write(location, gson.toJson(map), StandardCharsets.UTF_8);
    }

    private static void toggleLanguage(Language language) {
        LanguageManager languageManager = Minecraft.getMinecraft().getLanguageManager();
        languageManager.setCurrentLanguage(language);
        languageManager.onResourceManagerReload(Minecraft.getMinecraft().getResourceManager());
    }
}
