package com.yourdomain.multipagelore;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiPageLorePlugin extends JavaPlugin {

    public static NamespacedKey HEADER_KEY;
    public static NamespacedKey PAGES_KEY;
    public static NamespacedKey FOOTER_KEY;
    public static NamespacedKey CURRENT_PAGE_KEY;

    private MultiPageListener listener;

    public void onEnable() {
        saveDefaultConfig();

        HEADER_KEY = new NamespacedKey(this, "header_data");
        PAGES_KEY = new NamespacedKey(this, "pages_data");
        FOOTER_KEY = new NamespacedKey(this, "footer_data");
        CURRENT_PAGE_KEY = new NamespacedKey(this, "current_page_index");

        LoreManager.init(this);

        listener = new MultiPageListener(this);
        getServer().getPluginManager().registerEvents((Listener) listener, (Plugin) this);

        if (getCommand("multipagelore") != null) {
            MultiPageCommand cmdHandler = new MultiPageCommand(this);
            getCommand("multipagelore").setExecutor((CommandExecutor) cmdHandler);
            getCommand("multipagelore").setTabCompleter((TabCompleter) cmdHandler);
        }

        getServer().getScheduler().runTaskTimer((Plugin) this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && !item.isEmpty()) {
                        LoreManager.bakeItemIfNeeded(item);
                    }
                }
            }
        }, 0L, 10L);

        getLogger().info("MultiPageLore has been successfully enabled!");
    }

    public void onDisable() {
        getLogger().info("MultiPageLore has been disabled.");
    }

    public MultiPageListener getListener() {
        return listener;
    }
}
