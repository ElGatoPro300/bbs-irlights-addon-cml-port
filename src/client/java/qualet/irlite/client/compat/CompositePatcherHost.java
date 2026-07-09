package qualet.irlite.client.compat;

import org.qualet.irl.patcher.PatcherHost;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges two {@link PatcherHost} delegates: primary owns the on-disk patch folder
 * name; bundled patch lists and streams are unioned so both mods' assets apply.
 */
public final class CompositePatcherHost implements PatcherHost
{
    private final PatcherHost primary;
    private final PatcherHost secondary;

    public CompositePatcherHost(PatcherHost primary, PatcherHost secondary)
    {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public Path gameDir()
    {
        return this.primary.gameDir();
    }

    @Override
    public Path shaderpacksDir()
    {
        Path dir = this.primary.shaderpacksDir();
        return dir != null ? dir : this.secondary.shaderpacksDir();
    }

    @Override
    public List<String> listShaderpacks()
    {
        return this.primary.listShaderpacks();
    }

    @Override
    public void openFolder(Path dir)
    {
        this.primary.openFolder(dir);
    }

    @Override
    public String patchesDirName()
    {
        return this.primary.patchesDirName();
    }

    @Override
    public List<String> bundledPatches()
    {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(this.primary.bundledPatches());
        merged.addAll(this.secondary.bundledPatches());
        return List.copyOf(merged);
    }

    @Override
    public InputStream openBundledPatch(String name)
    {
        InputStream stream = this.primary.openBundledPatch(name);
        if (stream != null)
        {
            return stream;
        }
        return this.secondary.openBundledPatch(name);
    }
}
