package com.yourdomain.multipagelore;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiPageLorePlugin extends JavaPlugin {
    
    public static NamespacedKey PAGES_KEY;
    public static NamespacedKey CURRENT_PAGE_KEY;
    public static NamespacedKey MAX_WIDTH_KEY;

    @Override
    public void onEnable() {
        PAGES_KEY = new NamespacedKey(this, "lore_pages");
        CURRENT_PAGE_KEY = new NamespacedKey(this, "current_page");
        MAX_WIDTH_KEY = new NamespacedKey(this, "max_width");
        
        getServer().getPluginManager().registerEvents(new MultiPageListener(this), this);
        getLogger().info("MultiPageLore (Enterprise Optimized) enabled.");
    }
}
