package mcheli.agnostic.eval.eval.rule;

import mcheli.agnostic.eval.eval.exp.AbstractExpression;
import mcheli.agnostic.eval.eval.exp.Col1Expression;
import mcheli.agnostic.eval.eval.lex.Lex;

public class SignRule extends AbstractRule {
   public SignRule(ShareRuleValue share) {
      super(share);
   }

   @Override
   public AbstractExpression parse(Lex lex) {
      switch (lex.getType()) {
         case 2147483634:
            String ope = lex.getOperator();
            if (this.isMyOperator(ope)) {
               int pos = lex.getPos();
               return Col1Expression.create(this.newExpression(ope, lex.getShare()), lex.getString(), pos, this.parse(lex.next()));
            }

            return this.nextRule.parse(lex);
         default:
            return this.nextRule.parse(lex);
      }
   }
}
