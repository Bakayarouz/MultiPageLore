package com.yourdomain.multipagelore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class LoreManager {

    private static MultiPageLorePlugin plugin;

    private static final String SEPARATOR_TEXT = "---page---";
    private static final String PAGE_DELIMITER = "\u0000"; 
    private static final String LINE_DELIMITER = "\u0001"; 

    // Native 1.21 Adventure Serializers
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static void init(MultiPageLorePlugin instance) {
        plugin = instance;
    }

    public static boolean bakeItemIfNeeded(ItemStack item) {
        // 1.21.1 Optimization: .isEmpty() is the fastest check for air blocks
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING)) return false; 

        List<Component> lore = meta.lore();
        if (lore == null) return false;

        List<List<Component>> pages = new ArrayList<>();
        List<Component> currentPage = new ArrayList<>();
        
        int maxCharLength = 0;
        if (meta.hasDisplayName()) {
            maxCharLength = PLAIN.serialize(meta.displayName()).length();
        }

        boolean hasSeparator = false;

        for (Component line : lore) {
            String plainText = PLAIN.serialize(line);
            
            if (plainText.contains(SEPARATOR_TEXT)) {
                hasSeparator = true;
                pages.add(new ArrayList<>(currentPage));
                currentPage.clear();
                continue;
            }
            
            currentPage.add(line);
            
            if (plainText.length() > maxCharLength) {
                maxCharLength = plainText.length();
            }
        }
        
        if (!hasSeparator) return false;
        if (!currentPage.isEmpty()) pages.add(currentPage);

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
        pdc.set(MultiPageLorePlugin.MAX_WIDTH_KEY, PersistentDataType.INTEGER, maxCharLength); 
        
        item.setItemMeta(meta);
        renderPage(item, 0);
        return true;
    }

    public static boolean flipPage(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String serializedData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        if (serializedData == null) return false;

        int totalPages = 1;
        for (int i = 0; i < serializedData.length(); i++) {
            if (serializedData.charAt(i) == '\u0000') totalPages++;
        }

        int currentPage = pdc.getOrDefault(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
        int nextPage = (currentPage + 1) % totalPages;

        pdc.set(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, nextPage);
        item.setItemMeta(meta);

        renderPage(item, nextPage);
        return true;
    }

    private static void renderPage(ItemStack item, int pageIndex) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        
        String serializedData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        int maxCharLength = pdc.getOrDefault(MultiPageLorePlugin.MAX_WIDTH_KEY, PersistentDataType.INTEGER, 20);
        
        if (serializedData == null) return;
        
        String[] pages = serializedData.split(PAGE_DELIMITER);
        String[] lines = pages[pageIndex].split(LINE_DELIMITER);

        List<Component> newLore = new ArrayList<>(lines.length + 2);
        for (String line : lines) {
            newLore.add(GSON.deserialize(line));
        }

        newLore.add(Component.empty());
        newLore.add(LEGACY.deserialize(generateCenteredFooter(maxCharLength, pageIndex, pages.length)));

        meta.lore(newLore);
        item.setItemMeta(meta);
    }

    private static String generateCenteredFooter(int maxCharLength, int currentPage, int totalPages) {
        String activeDot = plugin.getConfig().getString("active-dot", "&f●");
        String inactiveDot = plugin.getConfig().getString("inactive-dot", "&7○");
        String swapIcon = plugin.getConfig().getString("swap-icon", "&eⒻ");

        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < totalPages; i++) {
            dots.append(i == currentPage ? activeDot : inactiveDot).append(" ");
        }
        
        dots.append(swapIcon);

        // 1.21.1 Optimization: Use Native Adventure to strip colors instead of slow Regex
        String rawText = PLAIN.serialize(LEGACY.deserialize(dots.toString()));
        int dotsLen = rawText.length();
        
        int spacesRequired = Math.max(0, (maxCharLength - dotsLen) / 2);
        
        return " ".repeat(spacesRequired) + dots;
    }
}
