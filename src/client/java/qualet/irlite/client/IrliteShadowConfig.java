package qualet.irlite.client;

import org.qualet.irl.light.shadow.ShadowConfig;
import qualet.irlite.IrliteConfig;

/**
 * Client-side {@link ShadowConfig} adapter handed to
 * {@link org.qualet.irl.light.shadow.ShadowEngine} at init: the shadow-relevant subset
 * of {@link IrliteConfig}, delegating to its BBS-{@code Value*}-backed getters.
 *
 * <p>Why a separate class here (vs the redactor's nested {@code LightConfig.SHADOW}):
 * {@link IrliteConfig} lives in the main (server-safe) source set, which does not see
 * the client-scoped irl-core dependency. This adapter lives in the client source set
 * where {@link ShadowConfig} is visible, and reads {@code IrliteConfig}'s static
 * getters (visible from client).
 */
public final class IrliteShadowConfig implements ShadowConfig
{
    @Override public int shadowQuality()     { return IrliteConfig.shadowQuality(); }
    @Override public boolean shadowCache()   { return IrliteConfig.shadowCache(); }
    @Override public int shadowBakeBudget()  { return IrliteConfig.shadowBakeBudget(); }
    @Override public boolean shadowBlocks()  { return IrliteConfig.shadowBlocks(); }
    @Override public int shadowBlockRadius() { return IrliteConfig.shadowBlockRadius(); }
}
