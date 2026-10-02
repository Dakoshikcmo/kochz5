package ench5;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Ench5Plugin extends JavaPlugin implements Listener {

    private static final int LEVEL = 5;

    // Зачарование -> новый максимальный уровень в наковальне
    private final Map<Enchantment, Integer> maxLevels = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        putMax("protection", 5);
        putMax("unbreaking", 5); // удали эту строку, если Прочность не нужна
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void putMax(String key, int level) {
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (e != null) {
            maxLevels.put(e, level);
        }
    }

    // ---------- Наковальня ----------

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack left = event.getInventory().getItem(0);
        ItemStack right = event.getInventory().getItem(1);
        if (left == null || right == null) return;

        ItemStack fixed = result.clone();
        boolean changed = false;

        for (Map.Entry<Enchantment, Integer> entry : maxLevels.entrySet()) {
            Enchantment e = entry.getKey();
            int cap = entry.getValue();

            int resultLevel = levelOf(fixed, e);
            if (resultLevel == 0) continue; // ваниль это зачарование не оставила (несовместимо)

            int l1 = levelOf(left, e);
            int l2 = levelOf(right, e);
            if (l1 == 0 && l2 == 0) continue;

            int wanted = (l1 == l2) ? l1 + 1 : Math.max(l1, l2);
            wanted = Math.min(wanted, cap);

            if (wanted > resultLevel) {
                setLevel(fixed, e, wanted);
                changed = true;
            }
        }

        if (changed) {
            event.setResult(fixed);
        }
    }

    private int levelOf(ItemStack item, Enchantment e) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            return storage.getStoredEnchantLevel(e);
        }
        return item.getEnchantmentLevel(e);
    }

    private void setLevel(ItemStack item, Enchantment e, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.addStoredEnchant(e, level, true);
            item.setItemMeta(storage);
        } else {
            item.addUnsafeEnchantment(e, level);
        }
    }

    // ---------- Команды (как раньше) ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§e/ench5 book §7- выдать книги Защита V и Прочность V");
            player.sendMessage("§e/ench5 hand §7- наложить Защиту V и Прочность V на предмет в руке");
            return true;
        }

        Enchantment prot = Enchantment.getByKey(NamespacedKey.minecraft("protection"));
        Enchantment unb = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (prot == null || unb == null) {
            player.sendMessage("§cНе удалось найти зачарования на этой версии.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "book" -> {
                giveBook(player, prot);
                giveBook(player, unb);
                player.sendMessage("§aВыданы книги: Защита V и Прочность V.");
            }
            case "hand" -> {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType() == Material.AIR) {
                    player.sendMessage("§cВозьми предмет в руку.");
                    return true;
                }
                int applied = 0;
                if (prot.canEnchantItem(item)) {
                    item.addUnsafeEnchantment(prot, LEVEL);
                    applied++;
                }
                if (unb.canEnchantItem(item)) {
                    item.addUnsafeEnchantment(unb, LEVEL);
                    applied++;
                }
                player.sendMessage(applied > 0
                        ? "§aЗачарования наложены (" + applied + ")."
                        : "§cЭтот предмет не подходит для Защиты и Прочности.");
            }
            default -> player.sendMessage("§cИспользуй: /ench5 book | hand");
        }
        return true;
    }

    private void giveBook(Player player, Enchantment enchantment) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchantment, LEVEL, true);
        book.setItemMeta(meta);

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }
}
