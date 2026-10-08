package dev.elysium.ssdh;

import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockColorOverrideEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import sereneseasons.season.SeasonHooks;

/**
 * Paints seasonal snow onto Distant Horizons LODs.
 *
 * The problem: Serene Seasons adds and melts snow as real blocks, but only
 * in loaded chunks. A chunk nobody has visited since winter still has its
 * autumn blocks, and DH draws those stored blocks. Far LODs show the wrong
 * season until someone walks there.
 *
 * The fix is a fake. DH asks us for the color of every column it draws.
 * We ask Serene Seasons "is this spot cold enough to snow right now?".
 * Cold: blend the color toward white. Not cold: a snow layer gets the
 * grass color back. The world is not changed. Only how the LOD looks.
 *
 * Hacks, on purpose:
 *   - DH gives us a color per block, not per face. Cliff sides and tree
 *     trunks-adjacent faces go white along with the tops.
 *   - We can't see what is under a snow layer, so melted snow becomes the
 *     biome's grass color, even over stone or sand.
 *   - We can't see roofs. Anything DH draws gets snow.
 *   - Real snow in a chunk you visited gets the same treatment, so it
 *     looks the same. Only the stale chunks change.
 *
 * The classification is the same one Season Cache's coverage map uses
 * (biome + Serene Seasons temperature), so the result should match it.
 */
final class SeasonalSnow {

    // Close to DH's own average snow texture color. Eyeballed.
    private static final int SNOW_R = 0xF4, SNOW_G = 0xF8, SNOW_B = 0xF8;

    // How much of the snow color to mix in. Leaves keep some of their
    // shape and color, since real snow sits on top of them in clumps.
    private static final float SNOW_BLEND = 0.85f;
    private static final float LEAVES_BLEND = 0.55f;

    // /ssdh snow off turns it off. Not saved. Resets to on at launch.
    static volatile boolean enabled = true;

    static void register() {
        DhApiEventRegister.on(DhApiBlockColorOverrideEvent.class, new DhApiBlockColorOverrideEvent() {
            @Override
            public void onBlockColorOverridden(DhApiEventParam<EventParam> param) {
                if (!enabled || param.value == null) return;
                // Runs on DH worker threads, once per drawn column. Never throw.
                try {
                    apply(param.value);
                } catch (LinkageError | RuntimeException e) {
                    SeasonalLods.LOG.debug("Snow override skipped: {}", e.toString());
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void apply(DhApiBlockColorOverrideEvent.EventParam e) {
        var wrapper = e.getBlockStateWrapper();
        if (wrapper.isAir() || wrapper.isLiquid()) return;
        if (!(wrapper.getWrappedMcObject() instanceof BlockState state)) return;
        if (!(e.getBiomeWrapper().getWrappedMcObject() instanceof Holder<?> raw)) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        var biome = (Holder<Biome>) raw;
        var pos = new BlockPos(e.getBlockPosX(), e.getBlockPosY(), e.getBlockPosZ());
        boolean cold = SeasonHooks.coldEnoughToSnowSeasonal(level, biome, pos, level.getSeaLevel());

        if (cold) {
            if (isSnowy(state) || state.is(BlockTags.LOGS)) return;
            float t = state.is(BlockTags.LEAVES) ? LEAVES_BLEND : SNOW_BLEND;
            e.setColor(mix(e.getRed(), SNOW_R, t), mix(e.getGreen(), SNOW_G, t), mix(e.getBlue(), SNOW_B, t));
        } else if (state.is(Blocks.SNOW)) {
            // Snow layer in a spot that should have melted. Use seasonal grass.
            // BiomeColors.GRASS_COLOR_RESOLVER is Serene Seasons' version at runtime.
            int rgb = BiomeColors.GRASS_COLOR_RESOLVER.getColor(biome.value(), pos.getX(), pos.getZ());
            e.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
    }

    private static boolean isSnowy(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    private static int mix(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private SeasonalSnow() {}
}
