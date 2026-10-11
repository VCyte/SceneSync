package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneDefinition;
import io.github.vcyte.scenesync.api.SceneFrame;
import io.github.vcyte.scenesync.api.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.*;

/** Main-thread-only scene registry and server-authoritative actor movement. */
final class SceneManagerImpl implements SceneManager {
    private static final int MAX_CONFIG_SCENES = 64;
    private static final int MAX_API_SCENES = 128;
    private final SceneSyncPlugin plugin;
    private final SceneBridge bridge;
    private final Map<String, SceneDefinition> configured = new LinkedHashMap<>();
    private final Map<String, SceneDefinition> registered = new LinkedHashMap<>();
    private final Map<UUID, ActiveScene> active = new HashMap<>();
    private double viewDistance = 64.0;
    private boolean allowPlayerInput;
    private boolean internalTeleport;

    SceneManagerImpl(SceneSyncPlugin plugin, SceneBridge bridge) { this.plugin = plugin; this.bridge = bridge; }

    void loadConfiguration() {
        Map<String, SceneDefinition> candidate = new LinkedHashMap<>();
        try {
            plugin.reloadConfig();
            double configuredView = plugin.getConfig().getDouble("max-view-distance", 64.0);
            if (!Double.isFinite(configuredView) || configuredView < 8 || configuredView > 128)
                throw new IllegalArgumentException("max-view-distance must be in 8..128");
            viewDistance = configuredView;
            allowPlayerInput = plugin.getConfig().getBoolean("allow-player-input", false);
            var sections = plugin.getConfig().getConfigurationSection("scenes");
            if (sections != null) {
                if (sections.getKeys(false).size() > MAX_CONFIG_SCENES) throw new IllegalArgumentException("too many configured scenes");
                for (String id : sections.getKeys(false)) {
                    try {
                        var section = sections.getConfigurationSection(id);
                        if (section == null) throw new IllegalArgumentException("scene must be a mapping");
                        int duration = section.getInt("duration-ticks", -1);
                        var rawFrames = section.getMapList("frames");
                        List<SceneFrame> frames = new ArrayList<>();
                        if (rawFrames.size() > SceneDefinition.MAX_FRAMES) throw new IllegalArgumentException("too many frames");
                        for (Map<?, ?> raw : rawFrames) {
                            int tick = number(raw, "tick", -1).intValue();
                            double x = number(raw, "x", 0).doubleValue();
                            double y = number(raw, "y", 0).doubleValue();
                            double z = number(raw, "z", 0).doubleValue();
                            float yaw = number(raw, "yaw", 0).floatValue();
                            float pitch = number(raw, "pitch", 0).floatValue();
                            String animation = Objects.toString(raw.containsKey("animation") ? raw.get("animation") : "idle", "idle");
                            float cameraYaw = number(raw, "camera-yaw", 0).floatValue();
                            float cameraPitch = number(raw, "camera-pitch", 0).floatValue();
                            float cameraRoll = number(raw, "camera-roll", 0).floatValue();
                            float shake = number(raw, "camera-shake", 0).floatValue();
                            frames.add(new SceneFrame(tick, x, y, z, yaw, pitch, animation,
                                    cameraYaw, cameraPitch, cameraRoll, shake));
                        }
                        candidate.put(id, new SceneDefinition(id, duration, frames));
                    } catch (RuntimeException e) {
                        plugin.getLogger().warning("Ignored invalid scene '" + id + "': " + e.getMessage());
                    }
                }
            }
            configured.clear(); configured.putAll(candidate);
            plugin.getLogger().info("Loaded " + configured.size() + " configured SceneSync scene(s).");
        } catch (RuntimeException e) {
            plugin.getLogger().severe("SceneSync config reload failed; previous configured scenes remain active: " + e.getMessage());
        }
    }

    private static Number number(Map<?, ?> map, String key, Number fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n)) throw new IllegalArgumentException(key + " must be numeric");
        if (!Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(key + " must be finite");
        return n;
    }

    @Override public void register(SceneDefinition definition) {
        requireMainThread();
        Objects.requireNonNull(definition, "definition");
        if (!registered.containsKey(definition.id()) && registered.size() >= MAX_API_SCENES)
            throw new IllegalStateException("SceneSync API scene limit reached");
        registered.put(definition.id(), definition);
    }
    @Override public boolean unregister(String id) { requireMainThread(); return registered.remove(id) != null; }
    @Override public Set<String> sceneIds() {
        requireMainThread();
        Set<String> ids = new TreeSet<>(configured.keySet()); ids.addAll(registered.keySet());
        return Collections.unmodifiableSet(ids);
    }
    private SceneDefinition find(String id) { return registered.getOrDefault(id, configured.get(id)); }

    @Override public boolean play(Player actor, String sceneId) {
        requireMainThread(); Objects.requireNonNull(actor, "actor");
        SceneDefinition scene = find(sceneId);
        if (scene == null || !actor.isOnline() || actor.isDead() || actor.isInsideVehicle()
                || actor.getGameMode() == org.bukkit.GameMode.SPECTATOR || !bridge.isCompatible(actor)
                || active.size() >= 128) return false;
        stop(actor);
        ActiveScene run = new ActiveScene(UUID.randomUUID().toString().replace("-", ""), actor,
                scene, actor.getLocation().clone(), System.currentTimeMillis() + 250);
        active.put(actor.getUniqueId(), run);
        broadcastStart(run);
        return true;
    }

    private void broadcastStart(ActiveScene run) {
        for (Player viewer : run.actor.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(run.origin) <= viewDistance * viewDistance && bridge.isCompatible(viewer)) {
                bridge.sendPlay(viewer, run.actor, run.runId, run.startAtMillis, run.definition);
                run.viewers.add(viewer.getUniqueId());
            }
        }
    }

    void tick() {
        requireMainThread();
        for (ActiveScene run : new ArrayList<>(active.values())) {
            if (!run.actor.isOnline() || run.actor.isDead() || run.actor.isInsideVehicle()
                    || !run.actor.getWorld().equals(run.origin.getWorld())) { stopInternal(run); continue; }
            if (run.elapsedTicks >= run.definition.durationTicks()) { stopInternal(run); continue; }
            Location next = locationAt(run, run.elapsedTicks);
            if (next == null || !safe(next)) { stopInternal(run); continue; }
            internalTeleport = true;
            try {
                if (!run.actor.teleport(next, PlayerTeleportEvent.TeleportCause.PLUGIN)) { stopInternal(run); continue; }
            } finally { internalTeleport = false; }
            run.elapsedTicks++;
            if (run.elapsedTicks % 10 == 0) syncNewViewers(run);
        }
    }

    private void syncNewViewers(ActiveScene run) {
        for (Player viewer : run.actor.getWorld().getPlayers()) {
            if (run.viewers.contains(viewer.getUniqueId()) || !bridge.isCompatible(viewer)) continue;
            if (viewer.getLocation().distanceSquared(run.actor.getLocation()) <= viewDistance * viewDistance) {
                bridge.sendPlay(viewer, run.actor, run.runId, run.startAtMillis, run.definition);
                run.viewers.add(viewer.getUniqueId());
            }
        }
    }

    private Location locationAt(ActiveScene run, int tick) {
        List<SceneFrame> frames = run.definition.frames();
        SceneFrame a = frames.getFirst(); SceneFrame b = frames.getLast();
        for (int i = 1; i < frames.size(); i++) {
            if (tick <= frames.get(i).tick()) { a = frames.get(i - 1); b = frames.get(i); break; }
        }
        double span = Math.max(1, b.tick() - a.tick());
        double t = Math.max(0, Math.min(1, (tick - a.tick()) / span));
        double x = lerp(a.x(), b.x(), t), y = lerp(a.y(), b.y(), t), z = lerp(a.z(), b.z(), t);
        float yaw = (float)lerp(a.yaw(), b.yaw(), t), pitch = (float)lerp(a.pitch(), b.pitch(), t);
        double radians = Math.toRadians(run.origin.getYaw());
        double worldX = x * Math.cos(radians) - z * Math.sin(radians);
        double worldZ = x * Math.sin(radians) + z * Math.cos(radians);
        Location result = run.origin.clone().add(worldX, y, worldZ);
        double localDx = b.x() - a.x(), localDz = b.z() - a.z();
        if (Math.abs(localDx) + Math.abs(localDz) > 0.0001) {
            double worldDx = localDx * Math.cos(radians) - localDz * Math.sin(radians);
            double worldDz = localDx * Math.sin(radians) + localDz * Math.cos(radians);
            yaw = (float)Math.toDegrees(Math.atan2(-worldDx, worldDz));
        } else yaw += run.origin.getYaw();
        result.setYaw(yaw); result.setPitch(Math.max(-90, Math.min(90, run.origin.getPitch() + pitch)));
        return result;
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private boolean safe(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getWorldBorder().isInside(location)
                || location.getY() < world.getMinHeight() || location.getY() + 1.8 >= world.getMaxHeight()) return false;
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return false;
        return world.getBlockAt(location).isPassable()
                && world.getBlockAt(location.clone().add(0, 1.0, 0)).isPassable();
    }

    @Override public boolean stop(Player actor) {
        requireMainThread();
        ActiveScene run = active.get(actor.getUniqueId());
        if (run == null) return false;
        stopInternal(run); return true;
    }
    void stopAll() { for (ActiveScene run : new ArrayList<>(active.values())) stopInternal(run); }
    void removeViewer(Player viewer) {
        for (ActiveScene run : active.values()) run.viewers.remove(viewer.getUniqueId());
    }
    @Override public boolean isPlaying(Player actor) { return active.containsKey(actor.getUniqueId()); }
    boolean isInternalTeleport() { return internalTeleport; }
    boolean locksMovement(Player actor) { return !allowPlayerInput && active.containsKey(actor.getUniqueId()) && !internalTeleport; }

    private void stopInternal(ActiveScene run) {
        if (!active.remove(run.actor.getUniqueId(), run)) return;
        for (UUID id : run.viewers) {
            Player viewer = Bukkit.getPlayer(id);
            if (viewer != null && viewer.isOnline()) bridge.sendStop(viewer, run.actor.getEntityId(), run.runId);
        }
        run.viewers.clear();
    }
    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("SceneManager must be called on the Paper server thread");
    }

    private static final class ActiveScene {
        final String runId; final Player actor; final SceneDefinition definition; final Location origin;
        final long startAtMillis; final Set<UUID> viewers = new HashSet<>(); int elapsedTicks;
        ActiveScene(String runId, Player actor, SceneDefinition definition, Location origin, long startAtMillis) {
            this.runId = runId; this.actor = actor; this.definition = definition; this.origin = origin; this.startAtMillis = startAtMillis;
        }
    }
}
