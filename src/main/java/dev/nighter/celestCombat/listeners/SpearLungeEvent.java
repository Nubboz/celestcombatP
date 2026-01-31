package dev.nighter.celestCombat.listeners;

import com.comphenix.protocol.events.PacketEvent;
import lombok.Getter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class SpearLungeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    @Getter
    private final Player player;
    @Getter
    private final ItemStack item;
    @Getter
    private final PacketEvent event;
    private boolean cancelled;

    public SpearLungeEvent(PacketEvent event, Player player, ItemStack item) {
        this.player = player;
        this.item = item;
        this.event = event;
        player.sendMessage("ran lunge");
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {

        event.setCancelled(b);
        this.cancelled = b;
    }
}
