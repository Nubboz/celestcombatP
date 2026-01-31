package dev.nighter.celestCombat.listeners;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class SpearListener implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;

    // Track players with active spear countdown displays to avoid duplicates
    private final Map<UUID, Scheduler.Task> spearCountdownTasks = new ConcurrentHashMap<>();

    // Track thrown spears to their player owners
    private final Map<Integer, UUID> activeSpear = new ConcurrentHashMap<>();

    // Store original locations for riptide rollback
    private final Map<UUID, Location> spearOriginalLocations = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onSpearUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();

        // Check if player is right-clicking with a spear
        if (item != null && item.getType().toString().toLowerCase().contains("_spear") &&
                (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {

            // Check if spear usage is banned in this world
            if (combatManager.isSpearBanned(player)) {
                event.setCancelled(true);
                sendBannedMessage(player);
                return;
            }

            // Handle riptide spears differently - we need to prevent the interaction entirely
                if (combatManager.isSpearOnCooldown(player)) {
                    event.setCancelled(true);
                    sendCooldownMessage(player);
                    return;
                }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onLungeUse(SpearLungeEvent event) {
        Player player = event.getPlayer();
        // Check if spear usage is banned in this world
        if (combatManager.isSpearBanned(player)) {
            event.setCancelled(true);

            sendBannedMessage(player);
            //rollbackLunge(player);
            return;
        }

        // Check if spear is on cooldown
        if (combatManager.isSpearOnCooldown(player)) {
            event.setCancelled(true);
            sendCooldownMessage(player);
            //rollbackLunge(player);
            return;
        }

        // Set cooldown for riptide usage
        combatManager.setSpearCooldown(player);

        // Start displaying the countdown
        startSpearCountdown(player);

        // Refresh combat on riptide usage if enabled
        combatManager.refreshCombatOnSpearLand(player);

        // Clean up the stored location
        spearOriginalLocations.remove(player.getUniqueId());
    }


    private void rollbackLunge(Player player) {
        Location originalLocation = spearOriginalLocations.remove(player.getUniqueId());

        if (originalLocation != null) {
            // Method 2: Alternative approach - counter the velocity after a short delay
            Scheduler.runTaskLater(() -> {
                if (player.isOnline()) {
                    // Stop any remaining velocity
                    player.setVelocity(player.getVelocity().multiply(0));

                    // Ensure they're at the original location
                    if (player.getLocation().distance(originalLocation) > 5) {
                        player.teleport(originalLocation);
                    }
                }
            }, 2L);
        } else {
            // Fallback: just stop their velocity and add effects
            Scheduler.runTask(plugin, () -> {
                player.setVelocity(player.getVelocity().multiply(0));
            });
        }
    }

    /**
     * Starts a separate countdown task for spear cooldown display.
     * This ensures the countdown is shown regardless of combat status.
     */
    private void startSpearCountdown(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        // Cancel any existing countdown task for this player
        Scheduler.Task existingTask = spearCountdownTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // How often to update the countdown message (in ticks, 20 = 1 second)
        long updateInterval = 20L;

        // Create a new countdown task
        Scheduler.Task task = Scheduler.runTaskTimer(() -> {
            // Check if player is still online
            if (!player.isOnline()) {
                cancelSpearCountdown(playerUUID);
                return;
            }

            // Check if cooldown is still active
            if (!combatManager.isSpearOnCooldown(player)) {
                cancelSpearCountdown(playerUUID);
                return;
            }

            // Get remaining time
            int remainingTime = combatManager.getRemainingSpearCooldown(player);

            // Send the appropriate message
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("time", String.valueOf(remainingTime));

            // If player is in combat, CombatManager will handle the combined message
            // Otherwise, send a spear-specific message
            if (!combatManager.isInCombat(player)) {
                plugin.getMessageService().sendMessage(player, "spear_only_countdown", placeholders);
            }

        }, 0L, updateInterval);

        // Store the task
        spearCountdownTasks.put(playerUUID, task);
    }

    /**
     * Cancels and removes the spear countdown task for a player.
     */
    private void cancelSpearCountdown(UUID playerUUID) {
        Scheduler.Task task = spearCountdownTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Helper method to send banned message
     */
    private void sendBannedMessage(Player player) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        plugin.getMessageService().sendMessage(player, "spear_banned", placeholders);
    }

    /**
     * Helper method to send cooldown message
     */
    private void sendCooldownMessage(Player player) {
        int remainingTime = combatManager.getRemainingSpearCooldown(player);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("time", String.valueOf(remainingTime));
        plugin.getMessageService().sendMessage(player, "spear_cooldown", placeholders);
    }

    /**
     * Cleanup method to cancel all tasks when the plugin is disabled.
     * Call this from your main plugin's onDisable method.
     */
    public void shutdown() {
        spearCountdownTasks.values().forEach(Scheduler.Task::cancel);
        spearCountdownTasks.clear();
        activeSpear.clear();
        spearOriginalLocations.clear();
    }
}