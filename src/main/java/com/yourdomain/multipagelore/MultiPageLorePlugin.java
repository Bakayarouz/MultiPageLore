package com.yourdomain.multipagelore;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiPageLorePlugin extends JavaPlugin {

    // The keys used to store hidden data (NBT/PersistentDataContainer) on the items
    public static NamespacedKey PAGES_KEY;
    public static NamespacedKey CURRENT_PAGE_KEY;

    @Override
    public void onEnable() {
        // Generates the default config.yml if it doesn't exist yet
        saveDefaultConfig();

        // Initialize the NamespacedKeys (must be done after the plugin initializes)
        PAGES_KEY = new NamespacedKey(this, "pages_data");
        CURRENT_PAGE_KEY = new NamespacedKey(this, "current_page_index");

        // Pass the plugin instance to the LoreManager so it can read the config
        LoreManager.init(this);

        // Register our main listener
        getServer().getPluginManager().registerEvents(new MultiPageListener(this), this);

        getLogger().info("MultiPageLore has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MultiPageLore has been disabled.");
    }
}
