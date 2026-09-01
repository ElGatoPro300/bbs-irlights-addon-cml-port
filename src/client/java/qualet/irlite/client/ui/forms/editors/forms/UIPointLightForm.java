package qualet.irlite.client.ui.forms.editors.forms;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import qualet.irlite.client.ui.forms.editors.panels.UIPointLightFormPanel;
import qualet.irlite.forms.PointLightForm;

public class UIPointLightForm extends UIForm<PointLightForm>
{
    public UIPointLightForm()
    {
        super();

        this.defaultPanel = new UIPointLightFormPanel(this);

        this.registerPanel(this.defaultPanel, L10n.lang("irlite.forms.point_light.title"), Icons.LIGHT);
        this.registerDefaultPanels();
    }
}
