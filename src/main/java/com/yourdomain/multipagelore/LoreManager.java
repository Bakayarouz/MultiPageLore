package com.yourdomain.multipagelore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
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
    
    public static final String HEADER_TAG = "---header---";
    public static final String PAGE_TAG = "---page---";
    public static final String FOOTER_TAG = "---footer---";
    
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

        // 1. IDEMPOTENCY CHECK: Exits immediately if item is already baked
        if (pdc.has(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING)) {
            int currentPage = pdc.getOrDefault(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
            renderPage(item, currentPage);
            return false;
        }

        if (!meta.hasLore()) return false;

        List<Component> lore = meta.lore();
        if (lore == null) return false;

        boolean hasPageTag = false;
        boolean hasHeaderTag = false;
        boolean hasFooterTag = false;

        for (Component line : lore) {
            String plainText = PLAIN.serialize(line);
            if (plainText.contains(PAGE_TAG)) hasPageTag = true;
            if (plainText.contains(HEADER_TAG)) hasHeaderTag = true;
            if (plainText.contains(FOOTER_TAG)) hasFooterTag = true;
        }

        if (!hasPageTag) return false;

        List<Component> headerLines = new ArrayList<>();
        List<List<Component>> pages = new ArrayList<>();
        List<Component> footerLines = new ArrayList<>();
        List<Component> buffer = new ArrayList<>();

        int sectionState = hasHeaderTag ? 0 : 1; // 0 = Header, 1 = Pages, 2 = Footer

        for (Component line : lore) {
            String plainText = PLAIN.serialize(line);

            if (plainText.contains(HEADER_TAG)) {
                headerLines.addAll(buffer);
                buffer.clear();
                sectionState = 1;
                continue;
            }

            if (plainText.contains(FOOTER_TAG)) {
                if (sectionState == 1) {
                    pages.add(new ArrayList<>(buffer));
                    buffer.clear();
                }
                sectionState = 2;
                continue;
            }

            if (plainText.contains(PAGE_TAG)) {
                if (sectionState == 1) {
                    pages.add(new ArrayList<>(buffer));
                    buffer.clear();
                    if (pages.size() > MAX_ALLOWED_PAGES) return false;
                }
                continue;
            }

            buffer.add(line);
        }

        if (sectionState == 1 && !buffer.isEmpty()) {
            pages.add(buffer);
        } else if (sectionState == 2 && !buffer.isEmpty()) {
            footerLines.addAll(buffer);
        }

        // Store Header lines
        if (!headerLines.isEmpty()) {
            StringBuilder serializedHeader = new StringBuilder();
            for (int i = 0; i < headerLines.size(); i++) {
                serializedHeader.append(GSON.serialize(headerLines.get(i)));
                if (i < headerLines.size() - 1) serializedHeader.append(LINE_DELIMITER);
            }
            pdc.set(MultiPageLorePlugin.HEADER_KEY, PersistentDataType.STRING, serializedHeader.toString());
        }

        // Store Middle Pages
        StringBuilder serializedPages = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            List<Component> pageLines = pages.get(i);
            for (int j = 0; j < pageLines.size(); j++) {
                serializedPages.append(GSON.serialize(pageLines.get(j)));
                if (j < pageLines.size() - 1) serializedPages.append(LINE_DELIMITER);
            }
            if (i < pages.size() - 1) serializedPages.append(PAGE_DELIMITER);
        }
        pdc.set(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING, serializedPages.toString());

        // Store Footer lines
        if (!footerLines.isEmpty()) {
            StringBuilder serializedFooter = new StringBuilder();
            for (int i = 0; i < footerLines.size(); i++) {
                serializedFooter.append(GSON.serialize(footerLines.get(i)));
                if (i < footerLines.size() - 1) serializedFooter.append(LINE_DELIMITER);
            }
            pdc.set(MultiPageLorePlugin.FOOTER_KEY, PersistentDataType.STRING, serializedFooter.toString());
        }

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

        String rawPagesData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        if (rawPagesData == null) return;

        List<Component> finalLore = new ArrayList<>();

        // 1. Render Static Header (if present)
        if (pdc.has(MultiPageLorePlugin.HEADER_KEY, PersistentDataType.STRING)) {
            String rawHeader = pdc.get(MultiPageLorePlugin.HEADER_KEY, PersistentDataType.STRING);
            if (rawHeader != null && !rawHeader.isEmpty()) {
                for (String lineJson : rawHeader.split(LINE_DELIMITER, -1)) {
                    if (!lineJson.isEmpty()) finalLore.add(GSON.deserialize(lineJson));
                }
            }
        }

        // 2. Render Active Middle Page
        String[] rawPages = rawPagesData.split(PAGE_DELIMITER, -1);
        if (pageIndex < 0 || pageIndex >= rawPages.length) pageIndex = 0;

        String[] lineData = rawPages[pageIndex].split(LINE_DELIMITER, -1);
        for (String lineJson : lineData) {
            if (!lineJson.isEmpty()) {
                finalLore.add(GSON.deserialize(lineJson));
            }
        }

        // 3. Render Static Footer (if present)
        if (pdc.has(MultiPageLorePlugin.FOOTER_KEY, PersistentDataType.STRING)) {
            String rawFooter = pdc.get(MultiPageLorePlugin.FOOTER_KEY, PersistentDataType.STRING);
            if (rawFooter != null && !rawFooter.isEmpty()) {
                for (String lineJson : rawFooter.split(LINE_DELIMITER, -1)) {
                    if (!lineJson.isEmpty()) finalLore.add(GSON.deserialize(lineJson));
                }
            }
        }

        // 4. Render Centered Dot Indicator Footer
        if (rawPages.length > 1) {
            finalLore.add(Component.empty());
            finalLore.add(buildFooter(rawPages.length, pageIndex));
        }

        meta.lore(finalLore);
        item.setItemMeta(meta);
    }

    private static Component buildFooter(int totalPages, int currentPage) {
        StringBuilder footerBuilder = new StringBuilder();
        for (int i = 0; i < totalPages; i++) {
            if (i == currentPage) {
                footerBuilder.append("● ");
            } else {
                footerBuilder.append("○ ");
            }
        }
        footerBuilder.append("Ⓕ");

        int estimatedVisualLength = (totalPages * 2) + 2;
        int baseOffset = Math.max(1, (24 - estimatedVisualLength) / 2);
        String padding = " ".repeat(Math.max(0, baseOffset));

        String activeColor = plugin.getConfig().getString("footer.active-color", "&a");
        String inactiveColor = plugin.getConfig().getString("footer.inactive-color", "&7");
        String actionColor = plugin.getConfig().getString("footer.action-color", "&6");

        String formatted = padding + footerBuilder.toString()
                .replace("●", ChatColor.translateAlternateColorCodes('&', activeColor + "●"))
                .replace("○", ChatColor.translateAlternateColorCodes('&', inactiveColor + "○"))
                .replace("Ⓕ", ChatColor.translateAlternateColorCodes('&', actionColor + "Ⓕ"));

        return Component.text(ChatColor.translateAlternateColorCodes('&', formatted));
    }
}
