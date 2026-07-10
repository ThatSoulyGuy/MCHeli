package mcheli.eval.eval.exp;

import mcheli.eval.eval.EvalException;
import mcheli.eval.eval.lex.Lex;
import mcheli.eval.util.NumberUtil;

public class NumberExpression extends WordExpression {
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
      try {
         return Double.parseDouble(this.word);
      } catch (Exception e) {
         try {
            return NumberUtil.parseLong(this.word);
         } catch (Exception e2) {
            throw new EvalException(2003, this.word, this.string, this.pos, e);
         }
      }
   }

   @Override
   public Object evalObject() {
      try {
         return new Long(NumberUtil.parseLong(this.word));
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
