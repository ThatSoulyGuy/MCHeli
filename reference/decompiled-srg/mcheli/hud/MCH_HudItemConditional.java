/*
 * Decompiled with CFR 0.152.
 */
package mcheli.hud;

import mcheli.hud.MCH_HudItem;

/*
 * Exception performing whole class analysis ignored.
 */
public class MCH_HudItemConditional
extends MCH_HudItem {
    private final boolean isEndif;
    private final String conditional;

    public MCH_HudItemConditional(int fileLine, boolean isEndif, String conditional) {
        super(fileLine);
        this.isEndif = isEndif;
        this.conditional = conditional;
    }

    public boolean canExecute() {
        return true;
    }

    public void execute() {
        this.parent.isIfFalse = !this.isEndif ? MCH_HudItemConditional.calc((String)this.conditional) == 0.0 : false;
    }
}

