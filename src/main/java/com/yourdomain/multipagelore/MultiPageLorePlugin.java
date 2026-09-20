package com.yourdomain.multipagelore;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiPageLorePlugin extends JavaPlugin {

    public static NamespacedKey HEADER_KEY;
    public static NamespacedKey PAGES_KEY;
    public static NamespacedKey FOOTER_KEY;
    public static NamespacedKey CURRENT_PAGE_KEY;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        HEADER_KEY = new NamespacedKey(this, "header_data");
        PAGES_KEY = new NamespacedKey(this, "pages_data");
        FOOTER_KEY = new NamespacedKey(this, "footer_data");
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

        // Background Inventory Scanner: Automatically pre-bakes items given via /mi give or plugins within 0.5 seconds
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                ItemStack[] contents = player.getInventory().getContents();
                for (ItemStack item : contents) {
                    if (item != null && !item.isEmpty()) {
                        LoreManager.bakeItemIfNeeded(item);
                    }
                }
            }
        }, 0L, 10L);

        getLogger().info("MultiPageLore has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MultiPageLore has been disabled.");
    }
}
