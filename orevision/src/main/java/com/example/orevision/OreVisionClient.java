package com.example.orevision;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OreVisionClient implements ClientModInitializer {

    /** How far (in blocks) from you to scan in every direction. */
    private static final int RADIUS = 24;
    /** Ticks between scans (20 = once per second). */
    private static final int SCAN_INTERVAL = 20;
    /** Highlight opacity, 0..1. */
    private static final float ALPHA = 0.5f;

    private static final float[] NONE = new float[0];
    private static final Map<Block, float[]> COLOR_CACHE = new HashMap<>();

    private record Hit(BlockPos pos, float[] rgb) {}

    private static final List<Hit> HITS = new ArrayList<>();
    private static boolean enabled = false;
    private static int counter = 0;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orevision.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "category.orevision"));

        ClientTickEvents.END_CLIENT_TICK.register(OreVisionClient::onTick);
        WorldRenderEvents.LAST.register(OreVisionClient::render);
    }

    private static void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            counter = 0;
            if (!enabled) HITS.clear();
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("Ore Vision: " + (enabled ? "ON" : "OFF")), true);
            }
            if (enabled) scan(client);
        }

        if (!enabled || client.world == null || client.player == null) return;
        if (++counter % SCAN_INTERVAL == 0) scan(client);
    }

    private static void scan(MinecraftClient client) {
        World world = client.world;
        if (world == null || client.player == null) return;

        List<Hit> found = new ArrayList<>();
        BlockPos c = client.player.getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();

        int minY = Math.max(world.getBottomY(), c.getY() - RADIUS);
        int maxY = Math.min(world.getTopYInclusive(), c.getY() + RADIUS);

        for (int x = c.getX() - RADIUS; x <= c.getX() + RADIUS; x++) {
            for (int z = c.getZ() - RADIUS; z <= c.getZ() + RADIUS; z++) {
                for (int y = minY; y <= maxY; y++) {
                    m.set(x, y, z);
                    BlockState state = world.getBlockState(m);
                    if (state.isAir()) continue;
                    float[] rgb = colorFor(state.getBlock());
                    if (rgb != NONE) found.add(new Hit(m.toImmutable(), rgb));
                }
            }
        }

        HITS.clear();
        HITS.addAll(found);
    }

    private static float[] colorFor(Block block) {
        float[] cached = COLOR_CACHE.get(block);
        if (cached != null) return cached;

        String p = Registries.BLOCK.getId(block).getPath();
        float[] rgb = NONE;
        if (p.endsWith("_ore") || p.equals("ancient_debris")) {
            if (p.contains("diamond"))        rgb = new float[]{0.2f, 1.0f, 1.0f};
            else if (p.contains("emerald"))   rgb = new float[]{0.1f, 1.0f, 0.2f};
            else if (p.contains("gold"))      rgb = new float[]{1.0f, 0.85f, 0.1f};
            else if (p.contains("iron"))      rgb = new float[]{0.95f, 0.7f, 0.5f};
            else if (p.contains("coal"))      rgb = new float[]{0.6f, 0.6f, 0.6f};
            else if (p.contains("redstone"))  rgb = new float[]{1.0f, 0.1f, 0.1f};
            else if (p.contains("lapis"))     rgb = new float[]{0.2f, 0.3f, 1.0f};
            else if (p.contains("copper"))    rgb = new float[]{1.0f, 0.5f, 0.1f};
            else if (p.contains("quartz"))    rgb = new float[]{1.0f, 1.0f, 1.0f};
            else if (p.equals("ancient_debris")) rgb = new float[]{1.0f, 0.2f, 1.0f};
            else                              rgb = new float[]{0.8f, 0.4f, 1.0f}; // modded / other ores
        }
        COLOR_CACHE.put(block, rgb);
        return rgb;
    }

    private static void render(WorldRenderContext ctx) {
        if (!enabled || HITS.isEmpty()) return;
        MatrixStack stack = ctx.matrixStack();
        if (stack == null) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f mat = stack.peek().getPositionMatrix();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buf = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        final float e = 0.002f;
        for (Hit h : HITS) {
            float x0 = (float) (h.pos().getX() - cam.x) - e;
            float y0 = (float) (h.pos().getY() - cam.y) - e;
            float z0 = (float) (h.pos().getZ() - cam.z) - e;
            box(buf, mat, x0, y0, z0, x0 + 1 + 2 * e, y0 + 1 + 2 * e, z0 + 1 + 2 * e,
                    h.rgb()[0], h.rgb()[1], h.rgb()[2], ALPHA);
        }

        BuiltBuffer built = buf.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void box(BufferBuilder b, Matrix4f m,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float bl, float a) {
        // bottom
        v(b, m, x0, y0, z0, r, g, bl, a); v(b, m, x1, y0, z0, r, g, bl, a);
        v(b, m, x1, y0, z1, r, g, bl, a); v(b, m, x0, y0, z1, r, g, bl, a);
        // top
        v(b, m, x0, y1, z0, r, g, bl, a); v(b, m, x0, y1, z1, r, g, bl, a);
        v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x1, y1, z0, r, g, bl, a);
        // north
        v(b, m, x0, y0, z0, r, g, bl, a); v(b, m, x0, y1, z0, r, g, bl, a);
        v(b, m, x1, y1, z0, r, g, bl, a); v(b, m, x1, y0, z0, r, g, bl, a);
        // south
        v(b, m, x0, y0, z1, r, g, bl, a); v(b, m, x1, y0, z1, r, g, bl, a);
        v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x0, y1, z1, r, g, bl, a);
        // west
        v(b, m, x0, y0, z0, r, g, bl, a); v(b, m, x0, y0, z1, r, g, bl, a);
        v(b, m, x0, y1, z1, r, g, bl, a); v(b, m, x0, y1, z0, r, g, bl, a);
        // east
        v(b, m, x1, y0, z0, r, g, bl, a); v(b, m, x1, y1, z0, r, g, bl, a);
        v(b, m, x1, y1, z1, r, g, bl, a); v(b, m, x1, y0, z1, r, g, bl, a);
    }

    private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z,
                          float r, float g, float bl, float a) {
        b.vertex(m, x, y, z).color(r, g, bl, a);
    }
}
