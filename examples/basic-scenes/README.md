# Basic SceneSync API Example

A Paper plugin can obtain the service and register a bounded timeline without editing SceneSync's own config:

```java
var service = getServer().getServicesManager().load(
    io.github.vcyte.scenesync.api.SceneManager.class);
if (service == null) {
    getLogger().warning("SceneSync is not installed");
    return;
}
var scene = new io.github.vcyte.scenesync.api.SceneDefinition("example.intro", 40,
    java.util.List.of(
        new io.github.vcyte.scenesync.api.SceneFrame(0, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0),
        new io.github.vcyte.scenesync.api.SceneFrame(20, 0, 0, 2, 0, 0, "walk", 0, 0, 0, 0),
        new io.github.vcyte.scenesync.api.SceneFrame(40, 0, 0, 2, 0, 0, "wave", 0, 0, 0, 0)
    ));
service.register(scene);
service.play(player, scene.id());
```

Add the API and Paper API as `compileOnly` dependencies. See `docs/PLUGIN-DEVELOPMENT.md` for lifecycle and safety details.
