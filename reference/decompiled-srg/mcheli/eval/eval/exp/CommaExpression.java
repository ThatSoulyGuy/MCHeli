/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.Col2OpeExpression;
import mcheli.eval.eval.exp.ShareExpValue;

public class CommaExpression
extends Col2OpeExpression {
    public CommaExpression() {
        this.setOperator(",");
    }

    protected CommaExpression(CommaExpression from, ShareExpValue s) {
        super((Col2Expression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new CommaExpression(this, s);
    }

    public long evalLong() {
        this.expl.evalLong();
        return this.expr.evalLong();
    }

    public double evalDouble() {
        this.expl.evalDouble();
        return this.expr.evalDouble();
    }

    public Object evalObject() {
        this.expl.evalObject();
        return this.expr.evalObject();
    }

    protected String toStringLeftSpace() {
        return "";
    }
}

