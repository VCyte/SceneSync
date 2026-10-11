package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneDefinition;
import io.github.vcyte.scenesync.api.CameraMode;
import io.github.vcyte.scenesync.api.DamagePolicy;
import io.github.vcyte.scenesync.api.SceneFrame;
import io.github.vcyte.scenesync.api.SceneCameraSettings;
import io.github.vcyte.scenesync.api.BonePose;
import io.github.vcyte.scenesync.api.ModelPose;
import io.github.vcyte.scenesync.api.SceneManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/** Main-thread-only scene registry, path validation, and client-driven actor playback. */
final class SceneManagerImpl implements SceneManager {
    private static final int MAX_CONFIG_SCENES = 64;
    private static final int MAX_API_SCENES = 128;
    private final SceneSyncPlugin plugin;
    private final SceneBridge bridge;
    private final Map<String, SceneDefinition> configured = new LinkedHashMap<>();
    private final Map<String, SceneDefinition> registered = new LinkedHashMap<>();
    private final Map<UUID, ActiveScene> active = new HashMap<>();
    private double viewDistance = 64.0;

    SceneManagerImpl(SceneSyncPlugin plugin, SceneBridge bridge) { this.plugin = plugin; this.bridge = bridge; }

    void loadConfiguration() {
        Map<String, SceneDefinition> candidate = new LinkedHashMap<>();
        try {
            plugin.reloadConfig();
            double configuredView = plugin.getConfig().getDouble("max-view-distance", 64.0);
            if (!Double.isFinite(configuredView) || configuredView < 8 || configuredView > 128)
                throw new IllegalArgumentException("max-view-distance must be in 8..128");
            viewDistance = configuredView;
            var sections = plugin.getConfig().getConfigurationSection("scenes");
            if (sections != null) {
                if (sections.getKeys(false).size() > MAX_CONFIG_SCENES) throw new IllegalArgumentException("too many configured scenes");
                for (String id : sections.getKeys(false)) {
                    try {
                        var section = sections.getConfigurationSection(id);
                        if (section == null) throw new IllegalArgumentException("scene must be a mapping");
                        int duration = section.getInt("duration-ticks", -1);
                        String rawPolicy = section.getString("damage-policy", "CANCEL_SCENE");
                        DamagePolicy damagePolicy = DamagePolicy.valueOf(rawPolicy.trim().toUpperCase(Locale.ROOT));
                        SceneCameraSettings cameraSettings = SceneCameraSettings.DEFAULT;
                        var cameraSection = section.getConfigurationSection("camera");
                        if (cameraSection != null) {
                            String rawMode = cameraSection.getString("mode", "FREE_LOOK");
                            CameraMode mode = CameraMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
                            double maxYaw = cameraSection.getDouble("max-yaw-degrees", 80.0);
                            double maxPitch = cameraSection.getDouble("max-pitch-degrees", 45.0);
                            cameraSettings = new SceneCameraSettings(mode, (float)maxYaw, (float)maxPitch);
                        }
                        Map<String, DamagePolicy> animationPolicies = new LinkedHashMap<>();
                        var animationPolicySection = section.getConfigurationSection("animation-damage-policies");
                        if (animationPolicySection != null) {
                            if (animationPolicySection.getKeys(false).size() > SceneDefinition.MAX_FRAMES)
                                throw new IllegalArgumentException("too many animation damage policies");
                            for (String animation : animationPolicySection.getKeys(false)) {
                                String policyName = animationPolicySection.getString(animation);
                                if (policyName == null) throw new IllegalArgumentException("animation damage policy must be text");
                                animationPolicies.put(animation,
                                        DamagePolicy.valueOf(policyName.trim().toUpperCase(Locale.ROOT)));
                            }
                        }
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
                            float cameraFov = number(raw, "camera-fov", 0).floatValue();
                            ModelPose pose = parsePose(raw.get("pose"));
                            frames.add(new SceneFrame(tick, x, y, z, yaw, pitch, animation,
                                    cameraYaw, cameraPitch, cameraRoll, shake, cameraFov, pose));
                        }
                        candidate.put(id, new SceneDefinition(id, duration, frames, damagePolicy, animationPolicies, cameraSettings));
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

    private static ModelPose parsePose(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?> parts) || parts.size() > 6)
            throw new IllegalArgumentException("pose must be a mapping with at most six humanoid parts");
        Set<String> allowed = Set.of("head", "body", "left-arm", "right-arm", "left-leg", "right-leg");
        for (Object key : parts.keySet()) if (!(key instanceof String name) || !allowed.contains(name))
            throw new IllegalArgumentException("unknown pose part: " + key);
        return new ModelPose(parseBone(parts, "head"), parseBone(parts, "body"),
                parseBone(parts, "left-arm"), parseBone(parts, "right-arm"),
                parseBone(parts, "left-leg"), parseBone(parts, "right-leg"));
    }

    private static BonePose parseBone(Map<?, ?> parts, String name) {
        Object raw = parts.get(name);
        if (raw == null) return BonePose.ZERO;
        if (!(raw instanceof Map<?, ?> bone) || bone.size() > 6)
            throw new IllegalArgumentException(name + " pose must be a transform mapping");
        Set<String> allowed = Set.of("x", "y", "z", "pitch", "yaw", "roll");
        for (Object key : bone.keySet()) if (!(key instanceof String field) || !allowed.contains(field))
            throw new IllegalArgumentException("unknown " + name + " pose field: " + key);
        return new BonePose(number(bone, "x", 0).floatValue(), number(bone, "y", 0).floatValue(),
                number(bone, "z", 0).floatValue(), number(bone, "pitch", 0).floatValue(),
                number(bone, "yaw", 0).floatValue(), number(bone, "roll", 0).floatValue());
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
                scene, actor.getLocation().clone(), System.currentTimeMillis() + 500);
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
            long now = System.currentTimeMillis();
            if (now < run.startAtMillis) continue;
            int timelineTick = (int)((now - run.startAtMillis) / 50L);
            if (timelineTick > run.definition.durationTicks()) { stopInternal(run); continue; }
            boolean validPath = true;
            for (int tick = Math.max(0, run.elapsedTicks + 1); tick <= timelineTick; tick++) {
                if (tick == 0) {
                    Location origin = locationAt(run, 0.0);
                    if (origin == null || !sweptSafe(origin, origin)) { validPath = false; break; }
                    continue;
                }
                for (int sub = 1; sub <= 4; sub++) {
                    double fromTick = tick - 1 + (sub - 1) / 4.0;
                    double toTick = tick - 1 + sub / 4.0;
                    Location previous = locationAt(run, fromTick);
                    Location expected = locationAt(run, toTick);
                    if (expected == null || previous == null || !sweptSafe(previous, expected)) {
                        validPath = false;
                        break;
                    }
                }
                if (!validPath) break;
            }
            if (!validPath) { stopInternal(run); continue; }
            run.elapsedTicks = timelineTick;
            if (run.elapsedTicks % 10 == 0) syncNewViewers(run);
        }
    }

    boolean acceptClientMovement(Player actor, Location from, Location to) {
        ActiveScene run = active.get(actor.getUniqueId());
        if (run == null || to == null || !Bukkit.isPrimaryThread()) return true;
        if (to.getWorld() == null || !to.getWorld().equals(run.origin.getWorld())) {
            stopInternal(run);
            return true;
        }
        if (from != null && from.getWorld() != null && from.getWorld().equals(to.getWorld())
                && from.distanceSquared(to) < 1.0e-8) return true;
        long now = System.currentTimeMillis();
        if (now < run.startAtMillis) return true;
        double currentTick = (now - run.startAtMillis) / 50.0;
        double firstTick = Math.max(0.0, currentTick - 6.0);
        double lastTick = Math.min(run.definition.durationTicks(), currentTick + 1.0);
        if (ScenePathMath.matches(run.definition.frames(), firstTick, lastTick,
                run.origin.getX(), run.origin.getY(), run.origin.getZ(), run.origin.getYaw(),
                to.getX(), to.getY(), to.getZ(), 0.75)) {
            if (from != null && !sweptSafe(from, to)) {
                stopInternal(run);
                return false;
            }
            return true;
        }
        // A collision, competing movement plugin, or client desync must not trigger a correction loop.
        stopInternal(run);
        return true;
    }

    void handleDamage(Player actor, org.bukkit.event.entity.EntityDamageEvent event) {
        requireMainThread();
        ActiveScene run = active.get(actor.getUniqueId());
        if (run == null || event.getFinalDamage() <= 0.0) return;
        long now = System.currentTimeMillis();
        int sceneTick = now <= run.startAtMillis ? 0
                : (int)Math.min(run.definition.durationTicks(), (now - run.startAtMillis) / 50L);
        DamagePolicy policy = run.definition.damagePolicyAtTick(sceneTick);
        if (policy == DamagePolicy.IGNORE_DAMAGE) {
            event.setCancelled(true);
        } else {
            // Clear the scene track before the normal damage/hurt packets are sent.
            stopInternal(run);
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

    private Location locationAt(ActiveScene run, double tick) {
        List<SceneFrame> frames = run.definition.frames();
        SceneFrame a = frames.getFirst(); SceneFrame b = frames.getLast();
        for (int i = 1; i < frames.size(); i++) {
            if (tick <= frames.get(i).tick()) { a = frames.get(i - 1); b = frames.get(i); break; }
        }
        double span = Math.max(1, b.tick() - a.tick());
        double t = Math.max(0, Math.min(1, (tick - a.tick()) / span));
        ScenePathMath.Point point = ScenePathMath.sample(frames, tick);
        double x = point.x(), y = point.y(), z = point.z();
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
                && world.getBlockAt(location.clone().add(0, 1.0, 0)).isPassable()
                && world.getBlockAt(location.clone().add(0, 1.7, 0)).isPassable();
    }

    /** Sample fast client-driven movement at <=0.25-block intervals so a dash cannot skip a wall. */
    private boolean sweptSafe(Location from, Location to) {
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return false;
        double distance = from.distance(to);
        if (!Double.isFinite(distance) || distance > 16.0) return false;
        int steps = Math.max(1, (int)Math.ceil(distance / 0.25));
        if (steps > 64) return false;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double)steps;
            Location sample = from.clone().add((to.getX() - from.getX()) * t,
                    (to.getY() - from.getY()) * t, (to.getZ() - from.getZ()) * t);
            if (!safe(sample)) return false;
            for (double dx : new double[]{-0.3, 0.3}) {
                for (double dz : new double[]{-0.3, 0.3}) {
                    if (!safe(sample.clone().add(dx, 0, dz))) return false;
                }
            }
        }
        return true;
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
        final long startAtMillis; final Set<UUID> viewers = new HashSet<>(); int elapsedTicks = -1;
        ActiveScene(String runId, Player actor, SceneDefinition definition, Location origin, long startAtMillis) {
            this.runId = runId; this.actor = actor; this.definition = definition; this.origin = origin; this.startAtMillis = startAtMillis;
        }
    }
}
