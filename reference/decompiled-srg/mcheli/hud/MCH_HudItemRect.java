/*
 * Decompiled with CFR 0.152.
 */
package mcheli.hud;

import mcheli.hud.MCH_HudItem;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_HudItemRect
extends MCH_HudItem {
    private final String left;
    private final String top;
    private final String width;
    private final String height;

    public MCH_HudItemRect(int fileLine, String left, String top, String width, String height) {
        super(fileLine);
        this.left = MCH_HudItemRect.toFormula((String)left);
        this.top = MCH_HudItemRect.toFormula((String)top);
        this.width = MCH_HudItemRect.toFormula((String)width);
        this.height = MCH_HudItemRect.toFormula((String)height);
    }

    public void execute() {
        double x2 = centerX + MCH_HudItemRect.calc((String)this.left);
        double y2 = centerY + MCH_HudItemRect.calc((String)this.top);
        double x1 = x2 + (double)((int)MCH_HudItemRect.calc((String)this.width));
        double y1 = y2 + (double)((int)MCH_HudItemRect.calc((String)this.height));
        MCH_HudItemRect.drawRect((double)x1, (double)y1, (double)x2, (double)y2, (int)colorSetting);
    }
}

