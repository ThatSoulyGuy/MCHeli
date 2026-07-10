/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.MultExpression;
import mcheli.eval.eval.exp.ShareExpValue;

public class LetMultExpression
extends MultExpression {
    public LetMultExpression() {
        this.setOperator("*=");
    }

    protected LetMultExpression(LetMultExpression from, ShareExpValue s) {
        super((MultExpression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new LetMultExpression(this, s);
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

