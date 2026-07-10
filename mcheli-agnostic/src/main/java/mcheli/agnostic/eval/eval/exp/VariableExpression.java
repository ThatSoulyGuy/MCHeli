package mcheli.agnostic.eval.eval.exp;

import mcheli.agnostic.eval.eval.EvalException;
import mcheli.agnostic.eval.eval.lex.Lex;

public class VariableExpression extends WordExpression {
   public static AbstractExpression create(Lex lex, int prio) {
      AbstractExpression exp = new VariableExpression(lex.getWord());
      exp.setPos(lex.getString(), lex.getPos());
      exp.setPriority(prio);
      exp.share = lex.getShare();
      return exp;
   }

   public VariableExpression(String str) {
      super(str);
   }

   protected VariableExpression(VariableExpression from, ShareExpValue s) {
      super(from, s);
   }

   @Override
   public AbstractExpression dup(ShareExpValue s) {
      return new VariableExpression(this, s);
   }

   @Override
   public long evalLong() {
      try {
         return this.share.var.evalLong(this.getVarValue());
      } catch (EvalException e) {
         throw e;
      } catch (Exception e) {
         throw new EvalException(2003, this.word, this.string, this.pos, e);
      }
   }

   @Override
   public double evalDouble() {
      try {
         return this.share.var.evalDouble(this.getVarValue());
      } catch (EvalException e) {
         throw e;
      } catch (Exception e) {
         throw new EvalException(2003, this.word, this.string, this.pos, e);
      }
   }

   @Override
   public Object evalObject() {
      return this.getVarValue();
   }

   @Override
   protected void let(Object val, int pos) {
      String name = this.getWord();

      try {
         this.share.var.setValue(name, val);
      } catch (EvalException e) {
         throw e;
      } catch (Exception e) {
         throw new EvalException(2102, name, this.string, pos, e);
      }
   }

   private Object getVarValue() {
      String word = this.getWord();

      Object val;
      try {
         val = this.share.var.getObject(word);
      } catch (EvalException e) {
         throw e;
      } catch (Exception e) {
         throw new EvalException(2101, word, this.string, this.pos, e);
      }

      if (val == null) {
         throw new EvalException(2103, word, this.string, this.pos, null);
      } else {
         return val;
      }
   }

   @Override
   protected Object getVariable() {
      try {
         return this.share.var.getObject(this.word);
      } catch (EvalException e) {
         throw e;
      } catch (Exception e) {
         throw new EvalException(2002, this.word, this.string, this.pos, null);
      }
   }
}
