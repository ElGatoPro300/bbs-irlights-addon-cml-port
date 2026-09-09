package qualet.irlite.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.qualet.irl.light.CookieArrayBase;
import qualet.irlite.client.light.cookie.CookieArray;
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
import java.util.stream.Stream;

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
}
