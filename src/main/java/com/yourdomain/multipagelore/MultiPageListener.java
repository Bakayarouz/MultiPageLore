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

import java.util.HashMap;
import java.util.UUID;

public class MultiPageListener implements Listener {

    private final MultiPageLorePlugin plugin;
    
    // Cooldown map to prevent macro spam and exploit packets (Stores Player UUID -> Epoch Millis)
    private final HashMap<UUID, Long> flipCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 250; // 0.25 seconds between page turns

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
            Player player = (Player) event.getWhoClicked();
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();

            // COOLDOWN CHECK: Drop out if the player is spamming the flip action too fast
            if (flipCooldowns.containsKey(playerId)) {
                long lastFlip = flipCooldowns.get(playerId);
                if (now - lastFlip < COOLDOWN_MS) {
                    event.setCancelled(true); // Keep the click safe even during spam
                    return;
                }
            }

            // Update cooldown timestamp
            flipCooldowns.put(playerId, now);

            if (LoreManager.flipPage(item)) {
                event.setCancelled(true);
                
                String soundName = plugin.getConfig().getString("flip-sound", "ITEM_BOOK_PAGE_TURN").toUpperCase();
                try {
                    Sound sound = Sound.valueOf(soundName);
                    player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
