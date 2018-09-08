package de.mrstein.customheads.utils;

/*
 *  Project: CustomHeads in PlayerWrapper
 *     by LikeWhat
 *
 *  created on 20.08.2018 at 17:17
 */

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import de.mrstein.customheads.CustomHeads;
import de.mrstein.customheads.api.CustomHeadsPlayer;
import de.mrstein.customheads.category.Category;
import de.mrstein.customheads.stuff.GetHistory;
import de.mrstein.customheads.stuff.SearchHistory;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerWrapper implements CustomHeadsPlayer {

    private static HashMap<UUID, CustomHeadsPlayer> wrappedPlayersCache = new HashMap<>();
    private OfflinePlayer player;
    private List<Category> unlockedCategories = new ArrayList<>();
    private List<ItemStack> savedHeads = new ArrayList<>();
    private SearchHistory searchHistory;
    private GetHistory getHistory;

    private PlayerWrapper(OfflinePlayer player) {
        this.player = player;
        try {
            JsonObject dataRoot = CustomHeads.getPlayerDataFile().getJson().getAsJsonObject();
            if (dataRoot.has(player.getUniqueId().toString())) {
                JsonObject uuidObject = dataRoot.getAsJsonObject(player.getUniqueId().toString());
                uuidObject.getAsJsonObject("savedHeads").entrySet().forEach(entry -> {
                    ItemEditor editor = new ItemEditor(Material.SKULL_ITEM, (short) 3).setDisplayName(entry.getKey());
                    String textureValue = entry.getValue().getAsString();
                    if (textureValue.length() > 16) {
                        editor.setTexture(textureValue);
                    } else {
                        editor.setOwner(textureValue);
                    }
                    savedHeads.add(editor.getItem());
                });

                searchHistory = new SearchHistory(player);
                getHistory = new GetHistory(player);
                List<Category> categories = new ArrayList<>();
                uuidObject.getAsJsonArray("unlockedCategories").forEach(categoryId -> categories.add(CustomHeads.getCategoryLoader().getCategory(categoryId.getAsString())));
                unlockedCategories = categories;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        wrappedPlayersCache.put(player.getUniqueId(), this);
    }

    public static CustomHeadsPlayer wrapPlayer(OfflinePlayer player) {
        return wrappedPlayersCache.containsKey(player.getUniqueId()) ? wrappedPlayersCache.get(player.getUniqueId()) : new PlayerWrapper(player);
    }

    public static void clearCache() {
        if (wrappedPlayersCache.isEmpty())
            return;

        JsonFile jsonFile = CustomHeads.getPlayerDataFile();
        JsonObject rootObjects = jsonFile.getJson().getAsJsonObject();
        for (UUID uuids : wrappedPlayersCache.keySet()) {
            CustomHeadsPlayer customHeadsPlayer = wrappedPlayersCache.get(uuids);
            JsonObject uuidObject = rootObjects.has(uuids.toString()) ? rootObjects.getAsJsonObject(uuids.toString()) : new JsonObject();
            JsonObject savedHeads = new JsonObject();
            List<ItemStack> saved = customHeadsPlayer.getSavedHeads();
            for (ItemStack head : saved) {
                ItemEditor customHead = new ItemEditor(head);
                savedHeads.addProperty(Utils.toConfigString(customHead.getDisplayName()), customHead.getOwner() == null ? customHead.getTexture() : customHead.getOwner());
            }
            uuidObject.add("savedHeads", savedHeads);
            JsonArray unlockedCategories = new JsonArray();
            for (Category category : customHeadsPlayer.getUnlockedCategories(true)) {
                unlockedCategories.add(new JsonPrimitive(category.getId()));
            }
            uuidObject.add("unlockedCategories", unlockedCategories);
            JsonObject historyObject = new JsonObject();
            JsonArray searchHistory = new JsonArray();
            for (String entry : customHeadsPlayer.getSearchHistory().getEntries()) {
                searchHistory.add(new JsonPrimitive(entry));
            }
            historyObject.add("searchHistory", searchHistory);
            JsonArray getHistory = new JsonArray();
            for (String entry : customHeadsPlayer.getGetHistory().getEntries()) {
                getHistory.add(new JsonPrimitive(entry));
            }
            historyObject.add("getHistory", getHistory);
            uuidObject.add("history", historyObject);

            rootObjects.add(uuids.toString(), uuidObject);
        }
        jsonFile.setJson(rootObjects);
        jsonFile.saveJson();
        wrappedPlayersCache.clear();
    }

    public List<Category> getUnlockedCategories(boolean ignorePermission) {
        return CustomHeads.getCategoryLoader().getCategoryList().stream().filter(category -> (!ignorePermission && Utils.hasPermission(player.getPlayer(), category.getPermission())) || unlockedCategories.contains(category)).collect(Collectors.toList());
    }

    public List<ItemStack> getSavedHeads() {
        return savedHeads;
    }

    public Player unwrap() {
        return player.getPlayer();
    }

    public SearchHistory getSearchHistory() {
        return searchHistory;
    }

    public GetHistory getGetHistory() {
        return getHistory;
    }

    public boolean unlockCategory(Category category) {
        if (unlockedCategories.contains(category)) {
            return false;
        } else {
            unlockedCategories.add(category);
            return true;
        }
    }

    public boolean lockCategory(Category category) {
        if (!unlockedCategories.contains(category)) {
            return false;
        } else {
            unlockedCategories.remove(category);
            return true;
        }
    }

    public boolean saveHead(String name, String texture) {
        if (hasHead(name)) {
            return false;
        } else {
            ItemEditor editor = new ItemEditor(Material.SKULL_ITEM, (short) 3).setDisplayName(name);
            if (texture.length() > 16) {
                editor.setTexture(texture);
            } else {
                editor.setOwner(texture);
            }
            savedHeads.add(editor.getItem());
            return true;
        }
    }

    public boolean deleteHead(String name) {
        if (!hasHead(name)) {
            return false;
        } else {
            savedHeads.remove(getHead(name));
            return true;
        }
    }

    public ItemStack getHead(String name) {
        return savedHeads.stream().filter(itemStack -> Utils.toConfigString(itemStack.getItemMeta().getDisplayName()).equals(name)).iterator().next();
    }

    public boolean hasHead(String name) {
        return savedHeads.stream().filter(itemStack -> Utils.toConfigString(itemStack.getItemMeta().getDisplayName()).equals(name)).iterator().hasNext();
    }

}