package com.yourdomain.multipagelore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class MultiPageListener implements Listener {

    private final MultiPageLorePlugin plugin;
    private final HashMap<UUID, Long> flipCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 250L;

    // Cache of resolved "ignored" holder classes, rebuilt on plugin construction / reload.
    private List<Class<?>> ignoredHolderClasses;

    public MultiPageListener(MultiPageLorePlugin plugin) {
        this.plugin = plugin;
        loadIgnoredHolderClasses();
    }

    /**
     * Reads "ignored-inventory-holders" from config.yml — a list of fully-qualified
     * InventoryHolder class names whose menus should never be treated as a "safe"
     * inventory for baking/flipping. Missing plugins are skipped silently. Call this
     * again after a config reload to pick up changes without a restart.
     */
    public void loadIgnoredHolderClasses() {
        List<Class<?>> resolved = new ArrayList<>();
        List<String> configured = plugin.getConfig().getStringList("ignored-inventory-holders");

        // Sensible default if the config key is missing entirely (e.g. old config.yml).
        if (configured.isEmpty() && !plugin.getConfig().isSet("ignored-inventory-holders")) {
            configured = List.of("net.Indyuce.mmoitems.gui.PluginInventory");
        }

        for (String className : configured) {
            try {
                resolved.add(Class.forName(className));
            } catch (ClassNotFoundException ignored) {
                // Plugin providing this class isn't installed — nothing to ignore.
            }
        }
        this.ignoredHolderClasses = resolved;
    }

    private boolean isSafeInventory(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder holder = inv.getHolder();
        if (holder == null) {
            return false;
        }

        for (Class<?> ignoredClass : ignoredHolderClasses) {
            if (ignoredClass.isInstance(holder)) {
                return false;
            }
        }

        return holder instanceof Player
                || holder instanceof Container
                || holder instanceof AbstractHorse
                || inv.getType() == InventoryType.CRAFTING
                || inv.getType() == InventoryType.PLAYER;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Prevent the cooldown map from growing forever across server uptime.
        flipCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.isEmpty()) {
            return;
        }

        LoreManager.bakeItemIfNeeded(item);

        if (!item.hasItemMeta()) {
            return;
        }

        boolean isVirtualGUI = !isSafeInventory(event.getClickedInventory());
        if (!isVirtualGUI && event.isCancelled()) {
            return;
        }

        String configAction = plugin.getConfig().getString("flip-action", "SWAP_OFFHAND").toUpperCase();
        ClickType targetClickType;
        try {
            targetClickType = ClickType.valueOf(configAction);
        } catch (IllegalArgumentException e) {
            targetClickType = ClickType.SWAP_OFFHAND;
        }

        if (event.getClick() != targetClickType) {
            return;
        }

        boolean isBaked = item.getItemMeta().getPersistentDataContainer().has(MultiPageLorePlugin.PAGES_KEY);
        if (!isBaked) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastFlip = flipCooldowns.get(playerId);
        if (lastFlip != null && now - lastFlip < COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        flipCooldowns.put(playerId, now);

        if (LoreManager.flipPage(item)) {
            event.setCancelled(true);
            String soundName = plugin.getConfig().getString("flip-sound", "ITEM_BOOK_PAGE_TURN").toUpperCase();
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            } catch (IllegalArgumentException ignored) {
                // Invalid sound name in config — just skip the sound.
            }
        }
    }
}
