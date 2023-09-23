package com.badbones69.crazyenchantments.paper.listeners;

import com.badbones69.crazyenchantments.paper.CrazyEnchantments;
import com.badbones69.crazyenchantments.paper.Methods;
import com.badbones69.crazyenchantments.paper.Starter;
import com.badbones69.crazyenchantments.paper.api.FileManager.Files;
import com.badbones69.crazyenchantments.paper.api.enums.Messages;
import com.badbones69.crazyenchantments.paper.api.enums.pdc.DataKeys;
import com.badbones69.crazyenchantments.paper.api.objects.ItemBuilder;
import com.badbones69.crazyenchantments.paper.controllers.settings.EnchantmentBookSettings;
import com.badbones69.crazyenchantments.paper.utilities.misc.ColorUtils;
import com.ryderbelserion.cluster.bukkit.utils.LegacyUtils;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class ScramblerListener implements Listener {

    private final @NotNull CrazyEnchantments plugin = JavaPlugin.getPlugin(CrazyEnchantments.class);

    private final Starter starter = this.plugin.getStarter();

    private final Methods methods = this.starter.getMethods();

    private final EnchantmentBookSettings enchantmentBookSettings = this.starter.getEnchantmentBookSettings();

    private final HashMap<UUID, BukkitTask> roll = new HashMap<>();

    private ItemBuilder scramblerItem;
    private ItemBuilder pointer;
    private boolean animationToggle;
    private String guiName;

    public void loadScrambler() {
        FileConfiguration config = Files.CONFIG.getFile();
        this.scramblerItem = new ItemBuilder()
        .setMaterial(Objects.requireNonNull(config.getString("Settings.Scrambler.Item")))
        .setName(config.getString("Settings.Scrambler.Name"))
        .setLore(config.getStringList("Settings.Scrambler.Lore"))
        .setGlow(config.getBoolean("Settings.Scrambler.Glowing"));
        this.pointer = new ItemBuilder()
        .setMaterial(Objects.requireNonNull(config.getString("Settings.Scrambler.GUI.Pointer.Item")))
        .setName(config.getString("Settings.Scrambler.GUI.Pointer.Name"))
        .setLore(config.getStringList("Settings.Scrambler.GUI.Pointer.Lore"));
        this.animationToggle = Files.CONFIG.getFile().getBoolean("Settings.Scrambler.GUI.Toggle");
        this.guiName = LegacyUtils.color(Files.CONFIG.getFile().getString("Settings.Scrambler.GUI.Name"));
    }

    /**
     * Get the scrambler item stack.
     * @return The scramblers.
     */
    public ItemStack getScramblers() {
        return getScramblers(1);
    }

    /**
     * Get the scrambler item stack.
     * @param amount The amount you want.
     * @return The scramblers.
     */
    public ItemStack getScramblers(int amount) {
        ItemStack item = this.scramblerItem.setAmount(amount).build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(DataKeys.SCRAMBLER.getKey(), PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isScrambler(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(DataKeys.SCRAMBLER.getKey());
    }

    private void setGlass(Inventory inv) {
        for (int slot = 0; slot < 9; slot++) {
            if (slot != 4) {
                inv.setItem(slot, this.methods.getRandomPaneColor().setName(" ").build());
                inv.setItem(slot + 18, this.methods.getRandomPaneColor().setName(" ").build());
            } else {
                inv.setItem(slot, this.pointer.build());
                inv.setItem(slot + 18, this.pointer.build());
            }
        }
    }

    public void openScrambler(Player player, ItemStack book) {
        Inventory inventory = this.plugin.getServer().createInventory(null, 27, guiName);
        setGlass(inventory);

        for (int slot = 9; slot > 8 && slot < 18; slot++) {
            inventory.setItem(slot, this.enchantmentBookSettings.getNewScrambledBook(book));
        }

        player.openInventory(inventory);
        startScrambler(player, inventory, book);
    }

    private void startScrambler(final Player player, final Inventory inventory, final ItemStack book) {
        this.roll.put(player.getUniqueId(), new BukkitRunnable() {
            int time = 1;
            int full = 0;
            int open = 0;

            @Override
            public void run() {
                if (this.full <= 50) { // When spinning.
                    moveItems(inventory, book);
                    setGlass(inventory);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 1f, 1f);
                }

                this.open++;

                if (this.open >= 5) {
                    player.openInventory(inventory);
                    this.open = 0;
                }

                this.full++;
                if (this.full > 51) {
                    if (slowSpin().contains(time)) { // When Slowing Down
                        moveItems(inventory, book);
                        setGlass(inventory);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 1f, 1f);
                    }

                    this.time++;

                    if (time == 60) { // When done
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f);
                        cancel();
                        roll.remove(player.getUniqueId());
                        ItemStack item = inventory.getItem(13).clone();
                        item.setType(enchantmentBookSettings.getEnchantmentBookItem().getType());
                        methods.setDurability(item, methods.getDurability(enchantmentBookSettings.getEnchantmentBookItem()));

                        if (methods.isInventoryFull(player)) {
                            player.getWorld().dropItem(player.getLocation(), item);
                        } else {
                            player.getInventory().addItem(item);
                        }

                    } else if (time > 60) { // Just in case the cancel fails.
                        cancel();
                    }
                }
            }
        }.runTaskTimer(this.plugin, 1, 1));
    }

    private List<Integer> slowSpin() {
        List<Integer> slow = new ArrayList<>();
        int full = 120;
        int cut = 15;

        for (int amount = 120; cut > 0; full--) {
            if (full <= amount - cut || full >= amount - cut) {
                slow.add(amount);
                amount = amount - cut;
                cut--;
            }
        }

        return slow;
    }

    private void moveItems(Inventory inv, ItemStack book) {
        List<ItemStack> items = new ArrayList<>();

        for (int slot = 9; slot > 8 && slot < 17; slot++) {
            items.add(inv.getItem(slot));
        }

        ItemStack newBook = this.enchantmentBookSettings.getNewScrambledBook(book);
        newBook.setType(this.methods.getRandomPaneColor().getMaterial());
        inv.setItem(9, newBook);

        for (int amount = 0; amount < 8; amount++) {
            inv.setItem(amount + 10, items.get(amount));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onReRoll(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() != null) {
            ItemStack book = event.getCurrentItem() != null ? event.getCurrentItem() : new ItemStack(Material.AIR);
            ItemStack scrambler = event.getCursor() != null ? event.getCursor() : new ItemStack(Material.AIR);

            if (book.getType() == Material.AIR || scrambler.getType() == Material.AIR) return;
            if (book.getAmount() != 1 || scrambler.getAmount() != 1) return;
            if (!isScrambler(scrambler) || !this.enchantmentBookSettings.isEnchantmentBook(book)) return;
            if (event.getClickedInventory().getType() != InventoryType.PLAYER) {
                player.sendMessage(Messages.NEED_TO_USE_PLAYER_INVENTORY.getMessage());
                return;
            }

            event.setCancelled(true);
            player.setItemOnCursor(new ItemStack(Material.AIR));

            if (this.animationToggle) {
                event.setCurrentItem(new ItemStack(Material.AIR));
                openScrambler(player, book);
            } else {
                event.setCurrentItem(this.enchantmentBookSettings.getNewScrambledBook(book));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(this.guiName)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onScramblerClick(PlayerInteractEvent event) {
        ItemStack item = this.methods.getItemInHand(event.getPlayer());

        if (item != null) {
            if (getScramblers().isSimilar(item)) event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerLeave(PlayerQuitEvent event) {
        final Player player = event.getPlayer();

        try {
            this.roll.get(player.getUniqueId()).cancel();
            this.roll.remove(player.getUniqueId());
        } catch (Exception ignored) {}
    }
}