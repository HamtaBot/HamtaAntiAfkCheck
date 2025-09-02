package fr.hamta.antiafk;

import fr.hamta.antiafk.commands.ReloadCommand;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HamtaAntiAfkCheck extends JavaPlugin implements Listener {

    private static HamtaAntiAfkCheck instance;
    private FileConfiguration config;
    private final HashSet<UUID> clickedPlayerRecently = new HashSet<>();

    public static HamtaAntiAfkCheck getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        config = getConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        // Commande giveafkchecker
        getCommand("giveafkchecker").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Commande uniquement pour les joueurs.");
                return true;
            }

            if (!player.hasPermission("hamtaafk.give")) {
                player.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'utiliser cette commande !");
                return true;
            }

            player.getInventory().addItem(createCheckerItem());
            player.sendMessage(ChatColor.GREEN + "Tu as reçu l'item AntiAfk Checker !");
            return true;
        });

        // Commande reload
        getCommand("hamtaafk").setExecutor(new ReloadCommand());

        getLogger().info("HamtaAntiAfkCheck enabled !");
    }

    @Override
    public void onDisable() {
        getLogger().info("HamtaAntiAfkCheck disabled !");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        config = getConfig();
    }

    private ItemStack createCheckerItem() {
        String itemTypeName = config.getString("paper.item-type", "PAPER");
        Material itemType = Material.matchMaterial(itemTypeName.toUpperCase());
        if (itemType == null) itemType = Material.PAPER;

        ItemStack item = new ItemStack(itemType, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            // Masque les attributs automatiques (Attack Damage / Attack Speed)
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            // Nom avec HEX legacy et codes & (ex: &#006EFF&lV...)
            String nameString = config.getString("paper.name", "&e&lAntiAfk Checker");
            meta.setDisplayName(parseColors(nameString));

            // Lore multi-lignes
            if (config.contains("paper.lore")) {
                List<String> loreList = config.getStringList("paper.lore");
                List<String> loreColored = new ArrayList<>();
                for (String line : loreList) {
                    loreColored.add(parseColors(line));
                }
                meta.setLore(loreColored);
            }

            // CustomModelData
            if (config.contains("paper.custom-model-data")) {
                meta.setCustomModelData(config.getInt("paper.custom-model-data"));
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    // Méthode utilitaire pour parser HEX legacy et & codes
    private String parseColors(String input) {
        // Remplace &#RRGGBB par §x§R§R§G§G§B§B
        StringBuilder sb = new StringBuilder();
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 7 < chars.length && chars[i+1] == '#') {
                sb.append('§').append('x');
                for (int j = i+2; j <= i+7; j++) {
                    sb.append('§').append(chars[j]);
                }
                i += 7;
            } else {
                sb.append(chars[i]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    // Clic sur un joueur
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("hamtaafk.use")) {
            player.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'utiliser ce papier !");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        Material expectedType = Material.matchMaterial(config.getString("paper.item-type", "PAPER"));
        if (item.getType() != expectedType) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;
        if (meta.getCustomModelData() != config.getInt("paper.custom-model-data")) return;

        clickedPlayerRecently.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> clickedPlayerRecently.remove(player.getUniqueId()), 1L);

        CompletableFuture.supplyAsync(() -> {
            try {
                String placeholder = "%jetsantiafkpro_afk_minutes%";
                return PlaceholderAPI.setPlaceholders(target, placeholder);
            } catch (Exception e) {
                return null;
            }
        }).orTimeout(3, TimeUnit.SECONDS).whenComplete((result, error) -> {
            Bukkit.getScheduler().runTask(this, () -> {
                if (result != null && !result.isEmpty() && error == null) {
                    String msg = config.getString("messages.result", "&aLe joueur %player% est AFK depuis %minutes% minutes.");
                    msg = msg.replace("%player%", target.getName()).replace("%minutes%", result);
                    player.sendMessage(msg);
                } else {
                    String msg = config.getString("messages.no-result", "&cImpossible de récupérer les données AFK de %player%.")
                            .replace("%player%", target.getName());
                    player.sendMessage(msg);
                }
            });
        });
    }

    // Clic dans le vide ou sur un bloc
    @EventHandler
    public void onRightClickAir(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;

        Player player = event.getPlayer();
        if (clickedPlayerRecently.contains(player.getUniqueId())) return;

        if (!player.hasPermission("hamtaafk.use")) {
            player.sendMessage(ChatColor.RED + "Tu n'as pas la permission d'utiliser ce papier !");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        Material expectedType = Material.matchMaterial(config.getString("paper.item-type", "PAPER"));
        if (item.getType() != expectedType) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return;
        if (meta.getCustomModelData() != config.getInt("paper.custom-model-data")) return;

        String msg = config.getString("messages.not-a-player", "&cVous devez viser un joueur !");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }
}
