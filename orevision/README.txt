ORE VISION (Fabric, Minecraft 1.21.4)
Press X in-game to toggle ore highlights through walls (rebind in Controls).

BUILD THE JAR
1. Install JDK 21 (e.g. Temurin) and Gradle 8.12+  (or use the GitHub Actions
   workflow in .github/workflows: push this folder to a GitHub repo, open the
   Actions tab, run "build", download the artifact).
2. In this folder run:   gradle build
3. Take build/libs/orevision-1.0.0.jar (NOT the -sources jar) and drop it in
   .minecraft/mods together with Fabric API 0.119.2+1.21.4.

TWEAK: RADIUS, SCAN_INTERVAL and ALPHA are constants at the top of OreVisionClient.java.
