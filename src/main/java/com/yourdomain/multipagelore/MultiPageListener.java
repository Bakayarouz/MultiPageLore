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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class MultiPageListener implements Listener {

    private final MultiPageLorePlugin plugin;

    public MultiPageListener(MultiPageLorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isSafeInventory(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder holder = inv.getHolder();
        return holder instanceof org.bukkit.entity.Player || 
               holder instanceof org.bukkit.block.Container || 
               holder instanceof org.bukkit.entity.AbstractHorse;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (isSafeInventory(event.getInventory())) {
            for (ItemStack item : event.getInventory().getContents()) {
                if (item != null && !item.isEmpty()) {
                    LoreManager.bakeItemIfNeeded(item);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (item != null && !item.isEmpty()) {
            LoreManager.bakeItemIfNeeded(item);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            if (item != null && !item.isEmpty()) {
                LoreManager.bakeItemIfNeeded(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return;

        boolean isVirtualGUI = !isSafeInventory(event.getClickedInventory());

        // Stop if real inventory action was blocked by an anti-cheat, combat log, or trade plugin
        if (!isVirtualGUI && event.isCancelled()) return;

        LoreManager.bakeItemIfNeeded(item);

        String configAction = plugin.getConfig().getString("flip-action", "SWAP_OFFHAND").toUpperCase();
        ClickType targetClickType;
        try {
            targetClickType = ClickType.valueOf(configAction);
        } catch (IllegalArgumentException e) {
            targetClickType = ClickType.SWAP_OFFHAND;
        }

        if (event.getClick() != targetClickType) return;

        boolean isBaked = item.getItemMeta().getPersistentDataContainer().has(MultiPageLorePlugin.PAGES_KEY);

        if (isBaked) {
            if (LoreManager.flipPage(item)) {
                event.setCancelled(true);
                
                Player player = (Player) event.getWhoClicked();
                
                // SECURITY PATCH: Force server inventory sync to the client 
                // to prevent client-side ghost items and duplication glitches.
                player.updateInventory();

                String soundName = plugin.getConfig().getString("flip-sound", "ITEM_BOOK_PAGE_TURN").toUpperCase();
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
