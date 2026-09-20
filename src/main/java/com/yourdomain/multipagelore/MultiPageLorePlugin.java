package com.yourdomain.multipagelore;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiPageLorePlugin extends JavaPlugin {
    
    public static NamespacedKey PAGES_KEY;
    public static NamespacedKey CURRENT_PAGE_KEY;
    public static NamespacedKey MAX_WIDTH_KEY;

    @Override
    public void onEnable() {
        saveDefaultConfig(); 

        PAGES_KEY = new NamespacedKey(this, "lore_pages");
        CURRENT_PAGE_KEY = new NamespacedKey(this, "current_page");
        MAX_WIDTH_KEY = new NamespacedKey(this, "max_width");
        
        LoreManager.init(this);
        
        getServer().getPluginManager().registerEvents(new MultiPageListener(this), this);

        // UNIVERSAL ITEM CATCHER - OPTIMIZED FOR 1.21.1
        // Runs every 10 ticks (0.5 seconds).
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    // Fast-fail before passing to the manager to save CPU
                    if (item != null && !item.isEmpty() && item.hasItemMeta()) {
                        LoreManager.bakeItemIfNeeded(item);
                    }
                }
            }
        }, 10L, 10L);

        getLogger().info("MultiPageLore optimized for 1.21.1 enabled.");
    }
}
