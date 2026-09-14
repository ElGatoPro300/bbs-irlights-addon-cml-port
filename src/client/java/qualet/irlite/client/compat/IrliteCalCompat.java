package qualet.irlite.client.compat;

import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.cookie.CookieArray;

import org.qualet.irl.light.CookieArrayBase;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import elgatopro300.cal_lights.light.LightConfig;

/**
 * Compatibility bridge between IRLite (BBS Addon) and IRL CAL Editor (irlcal_editor).
 * Enables simultaneous rendering of BBS lights and CAL lights in the same frame pipeline.
 */
public final class IrliteCalCompat
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite-cal-compat");
    public static final String CAL_MOD_ID = "irlcal_editor";

    private static boolean calPresent;
    private static MethodHandle calCollectHandle;
    private static MethodHandle calResetShadowRampHandle;
    private static boolean cookiesBridged;

    // Cookie catalog & custom path mappings for CAL gobos
    private static final String[] BUILTINS = {"Window", "Blinds", "Circle", "Noise"};
    private static final Map<String, Path> nameToPath = new HashMap<>();
    private static final List<String> catalog = new ArrayList<>();

    static
    {
        calPresent = FabricLoader.getInstance().isModLoaded(CAL_MOD_ID);
        if (calPresent)
        {
            initReflection();
        }
    }

    private static void initReflection()
    {
        try
        {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> driverClass = Class.forName("elgatopro300.cal_lights.light.LightDriver");
            calCollectHandle = lookup.findStatic(driverClass, "collect",
                MethodType.methodType(void.class, ClientWorld.class, Vec3d.class, float.class));
            calResetShadowRampHandle = lookup.findStatic(driverClass, "resetAutoShadowRamp",
                MethodType.methodType(void.class));
            LOG.info("IRLite <-> CAL Editor compatibility hooks linked successfully.");
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to bind CAL LightDriver hooks", t);
        }
    }

    public static boolean isCalPresent()
    {
        return calPresent;
    }

    /**
     * Collects CAL lights during the single unified FramePipeline pass.
     */
    public static void collectCalLights(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        if (!calPresent || calCollectHandle == null)
        {
            return;
        }
        try
        {
            calCollectHandle.invokeExact(world, cameraPos, tickDelta);
        }
        catch (Throwable t)
        {
            LOG.error("Error executing CAL LightDriver.collect", t);
        }
    }

    /**
     * Resets CAL's auto shadow ramp when shaders are disabled/reset.
     */
    public static void resetCalAutoShadowRamp()
    {
        if (!calPresent || calResetShadowRampHandle == null)
        {
            return;
        }
        try
        {
            calResetShadowRampHandle.invokeExact();
        }
        catch (Throwable t)
        {
            LOG.error("Error executing CAL LightDriver.resetAutoShadowRamp", t);
        }
    }

    /**
     * Checks if CAL lights have surface or volumetric shadows enabled.
     */
    public static boolean isCalShadowsEnabled()
    {
        if (!calPresent)
        {
            return false;
        }
        try
        {
            Class<?> configClass = Class.forName("elgatopro300.cal_lights.light.LightConfig");
            boolean live = configClass.getField("shadowsLive").getBoolean(null);
            boolean vl = configClass.getField("vlShadows").getBoolean(null);
            return live || vl;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Synchronizes configuration settings bidirectionally between BBS and CAL Editor.
     * Ensures any changes in BBS settings reflect in CAL and vice-versa.
     */
    public static void syncConfigs()
    {
        if (!calPresent)
        {
            return;
        }
        try
        {
            CalSyncBridge.sync();
        }
        catch (Throwable t)
        {
            LOG.error("Error synchronizing configs with CAL Editor", t);
        }
    }

    /**
     * Called reflectively by CALLightsClient to bridge CookieArray lookups
     * so CAL spotlights share the active irl_cookieArray texture with BBS.
     */
    public static void ensureCookiesReady()
    {
        if (cookiesBridged)
        {
            return;
        }
        cookiesBridged = true;

        try
        {
            Class<?> calCookieClass = Class.forName("elgatopro300.cal_lights.light.cookie.CookieArray");
            Class<?> hostInterface = Class.forName("elgatopro300.cal_lights.light.cookie.CookieArray$Host");

            Object hostProxy = Proxy.newProxyInstance(
                hostInterface.getClassLoader(),
                new Class<?>[]{hostInterface},
                new CalCookieHostInvocationHandler()
            );

            Method installHostMethod = calCookieClass.getMethod("installHost", hostInterface);
            installHostMethod.invoke(null, hostProxy);
            LOG.info("CAL CookieArray host installed into IRLite shared CookieArray.");
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to install CAL CookieArray host bridge", t);
        }
    }

    private static class CalCookieHostInvocationHandler implements InvocationHandler
    {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
        {
            String name = method.getName();
            switch (name)
            {
                case "init":
                    initCookies();
                    return null;
                case "resolveName":
                    return resolveCookie((String) args[0]);
                case "textureId":
                    return CookieArray.getGlTextureId();
                case "catalog":
                    return getCatalog();
                case "reload":
                    reloadCookies();
                    return null;
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "IrliteCalCookieHostBridge";
                default:
                    return null;
            }
        }
    }

    private static synchronized void initCookies()
    {
        scanCatalog();
    }

    private static synchronized List<String> getCatalog()
    {
        if (catalog.isEmpty())
        {
            scanCatalog();
        }
        return new ArrayList<>(catalog);
    }

    private static synchronized void reloadCookies()
    {
        nameToPath.clear();
        catalog.clear();
        scanCatalog();
    }

    private static synchronized int resolveCookie(String name)
    {
        if (name == null || name.isEmpty() || "None".equalsIgnoreCase(name))
        {
            return -1;
        }

        int existing = CookieArray.getLayer(name);
        if (existing >= 0)
        {
            return existing;
        }

        String key = displayName(name);
        existing = CookieArray.getLayer(key);
        if (existing >= 0)
        {
            return existing;
        }

        // Check if built-in
        for (String builtin : BUILTINS)
        {
            if (builtin.equalsIgnoreCase(key))
            {
                ByteBuffer pixels = generateBuiltin(builtin);
                return CookieArray.resolveRaw(key, pixels);
            }
        }

        // Custom file
        Path path = nameToPath.get(key);
        if (path == null)
        {
            scanCatalog();
            path = nameToPath.get(key);
        }
        if (path == null)
        {
            return -1;
        }

        byte[] raw;
        try
        {
            raw = Files.readAllBytes(path);
        }
        catch (IOException e)
        {
            LOG.warn("Failed to read CAL cookie file: {}", path, e);
            return -1;
        }

        ByteBuffer pixels = CookieArrayBase.decode(raw);
        if (pixels == null)
        {
            LOG.warn("Failed to decode CAL cookie file: {} ({})", key, STBImage.stbi_failure_reason());
            return -1;
        }

        return CookieArray.resolveRaw(key, pixels);
    }

    private static void scanCatalog()
    {
        catalog.clear();
        nameToPath.clear();
        for (String builtin : BUILTINS)
        {
            catalog.add(builtin);
        }

        Path configDir = FabricLoader.getInstance().getConfigDir();
        scanDir(configDir.resolve("cal_lights").resolve("gobos"));
        scanDir(configDir.resolve("irl-redactor").resolve("cookies"));
    }

    private static void scanDir(Path folder)
    {
        try
        {
            if (!Files.isDirectory(folder))
            {
                Files.createDirectories(folder);
                return;
            }
            try (Stream<Path> stream = Files.list(folder))
            {
                stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(IrliteCalCompat::isImage)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(fileName -> {
                        String key = displayName(fileName);
                        if (!nameToPath.containsKey(key))
                        {
                            nameToPath.put(key, folder.resolve(fileName));
                            if (!catalog.contains(key))
                            {
                                catalog.add(key);
                            }
                        }
                    });
            }
        }
        catch (IOException e)
        {
            LOG.warn("Error scanning cookie folder: {}", folder, e);
        }
    }

    private static boolean isImage(String name)
    {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            || lower.endsWith(".tga") || lower.endsWith(".bmp");
    }

    private static String displayName(String fileName)
    {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static ByteBuffer generateBuiltin(String name)
    {
        int res = CookieArray.RES;
        byte[] gray = switch (name)
        {
            case "Window" -> windowMask(res);
            case "Blinds" -> blindsMask(res);
            case "Circle" -> circleMask(res);
            case "Noise" -> noiseMask(res);
            default -> new byte[res * res];
        };
        ByteBuffer buf = MemoryUtil.memAlloc(gray.length);
        buf.put(gray).flip();
        return buf;
    }

    private static byte[] windowMask(int res)
    {
        byte[] data = new byte[res * res];
        int border = res / 32;
        int centerThickness = res / 42;
        int centerStart = res / 2 - centerThickness / 2;
        int centerEnd = res / 2 + centerThickness / 2;

        for (int y = 0; y < res; y++)
        {
            for (int x = 0; x < res; x++)
            {
                boolean isBorder = x < border || x >= res - border || y < border || y >= res - border;
                boolean isCross = (x >= centerStart && x < centerEnd) || (y >= centerStart && y < centerEnd);
                data[y * res + x] = (isBorder || isCross) ? 0 : (byte) 255;
            }
        }
        return data;
    }

    private static byte[] blindsMask(int res)
    {
        byte[] data = new byte[res * res];
        int band = res / 16;
        for (int y = 0; y < res; y++)
        {
            boolean solid = (y / band) % 2 == 0;
            byte val = solid ? (byte) 255 : 0;
            for (int x = 0; x < res; x++)
            {
                data[y * res + x] = val;
            }
        }
        return data;
    }

    private static byte[] circleMask(int res)
    {
        byte[] data = new byte[res * res];
        float cx = (res - 1) * 0.5f;
        float cy = (res - 1) * 0.5f;
        float rInner = res * 0.39f;
        float rOuter = res * 0.47f;

        for (int y = 0; y < res; y++)
        {
            for (int x = 0; x < res; x++)
            {
                float dx = x - cx;
                float dy = y - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                byte val;
                if (dist < rInner)
                {
                    val = (byte) 255;
                }
                else if (dist > rOuter)
                {
                    val = 0;
                }
                else
                {
                    float t = (dist - rInner) / (rOuter - rInner);
                    val = (byte) ((1.0f - t) * 255.0f);
                }
                data[y * res + x] = val;
            }
        }
        return data;
    }

    private static byte[] noiseMask(int res)
    {
        int grid = 32;
        float[][] gridVals = new float[grid][grid];
        for (int y = 0; y < grid; y++)
        {
            for (int x = 0; x < grid; x++)
            {
                gridVals[y][x] = ThreadLocalRandom.current().nextFloat();
            }
        }

        byte[] data = new byte[res * res];
        for (int y = 0; y < res; y++)
        {
            float gy = (float) y / res * (grid - 1);
            int yLow = (int) Math.floor(gy);
            int yHigh = Math.min(yLow + 1, grid - 1);
            float yWeight = gy - yLow;

            for (int x = 0; x < res; x++)
            {
                float gx = (float) x / res * (grid - 1);
                int xLow = (int) Math.floor(gx);
                int xHigh = Math.min(xLow + 1, grid - 1);
                float xWeight = gx - xLow;

                float v1 = gridVals[yLow][xLow];
                float v2 = gridVals[yLow][xHigh];
                float v3 = gridVals[yHigh][xLow];
                float v4 = gridVals[yHigh][xHigh];

                float val = (1f - xWeight) * (1f - yWeight) * v1
                    + xWeight * (1f - yWeight) * v2
                    + (1f - xWeight) * yWeight * v3
                    + xWeight * yWeight * v4;
                data[y * res + x] = (byte) (val * 255f);
            }
        }
        return data;
    }

    /**
     * Internal bridge class that directly touches CAL Editor's LightConfig fields.
     * Isolated into an inner class so the JVM only loads it when CAL Editor is present.
     */
    private static final class CalSyncBridge
    {
        private static boolean initialized = false;

        private static boolean lastShowGuides;
        private static int lastShadowQuality;
        private static boolean lastShadowBlocks;
        private static boolean lastShadowsLive;
        private static float lastShadowSoftness;

        private static float lastVlIntensity;
        private static int lastVlSteps;
        private static float lastVlMaxDist;
        private static boolean lastVlShadows;
        private static int lastVlShadowStride;
        private static float lastVlTipBoost;
        private static float lastVlTipRadius;
        private static boolean lastVlNoise;
        private static float lastVlNoiseAmount;
        private static float lastVlNoiseScale;
        private static float lastVlNoiseSpeed;
        private static float lastVlNoiseMorph;
        private static int lastVlNoiseStride;
        private static boolean lastVlBlueNoise;
        private static boolean lastVlDitherTemporal;
        private static boolean lastVlClusterCull;
        private static boolean lastVlShadowHiz;

        private static boolean lastOutline;
        private static int lastOutlineTarget;
        private static float lastOutlineStrength;
        private static int lastOutlinePixelSize;
        private static float lastOutlineFresnelPower;
        private static float lastOutlineBack;
        private static boolean lastOutlineFront;
        private static float lastOutlineFrontStrength;
        private static boolean lastOutlineGlow;
        private static float lastOutlineGlowStrength;

        @FunctionalInterface
        private interface BoolConsumer
        {
            void accept(boolean val);
        }

        @FunctionalInterface
        private interface FloatConsumer
        {
            void accept(float val);
        }

        static void sync()
        {
            if (IrliteConfig.outline == null)
            {
                return;
            }

            if (!initialized)
            {
                pushAllBbsToCal();
                initialized = true;
                LOG.info("IRLite <-> CAL Editor bidirectional configuration bridge initialized.");
                return;
            }

            // General & Shadow
            lastShowGuides = syncBool(IrliteConfig.showGuides, LightConfig.showGuides, lastShowGuides, v -> LightConfig.showGuides = v);
            lastShadowQuality = syncInt(IrliteConfig.shadowQuality, LightConfig.shadowQuality, lastShadowQuality, v -> LightConfig.shadowQuality = v);
            lastShadowBlocks = syncBool(IrliteConfig.shadowBlocks, LightConfig.shadowBlocks, lastShadowBlocks, v -> LightConfig.shadowBlocks = v);
            lastShadowsLive = syncBool(IrliteConfig.shadowsLive, LightConfig.shadowsLive, lastShadowsLive, v -> LightConfig.shadowsLive = v);
            lastShadowSoftness = syncFloat(IrliteConfig.shadowSoftness, LightConfig.shadowSoftness, lastShadowSoftness, v -> LightConfig.shadowSoftness = v);

            // Volumetrics
            lastVlIntensity = syncFloat(IrliteConfig.vlIntensity, LightConfig.vlIntensity, lastVlIntensity, v -> LightConfig.vlIntensity = v);
            lastVlSteps = syncInt(IrliteConfig.vlSteps, LightConfig.vlSteps, lastVlSteps, v -> LightConfig.vlSteps = v);
            lastVlMaxDist = syncFloat(IrliteConfig.vlMaxDist, LightConfig.vlMaxDist, lastVlMaxDist, v -> LightConfig.vlMaxDist = v);
            lastVlShadows = syncBool(IrliteConfig.vlShadowsLive, LightConfig.vlShadows, lastVlShadows, v -> LightConfig.vlShadows = v);
            lastVlShadowStride = syncInt(IrliteConfig.vlShadowStride, LightConfig.vlShadowStride, lastVlShadowStride, v -> LightConfig.vlShadowStride = v);
            lastVlTipBoost = syncFloat(IrliteConfig.vlTipBoost, LightConfig.vlTipBoost, lastVlTipBoost, v -> LightConfig.vlTipBoost = v);
            lastVlTipRadius = syncFloat(IrliteConfig.vlTipRadius, LightConfig.vlTipRadius, lastVlTipRadius, v -> LightConfig.vlTipRadius = v);
            lastVlNoise = syncBool(IrliteConfig.vlNoiseLive, LightConfig.vlNoise, lastVlNoise, v -> LightConfig.vlNoise = v);
            lastVlNoiseAmount = syncFloat(IrliteConfig.vlNoiseAmount, LightConfig.vlNoiseAmount, lastVlNoiseAmount, v -> LightConfig.vlNoiseAmount = v);
            lastVlNoiseScale = syncFloat(IrliteConfig.vlNoiseScale, LightConfig.vlNoiseScale, lastVlNoiseScale, v -> LightConfig.vlNoiseScale = v);
            lastVlNoiseSpeed = syncFloat(IrliteConfig.vlNoiseSpeed, LightConfig.vlNoiseSpeed, lastVlNoiseSpeed, v -> LightConfig.vlNoiseSpeed = v);
            lastVlNoiseMorph = syncFloat(IrliteConfig.vlNoiseMorph, LightConfig.vlNoiseMorph, lastVlNoiseMorph, v -> LightConfig.vlNoiseMorph = v);
            lastVlNoiseStride = syncInt(IrliteConfig.vlNoiseStride, LightConfig.vlNoiseStride, lastVlNoiseStride, v -> LightConfig.vlNoiseStride = v);
            lastVlBlueNoise = syncBool(IrliteConfig.vlBlueNoise, LightConfig.vlBlueNoise, lastVlBlueNoise, v -> LightConfig.vlBlueNoise = v);
            lastVlDitherTemporal = syncBool(IrliteConfig.vlDitherTemporal, LightConfig.vlDitherTemporal, lastVlDitherTemporal, v -> LightConfig.vlDitherTemporal = v);
            lastVlClusterCull = syncBool(IrliteConfig.vlClusterCull, LightConfig.vlClusterCull, lastVlClusterCull, v -> LightConfig.vlClusterCull = v);
            lastVlShadowHiz = syncBool(IrliteConfig.vlShadowHiz, LightConfig.vlShadowHiz, lastVlShadowHiz, v -> LightConfig.vlShadowHiz = v);

            // Outline
            lastOutline = syncBool(IrliteConfig.outline, LightConfig.outline, lastOutline, v -> LightConfig.outline = v);
            lastOutlineTarget = syncInt(IrliteConfig.outlineTarget, LightConfig.outlineTarget, lastOutlineTarget, v -> LightConfig.outlineTarget = v);
            lastOutlineStrength = syncFloat(IrliteConfig.outlineStrength, LightConfig.outlineStrength, lastOutlineStrength, v -> LightConfig.outlineStrength = v);
            lastOutlinePixelSize = syncInt(IrliteConfig.outlinePixelSize, LightConfig.outlinePixelSize, lastOutlinePixelSize, v -> LightConfig.outlinePixelSize = v);
            lastOutlineFresnelPower = syncFloat(IrliteConfig.outlineFresnelPower, LightConfig.outlineFresnelPower, lastOutlineFresnelPower, v -> LightConfig.outlineFresnelPower = v);
            lastOutlineBack = syncFloat(IrliteConfig.outlineBack, LightConfig.outlineBack, lastOutlineBack, v -> LightConfig.outlineBack = v);
            lastOutlineFront = syncBool(IrliteConfig.outlineFront, LightConfig.outlineFront, lastOutlineFront, v -> LightConfig.outlineFront = v);
            lastOutlineFrontStrength = syncFloat(IrliteConfig.outlineFrontStrength, LightConfig.outlineFrontStrength, lastOutlineFrontStrength, v -> LightConfig.outlineFrontStrength = v);
            lastOutlineGlow = syncBool(IrliteConfig.outlineGlow, LightConfig.outlineGlow, lastOutlineGlow, v -> LightConfig.outlineGlow = v);
            lastOutlineGlowStrength = syncFloat(IrliteConfig.outlineGlowStrength, LightConfig.outlineGlowStrength, lastOutlineGlowStrength, v -> LightConfig.outlineGlowStrength = v);
        }

        private static boolean syncBool(ValueBoolean bbsVal, boolean calVal, boolean lastVal, BoolConsumer setCal)
        {
            boolean bbs = bbsVal != null ? bbsVal.get() : calVal;
            if (bbs != lastVal)
            {
                setCal.accept(bbs);
                return bbs;
            }
            if (calVal != lastVal)
            {
                if (bbsVal != null)
                {
                    bbsVal.set(calVal);
                }
                return calVal;
            }
            return lastVal;
        }

        private static int syncInt(ValueInt bbsVal, int calVal, int lastVal, IntConsumer setCal)
        {
            int bbs = bbsVal != null ? bbsVal.get() : calVal;
            if (bbs != lastVal)
            {
                setCal.accept(bbs);
                return bbs;
            }
            if (calVal != lastVal)
            {
                if (bbsVal != null)
                {
                    bbsVal.set(calVal);
                }
                return calVal;
            }
            return lastVal;
        }

        private static float syncFloat(ValueFloat bbsVal, float calVal, float lastVal, FloatConsumer setCal)
        {
            float bbs = bbsVal != null ? bbsVal.get() : calVal;
            if (Math.abs(bbs - lastVal) > 1e-4f)
            {
                setCal.accept(bbs);
                return bbs;
            }
            if (Math.abs(calVal - lastVal) > 1e-4f)
            {
                if (bbsVal != null)
                {
                    bbsVal.set(calVal);
                }
                return calVal;
            }
            return lastVal;
        }

        private static void pushAllBbsToCal()
        {
            lastShowGuides = LightConfig.showGuides = IrliteConfig.showGuides();
            lastShadowQuality = LightConfig.shadowQuality = IrliteConfig.shadowQuality();
            lastShadowBlocks = LightConfig.shadowBlocks = IrliteConfig.shadowBlocks();
            lastShadowsLive = LightConfig.shadowsLive = IrliteConfig.shadowsLive();
            lastShadowSoftness = LightConfig.shadowSoftness = IrliteConfig.shadowSoftness();

            lastVlIntensity = LightConfig.vlIntensity = IrliteConfig.vlIntensity();
            lastVlSteps = LightConfig.vlSteps = IrliteConfig.vlSteps();
            lastVlMaxDist = LightConfig.vlMaxDist = IrliteConfig.vlMaxDist();
            lastVlShadows = LightConfig.vlShadows = IrliteConfig.vlShadowsLive();
            lastVlShadowStride = LightConfig.vlShadowStride = IrliteConfig.vlShadowStride();
            lastVlTipBoost = LightConfig.vlTipBoost = IrliteConfig.vlTipBoost();
            lastVlTipRadius = LightConfig.vlTipRadius = IrliteConfig.vlTipRadius();
            lastVlNoise = LightConfig.vlNoise = IrliteConfig.vlNoiseLive();
            lastVlNoiseAmount = LightConfig.vlNoiseAmount = IrliteConfig.vlNoiseAmount();
            lastVlNoiseScale = LightConfig.vlNoiseScale = IrliteConfig.vlNoiseScale();
            lastVlNoiseSpeed = LightConfig.vlNoiseSpeed = IrliteConfig.vlNoiseSpeed();
            lastVlNoiseMorph = LightConfig.vlNoiseMorph = IrliteConfig.vlNoiseMorph();
            lastVlNoiseStride = LightConfig.vlNoiseStride = IrliteConfig.vlNoiseStride();
            lastVlBlueNoise = LightConfig.vlBlueNoise = IrliteConfig.vlBlueNoise();
            lastVlDitherTemporal = LightConfig.vlDitherTemporal = IrliteConfig.vlDitherTemporal();
            lastVlClusterCull = LightConfig.vlClusterCull = IrliteConfig.vlClusterCull();
            lastVlShadowHiz = LightConfig.vlShadowHiz = IrliteConfig.vlShadowHiz();

            lastOutline = LightConfig.outline = IrliteConfig.outline();
            lastOutlineTarget = LightConfig.outlineTarget = IrliteConfig.outlineTarget();
            lastOutlineStrength = LightConfig.outlineStrength = IrliteConfig.outlineStrength();
            lastOutlinePixelSize = LightConfig.outlinePixelSize = IrliteConfig.outlinePixelSize();
            lastOutlineFresnelPower = LightConfig.outlineFresnelPower = IrliteConfig.outlineFresnelPower();
            lastOutlineBack = LightConfig.outlineBack = IrliteConfig.outlineBack();
            lastOutlineFront = LightConfig.outlineFront = IrliteConfig.outlineFront();
            lastOutlineFrontStrength = LightConfig.outlineFrontStrength = IrliteConfig.outlineFrontStrength();
            lastOutlineGlow = LightConfig.outlineGlow = IrliteConfig.outlineGlow();
            lastOutlineGlowStrength = LightConfig.outlineGlowStrength = IrliteConfig.outlineGlowStrength();
        }
    }
}
