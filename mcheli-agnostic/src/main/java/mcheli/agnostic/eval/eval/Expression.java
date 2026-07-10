package mcheli.agnostic.eval.eval;

import mcheli.agnostic.eval.eval.exp.AbstractExpression;
import mcheli.agnostic.eval.eval.func.Function;
import mcheli.agnostic.eval.eval.oper.Operator;
import mcheli.agnostic.eval.eval.ref.Refactor;
import mcheli.agnostic.eval.eval.repl.Replace;
import mcheli.agnostic.eval.eval.srch.Search;
import mcheli.agnostic.eval.eval.var.Variable;

public abstract class Expression {
   public Variable var;
   public Function func;
   public Operator oper;
   public Search srch;
   public Replace repl;
   protected AbstractExpression ae;

   public static Expression parse(String str) {
      return ExpRuleFactory.getDefaultRule().parse(str);
   }

   public void setVariable(Variable var) {
      this.var = var;
   }

   public void setFunction(Function func) {
      this.func = func;
   }

   public void setOperator(Operator oper) {
      this.oper = oper;
   }

   public abstract long evalLong();

   public abstract double evalDouble();

   public abstract Object eval();

   public abstract void optimizeLong(Variable var1);

   public abstract void optimizeDouble(Variable var1);

   public abstract void optimize(Variable var1, Operator var2);

   public abstract void search(Search var1);

   public abstract void refactorName(Refactor var1);

   public abstract void refactorFunc(Refactor var1, Rule var2);

   public abstract Expression dup();

   @Override
   public boolean equals(Object obj) {
      if (obj instanceof Expression) {
         AbstractExpression e = ((Expression)obj).ae;
         if (this.ae == null && e == null) {
            return true;
         } else {
            return this.ae != null && e != null ? this.ae.equals(e) : false;
         }
      } else {
         return super.equals(obj);
      }
   }

   @Override
   public int hashCode() {
      return this.ae == null ? 0 : this.ae.hashCode();
   }

   public boolean same(Expression obj) {
      AbstractExpression e = obj.ae;
      return this.ae == null ? e == null : this.ae.same(e);
   }

   public boolean isEmpty() {
      return this.ae == null;
   }

   @Override
   public String toString() {
      return this.ae == null ? "" : this.ae.toString();
   }
}
