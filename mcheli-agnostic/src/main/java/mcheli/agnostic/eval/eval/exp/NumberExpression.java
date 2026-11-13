package mcheli.agnostic.eval.eval.exp;

import mcheli.agnostic.eval.eval.EvalException;
import mcheli.agnostic.eval.eval.lex.Lex;
import mcheli.agnostic.eval.util.NumberUtil;

public class NumberExpression extends WordExpression {
   // Port: evalDouble's result is cached. The reference re-parsed the literal on every evaluation (a hex literal even
   // threw and caught a NumberFormatException each time), and the HUD evaluates every literal every frame. The value
   // depends only on the word, so caching changes no result; a literal that fails still throws on every call.
   private boolean doubleCached;
   private double doubleValue;

   public static AbstractExpression create(Lex lex, int prio) {
      AbstractExpression exp = new NumberExpression(lex.getWord());
      exp.setPos(lex.getString(), lex.getPos());
      exp.setPriority(prio);
      exp.share = lex.getShare();
      return exp;
   }

   public NumberExpression(String str) {
      super(str);
   }

   protected NumberExpression(NumberExpression from, ShareExpValue s) {
      super(from, s);
   }

   @Override
   public AbstractExpression dup(ShareExpValue s) {
      return new NumberExpression(this, s);
   }

   public static NumberExpression create(AbstractExpression from, String word) {
      NumberExpression n = new NumberExpression(word);
      n.string = from.string;
      n.pos = from.pos;
      n.prio = from.prio;
      n.share = from.share;
      return n;
   }

   @Override
   public long evalLong() {
      try {
         return NumberUtil.parseLong(this.word);
      } catch (Exception e) {
         try {
            return Long.parseLong(this.word);
         } catch (Exception ex) {
            try {
               return (long)Double.parseDouble(this.word);
            } catch (Exception exx) {
               throw new EvalException(2003, this.word, this.string, this.pos, exx);
            }
         }
      }
   }

   @Override
   public double evalDouble() {
      if (this.doubleCached) {
         return this.doubleValue;
      }

      double val;
      try {
         val = Double.parseDouble(this.word);
      } catch (Exception e) {
         try {
            val = NumberUtil.parseLong(this.word);
         } catch (Exception e2) {
            throw new EvalException(2003, this.word, this.string, this.pos, e);
         }
      }

      this.doubleValue = val;
      this.doubleCached = true;
      return val;
   }

   @Override
   protected void setWord(String word) {
      super.setWord(word);
      this.doubleCached = false;
   }

   @Override
   public Object evalObject() {
      try {
         return Long.valueOf(NumberUtil.parseLong(this.word));
      } catch (Exception e) {
         try {
            return Long.valueOf(this.word);
         } catch (Exception ex) {
            try {
               return Double.valueOf(this.word);
            } catch (Exception exx) {
               throw new EvalException(2003, this.word, this.string, this.pos, exx);
            }
         }
      }
   }
}
