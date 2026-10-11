package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;

public final class SceneSyncPlugin extends JavaPlugin implements Listener {
    private SceneBridge bridge;
    private SceneManagerImpl manager;

    @Override public void onEnable() {
        ensureInitialConfig();
        bridge = new SceneBridge(this);
        bridge.register();
        manager = new SceneManagerImpl(this, bridge);
        manager.loadConfiguration();
        Bukkit.getServicesManager().register(SceneManager.class, manager, this, ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand command = getCommand("scenesync");
        if (command == null) throw new IllegalStateException("scenesync command missing from plugin.yml");
        SceneCommand executor = new SceneCommand(this, manager);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 1L, 1L);
        getLogger().info("SceneSync 0.1.4 enabled (protocol 3).");
    }

    /** Copy the bundled examples only on the first installation, never on later restarts. */
    private void ensureInitialConfig() {
        File marker = new File(getDataFolder(), ".scenesync-config-initialized");
        if (marker.exists()) return;
        saveDefaultConfig();
        try {
            if (!marker.createNewFile()) getLogger().warning("Could not create config initialization marker");
        } catch (IOException exception) {
            getLogger().warning("Could not persist config initialization marker: " + exception.getMessage());
        }
    }

    @Override public void onDisable() {
        if (manager != null) manager.stopAll();
        Bukkit.getServicesManager().unregisterAll(this);
        if (bridge != null) bridge.unregister();
    }

    SceneBridge bridge() { return bridge; }
    SceneManagerImpl manager() { return manager; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMovement(PlayerMoveEvent event) {
        if (manager == null || event.getTo() == null) return;
        if (event instanceof PlayerTeleportEvent) manager.stop(event.getPlayer());
        if (!manager.acceptClientMovement(event.getPlayer(), event.getFrom(), event.getTo())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExternalTeleport(PlayerTeleportEvent event) {
        if (manager != null) manager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (manager != null) manager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (manager == null || !(event.getEntity() instanceof Player player)) return;
        manager.handleDamage(player, event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager != null) {
            manager.stop(player);
            manager.removeViewer(player);
        }
        if (bridge != null) bridge.remove(player);
    }
}
