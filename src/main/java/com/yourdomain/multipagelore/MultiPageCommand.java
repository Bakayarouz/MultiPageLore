package com.yourdomain.multipagelore;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MultiPageCommand implements CommandExecutor, TabCompleter {

    private final MultiPageLorePlugin plugin;

    public MultiPageCommand(MultiPageLorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("multipagelore.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "[MultiPageLore] Config successfully reloaded!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /multipagelore reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("multipagelore.admin")) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
