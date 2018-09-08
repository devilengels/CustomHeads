package de.mrstein.customheads;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.mrstein.customheads.api.CustomHeadsAPI;
import de.mrstein.customheads.api.CustomHeadsPlayer;
import de.mrstein.customheads.api.HeadUtil;
import de.mrstein.customheads.category.BaseCategory;
import de.mrstein.customheads.category.Category;
import de.mrstein.customheads.economy.EconomyManager;
import de.mrstein.customheads.headwriter.HeadFontType;
import de.mrstein.customheads.listener.InventoryListener;
import de.mrstein.customheads.listener.OtherListeners;
import de.mrstein.customheads.loader.CategoryLoader;
import de.mrstein.customheads.loader.Language;
import de.mrstein.customheads.loader.Looks;
import de.mrstein.customheads.reflection.TagEditor;
import de.mrstein.customheads.stuff.CHCommand;
import de.mrstein.customheads.stuff.CHTabCompleter;
import de.mrstein.customheads.updaters.AfterTask;
import de.mrstein.customheads.updaters.GitHubDownloader;
import de.mrstein.customheads.updaters.SpigetFetcher;
import de.mrstein.customheads.utils.*;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Getter
public class CustomHeads extends JavaPlugin {

    public static final String chPrefix = "§7[§eCustomHeads§7] ";
    public static final String chError = chPrefix + "§cError §7: §c";
    public static final String chWarning = chPrefix + "§6Warning §7: §6";
    public static final HashMap<String, String> uuidCache = new HashMap<>();
    public static int hisOverflow = 18;

    @Getter
    private static Configs headsConfig;
    @Getter
    private static Configs updateFile;
    @Getter
    private static Configs categoryLoaderConfig;

    @Getter
    private static JsonFile playerDataFile;

    @Getter
    private static Looks looks;
    @Getter
    private static CustomHeads instance;
    @Getter
    private static HeadUtil headUtil;
    @Getter
    private static Language languageManager;
    @Getter
    private static CustomHeadsAPI api;
    @Getter
    private static TagEditor tagEditor;
    @Getter
    private static SpigetFetcher spigetFetcher;
    @Getter
    private static EconomyManager economyManager;
    @Getter
    private static CategoryLoader categoryLoader;

    private static String packet = Bukkit.getServer().getClass().getPackage().getName();
    public static String version = packet.substring(packet.lastIndexOf('.') + 1);
    private static List<String> versions = Arrays.asList("v1_8_R1", "v1_8_R2", "v1_8_R3", "v1_9_R1", "v1_9_R2", "v1_10_R1", "v1_11_R1", "v1_12_R1");
    public static final boolean USETEXTURES = versions.contains(version);
    private static boolean historyEnabled = false;
    private static boolean canSeeOwnHistory = false;
    private static boolean hasEconomy = false;
    private static boolean keepCategoryPermissions = true;
    private String bukkitVersion = Bukkit.getVersion().substring(Bukkit.getVersion().lastIndexOf("("));
    private boolean isInit = false;

    // Search/Get History Loader
    public static void reloadHistoryData() {
        historyEnabled = headsConfig.get().getBoolean("history.enabled");
        canSeeOwnHistory = headsConfig.get().getBoolean("history.seeown");
        hisOverflow = (hisOverflow = headsConfig.get().getInt("history.overflow")) > 27 ? 27 : hisOverflow < 1 ? 1 : hisOverflow;
    }

    // Language/Looks Loader
    public static boolean reloadTranslations(String language) {
        CustomHeads.languageManager = new Language(language);
        categoryLoader = new CategoryLoader(language);
        looks = new Looks(language);
        return Language.isLoaded() && CategoryLoader.isLoaded() && Looks.isLoaded();
    }

    // Vault Support (added in v2.9.2)
    public static void reloadEconomy() {
        if (headsConfig.get().getBoolean("useEconomy")) {
            if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                Bukkit.getConsoleSender().sendMessage(chPrefix + "Trying to hook into Vault...");
                economyManager = new EconomyManager();
                if (economyManager.getEconomyPlugin() == null) {
                    Bukkit.getConsoleSender().sendMessage(chError + "Error hooking into Vault. Continuing without it...");
                } else {
                    Bukkit.getConsoleSender().sendMessage(chPrefix + "§7Successfully hooked into Vault");
                    hasEconomy = true;
                    return;
                }
            } else {
                Bukkit.getConsoleSender().sendMessage(chWarning + "I wasn't able to find Vault on your Server. Continuing without it...");
            }
        }
        hasEconomy = false;
    }

    public static boolean hasHistoryEnabled() {
        return historyEnabled;
    }

    public static boolean canSeeOwnHistory() {
        return canSeeOwnHistory;
    }

    public static boolean hasEconomy() {
        return hasEconomy;
    }

    public void onEnable() {
        instance = this;
        File oldHeadFile;
        if ((oldHeadFile = new File("plugins/CustomHeads", "heads.yml")).exists()) {
            oldHeadFile.renameTo(new File("plugins/CustomHeads", "config.yml"));
        }
        headsConfig = new Configs(instance, "config.yml", true);
        updateFile = new Configs(instance, "update.yml", true);

        if (headsConfig.get().getString("langFile").equals("none")) {
            headsConfig.get().set("langFile", Locale.getDefault().toString());
            headsConfig.save();
            headsConfig.reload();
        }

        // Check if Language File exists
        if (!new File("plugins/CustomHeads/language/" + headsConfig.get().getString("langFile")).exists()) {
            getServer().getConsoleSender().sendMessage(chWarning + "Could not find language/" + headsConfig.get().getString("langFile") + ". Using default instead");
            headsConfig.get().set("langFile", "en_EN");
            headsConfig.save();
            headsConfig.reload();

            // Check if default Language is present if not download it
            if (!new File("plugins/CustomHeads/language/en_EN").exists()) {
                if (new File("plugins/CustomHeads/downloads").listFiles() != null) {
                    for (File file : new File("plugins/CustomHeads/downloads").listFiles()) {
                        file.delete();
                    }
                }
                getServer().getConsoleSender().sendMessage(chWarning + "I wasn't able to find the Default Languge File on your Server...");
                getServer().getConsoleSender().sendMessage(chPrefix + "§7Downloading necessary Files...");
                GitHubDownloader gitHubDownloader = new GitHubDownloader("MrSteinMC", "CustomHeads").enableAutoUnzipping();
                gitHubDownloader.download(getDescription().getVersion(), "language.zip", getDataFolder(), (AfterTask) () -> {
                    getServer().getConsoleSender().sendMessage(chPrefix + "§7Done downloading! Have fun with the Plugin =D");
                    getServer().getConsoleSender().sendMessage(chPrefix + "§7---------------------------------------------");
                    loadRest();
                });
            }
        } else {
            loadRest();
        }

    }

    // Load rest of the Plugin after Language Download
    private void loadRest() {
        categoryLoaderConfig = new Configs(instance, "loadedCategories.yml", true);

        tagEditor = new TagEditor("chTags");

        JsonFile.setDefaultSubfolder("plugins/CustomHeads");

        // Convert old Head-Data if present
        playerDataFile = new JsonFile("playerData.json");
        if (headsConfig.get().contains("heads")) {
            Bukkit.getConsoleSender().sendMessage(chPrefix + "Found old Head Data! Trying to convert...");
            convertOldHeadData();
        }

        reloadEconomy();

        // Load Language
        if (!reloadTranslations(headsConfig.get().getString("langFile"))) {
            getServer().getConsoleSender().sendMessage(chError + "Unable to load Language from language/" + headsConfig.get().getString("langFile"));
            Bukkit.getServer().getPluginManager().disablePlugin(instance);
            return;
        }

        // Register Inventory Listener
        getServer().getPluginManager().registerEvents(new InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new OtherListeners(), this);

        // Setting up APIHandler
        APIHandler apiHandler = new APIHandler();
        headUtil = apiHandler;
        api = apiHandler;

        // Reload Configs
        reloadHistoryData();
        headsConfig.save();

        // Register Commands
        getCommand("heads").setExecutor(new CHCommand());
        getCommand("heads").setTabCompleter(new CHTabCompleter());

        // Check for updates
        spigetFetcher = new SpigetFetcher(29057);

        spigetFetcher.fetchUpdates(new SpigetFetcher.FetchResult() {
            public void updateAvailable(SpigetFetcher.ResourceRelease release, SpigetFetcher.ResourceUpdate update) {
                getServer().getConsoleSender().sendMessage(chPrefix + "§bNew Update for CustomHeads found! v" + release.getReleaseName() + " (Running on v" + getDescription().getVersion() + ") - You can Download it here https://www.spigotmc.org/resources/29057");
                if (!USETEXTURES) {
                    getServer().getConsoleSender().sendMessage(chWarning + "Uh oh. Seems like your Server Version " + bukkitVersion + " is not compatable with CustomHeads");
                    getServer().getConsoleSender().sendMessage(chWarning + "I'll disable Custom Textures from Skulls to prevent any Bugs but don't worry only Effects /heads add");
                }
            }

            public void noUpdate() {
            }
        });

        // -- Timers
        // Clear Cache every 5 Minutes
        new BukkitRunnable() {
            public void run() {
                uuidCache.clear();
                HeadFontType.clearCache();
                GitHubDownloader.clearCache();
                GameProfileBuilder.cache.clear();
                ScrollableInventory.clearCache();
                PlayerWrapper.clearCache();
            }
        }.runTaskTimer(instance, 6000, 6000);

        // Animation Timer
        new BukkitRunnable() {
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (player.getOpenInventory() != null && player.getOpenInventory().getType() == InventoryType.CHEST) {
                        if (CustomHeads.getLooks().getMenuTitles().contains(player.getOpenInventory().getTitle())) {
                            ItemStack[] inventoryContent = player.getOpenInventory().getTopInventory().getContents();
                            for (int i = 0; i < inventoryContent.length; i++) {
                                if (inventoryContent[i] == null) continue;
                                ItemStack contentItem = inventoryContent[i];
                                if (CustomHeads.getTagEditor().getTags(contentItem).contains("openCategory") && CustomHeads.getTagEditor().getTags(contentItem).contains("icon-loop")) {
                                    String[] categoryArgs = CustomHeads.getTagEditor().getTags(contentItem).get(CustomHeads.getTagEditor().indexOf(contentItem, "openCategory") + 1).split("#>");
                                    if (categoryArgs[0].equals("category")) {
                                        CustomHeadsPlayer customHeadsPlayer = api.wrapPlayer(player);
                                        Category category = CustomHeads.getCategoryLoader().getCategory(categoryArgs[1]);
                                        ItemStack nextIcon = category.nextIcon();
                                        nextIcon = new ItemEditor(nextIcon)
                                                .setDisplayName(Utils.hasPermission(player, category.getPermission()) ? "§a" + nextIcon.getItemMeta().getDisplayName() : "§7" + ChatColor.stripColor(nextIcon.getItemMeta().getDisplayName()) + " " + CustomHeads.getLanguageManager().LOCKED)
                                                .addLoreLine(customHeadsPlayer.getUnlockedCategories(false).stream().map(BaseCategory::getId).collect(Collectors.toList()).contains(category.getId()) ? languageManager.ECONOMY_BOUGHT : Utils.getPriceFormatted(category, true))
                                                .addLoreLines(Utils.hasPermission(player, "heads.view.permissions") ? Arrays.asList("§8>===-------", "§7§oPermission: " + category.getPermission()) : null)
                                                .getItem();
                                        contentItem = nextIcon;
                                    }
                                }
                                inventoryContent[i] = CustomHeads.getTagEditor().addTags(contentItem, "menuID", CustomHeads.getLooks().getIDbyTitle(player.getOpenInventory().getTopInventory().getTitle()));
                            }
                            player.getOpenInventory().getTopInventory().setContents(inventoryContent);
                        }
                    }
                }
            }
        }.runTaskTimer(instance, 0, 20);

        isInit = true;
    }

    public void onDisable() {
        if (isInit) {
            OtherListeners.saveLoc.values().forEach(loc -> loc.getBlock().setType(Material.AIR));
            PlayerWrapper.clearCache();
        }
    }

    private void convertOldHeadData() {
        JsonObject rootObject = playerDataFile.getJson().isJsonObject() ? playerDataFile.getJson().getAsJsonObject() : new JsonObject();
        try {
            for (String uuid : headsConfig.get().getConfigurationSection("heads").getKeys(false)) {
                JsonObject uuidObject = rootObject.has(uuid) ? rootObject.getAsJsonObject(uuid) : new JsonObject();
                JsonObject savedHeads = uuidObject.has("savedHeads") ? uuidObject.getAsJsonObject("savedHeads") : new JsonObject();
                for (String key : headsConfig.get().getConfigurationSection("heads." + uuid).getKeys(false)) {
                    savedHeads.addProperty(key, headsConfig.get().getString("heads." + uuid + "." + key + ".texture"));
                }
                uuidObject.add("unlockedCategories", new JsonArray());
                uuidObject.add("savedHeads", savedHeads);
                rootObject.add(uuid, uuidObject);
            }

            playerDataFile.setJson(rootObject);
            playerDataFile.saveJson();
            headsConfig.get().set("heads", null);
            headsConfig.save();
            getServer().getConsoleSender().sendMessage(chPrefix + "Successfully converted Head Data");
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to convert Head Data...", e);
        }
    }

}