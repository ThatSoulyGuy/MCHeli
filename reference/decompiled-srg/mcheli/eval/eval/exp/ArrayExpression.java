/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.EvalException;
import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.Col2OpeExpression;
import mcheli.eval.eval.exp.ShareExpValue;

public class ArrayExpression
extends Col2OpeExpression {
    public ArrayExpression() {
        this.setOperator("[");
        this.setEndOperator("]");
    }

    protected ArrayExpression(ArrayExpression from, ShareExpValue s) {
        super((Col2Expression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new ArrayExpression(this, s);
    }

    public long evalLong() {
        try {
            return this.share.var.evalLong(this.getVariable());
        }
        catch (EvalException e) {
            throw e;
        }
        catch (Exception e) {
            throw new EvalException(2201, this.toString(), this.string, this.pos, (Throwable)e);
        }
    }

    public double evalDouble() {
        try {
            return this.share.var.evalDouble(this.getVariable());
        }
        catch (EvalException e) {
            throw e;
        }
        catch (Exception e) {
            throw new EvalException(2201, this.toString(), this.string, this.pos, (Throwable)e);
        }
    }

    public Object evalObject() {
        return this.getVariable();
    }

    protected Object getVariable() {
        Object obj = this.expl.getVariable();
        if (obj == null) {
            throw new EvalException(2104, this.expl.toString(), this.string, this.pos, null);
        }
        int index = (int)this.expr.evalLong();
        try {
            return this.share.var.getObject(obj, index);
        }
        catch (EvalException e) {
            throw e;
        }
        catch (Exception e) {
            throw new EvalException(2201, this.toString(), this.string, this.pos, (Throwable)e);
        }
    }

    protected void let(Object val, int pos) {
        Object obj = this.expl.getVariable();
        if (obj == null) {
            throw new EvalException(2104, this.expl.toString(), this.string, pos, null);
        }
        int index = (int)this.expr.evalLong();
        try {
            this.share.var.setValue(obj, index, val);
        }
        catch (EvalException e) {
            throw e;
        }
        catch (Exception e) {
            throw new EvalException(2202, this.toString(), this.string, pos, (Throwable)e);
        }
    }

    protected AbstractExpression replaceVar() {
        this.expl = this.expl.replaceVar();
        this.expr = this.expr.replace();
        return this.share.repl.replaceVar2((Col2OpeExpression)this);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.expl.toString());
        sb.append('[');
        sb.append(this.expr.toString());
        sb.append(']');
        return sb.toString();
    }
}

