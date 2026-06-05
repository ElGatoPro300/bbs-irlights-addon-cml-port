package org.wemppy.irlite.client.forms;

import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import org.wemppy.irlite.forms.PointLightForm;

public class PointLightFormRenderer extends AbstractLightFormRenderer<PointLightForm>
{
    public PointLightFormRenderer(PointLightForm form)
    {
        super(form);
    }

    @Override
    protected Color lightColor()
    {
        return this.form.color.get();
    }

    @Override
    protected Icon icon()
    {
        return Icons.LIGHT;
    }

    @Override
    protected void renderGuide(FormRenderingContext context, Color color)
    {
        LightGuideRenderer.renderPointLight(context.stack, color, this.form.radius.get());
    }
}
