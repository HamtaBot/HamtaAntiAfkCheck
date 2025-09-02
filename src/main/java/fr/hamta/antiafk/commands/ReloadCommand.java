package fr.hamta.antiafk.commands;

import fr.hamta.antiafk.HamtaAntiAfkCheck;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("hamtaafk.reload")) {
            sender.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'exécuter cette commande !");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            HamtaAntiAfkCheck.getInstance().reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "HamtaAntiAfkCheck config reloaded!");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /hamtaafk reload");
        return true;
    }
}
