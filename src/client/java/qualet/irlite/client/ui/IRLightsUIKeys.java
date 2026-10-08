package qualet.irlite.client.ui;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.List;

/** Centralized presentation keys, resolved by the host language system. */
public final class IRLightsUIKeys
{
    public static final IKey SELECT_BOTH = L10n.lang("irlights.ui.select_both");
    public static final IKey REFRESH = L10n.lang("irlights.ui.refresh");
    public static final IKey OPEN_PACKS = L10n.lang("irlights.ui.open_packs");
    public static final IKey OPEN_PATCHES = L10n.lang("irlights.ui.open_patches");
    public static final IKey PACKS = L10n.lang("irlights.ui.packs");
    public static final IKey PATCHES = L10n.lang("irlights.ui.patches");
    public static final IKey CREATE_NEW = L10n.lang("irlights.ui.create_new");
    public static final IKey VALIDATE = L10n.lang("irlights.ui.validate");
    public static final IKey PATCH = L10n.lang("irlights.ui.patch");
    public static final IKey VALIDATE_HELP = L10n.lang("irlights.ui.validate_help");
    public static final IKey READ_FAILED = L10n.lang("irlights.ui.read_failed");
    public static final IKey CHOOSE_PACK = L10n.lang("irlights.ui.choose_pack");
    public static final IKey CHOOSE_PATCH = L10n.lang("irlights.ui.choose_patch");
    public static final IKey VALID = L10n.lang("irlights.ui.valid");
    public static final IKey ALREADY_PATCHED = L10n.lang("irlights.ui.already_patched");
    public static final IKey CONTRACT_MISMATCH = L10n.lang("irlights.ui.contract_mismatch");
    public static final IKey BAD_SOURCE = L10n.lang("irlights.ui.bad_source");
    public static final IKey IO_ERROR = L10n.lang("irlights.ui.io_error");
    public static final IKey INCOMPATIBLE = L10n.lang("irlights.ui.incompatible");
    public static final IKey TARGET_CHOOSE = L10n.lang("irlights.ui.target_choose");
    public static final IKey TARGET_MISMATCH = L10n.lang("irlights.ui.target_mismatch");
    public static final IKey TARGET_MATCH = L10n.lang("irlights.ui.target_match");
    public static final IKey CREATED = L10n.lang("irlights.ui.created");
    public static final IKey PRESETS = L10n.lang("irlights.ui.presets");
    public static final IKey QUALITY = L10n.lang("irlights.ui.quality");
    public static final IKey QUALITY_HELP = L10n.lang("irlights.ui.quality_help");
    public static final IKey BEAM_STYLE = L10n.lang("irlights.ui.beam_style");
    public static final IKey BEAM_HELP = L10n.lang("irlights.ui.beam_help");
    public static final IKey DEBUG = L10n.lang("irlights.ui.debug");
    public static final IKey HIDE_PERFORMANCE = L10n.lang("irlights.ui.hide_performance");
    public static final IKey SHOW_PERFORMANCE = L10n.lang("irlights.ui.show_performance");
    public static final IKey PERFORMANCE_HELP = L10n.lang("irlights.ui.performance_help");
    public static final IKey PERFORMANCE = L10n.lang("irlights.ui.performance");
    public static final IKey BALANCED = L10n.lang("irlights.ui.balanced");
    public static final IKey ULTRA = L10n.lang("irlights.ui.ultra");
    public static final IKey CUSTOM = L10n.lang("irlights.ui.custom");
    public static final IKey CLEAN = L10n.lang("irlights.ui.clean");
    public static final IKey DUSTY = L10n.lang("irlights.ui.dusty");
    public static final IKey SMOKY = L10n.lang("irlights.ui.smoky");
    public static final IKey LOW = L10n.lang("irlights.ui.low");
    public static final IKey MEDIUM = L10n.lang("irlights.ui.medium");
    public static final IKey HIGH = L10n.lang("irlights.ui.high");
    public static final IKey ALL = L10n.lang("irlights.ui.all");
    public static final IKey ENTITIES = L10n.lang("irlights.ui.entities");
    public static final IKey BLOCKS = L10n.lang("irlights.ui.blocks");

    public static final List<IKey> QUALITY_LABELS = List.of(PERFORMANCE, BALANCED, QUALITY, ULTRA, CUSTOM);
    public static final List<IKey> STYLE_LABELS = List.of(CLEAN, DUSTY, SMOKY, CUSTOM);

    private IRLightsUIKeys()
    {}
}
