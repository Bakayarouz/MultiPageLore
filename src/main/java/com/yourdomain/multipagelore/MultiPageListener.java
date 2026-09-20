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

    /**
     * GUI PROTECTION CHECK:
     * Returns true if the inventory is a real container (Player, Chest, Barrel, etc.)
     * Returns false if the inventory is a virtual UI created by another plugin (Crates, AH, Shops).
     */
    private boolean isSafeInventory(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder holder = inv.getHolder();
        // Only allow player inventories, physical block containers, and animal inventories (horses)
        return holder instanceof org.bukkit.entity.Player || 
               holder instanceof org.bukkit.block.Container || 
               holder instanceof org.bukkit.entity.AbstractHorse;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryOpen(InventoryOpenEvent event) {
        // Only automatically scan the opened inventory if it's a real chest/barrel
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

    // priority = HIGHEST and NO ignoreCancelled=true to allow ExcellentCrates/zAuctionHouse support
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        
        // 1.21.1 Fast-fail: Ignore empty clicks or items without meta instantly
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return;

        // Determine if this is a real inventory or a plugin GUI
        boolean isVirtualGUI = !isSafeInventory(event.getClickedInventory());

        // SECURITY CHECK FOR PVP/TRADE SERVERS: 
        // If it's a real inventory AND the event was cancelled (by an AntiCheat, CombatLog, or Trade plugin), STOP immediately.
        if (!isVirtualGUI && event.isCancelled()) return;

        // If it's a Virtual GUI (Crates, AH), we ignore the cancellation and bake anyway.
        LoreManager.bakeItemIfNeeded(item);

        String configAction = plugin.getConfig().getString("flip-action", "SWAP_OFFHAND").toUpperCase();
        ClickType targetClickType;
        try {
            targetClickType = ClickType.valueOf(configAction);
        } catch (IllegalArgumentException e) {
            targetClickType = ClickType.SWAP_OFFHAND;
        }

        // If they didn't use the flip key, stop here so the GUI plugin/vanilla mechanics can handle the normal click
        if (event.getClick() != targetClickType) return;

        boolean isBaked = item.getItemMeta().getPersistentDataContainer().has(MultiPageLorePlugin.PAGES_KEY);

        if (isBaked) {
            if (LoreManager.flipPage(item)) {
                // Always ensure the event stays cancelled after a page flip so they don't move the item
                event.setCancelled(true); 
                Player player = (Player) event.getWhoClicked();
                
                String soundName = plugin.getConfig().getString("flip-sound", "ITEM_BOOK_PAGE_TURN").toUpperCase();
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
