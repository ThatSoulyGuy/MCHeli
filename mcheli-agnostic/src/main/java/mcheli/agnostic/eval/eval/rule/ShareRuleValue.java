package mcheli.agnostic.eval.eval.rule;

import java.util.List;
import mcheli.agnostic.eval.eval.EvalException;
import mcheli.agnostic.eval.eval.Expression;
import mcheli.agnostic.eval.eval.Rule;
import mcheli.agnostic.eval.eval.exp.AbstractExpression;
import mcheli.agnostic.eval.eval.exp.ShareExpValue;
import mcheli.agnostic.eval.eval.lex.Lex;
import mcheli.agnostic.eval.eval.lex.LexFactory;
import mcheli.agnostic.eval.eval.oper.Operator;
import mcheli.agnostic.eval.eval.ref.Refactor;
import mcheli.agnostic.eval.eval.srch.Search;
import mcheli.agnostic.eval.eval.var.Variable;

public class ShareRuleValue extends Rule {
   public AbstractRule topRule;
   public AbstractRule funcArgRule;
   public LexFactory lexFactory;
   protected List[] opeList = new List[4];
   public AbstractExpression paren;

   @Override
   public Expression parse(String str) {
      if (str == null) {
         return null;
      }

      if (str.trim().length() <= 0) {
         return new ShareRuleValue.EmptyExpression();
      }

      ShareExpValue exp = new ShareExpValue();
      AbstractExpression x = this.parse(str, exp);
      exp.setAbstractExpression(x);
      return exp;
   }

   public AbstractExpression parse(String str, ShareExpValue exp) {
      if (str == null) {
         return null;
      } else {
         Lex lex = this.lexFactory.create(str, this.opeList, this, exp);
         lex.check();
         AbstractExpression x = this.topRule.parse(lex);
         if (lex.getType() != Integer.MAX_VALUE) {
            throw new EvalException(1005, lex);
         } else {
            return x;
         }
      }
   }

   class EmptyExpression extends Expression {
      @Override
      public long evalLong() {
         return 0L;
      }

      @Override
      public double evalDouble() {
         return 0.0;
      }

      @Override
      public Object eval() {
         return null;
      }

      @Override
      public void optimizeLong(Variable var) {
      }

      @Override
      public void optimizeDouble(Variable var) {
      }

      @Override
      public void optimize(Variable var, Operator oper) {
      }

      @Override
      public void search(Search srch) {
      }

      @Override
      public void refactorName(Refactor ref) {
      }

      @Override
      public void refactorFunc(Refactor ref, Rule rule) {
      }

      @Override
      public Expression dup() {
         return ShareRuleValue.this.new EmptyExpression();
      }

      @Override
      public boolean same(Expression obj) {
         return obj instanceof ShareRuleValue.EmptyExpression;
      }

      @Override
      public String toString() {
         return "";
      }
   }
}
