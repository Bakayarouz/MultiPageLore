package com.yourdomain.multipagelore;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public class MultiPageListener implements Listener {

    private final MultiPageLorePlugin plugin;

    public MultiPageListener(MultiPageLorePlugin plugin) {
        this.plugin = plugin;
    }

    // 1. Bake items when opening any inventory (Chests, Vaults, GUIs)
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryOpen(InventoryOpenEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            LoreManager.bakeItemIfNeeded(item);
        }
        // Also check the player's own inventory
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            LoreManager.bakeItemIfNeeded(item);
        }
    }

    // 2. Bake items when picked up from the ground
    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemPickup(EntityPickupItemEvent event) {
        LoreManager.bakeItemIfNeeded(event.getItem().getItemStack());
    }

    // 3. Bake items when a player logs in (checks their inventory)
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            LoreManager.bakeItemIfNeeded(item);
        }
    }

    // 4. Handle the actual Page Flip (Swap Hand key)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        
        // Safety net: bake on click just in case a plugin gave them the item directly
        LoreManager.bakeItemIfNeeded(item);

        if (event.getClick() != ClickType.SWAP_OFFHAND) return;
        if (item == null || !item.hasItemMeta()) return;

        boolean isBaked = item.getItemMeta().getPersistentDataContainer().has(MultiPageLorePlugin.PAGES_KEY);

        if (isBaked) {
            if (LoreManager.flipPage(item)) {
                event.setCancelled(true);
                Player player = (Player) event.getWhoClicked();
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
            }
        }
    }
}
