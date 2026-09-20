package com.yourdomain.multipagelore;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class MultiPageListener implements Listener {

    private final MultiPageLorePlugin plugin;

    public MultiPageListener(MultiPageLorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.SWAP_OFFHAND) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        boolean isBaked = item.getItemMeta().getPersistentDataContainer().has(MultiPageLorePlugin.PAGES_KEY);
        
        if (!isBaked) {
            isBaked = LoreManager.bakeItemIfNeeded(item); 
        }

        if (isBaked) {
            if (LoreManager.flipPage(item)) {
                event.setCancelled(true);
                
                Player player = (Player) event.getWhoClicked();
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            }
        }
    }
}
