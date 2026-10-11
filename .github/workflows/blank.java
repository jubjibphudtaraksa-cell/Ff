package com.fiatx2.rtp;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RTPMenu extends JavaPlugin implements Listener, CommandExecutor {

    private static final int RADIUS = 5000;
    private final Map<UUID, BukkitTask> countdowns = new HashMap<>();

    // ตัวระบุ GUI
    private static class Menu implements InventoryHolder {
        final boolean confirm;
        final World.Environment env; // เฉพาะหน้า confirm
        Inventory inv;
        Menu(boolean confirm, World.Environment env) { this.confirm = confirm; this.env = env; }
        @Override public Inventory getInventory() { return inv; }
    }

    @Override
    public void onEnable() {
        getCommand("rtp").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private ItemStack item(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(name);
        i.setItemMeta(meta);
        return i;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        Menu h = new Menu(false, null);
        Inventory inv = Bukkit.createInventory(h, 27, "RTP");
        h.inv = inv;
        inv.setItem(11, item(Material.GRASS_BLOCK, "§aOverworld"));
        inv.setItem(13, item(Material.NETHERRACK, "§cNether"));
        inv.setItem(15, item(Material.END_STONE, "§eThe End"));
        p.openInventory(inv);
        return true;
    }

    private void openConfirm(Player p, World.Environment env) {
        Menu h = new Menu(true, env);
        Inventory inv = Bukkit.createInventory(h, 27, "CONFIRM SELECTION");
        h.inv = inv;
        inv.setItem(11, item(Material.LIME_CONCRETE, "§aConfirm"));
        inv.setItem(15, item(Material.RED_CONCRETE, "§cCancel"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getSlot();

        if (!h.confirm) {
            switch (slot) {
                case 11 -> openConfirm(p, World.Environment.NORMAL);
                case 13 -> openConfirm(p, World.Environment.NETHER);
                case 15 -> openConfirm(p, World.Environment.THE_END);
            }
        } else {
            if (slot == 15) {
                p.closeInventory();
            } else if (slot == 11) {
                p.closeInventory();
                startCountdown(p, h.env);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ยกเลิกถ้าขยับระหว่างนับ
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!countdowns.containsKey(e.getPlayer().getUniqueId())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t != null && (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ())) {
            countdowns.remove(e.getPlayer().getUniqueId()).cancel();
            e.getPlayer().sendMessage("§cยกเลิกการวาป เพราะคุณขยับ");
        }
    }

    private void startCountdown(Player p, World.Environment env) {
        if (countdowns.containsKey(p.getUniqueId())) return;
        World w = Bukkit.getWorlds().stream().filter(x -> x.getEnvironment() == env).findFirst().orElse(null);
        if (w == null) { p.sendMessage("§cไม่พบโลกนี้"); return; }

        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            int n = 1;
            @Override public void run() {
                if (!p.isOnline()) { countdowns.remove(p.getUniqueId()); cancel(); return; }
                if (n <= 3) {
                    p.sendTitle("§e" + n, "", 0, 20, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    n++;
                } else {
                    countdowns.remove(p.getUniqueId());
                    cancel();
                    Location loc = findSafe(w);
                    if (loc == null) { p.sendMessage("§cหาจุดวาปไม่เจอ ลองใหม่"); return; }
                    p.teleport(loc);
                    p.sendMessage("§aวาปสำเร็จ!");
                }
            }
        }.runTaskTimer(this, 0L, 20L);
        countdowns.put(p.getUniqueId(), task);
    }

    private boolean unsafe(Material m) {
        return m == Material.LAVA || m == Material.WATER || m == Material.MAGMA_BLOCK
                || m == Material.CACTUS || m == Material.FIRE || m == Material.SOUL_FIRE
                || m == Material.CAMPFIRE || m == Material.SWEET_BERRY_BUSH || m == Material.POWDER_SNOW;
    }

    private Location findSafe(World w) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 100; i++) {
            int x = r.nextInt(-RADIUS, RADIUS + 1);
            int z = r.nextInt(-RADIUS, RADIUS + 1);

            if (w.getEnvironment() == World.Environment.NETHER) {
                x /= 5; z /= 5; // เนเทอร์เล็กกว่า
                for (int y = 120; y > 32; y--) {
                    var below = w.getBlockAt(x, y - 1, z).getType();
                    if (below.isSolid() && !unsafe(below)
                            && w.getBlockAt(x, y, z).isEmpty()
                            && w.getBlockAt(x, y + 1, z).isEmpty()) {
                        return new Location(w, x + 0.5, y, z + 0.5);
                    }
                }
            } else {
                int y = w.getHighestBlockYAt(x, z);
                var b = w.getBlockAt(x, y, z).getType();
                if (b.isAir() || unsafe(b)) continue;
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return null;
    }
}

