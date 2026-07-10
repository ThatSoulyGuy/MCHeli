/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.exp;

import mcheli.eval.eval.exp.AbstractExpression;
import mcheli.eval.eval.exp.NumberExpression;
import mcheli.eval.eval.exp.OptimizeObject;

public class OptimizeDouble
extends OptimizeObject {
    protected boolean isTrue(AbstractExpression x) {
        return x.evalDouble() != 0.0;
    }

    protected AbstractExpression toConst(AbstractExpression exp) {
        try {
            double val = exp.evalDouble();
            return NumberExpression.create((AbstractExpression)exp, (String)Double.toString(val));
        }
        catch (Exception e) {
            return exp;
        }
    }
}

