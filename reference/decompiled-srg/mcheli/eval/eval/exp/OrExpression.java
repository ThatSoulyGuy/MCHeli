/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.Col2OpeExpression;
import mcheli.eval.eval.exp.ShareExpValue;

public class OrExpression
extends Col2OpeExpression {
    public OrExpression() {
        this.setOperator("||");
    }

    protected OrExpression(OrExpression from, ShareExpValue s) {
        super((Col2Expression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new OrExpression(this, s);
    }

    public long evalLong() {
        long val = this.expl.evalLong();
        if (val != 0L) {
            return val;
        }
        return this.expr.evalLong();
    }

    public double evalDouble() {
        double val = this.expl.evalDouble();
        if (val != 0.0) {
            return val;
        }
        return this.expr.evalDouble();
    }

    public Object evalObject() {
        Object val = this.expl.evalObject();
        if (this.share.oper.bool(val)) {
            return val;
        }
        return this.expr.evalObject();
    }
}

