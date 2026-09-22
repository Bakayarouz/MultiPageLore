package com.yourdomain.multipagelore;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class LoreManager {

    private static MultiPageLorePlugin plugin;

    public static final String PAGE_DELIMITER = "\u0000";
    public static final String LINE_DELIMITER = "\u0001";
    public static final String HEADER_TAG = "---header---";
    public static final String PAGE_TAG = "---page---";
    public static final String FOOTER_TAG = "---footer---";
    private static final int DEFAULT_MAX_PAGES = 5;

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public static void init(MultiPageLorePlugin instance) {
        plugin = instance;
    }

    /**
     * Reads the configurable page cap from config.yml ("max-pages"), falling back
     * to DEFAULT_MAX_PAGES if unset. Re-read on every call so a /multipagelore reload
     * picks up changes immediately.
     */
    private static int getMaxPages() {
        if (plugin == null) {
            return DEFAULT_MAX_PAGES;
        }
        int configured = plugin.getConfig().getInt("max-pages", DEFAULT_MAX_PAGES);
        return configured > 0 ? configured : DEFAULT_MAX_PAGES;
    }

    public static boolean bakeItemIfNeeded(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING)) {
            int currentPage = pdc.getOrDefault(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
            renderPage(item, currentPage);
            return false;
        }

        if (!meta.hasLore()) {
            return false;
        }

        List<Component> lore = meta.lore();
        if (lore == null) {
            return false;
        }

        boolean hasPageTag = false;
        boolean hasHeaderTag = false;

        for (Component line : lore) {
            String plainText = PLAIN.serialize(line);
            if (plainText.contains(PAGE_TAG)) {
                hasPageTag = true;
            }
            if (plainText.contains(HEADER_TAG)) {
                hasHeaderTag = true;
            }
        }

        if (!hasPageTag) {
            return false;
        }

        int maxPages = getMaxPages();

        List<Component> headerLines = new ArrayList<>();
        List<List<Component>> pages = new ArrayList<>();
        List<Component> footerLines = new ArrayList<>();
        List<Component> buffer = new ArrayList<>();

        int sectionState = hasHeaderTag ? 0 : 1; // 0 = header, 1 = pages, 2 = footer

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
                if (sectionState != 1) {
                    continue;
                }
                pages.add(new ArrayList<>(buffer));
                buffer.clear();
                if (pages.size() > maxPages) {
                    return false;
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

        if (pages.size() > maxPages) {
            return false;
        }

        if (!headerLines.isEmpty()) {
            pdc.set(MultiPageLorePlugin.HEADER_KEY, PersistentDataType.STRING, serializeLines(headerLines));
        }

        StringBuilder serializedPages = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            serializedPages.append(serializeLines(pages.get(i)));
            if (i < pages.size() - 1) {
                serializedPages.append(PAGE_DELIMITER);
            }
        }
        pdc.set(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING, serializedPages.toString());

        if (!footerLines.isEmpty()) {
            pdc.set(MultiPageLorePlugin.FOOTER_KEY, PersistentDataType.STRING, serializeLines(footerLines));
        }

        pdc.set(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
        item.setItemMeta(meta);
        renderPage(item, 0);
        return true;
    }

    private static String serializeLines(List<Component> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(GSON.serialize(lines.get(i)));
            if (i < lines.size() - 1) {
                builder.append(LINE_DELIMITER);
            }
        }
        return builder.toString();
    }

    public static boolean flipPage(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING)) {
            return false;
        }

        String rawData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        if (rawData == null) {
            return false;
        }

        String[] rawPages = rawData.split(PAGE_DELIMITER, -1);
        int totalPages = rawPages.length;
        if (totalPages <= 1) {
            return false;
        }

        int currentPage = pdc.getOrDefault(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, 0);
        int nextPage = (currentPage + 1) % totalPages;

        pdc.set(MultiPageLorePlugin.CURRENT_PAGE_KEY, PersistentDataType.INTEGER, nextPage);
        item.setItemMeta(meta);
        renderPage(item, nextPage);
        return true;
    }

    public static void renderPage(ItemStack item, int pageIndex) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String rawPagesData = pdc.get(MultiPageLorePlugin.PAGES_KEY, PersistentDataType.STRING);
        if (rawPagesData == null) {
            return;
        }

        List<Component> finalLore = new ArrayList<>();

        String rawHeader = pdc.get(MultiPageLorePlugin.HEADER_KEY, PersistentDataType.STRING);
        if (rawHeader != null && !rawHeader.isEmpty()) {
            for (String lineJson : rawHeader.split(LINE_DELIMITER, -1)) {
                if (!lineJson.isEmpty()) {
                    finalLore.add(GSON.deserialize(lineJson));
                }
            }
        }

        String[] rawPages = rawPagesData.split(PAGE_DELIMITER, -1);
        if (pageIndex < 0 || pageIndex >= rawPages.length) {
            pageIndex = 0;
        }

        for (String lineJson : rawPages[pageIndex].split(LINE_DELIMITER, -1)) {
            if (!lineJson.isEmpty()) {
                finalLore.add(GSON.deserialize(lineJson));
            }
        }

        String rawFooter = pdc.get(MultiPageLorePlugin.FOOTER_KEY, PersistentDataType.STRING);
        if (rawFooter != null && !rawFooter.isEmpty()) {
            for (String lineJson : rawFooter.split(LINE_DELIMITER, -1)) {
                if (!lineJson.isEmpty()) {
                    finalLore.add(GSON.deserialize(lineJson));
                }
            }
        }

        int maxLineWidth = 0;
        for (Component component : finalLore) {
            int len = PLAIN.serialize(component).length();
            if (len > maxLineWidth) {
                maxLineWidth = len;
            }
        }

        if (rawPages.length > 1) {
            finalLore.add(Component.empty());
            finalLore.add(buildFooter(rawPages.length, pageIndex, maxLineWidth));
        }

        meta.lore(finalLore);
        item.setItemMeta(meta);
    }

    private static Component buildFooter(int totalPages, int currentPage, int maxLineWidth) {
        StringBuilder footerBuilder = new StringBuilder();
        for (int i = 0; i < totalPages; i++) {
            footerBuilder.append(i == currentPage ? "\u25cf " : "\u25cb ");
        }
        footerBuilder.append("\u24bb");

        String rawFooterText = footerBuilder.toString();
        int footerLength = rawFooterText.length();
        int padCount = Math.max(0, (maxLineWidth - footerLength) / 2);
        String padding = " ".repeat(padCount);

        String activeColor = plugin.getConfig().getString("footer.active-color", "&a");
        String inactiveColor = plugin.getConfig().getString("footer.inactive-color", "&7");
        String actionColor = plugin.getConfig().getString("footer.action-color", "&6");

        String formatted = padding + rawFooterText
                .replace("\u25cf", ChatColor.translateAlternateColorCodes('&', activeColor + "\u25cf"))
                .replace("\u25cb", ChatColor.translateAlternateColorCodes('&', inactiveColor + "\u25cb"))
                .replace("\u24bb", ChatColor.translateAlternateColorCodes('&', actionColor + "\u24bb"));

        return Component.text(ChatColor.translateAlternateColorCodes('&', formatted));
    }
}
