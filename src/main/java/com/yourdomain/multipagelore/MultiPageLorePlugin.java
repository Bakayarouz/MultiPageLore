package com.yourdomain.multipagelore;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiPageLorePlugin extends JavaPlugin {

    public static NamespacedKey PAGES_KEY;
    public static NamespacedKey CURRENT_PAGE_KEY;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PAGES_KEY = new NamespacedKey(this, "pages_data");
        CURRENT_PAGE_KEY = new NamespacedKey(this, "current_page_index");

        LoreManager.init(this);

        // Register event listener
        getServer().getPluginManager().registerEvents(new MultiPageListener(this), this);

        // Register command and tab completer
        if (getCommand("multipagelore") != null) {
            MultiPageCommand cmdHandler = new MultiPageCommand(this);
            getCommand("multipagelore").setExecutor(cmdHandler);
            getCommand("multipagelore").setTabCompleter(cmdHandler);
        }

        getLogger().info("MultiPageLore has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MultiPageLore has been disabled.");
    }
}
