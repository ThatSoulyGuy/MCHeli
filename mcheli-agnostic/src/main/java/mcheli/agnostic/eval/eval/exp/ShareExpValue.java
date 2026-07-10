package mcheli.agnostic.eval.eval.exp;

import mcheli.agnostic.eval.eval.Expression;
import mcheli.agnostic.eval.eval.Rule;
import mcheli.agnostic.eval.eval.func.InvokeFunction;
import mcheli.agnostic.eval.eval.oper.JavaExOperator;
import mcheli.agnostic.eval.eval.oper.Operator;
import mcheli.agnostic.eval.eval.ref.Refactor;
import mcheli.agnostic.eval.eval.repl.Replace;
import mcheli.agnostic.eval.eval.srch.Search;
import mcheli.agnostic.eval.eval.var.MapVariable;
import mcheli.agnostic.eval.eval.var.Variable;

public class ShareExpValue extends Expression {
   public AbstractExpression paren;

   public void setAbstractExpression(AbstractExpression ae) {
      this.ae = ae;
   }

   public void initVar() {
      if (this.var == null) {
         this.var = new MapVariable();
      }
   }

   public void initOper() {
      if (this.oper == null) {
         this.oper = new JavaExOperator();
      }
   }

   public void initFunc() {
      if (this.func == null) {
         this.func = new InvokeFunction();
      }
   }

   @Override
   public long evalLong() {
      this.initVar();
      this.initFunc();
      return this.ae.evalLong();
   }

   @Override
   public double evalDouble() {
      this.initVar();
      this.initFunc();
      return this.ae.evalDouble();
   }

   @Override
   public Object eval() {
      this.initVar();
      this.initOper();
      this.initFunc();
      return this.ae.evalObject();
   }

   @Override
   public void optimizeLong(Variable var) {
      this.optimize(var, new OptimizeLong());
   }

   @Override
   public void optimizeDouble(Variable var) {
      this.optimize(var, new OptimizeDouble());
   }

   @Override
   public void optimize(Variable var, Operator oper) {
      Operator bak = this.oper;
      this.oper = oper;

      try {
         this.optimize(var, new OptimizeObject());
      } finally {
         this.oper = bak;
      }
   }

   protected void optimize(Variable var, Replace repl) {
      Variable bak = this.var;
      if (var == null) {
         var = new MapVariable();
      }

      this.var = var;
      this.repl = repl;

      try {
         this.ae = this.ae.replace();
      } finally {
         this.var = bak;
      }
   }

   @Override
   public void search(Search srch) {
      if (srch == null) {
         throw new NullPointerException();
      }

      this.srch = srch;
      this.ae.search();
   }

   @Override
   public void refactorName(Refactor ref) {
      if (ref == null) {
         throw new NullPointerException();
      }

      this.srch = new Search4RefactorName(ref);
      this.ae.search();
   }

   @Override
   public void refactorFunc(Refactor ref, Rule rule) {
      if (ref == null) {
         throw new NullPointerException();
      }

      this.repl = new Replace4RefactorGetter(ref, rule);
      this.ae.replace();
   }

   @Override
   public boolean same(Expression obj) {
      if (!(obj instanceof ShareExpValue)) {
         return false;
      }

      AbstractExpression p = ((ShareExpValue)obj).paren;
      return this.paren.same(p) && super.same(obj);
   }

   @Override
   public Expression dup() {
      ShareExpValue n = new ShareExpValue();
      n.ae = this.ae.dup(n);
      n.paren = this.paren.dup(n);
      return n;
   }
}
