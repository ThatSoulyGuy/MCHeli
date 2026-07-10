/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.ShareExpValue;
import mcheli.eval.eval.exp.ShiftRightExpression;

public class LetShiftRightExpression
extends ShiftRightExpression {
    public LetShiftRightExpression() {
        this.setOperator(">>=");
    }

    protected LetShiftRightExpression(LetShiftRightExpression from, ShareExpValue s) {
        super((ShiftRightExpression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new LetShiftRightExpression(this, s);
    }

    public long evalLong() {
        long val = super.evalLong();
        this.expl.let(val, this.pos);
        return val;
    }

    public double evalDouble() {
        double val = super.evalDouble();
        this.expl.let(val, this.pos);
        return val;
    }

    public Object evalObject() {
        Object val = super.evalObject();
        this.expl.let(val, this.pos);
        return val;
    }

    protected AbstractExpression replace() {
        this.expl = this.expl.replaceVar();
        this.expr = this.expr.replace();
        return this.share.repl.replaceLet((Col2Expression)this);
    }
}

