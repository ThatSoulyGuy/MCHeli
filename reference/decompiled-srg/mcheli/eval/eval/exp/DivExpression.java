/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.ShareExpValue;

public class DivExpression
extends Col2Expression {
    public DivExpression() {
        this.setOperator("/");
    }

    protected DivExpression(DivExpression from, ShareExpValue s) {
        super((Col2Expression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new DivExpression(this, s);
    }

    protected long operateLong(long vl, long vr) {
        return vl / vr;
    }

    protected double operateDouble(double vl, double vr) {
        return vl / vr;
    }

    protected Object operateObject(Object vl, Object vr) {
        return this.share.oper.div(vl, vr);
    }
}

