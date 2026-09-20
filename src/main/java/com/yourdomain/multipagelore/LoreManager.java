package com.yourdomain.multipagelore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class LoreManager {

    private static MultiPageLorePlugin plugin;
    public static final String PAGE_DELIMITER = "\u0000";
    public static final String LINE_DELIMITER = "\u0001";
    public static final String SEPARATOR_TEXT = "---page---";
    private static final int MAX_ALLOWED_PAGES = 5;

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public static void init(MultiPageLorePlugin instance) {
        plugin = instance;
    }

    public static boolean bakeItemIfNeeded(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. IDEMPOTENCY CHECK: If already baked, do NOT re-parse. 
        // This stops MMOItems or dynamic UI refreshes from resetting the player's active page.
        if (pdc.has(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING)) {
            int currentPage = pdc.getOrDefault(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
            renderPage(item, currentPage);
            return false;
        }

        if (!meta.hasLore()) return false;

        List<Component> lore = meta.lore();
        if (lore == null) return false;

        List<List<Component>> pages = new ArrayList<>();
        List<Component> currentPageLines = new ArrayList<>();
        boolean hasSeparator = false;

        for (Component line : lore) {
            String plainText = PLAIN.serialize(line);
            if (plainText.contains(SEPARATOR_TEXT)) {
                hasSeparator = true;
                pages.add(new ArrayList<>(currentPageLines));
                currentPageLines.clear();
                if (pages.size() > MAX_ALLOWED_PAGES) return false;
                continue;
            }
            currentPageLines.add(line);
        }

        if (!hasSeparator) return false;
        if (!currentPageLines.isEmpty()) pages.add(currentPageLines);

        StringBuilder serializedData = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            List<Component> pageLines = pages.get(i);
            for (int j = 0; j < pageLines.size(); j++) {
                serializedData.append(GSON.serialize(pageLines.get(j)));
                if (j < pageLines.size() - 1) serializedData.append(LINE_DELIMITER);
            }
            if (i < pages.size() - 1) serializedData.append(PAGE_DELIMITER);
        }

        pdc.set(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING, serializedData.toString());
        pdc.set(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);

        item.setItemMeta(meta);
        renderPage(item, 0);
        return true;
    }

    public static boolean flipPage(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING)) return false;

        String rawData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        if (rawData == null) return false;

        String[] rawPages = rawData.split(PAGE_DELIMITER, -1);
        int totalPages = rawPages.length;
        if (totalPages <= 1) return false;

        int currentPage = pdc.getOrDefault(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
        int nextPage = (currentPage + 1) % totalPages;

        pdc.set(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, nextPage);
        item.setItemMeta(meta);

        renderPage(item, nextPage);
        return true;
    }

    public static void renderPage(ItemStack item, int pageIndex) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String rawData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        if (rawData == null) return;

        String[] rawPages = rawData.split(PAGE_DELIMITER, -1);
        if (pageIndex < 0 || pageIndex >= rawPages.length) pageIndex = 0;

        List<Component> finalLore = new ArrayList<>();
        String[] lineData = rawPages[pageIndex].split(LINE_DELIMITER, -1);
        for (String lineJson : lineData) {
            if (!lineJson.isEmpty()) {
                finalLore.add(GSON.deserialize(lineJson));
            }
        }

        // Generate centered dot-indicator footer
        if (rawPages.length > 1) {
            finalLore.add(Component.empty());
            finalLore.add(buildFooter(rawPages.length, pageIndex, meta.hasDisplayName() ? PLAIN.serialize(meta.displayName()) : item.getType().name()));
        }

        meta.lore(finalLore);
        item.setItemMeta(meta);
    }

    private static Component buildFooter(int totalPages, int currentPage, String itemName) {
        StringBuilder footerBuilder = new StringBuilder();
        for (int i = 0; i < totalPages; i++) {
            if (i == currentPage) {
                footerBuilder.append("● ");
            } else {
                footerBuilder.append("○ ");
            }
        }
        footerBuilder.append("Ⓕ");

        // Centering calculation wrapper
        String dotsText = footerBuilder.toString();
        int maxLineLength = Math.max(15, itemName.length() * 2);
        int paddingSize = Math.max(0, (maxLineLength - dotsText.length()) / 2);
        String padding = " ".repeat(Math.min(paddingSize, 12));

        String activeColor = plugin.getConfig().getString("footer.active-color", "&a");
        String inactiveColor = plugin.getConfig().getString("footer.inactive-color", "&7");
        String actionColor = plugin.getConfig().getString("footer.action-color", "&6");

        String formatted = padding + dotsText
                .replace("●", ChatColor.translateAlternateColorCodes('&', activeColor + "●"))
                .replace("○", ChatColor.translateAlternateColorCodes('&', inactiveColor + "○"))
                .replace("Ⓕ", ChatColor.translateAlternateColorCodes('&', actionColor + "Ⓕ"));

        return Component.text(ChatColor.translateAlternateColorCodes('&', formatted));
    }
}
