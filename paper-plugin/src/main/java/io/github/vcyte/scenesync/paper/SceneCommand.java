package io.github.vcyte.scenesync.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SceneCommand implements CommandExecutor, TabCompleter {
    private final SceneSyncPlugin plugin;
    private final SceneManagerImpl manager;
    SceneCommand(SceneSyncPlugin plugin, SceneManagerImpl manager) { this.plugin = plugin; this.manager = manager; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("/scenesync list | status [player] | play <scene> [player] | stop [player] | reload");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            sender.sendMessage("Scenes: " + (manager.sceneIds().isEmpty() ? "(none)" : String.join(", ", manager.sceneIds())));
            return true;
        }
        if (sub.equals("status")) {
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target == null) { sender.sendMessage("Player not found; console must specify one."); return true; }
            sender.sendMessage(target.getName() + ": " + (manager.isPlaying(target) ? "playing a scene" : "idle")
                    + ", client mod=" + (plugin.bridge().isCompatible(target) ? "connected" : "not detected"));
            return true;
        }
        if (!sender.hasPermission("scenesync.admin")) { sender.sendMessage("You do not have permission."); return true; }
        if (sub.equals("play")) {
            if (args.length < 2 || args.length > 3) { sender.sendMessage("Usage: /scenesync play <scene> [player]"); return true; }
            Player actor = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
            if (actor == null) { sender.sendMessage("Player not found; console must specify a player."); return true; }
            if (!plugin.bridge().isCompatible(actor)) { sender.sendMessage("Target player needs the SceneSync client mod and a completed handshake."); return true; }
            if (!manager.play(actor, args[1])) { sender.sendMessage("Could not start scene. Check the scene ID, actor state, and configured limits."); return true; }
            sender.sendMessage("Started scene '" + args[1] + "' for " + actor.getName() + ".");
            return true;
        }
        if (sub.equals("stop")) {
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
            if (target == null) { sender.sendMessage("Player not found; console must specify one."); return true; }
            sender.sendMessage(manager.stop(target) ? "Stopped scene for " + target.getName() + "." : "That player is not in a scene.");
            return true;
        }
        if (sub.equals("reload")) {
            manager.loadConfiguration();
            sender.sendMessage("SceneSync configuration reloaded. Invalid scenes were skipped; prior config scenes were retained if the file could not be read.");
            return true;
        }
        sender.sendMessage("Unknown subcommand. Use /scenesync help.");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(List.of("help", "list", "status", "play", "stop", "reload"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("play")) return prefix(new ArrayList<>(manager.sceneIds()), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("play") && sender.hasPermission("scenesync.admin"))
            return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) return prefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        return List.of();
    }
    private static List<String> prefix(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
