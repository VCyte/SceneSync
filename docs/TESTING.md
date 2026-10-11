# Testing

Run the complete project test/build suite with JDK 25:

```bash
./gradlew clean test build
```

The API tests cover frame bounds, identifier validation, defensive copies, frame ordering, and duration constraints. Fabric timeline tests cover interpolated world positions, actor-local forward rotation, and facing along travel. The Paper and Fabric modules compile against Minecraft/Paper 26.2 and run their available unit tests. The CI workflow builds all modules and verifies the produced plugin and mod artifacts.

A build does not replace an in-game integration test. Before production deployment, test at least: Paper 26.2 with Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.2; one actor and multiple viewers; a viewer entering range mid-scene; actor-local forward/right path alignment at several starting yaw angles; actor-facing travel direction; actor-camera path and look controls; render continuity at 30/60/144 FPS; worn armor and held-item tracking; disconnect, world transfer, reload, and plugin disable during playback; collision and world-border boundaries; no-client and mismatched-protocol clients; and a representative animation/camera modpack. CI only verifies compilation and packaging and does not claim these live visual checks have passed.
