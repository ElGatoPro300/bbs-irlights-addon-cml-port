package qualet.irlite.client.compat;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryUtil;
import org.qualet.irl.light.CookieArrayBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Single {@code GL_TEXTURE_2D_ARRAY} for BBS spotlight cookies and CAL Editor gobos
 * when both mods are installed. CAL names keep their original layer indices (0–19);
 * BBS {@link Link} cookies occupy layers 20+ with LRU eviction.
 */
public final class UnifiedCookieArray extends CookieArrayBase
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    static final UnifiedCookieArray INSTANCE = new UnifiedCookieArray();

    private static final String[] CAL_BUILTINS = {"Window", "Blinds", "Circle", "Noise"};
    /** Matches CAL {@code CookieArray.MAX_LAYERS}. */
    private static final int CAL_RESERVED = 20;
    private static final int BBS_OFFSET = CAL_RESERVED;
    private static final int BBS_MAX = 32;

    private final Map<String, Integer> calNameToLayer = new HashMap<>();
    private final Map<String, Path> calNameToPath = new HashMap<>();
    private final List<String> catalog = new ArrayList<>();

    private final Map<String, Integer> bbsLayerByKey = new HashMap<>();
    private final Map<String, Long> bbsLastUse = new HashMap<>();
    private final Set<String> bbsFailed = new HashSet<>();
    private long bbsUseCounter = 0;
    private int nextBbsSlot = 0;

    private boolean initialized;
    private int nextCalLayer;

    private UnifiedCookieArray()
    {
        super(CAL_RESERVED + BBS_MAX);
    }

    public void init()
    {
        if (initialized)
        {
            return;
        }

        nextCalLayer = 0;
        initCalBuiltins();
        scanCalCatalog();
        initialized = true;
    }

    public int getGlTextureId()
    {
        return textureId();
    }

    public List<String> available()
    {
        if (!initialized)
        {
            init();
        }
        return List.copyOf(catalog);
    }

    public int resolveCal(String name)
    {
        if (!initialized)
        {
            init();
        }

        if (name == null || name.isEmpty() || "None".equalsIgnoreCase(name))
        {
            return -1;
        }

        Integer cached = calNameToLayer.get(name);
        if (cached != null)
        {
            return cached;
        }

        String key = displayName(name);
        cached = calNameToLayer.get(key);
        if (cached != null)
        {
            return cached;
        }

        if (nextCalLayer >= CAL_RESERVED)
        {
            return -1;
        }

        Path path = calNameToPath.get(key);
        if (path == null)
        {
            path = calNameToPath.get(name);
        }
        if (path == null)
        {
            return -1;
        }

        int layer = loadCalFile(path, key);
        if (layer < 0)
        {
            return -1;
        }

        calNameToLayer.put(key, layer);
        if (!key.equals(name))
        {
            calNameToLayer.put(name, layer);
        }
        return layer;
    }

    public int resolveBbs(Link link)
    {
        if (!initialized)
        {
            init();
        }

        if (link == null)
        {
            return -1;
        }

        String key = link.toString();
        if (key.isEmpty())
        {
            return -1;
        }

        Integer cached = bbsLayerByKey.get(key);
        if (cached != null)
        {
            bbsLastUse.put(key, ++bbsUseCounter);
            return cached;
        }
        if (bbsFailed.contains(key))
        {
            return -1;
        }

        ByteBuffer pixels = decodeBbs(link, key);
        if (pixels == null)
        {
            bbsFailed.add(key);
            return -1;
        }

        try
        {
            int slot = (nextBbsSlot < BBS_MAX) ? nextBbsSlot++ : evictBbsLru();
            int layer = BBS_OFFSET + slot;
            uploadLayer(pixels, layer);
            bbsLayerByKey.put(key, layer);
            bbsLastUse.put(key, ++bbsUseCounter);
            LOG.info("BBS cookie loaded '{}' -> layer {}", key, layer);
            return layer;
        }
        finally
        {
            MemoryUtil.memFree(pixels);
        }
    }

    public void reload()
    {
        calNameToLayer.clear();
        calNameToPath.clear();
        catalog.clear();
        bbsLayerByKey.clear();
        bbsLastUse.clear();
        bbsFailed.clear();
        bbsUseCounter = 0;
        nextBbsSlot = 0;
        nextCalLayer = 0;
        initialized = false;
        deleteTexture();
        init();
    }

    private void initCalBuiltins()
    {
        Method generateBuiltin = calGenerateBuiltinMethod();
        if (generateBuiltin == null)
        {
            LOG.warn("CAL builtin gobos unavailable; only file-based gobos will load.");
            return;
        }

        for (String name : CAL_BUILTINS)
        {
            try
            {
                ByteBuffer pixels = (ByteBuffer) generateBuiltin.invoke(null, name);
                int layer = nextCalLayer++;
                uploadLayer(pixels, layer);
                MemoryUtil.memFree(pixels);
                calNameToLayer.put(name, layer);
                LOG.info("CAL builtin gobo '{}' -> layer {}", name, layer);
            }
            catch (ReflectiveOperationException e)
            {
                LOG.warn("CAL builtin gobo '{}' failed: {}", name, e.toString());
            }
        }
    }

    private void scanCalCatalog()
    {
        catalog.clear();
        calNameToPath.clear();

        for (String builtin : CAL_BUILTINS)
        {
            catalog.add(builtin);
        }

        scanCalDir(calGobosDir());
        scanCalDir(calCookiesDir());
    }

    private void scanCalDir(Path folder)
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
                    .filter(UnifiedCookieArray::isImage)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(fileName ->
                    {
                        String key = displayName(fileName);
                        if (!calNameToPath.containsKey(key))
                        {
                            calNameToPath.put(key, folder.resolve(fileName));
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
            LOG.warn("CAL gobo folder list failed: {}", folder, e);
        }
    }

    private int loadCalFile(Path path, String key)
    {
        byte[] raw;
        try
        {
            raw = Files.readAllBytes(path);
        }
        catch (IOException e)
        {
            LOG.warn("CAL gobo read failed: {}", path, e);
            return -1;
        }

        ByteBuffer pixels = CookieArrayBase.decode(raw);
        if (pixels == null)
        {
            LOG.warn("CAL gobo decode failed: {} ({})", key, STBImage.stbi_failure_reason());
            return -1;
        }

        try
        {
            int layer = nextCalLayer++;
            uploadLayer(pixels, layer);
            LOG.info("CAL gobo loaded '{}' -> layer {}", key, layer);
            return layer;
        }
        finally
        {
            MemoryUtil.memFree(pixels);
        }
    }

    private int evictBbsLru()
    {
        String lruKey = null;
        long lruStamp = Long.MAX_VALUE;
        for (Map.Entry<String, Long> entry : bbsLastUse.entrySet())
        {
            if (entry.getValue() < lruStamp)
            {
                lruStamp = entry.getValue();
                lruKey = entry.getKey();
            }
        }

        int layer = bbsLayerByKey.remove(lruKey);
        bbsLastUse.remove(lruKey);
        LOG.info("BBS cookie evicted '{}' -> reusing layer {}", lruKey, layer);
        return layer - BBS_OFFSET;
    }

    private ByteBuffer decodeBbs(Link link, String key)
    {
        byte[] raw;
        try (InputStream in = BBSMod.getProvider().getAsset(link))
        {
            raw = in.readAllBytes();
        }
        catch (Exception e)
        {
            LOG.warn("BBS cookie asset read failed: {}", key, e);
            return null;
        }

        ByteBuffer pixels = CookieArrayBase.decode(raw);
        if (pixels == null)
        {
            LOG.warn("BBS cookie decode failed: {} ({})", key, STBImage.stbi_failure_reason());
        }
        return pixels;
    }

    private static Method calGenerateBuiltinMethod()
    {
        try
        {
            Method method = Class.forName("elgatopro300.cal_lights.light.cookie.CookieArray")
                .getDeclaredMethod("generateBuiltin", String.class);
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException e)
        {
            return null;
        }
    }

    private static Path calGobosDir()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("cal_lights")
            .resolve("gobos");
    }

    private static Path calCookiesDir()
    {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("irl-redactor")
            .resolve("cookies");
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
}
