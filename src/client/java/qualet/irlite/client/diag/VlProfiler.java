package qualet.irlite.client.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.opengl.GL33C;

/**
 * Dev-only GPU profiler for the IRLite volumetric/light pipeline, enabled with
 * {@code -Dirlite.profileVl=true} (same boot-time pattern as irlite.profileShadows).
 *
 * <p>Level 1 — GL timer queries: every Iris fullscreen pass (deferred*, composite*,
 * begin*, prepare*, shadowcomp*) is bracketed with GL_TIME_ELAPSED via
 * CompositeRendererTimerMixin, and the mod-side shadow bake is bracketed around
 * FramePipeline.frame in GameRendererLightMixin (the bake runs strictly before the
 * Iris pass sequence, so the sibling brackets never nest). The bake bracket is
 * further PARTITIONED into sibling segments at the bakeInner seams by the
 * core-side {@code ShadowBakeProbe} (installed in IrliteClient when ENABLED):
 * bake-head (collect/prioritize + pre-loop setup) -> bake-spot -> bake-spot-filter
 * -> bake-point -> bake-point-filter -> bake-tail, with a derived "bake=SUM" cell
 * in the window line. The probe also feeds per-window WORK COUNTERS (full static
 * bakes per type+tier, overlay draws, static->live copies, faces baked/copied,
 * pyramid/EVSM flush sizes) printed as a second "[irlite] bake:" line. Results
 * are read back asynchronously from a query pool a few frames later — the
 * pipeline is never stalled — and aggregated into 1-second windows printed to
 * the log and mirrored onto a small HUD overlay.</p>
 *
 * <p>Level 2 — differential sweep ({@link VlSweep}): cycles VlGlobals UBO flag
 * configs frame-by-frame to attribute the deferred2 (VL march) cost to shadows /
 * noise / morph / Hi-Z / cluster-cull, printing a table to the log and the chat.</p>
 */
public final class VlProfiler
{
    public static final boolean ENABLED = Boolean.getBoolean("irlite.profileVl");

    /** Synthetic pass name for the mod-side shadow bake bracket opened at
     *  renderWorld HEAD. The core-side ShadowBakeProbe sections then switch it
     *  to bake-spot/-spot-filter/-point/-point-filter/-tail siblings, so this
     *  name ends up covering only the pre-spot-loop head (collect/prioritize,
     *  quality apply, beginBake) — the derived "bake" total in the window line
     *  is the old whole-bracket number. */
    public static final String PASS_BAKE = "bake-head";

    /** Every bake segment (the head bracket + the probe-switched siblings)
     *  carries this prefix; the window flush sums them into the derived total. */
    private static final String BAKE_PREFIX = "bake-";

    /** The Iris pass name of our VL march program (the sweep target). */
    public static final String PASS_VL = "deferred2";

    private static final int POOL_LIMIT = 512;
    private static final long WINDOW_NS = 1_000_000_000L;

    /** Frames after which an unavailable FIFO head is presumed poisoned (a query
     *  whose begin never took) and evicted so the drain can continue behind it. */
    private static final int STUCK_FRAMES = 120;

    /** Iris Program -> pass name, filled at pipeline construction (createProgram
     *  RETURN inject). Weak keys: programs are dropped wholesale on F3+R reload. */
    private static final Map<Object, String> PASS_NAMES = new WeakHashMap<>();

    /** GL query ids not currently in flight (allocated lazily, never deleted). */
    private static final ArrayDeque<Integer> freeQueries = new ArrayDeque<>();
    private static int allocatedQueries;

    /** In-flight queries, FIFO — timer queries complete in submission order, so
     *  the drain can stop at the first unavailable result. */
    private static final ArrayDeque<Pending> pending = new ArrayDeque<>();

    private static int activeQuery = -1;
    private static String activePass;
    private static long frameNo;
    private static int droppedSamples;
    private static int externalTimerSkips;

    private static final Map<String, Stat> window = new HashMap<>();
    private static long windowStart;
    private static volatile List<String> hudLines = List.of();

    /** Per-window work counters fed by the core-side ShadowBakeProbe (bakes per
     *  type+tier, faces copied/cleared, pyramid/EVSM flush sizes). Values are
     *  WINDOW SUMS; the flush line prints the window's frame count next to them
     *  so per-frame rates can be read off. Render thread only. */
    private static final Map<String, long[]> counters = new HashMap<>();
    /** Frames seen since the last window flush (normalizes the counters). */
    private static int windowFrames;

    private VlProfiler()
    {}

    private static final class Pending
    {
        final int query;
        final String pass;
        final long frame;

        Pending(int query, String pass, long frame)
        {
            this.query = query;
            this.pass = pass;
            this.frame = frame;
        }
    }

    private static final class Stat
    {
        long sumNs;
        long maxNs;
        int samples;

        void add(long ns)
        {
            this.sumNs += ns;
            this.maxNs = Math.max(this.maxNs, ns);
            this.samples += 1;
        }

        double avgMs()
        {
            return this.samples == 0 ? 0D : this.sumNs / 1_000_000D / this.samples;
        }
    }

    /* ---- wiring from mixins ------------------------------------------------ */

    /** CompositeRendererTimerMixin, at createProgram RETURN: remembers which Iris
     *  Program object is which pass ("deferred2", "composite1", ...). */
    public static void registerPassName(Object program, String name)
    {
        if (!ENABLED || program == null || name == null)
        {
            return;
        }
        PASS_NAMES.put(program, name);
    }

    public static String irisPassName(Object program)
    {
        String name = PASS_NAMES.get(program);
        return name == null ? "unnamed" : name;
    }

    /**
     * Once per frame, at renderWorld HEAD, before any bracket of this frame is
     * opened: drains finished queries, advances the sweep state machine and
     * flushes the 1-second stats window. All GL work happens on the render
     * thread with the context current.
     */
    public static void frameTick()
    {
        if (!ENABLED)
        {
            return;
        }
        frameNo += 1;
        drainCompleted();
        VlSweep.tick(frameNo);
        maybeFlushWindow();
        // AFTER the flush: this tick's bake counters land after the flush too,
        // so the window's tick count and its bake-frame span coincide exactly
        // (incrementing before the flush over-counted the first window by one).
        windowFrames += 1;
    }

    /**
     * Core-side ShadowBakeProbe.section: close the current bake bracket and
     * open the named sibling. Fired at the bakeInner seams while the mixin's
     * bake-head bracket is active, so the whole bake stays covered by
     * consecutive sibling brackets (GL_TIME_ELAPSED cannot nest). If the head
     * bracket never opened this frame (F3 timer active, pool exhausted), both
     * halves degrade to the same no-op/skip and the segment samples drop
     * consistently.
     */
    public static void switchPass(String name)
    {
        if (!ENABLED)
        {
            return;
        }
        endPass();
        beginPass(name);
    }

    /** Core-side ShadowBakeProbe.counter: accumulate into the 1-second window. */
    public static void counter(String key, int amount)
    {
        if (!ENABLED)
        {
            return;
        }
        counters.computeIfAbsent(key, k -> new long[1])[0] += amount;
    }

    /** Opens a GL_TIME_ELAPSED bracket. No-ops (and drops the sample) if a
     *  bracket is already active — timer queries cannot nest. */
    public static void beginPass(String name)
    {
        if (!ENABLED)
        {
            return;
        }
        if (activeQuery != -1)
        {
            droppedSamples += 1;
            return;
        }
        // Vanilla GlTimer (F3 GPU% / debug recorder) owns GL_TIME_ELAPSED for the
        // whole frame — a nested begin would GL_INVALID_OPERATION, and ending it
        // would kill the vanilla query and poison our FIFO. Skip those frames.
        if (GL33C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL33C.GL_CURRENT_QUERY) != 0)
        {
            externalTimerSkips += 1;
            return;
        }
        int query = allocQuery();
        if (query == -1)
        {
            droppedSamples += 1;
            return;
        }
        GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, query);
        if (GL33C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL33C.GL_CURRENT_QUERY) != query)
        {
            // The begin didn't take (an external timer raced in) — recycle the id
            // and drop the sample instead of tracking a query that never began.
            freeQueries.addLast(query);
            droppedSamples += 1;
            return;
        }
        activeQuery = query;
        activePass = name;
    }

    /** Closes the currently active bracket, if any. */
    public static void endPass()
    {
        if (!ENABLED || activeQuery == -1)
        {
            return;
        }
        GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
        pending.addLast(new Pending(activeQuery, activePass, frameNo));
        activeQuery = -1;
        activePass = null;
    }

    /**
     * LightCollector.collect, right after the user-config VlGlobals push: while
     * the sweep is measuring a non-baseline config it re-issues the push with
     * that config's flag/morph override — last write wins before the upload.
     * The next frame's ordinary push restores the user values automatically.
     */
    public static void overrideVlGlobals()
    {
        if (!ENABLED)
        {
            return;
        }
        VlSweep.overrideVlGlobals();
    }

    /* ---- query pool -------------------------------------------------------- */

    private static int allocQuery()
    {
        Integer free = freeQueries.pollFirst();
        if (free != null)
        {
            return free;
        }
        if (allocatedQueries >= POOL_LIMIT)
        {
            return -1;
        }
        allocatedQueries += 1;
        return GL33C.glGenQueries();
    }

    private static void drainCompleted()
    {
        Pending head;
        while ((head = pending.peekFirst()) != null)
        {
            if (GL33C.glGetQueryObjecti(head.query, GL33C.GL_QUERY_RESULT_AVAILABLE) == 0)
            {
                // Self-heal: a head whose begin silently failed would report
                // "unavailable" forever and dam the whole FIFO — evict it and
                // keep draining the valid results queued behind it.
                if (frameNo - head.frame > STUCK_FRAMES)
                {
                    pending.pollFirst();
                    GL33C.glDeleteQueries(head.query);
                    allocatedQueries -= 1;
                    System.out.println("[irlite] gpu: evicted stuck timer query for pass " + head.pass);
                    continue;
                }
                break;
            }
            long ns = GL33C.glGetQueryObjecti64(head.query, GL33C.GL_QUERY_RESULT);
            pending.pollFirst();
            freeQueries.addLast(head.query);
            record(head.pass, head.frame, ns);
        }
    }

    private static void record(String pass, long frame, long ns)
    {
        window.computeIfAbsent(pass, key -> new Stat()).add(ns);
        if (PASS_VL.equals(pass))
        {
            VlSweep.addSample(frame, ns);
        }
    }

    /* ---- 1-second window + HUD -------------------------------------------- */

    private static void maybeFlushWindow()
    {
        long now = System.nanoTime();
        if (windowStart == 0L)
        {
            windowStart = now;
            return;
        }
        if (now - windowStart < WINDOW_NS)
        {
            return;
        }

        List<Map.Entry<String, Stat>> entries = new ArrayList<>(window.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().sumNs, a.getValue().sumNs));

        // Derived whole-bake total: the probe partitions the old single
        // shadow-bake bracket into bake-* siblings, so their sum restores the
        // number every earlier measurement ("shadow-bake 3.4-4.0 ms") reported.
        long bakeSumNs = 0L;
        int bakeSegments = 0;
        int bakeSamples = 0;
        for (Map.Entry<String, Stat> e : entries)
        {
            if (e.getKey().startsWith(BAKE_PREFIX))
            {
                bakeSumNs += e.getValue().sumNs;
                bakeSamples = Math.max(bakeSamples, e.getValue().samples);
                bakeSegments += 1;
            }
        }

        StringBuilder line = new StringBuilder("[irlite] gpu:");
        List<String> hud = new ArrayList<>();
        int shown = 0;
        if (bakeSegments >= 2 && bakeSamples > 0)
        {
            String cell = String.format(Locale.ROOT, "bake %.2f ms", bakeSumNs / 1_000_000D / bakeSamples);
            line.append(' ').append(cell);
            hud.add(cell);
            shown += 1;
        }
        for (Map.Entry<String, Stat> e : entries)
        {
            Stat s = e.getValue();
            String cell = String.format(Locale.ROOT, "%s %.2f/%.2f ms",
                e.getKey(), s.avgMs(), s.maxNs / 1_000_000D);
            if (shown < 16)
            {
                line.append(shown == 0 ? " " : " | ").append(cell);
            }
            if (hud.size() < 14)
            {
                hud.add(cell);
            }
            shown += 1;
        }
        if (shown == 0)
        {
            line.append(" (no samples — shaders off or no passes yet)");
        }
        if (droppedSamples > 0)
        {
            line.append(" | dropped ").append(droppedSamples);
            droppedSamples = 0;
        }
        if (externalTimerSkips > 0)
        {
            line.append(" | skipped ").append(externalTimerSkips).append(" (F3 timer active)");
            externalTimerSkips = 0;
        }
        System.out.println(line);

        // Second line: the bake work counters (window sums + the frame count
        // to read per-frame rates off), mirrored onto the HUD in packed rows.
        if (!counters.isEmpty())
        {
            List<String> keys = new ArrayList<>(counters.keySet());
            Collections.sort(keys);
            StringBuilder bakeLine = new StringBuilder("[irlite] bake:");
            List<String> cells = new ArrayList<>(keys.size());
            for (String key : keys)
            {
                String cell = key + " " + counters.get(key)[0];
                bakeLine.append(cells.isEmpty() ? " " : " | ").append(cell);
                cells.add(cell);
            }
            bakeLine.append(" | ").append(windowFrames).append(" frames");
            System.out.println(bakeLine);

            for (int i = 0; i < cells.size(); i += 4)
            {
                hud.add(String.join(" | ", cells.subList(i, Math.min(i + 4, cells.size()))));
            }
            hud.add(windowFrames + " frames");
            counters.clear();
        }
        windowFrames = 0;

        hudLines = hud;
        window.clear();
        windowStart = now;
    }

    /** HudRenderCallback (registered in IrliteClient only when ENABLED). */
    public static void renderHud(DrawContext ctx)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null)
        {
            return;
        }
        int y = 4;
        List<String> lines = hudLines;
        String sweepStatus = VlSweep.statusLine();
        if (sweepStatus != null)
        {
            ctx.drawText(mc.textRenderer, sweepStatus, 4, y, 0xFFFFD080, true);
            y += 10;
        }
        for (String lineText : lines)
        {
            ctx.drawText(mc.textRenderer, lineText, 4, y, 0xFFE0E0E0, true);
            y += 10;
        }
    }

    /* ---- output helpers (also used by VlSweep) ----------------------------- */

    static void log(String message)
    {
        System.out.println("[irlite] vl-sweep: " + message);
    }

    static void chat(String message)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.world != null && mc.inGameHud != null)
        {
            mc.inGameHud.getChatHud().addMessage(Text.literal(message));
        }
    }
}
