package org.wemppy.irlite.client.ui.patcher;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wemppy.irlite.client.patcher.IrlPatch;
import org.wemppy.irlite.client.patcher.IrlPatchApplier;
import org.wemppy.irlite.client.patcher.IrlPatchParser;
import org.wemppy.irlite.client.patcher.PatchLibrary;
import org.wemppy.irlite.client.patcher.PatchResult;
import org.wemppy.irlite.client.patcher.Shaderpacks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The IRLite shader patcher, rendered as controls inside the IRLite settings section. */
public final class UIPatcherSection
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    private static final int OK_COLOR = 0x55FF55;
    private static final int ERR_COLOR = 0xFF5555;

    // Selection persists across settings refreshes.
    private static String selectedPack;
    private static Path selectedPatch;
    private static boolean createNew = false;
    private static String status = "Select a shaderpack and a patch.";
    private static int statusColor = Colors.WHITE;

    private static UILabel statusLabel;
    private static Runnable rebuild;

    private UIPatcherSection()
    {}

    public static void append(UIScrollView options, Runnable rebuildCallback)
    {
        rebuild = rebuildCallback;

        List<String> packs = Shaderpacks.list();
        List<Path> patches = PatchLibrary.list();

        // --- shaderpack list (header row: label + refresh + open folder) ---
        UIIcon refresh = new UIIcon(Icons.REFRESH, (b) ->
        {
            if (rebuild != null)
            {
                rebuild.run();
            }
        });
        refresh.tooltip(IKey.constant("Refresh lists"));

        UIIcon openPacks = new UIIcon(Icons.FOLDER, (b) -> Shaderpacks.openFolder());
        openPacks.tooltip(IKey.constant("Open shaderpacks folder"));

        options.add(headerRow("Shaderpacks", refresh, openPacks));

        UILabelList<String> packList = new UILabelList<>((selected) ->
        {
            if (!selected.isEmpty())
            {
                selectedPack = selected.get(0).value;
            }
        });
        packList.background();
        packList.h(96);
        for (String pack : packs)
        {
            packList.add(IKey.constant(pack), pack);
        }
        if (selectedPack != null)
        {
            packList.setCurrentValue(selectedPack);
        }
        options.add(packList);

        // --- patch list (header row: label + open folder) ---
        UIIcon openPatches = new UIIcon(Icons.FOLDER, (b) -> PatchLibrary.openFolder());
        openPatches.tooltip(IKey.constant("Open patches folder"));

        options.add(headerRow("Patches", openPatches));

        UILabelList<Path> patchList = new UILabelList<>((selected) ->
        {
            if (!selected.isEmpty())
            {
                selectedPatch = selected.get(0).value;
            }
        });
        patchList.background();
        patchList.h(96);
        for (Path patch : patches)
        {
            patchList.add(IKey.constant(patch.getFileName().toString()), patch);
        }
        if (selectedPatch != null)
        {
            patchList.setCurrentValue(selectedPatch);
        }
        options.add(patchList);

        // --- options + primary action ---
        UIToggle createNewToggle = new UIToggle(IKey.constant("Create new pack each time"), (t) -> createNew = t.getValue());
        createNewToggle.setValue(createNew);
        options.add(createNewToggle);

        UIButton patch = new UIButton(IKey.constant("Patch"), (b) -> runPatch());
        options.add(patch);

        statusLabel = new UILabel(IKey.constant(status), statusColor);
        statusLabel.h(28);
        options.add(statusLabel);
    }

    private static void runPatch()
    {
        if (selectedPack == null)
        {
            setStatus(false, "Select a shaderpack first.");
            return;
        }
        if (selectedPatch == null)
        {
            setStatus(false, "Select a patch first.");
            return;
        }

        IrlPatch parsed;
        try
        {
            String text = Files.readString(selectedPatch, StandardCharsets.UTF_8);
            parsed = IrlPatchParser.parse(text);
        }
        catch (IrlPatchParser.ParseException e)
        {
            setStatus(false, "Parse error: " + e.getMessage());
            return;
        }
        catch (Exception e)
        {
            setStatus(false, "Cannot read patch: " + e.getMessage());
            return;
        }

        Path source = Shaderpacks.packPath(selectedPack);
        if (!Files.isDirectory(source))
        {
            setStatus(false, "Pack is not a folder (ZIP not supported yet): " + selectedPack);
            return;
        }

        Path output = Shaderpacks.dir().resolve(outputName(selectedPack));
        PatchResult result = IrlPatchApplier.apply(source, output, parsed);

        for (String line : result.log)
        {
            LOG.info("[patch] {}", line);
        }
        setStatus(result.ok, result.summary);

        // Re-read lists so a newly created patched pack shows up.
        if (rebuild != null)
        {
            rebuild.run();
        }
    }

    private static String outputName(String packName)
    {
        String base = packName;
        if (base.toLowerCase().endsWith(".zip"))
        {
            base = base.substring(0, base.length() - 4);
        }
        base = base + "_IRLite";

        if (!createNew)
        {
            return base;
        }

        if (!Files.exists(Shaderpacks.dir().resolve(base)))
        {
            return base;
        }
        for (int i = 2; i < 1000; i++)
        {
            String candidate = base + "_" + i;
            if (!Files.exists(Shaderpacks.dir().resolve(candidate)))
            {
                return candidate;
            }
        }
        return base;
    }

    private static void setStatus(boolean ok, String message)
    {
        status = message;
        statusColor = ok ? OK_COLOR : ERR_COLOR;
        if (statusLabel != null)
        {
            statusLabel.label = IKey.constant(message);
            statusLabel.color(statusColor, true);
        }
    }

    /** A header row with the label flexing on the left and fixed icon buttons on the right (icons vertically centered with the text). */
    private static UIElement headerRow(String text, UIIcon... icons)
    {
        int rowH = 18;

        UIElement row = new UIElement();
        row.row(4).preferred(0);
        row.h(rowH);
        row.marginTop(6);

        // Vertically center the label text so it lines up with the centered icons.
        // anchorY centers within (area.h - fontHeight), so the label must span the
        // full row height — UILabel otherwise auto-sizes to the font height.
        UILabel header = UI.label(IKey.constant(text));
        header.labelAnchor(0F, 0.5F);
        header.h(rowH);
        row.add(header);
        for (UIIcon icon : icons)
        {
            icon.w(20).h(rowH);
            row.add(icon);
        }

        return row;
    }
}
