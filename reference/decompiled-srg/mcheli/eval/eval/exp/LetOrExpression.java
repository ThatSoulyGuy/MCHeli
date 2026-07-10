/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.BitOrExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.ShareExpValue;

public class LetOrExpression
extends BitOrExpression {
    public LetOrExpression() {
        this.setOperator("|=");
    }

    protected LetOrExpression(LetOrExpression from, ShareExpValue s) {
        super((BitOrExpression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new LetOrExpression(this, s);
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

