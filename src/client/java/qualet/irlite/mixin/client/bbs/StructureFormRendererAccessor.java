package qualet.irlite.mixin.client.bbs;

import mchorse.bbs_mod.forms.renderers.StructureFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.StructureData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = StructureFormRenderer.class, remap = false)
public interface StructureFormRendererAccessor {
    @Accessor("data")
    StructureData irlite$getData();

    @Invoker("ensureLoaded")
    void irlite$ensureLoaded();
}
