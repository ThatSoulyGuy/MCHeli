package mcheli.agnostic.eval.eval.rule;

import mcheli.agnostic.eval.eval.exp.AbstractExpression;
import mcheli.agnostic.eval.eval.exp.Col2Expression;
import mcheli.agnostic.eval.eval.lex.Lex;

public class Col2RightJoinRule extends AbstractRule {
   public Col2RightJoinRule(ShareRuleValue share) {
      super(share);
   }

   @Override
   protected AbstractExpression parse(Lex lex) {
      AbstractExpression x = this.nextRule.parse(lex);
      switch (lex.getType()) {
         case 2147483634:
            String ope = lex.getOperator();
            if (this.isMyOperator(ope)) {
               int pos = lex.getPos();
               AbstractExpression y = this.parse(lex.next());
               x = Col2Expression.create(this.newExpression(ope, lex.getShare()), lex.getString(), pos, x, y);
            }

            return x;
         default:
            return x;
      }
   }
}
