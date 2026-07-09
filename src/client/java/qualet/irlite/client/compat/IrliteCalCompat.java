package qualet.irlite.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.qualet.irl.light.shadow.ShadowCasterSource;
import org.qualet.irl.patcher.PatcherHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qualet.irlite.IrliteConfig;
import qualet.irlite.client.patcher.BbsPatcherHost;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Optional bridge to IRL CAL Editor ({@code irlcal_editor}) when both mods are
 * installed. Uses reflection so irlite builds without a compile-time dependency
 * on the editor.
 */
public final class IrliteCalCompat
{
    public static final String MOD_ID = "irlcal_editor";

    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private static final Method COLLECT;
    private static final Method RESET_RAMP;
    private static final Constructor<?> CAL_PATCHER_HOST;
    private static final Constructor<?> CAL_CASTER;

    static
    {
        Method collect = null;
        Method reset = null;
        Constructor<?> patcherHost = null;
        Constructor<?> caster = null;

        if (LOADED)
        {
            try
            {
                Class<?> driver = Class.forName("elgatopro300.cal_lights.light.LightDriver");
                collect = driver.getMethod("collect", ClientWorld.class, Vec3d.class, float.class);
                reset = driver.getMethod("resetAutoShadowRamp");
            }
            catch (ReflectiveOperationException e)
            {
                LOG.warn("IRL CAL Editor is present but LightDriver could not be linked: {}", e.toString());
            }

            try
            {
                patcherHost = Class.forName("elgatopro300.cal_lights.patcher.CALPatcherHost")
                    .getDeclaredConstructor();
            }
            catch (ReflectiveOperationException e)
            {
                LOG.warn("IRL CAL Editor is present but CALPatcherHost could not be linked: {}", e.toString());
            }

            try
            {
                caster = Class.forName("org.qualet.irl.light.shadow.RedactorEntityCasterSource")
                    .getDeclaredConstructor();
            }
            catch (ReflectiveOperationException e)
            {
                LOG.warn("IRL CAL Editor is present but RedactorEntityCasterSource could not be linked: {}", e.toString());
            }
        }

        COLLECT = collect;
        RESET_RAMP = reset;
        CAL_PATCHER_HOST = patcherHost;
        CAL_CASTER = caster;
    }

    private IrliteCalCompat()
    {
    }

    public static boolean isLoaded()
    {
        return LOADED && COLLECT != null;
    }

    public static PatcherHost createPatcherHost()
    {
        if (!LOADED || CAL_PATCHER_HOST == null)
        {
            return new BbsPatcherHost();
        }

        try
        {
            PatcherHost calHost = (PatcherHost) CAL_PATCHER_HOST.newInstance();
            mergeCalPatchesOntoDisk(calHost);
            return new CompositePatcherHost(new BbsPatcherHost(), calHost);
        }
        catch (ReflectiveOperationException e)
        {
            LOG.warn("Falling back to BBS-only patcher host: {}", e.toString());
            return new BbsPatcherHost();
        }
    }

    public static ShadowCasterSource createCalCasterSource()
    {
        if (!LOADED || CAL_CASTER == null)
        {
            return null;
        }

        try
        {
            return (ShadowCasterSource) CAL_CASTER.newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            LOG.warn("CAL entity shadow caster unavailable: {}", e.toString());
            return null;
        }
    }

    public static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta)
    {
        if (COLLECT == null)
        {
            return;
        }

        try
        {
            COLLECT.invoke(null, world, cameraPos, tickDelta);
        }
        catch (ReflectiveOperationException e)
        {
            LOG.warn("CAL light collect failed: {}", e.toString());
        }
    }

    public static void resetAutoShadowRamp()
    {
        if (RESET_RAMP == null)
        {
            return;
        }

        try
        {
            RESET_RAMP.invoke(null);
        }
        catch (ReflectiveOperationException e)
        {
            LOG.warn("CAL shadow ramp reset failed: {}", e.toString());
        }
    }

    /**
     * Mirrors BBS shadow settings into CAL's {@code LightConfig} static fields so
     * the editor UI and auto-lights stay aligned with irlite's settings panel.
     */
    public static void syncShadowSettings()
    {
        if (!LOADED)
        {
            return;
        }

        try
        {
            Class<?> config = Class.forName("elgatopro300.cal_lights.light.LightConfig");
            setStaticInt(config, "shadowQuality", IrliteConfig.shadowQuality());
            setStaticBoolean(config, "shadowCache", IrliteConfig.shadowCache());
            setStaticBoolean(config, "shadowBlocks", IrliteConfig.shadowBlocks());
            setStaticInt(config, "shadowBlockRadius", IrliteConfig.shadowBlockRadius());
            setStaticInt(config, "shadowBakeBudget", IrliteConfig.shadowBakeBudget());
        }
        catch (ReflectiveOperationException e)
        {
            LOG.warn("CAL shadow settings sync failed: {}", e.toString());
        }
    }

    private static void mergeCalPatchesOntoDisk(PatcherHost calHost)
    {
        try
        {
            Path calDir = calHost.gameDir()
                .resolve(calHost.patchesDirName())
                .resolve("patches");
            Path irliteDir = calHost.gameDir()
                .resolve("irlite")
                .resolve("patches");

            if (!Files.isDirectory(calDir))
            {
                return;
            }

            Files.createDirectories(irliteDir);

            try (Stream<Path> files = Files.list(calDir))
            {
                for (Path src : files.toList())
                {
                    if (!src.getFileName().toString().endsWith(".irlights"))
                    {
                        continue;
                    }

                    Path dest = irliteDir.resolve(src.getFileName());
                    if (!Files.exists(dest))
                    {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        catch (Exception e)
        {
            LOG.warn("Could not merge CAL patch folder into irlite/patches: {}", e.toString());
        }
    }

    private static void setStaticInt(Class<?> type, String field, int value) throws ReflectiveOperationException
    {
        Field f = type.getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(null, value);
    }

    private static void setStaticBoolean(Class<?> type, String field, boolean value) throws ReflectiveOperationException
    {
        Field f = type.getDeclaredField(field);
        f.setAccessible(true);
        f.setBoolean(null, value);
    }
}
