/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import java.util.List;
import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.Col2Expression;
import mcheli.eval.eval.exp.Col2OpeExpression;
import mcheli.eval.eval.exp.ShareExpValue;

public class FuncArgExpression
extends Col2OpeExpression {
    public FuncArgExpression() {
        this.setOperator(",");
    }

    protected FuncArgExpression(FuncArgExpression from, ShareExpValue s) {
        super((Col2Expression)from, s);
    }

    public AbstractExpression dup(ShareExpValue s) {
        return new FuncArgExpression(this, s);
    }

    protected void evalArgsLong(List args) {
        this.expl.evalArgsLong(args);
        this.expr.evalArgsLong(args);
    }

    protected void evalArgsDouble(List args) {
        this.expl.evalArgsDouble(args);
        this.expr.evalArgsDouble(args);
    }

    protected void evalArgsObject(List args) {
        this.expl.evalArgsObject(args);
        this.expr.evalArgsObject(args);
    }

    protected String toStringLeftSpace() {
        return "";
    }
}

