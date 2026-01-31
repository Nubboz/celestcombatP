package dev.nighter.celestCombat.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.combat.CombatManager;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class ItemRestrictionListener implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;


    private final Map<UUID, Integer> heldSlots = new ConcurrentHashMap<>();

    public static String formatItemName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }

        // Convert from UPPERCASE_WITH_UNDERSCORES to Title Case
        String[] words = material.name().split("_");
        StringBuilder formattedName = new StringBuilder();

        for (String word : words) {
            // Capitalize first letter, rest lowercase
            formattedName
                    .append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }

        return formattedName.toString().trim();
    }

    public void registerPacketListener(ProtocolManager protocolManager) {
         Set<PacketType> NOISE = Set.of(
                PacketType.Play.Server.REL_ENTITY_MOVE,
                PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                PacketType.Play.Server.ENTITY_POSITION_SYNC,
                PacketType.Play.Server.ENTITY_VELOCITY,
                PacketType.Play.Client.CLIENT_TICK_END
        );

        protocolManager.addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.LOWEST,
                PacketType.Play.Client.HELD_ITEM_SLOT,
                PacketType.Play.Client.BLOCK_DIG) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();

                // Track held slot immediately from packet
                if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_SLOT) {
                    int slot = event.getPacket().getIntegers().read(0);
                    heldSlots.put(player.getUniqueId(), slot);
                    return;
                }

                // Now handle left click (BLOCK_DIG)
                if (event.getPacketType() == PacketType.Play.Client.BLOCK_DIG) {
                    // Use tracked slot (fallback to held item slot)
                    int slot = heldSlots.getOrDefault(player.getUniqueId(), player.getInventory().getHeldItemSlot());
                    ItemStack item = player.getInventory().getItem(slot);
                    assert item != null;

                    // Only continue if it’s a spear
                    if (item != null && item.getType().name().endsWith("_SPEAR")) {
                        // Fire your custom event synchronously
                        SpearLungeEvent lungeEvent = new SpearLungeEvent(event, player, item);
                        Scheduler.runEntityTask(player, () ->{
                                Bukkit.getPluginManager().callEvent(lungeEvent);}
                        );

                        // Check restrictions
                        List<String> disabledItems = plugin.getConfig().getStringList("combat.item_restrictions.disabled_items");

                        if (combatManager.isSpearOnCooldown(player)
                                || combatManager.isSpearBanned(player)
                                || (combatManager.isInCombat(player)
                                && plugin.getConfig().getBoolean("combat.item_restrictions.enabled", true)
                                && isItemDisabled(item.getType(), disabledItems))) {

                            event.setCancelled(true);
                        }
                    }
                }
            }
        });

    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerLunge(SpearLungeEvent event) {
        // Check if item restrictions are enabled
        if (!plugin.getConfig().getBoolean("combat.item_restrictions.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("player", player.getName());
        placeholders.put("item", formatItemName(item.getType()));

            if (combatManager.isInCombat(player)) {
                List<String> disabledItems = plugin.getConfig().getStringList("combat.item_restrictions.disabled_items");

                // Check if the consumed item is in the disabled items list
                if (isItemDisabled(item.getType(), disabledItems)) {
                    event.setCancelled(true);
                    plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
                }
            }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        // Check if item restrictions are enabled
        if (!plugin.getConfig().getBoolean("combat.item_restrictions.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            List<String> disabledItems = plugin.getConfig().getStringList("combat.item_restrictions.disabled_items");

            ItemStack item = event.getItem();
            Map<String, String> placeholders = new HashMap<>();

            placeholders.put("player", player.getName());
            placeholders.put("item", formatItemName(item.getType()));
            // Check if the consumed item is in the disabled items list
            if (isItemDisabled(item.getType(), disabledItems)) {
                event.setCancelled(true);
                plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemUse(PlayerInteractEvent event) {
        // Check if item restrictions are enabled
        if (!plugin.getConfig().getBoolean("combat.item_restrictions.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Map<String, String> placeholders = new HashMap<>();

        if (combatManager.isInCombat(player)) {
            List<String> disabledItems = plugin.getConfig().getStringList("combat.item_restrictions.disabled_items");

            // Check if the consumed item is in the disabled items list
            if (isItemDisabled(item.getType(), disabledItems)) {
                event.setCancelled(true);

                placeholders.put("player", player.getName());
                placeholders.put("item", formatItemName(item.getType()));
                plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        // Check if item restrictions are enabled
        if (!plugin.getConfig().getBoolean("combat.item_restrictions.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            List<String> disabledItems = plugin.getConfig().getStringList("combat.item_restrictions.disabled_items");
            if (disabledItems.contains("ELYTRA") && player.isGliding()) {
                player.setGliding(false);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", "Elytra");
                plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
            }
        }
    }

    private boolean isItemDisabled(Material itemType, List<String> disabledItems) {
        return disabledItems.stream()
                .anyMatch(disabledItem ->
                        itemType.name().equalsIgnoreCase(disabledItem) ||
                                itemType.name().contains(disabledItem)
                );
    }
}