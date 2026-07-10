package mcheli.agnostic.eval.eval.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import mcheli.agnostic.eval.eval.exp.AbstractExpression;
import mcheli.agnostic.eval.eval.exp.ParenExpression;
import mcheli.agnostic.eval.eval.exp.ShareExpValue;
import mcheli.agnostic.eval.eval.lex.Lex;

public abstract class AbstractRule {
   public AbstractRule nextRule;
   protected ShareRuleValue share;
   private final Map opes = new HashMap();
   public int prio;

   public AbstractRule(ShareRuleValue share) {
      this.share = share;
   }

   public final void addExpression(AbstractExpression exp) {
      if (exp != null) {
         String ope = exp.getOperator();
         this.addOperator(ope, exp);
         this.addLexOperator(exp.getEndOperator());
         if (exp instanceof ParenExpression) {
            this.share.paren = exp;
         }
      }
   }

   public final void addOperator(String ope, AbstractExpression exp) {
      this.opes.put(ope, exp);
      this.addLexOperator(ope);
   }

   public final String[] getOperators() {
      List list = new ArrayList();
      Iterator i = this.opes.keySet().iterator();

      while (i.hasNext()) {
         list.add(i.next());
      }

      return (String[])list.toArray(new String[list.size()]);
   }

   public final void addLexOperator(String ope) {
      if (ope != null) {
         int n = ope.length() - 1;
         if (this.share.opeList[n] == null) {
            this.share.opeList[n] = new ArrayList();
         }

         this.share.opeList[n].add(ope);
      }
   }

   protected final boolean isMyOperator(String ope) {
      return this.opes.containsKey(ope);
   }

   protected final AbstractExpression newExpression(String ope, ShareExpValue share) {
      try {
         AbstractExpression org = (AbstractExpression)this.opes.get(ope);
         AbstractExpression n = org.dup(share);
         n.setPriority(this.prio);
         n.share = share;
         return n;
      } catch (RuntimeException e) {
         throw e;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   public final void initPriority(int prio) {
      this.prio = prio;
      if (this.nextRule != null) {
         this.nextRule.initPriority(prio + 1);
      }
   }

   protected abstract AbstractExpression parse(Lex var1);
}
