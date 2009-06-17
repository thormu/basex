package org.basex.query.ft;

import static org.basex.query.QueryTokens.*;
import org.basex.query.IndexContext;
import org.basex.query.QueryContext;
import org.basex.query.QueryException;
import org.basex.query.item.FTItem;
import org.basex.util.IntList;

/**
 * FTOr expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
public final class FTOr extends FTExpr {
  /**
   * Constructor.
   * @param e expression list
   */
  public FTOr(final FTExpr[] e) {
    super(e);
  }

  @Override
  public FTItem atomic(final QueryContext ctx) throws QueryException {
    FTItem it = null; 
    for(final FTExpr e : expr) {
      final FTItem i = e.atomic(ctx);
      if(it != null) {
        it.all.or(i.all);
        it.score(ctx.score.or(it.score(), i.score()));
      } else {
        it = i;
      }
    }
    return it;
  }

  @Override
  public String toString() {
    return toString(" " + FTOR + " ");
  }

  
  
  // [CG] FT: to be revised...
  
  /** Index of positive expressions. */
  private int[] pex;
  /** Index of negative (ftnot) expressions. */
  private int[] nex;
  
  @Override
  public boolean indexAccessible(final IndexContext ic) throws QueryException {
    // [CG] FT: skip index access
    if(1 == 1) return false;

    final IntList ip = new IntList();
    final IntList in = new IntList();
    final int min = ic.is;
    int sum = 0;

    for(int i = 0; i < expr.length; i++) {
      final boolean ftnot = ic.ftnot;
      ic.ftnot = false;
      final boolean ia = expr[i].indexAccessible(ic);
      final boolean ftn = ic.ftnot;
      ic.ftnot = ftnot;
      if(!ia) return false;

      if(ftn) {
        if(ic.is > 0) in.add(i);
        else {
          ic.seq = true;
          ic.is = Integer.MAX_VALUE;
          return false;
        }
      } else if(ic.is > 0) {
        ip.add(i);
        sum += ic.is;
      }
    }
    nex = in.finish();
    pex = ip.finish();

    if(pex.length == 0 && nex.length > 0) {
      ic.seq = true;
      ic.is = Integer.MAX_VALUE;
    } else if(nex.length > 0 && pex.length > 0) {
      ic.seq = true;
      ic.is = Integer.MAX_VALUE;
      /* [SG] find solution here
       *
       * Will get complicated for arbitrarily mixed and nested pos./neg.
       * expressions..  A | !(B & (!C & D)) etc.
       *
       * Approach from the relational world (but not really worth the trouble):
       * Normalization to DNF/CNF.
       */
      return false;
    } else {
      ic.is = sum > min ? min : sum;
    } 
    return true;
  }
  
  @Override
  public FTExpr indexEquivalent(final IndexContext ic) throws QueryException {
    for(int i = 0; i < expr.length; i++) expr[i] = expr[i].indexEquivalent(ic);

    if(pex.length == 0) {
      // !A FTOR !B = !(a ftand b)
      FTExpr[] nexpr = new FTExpr[nex.length];
      for(int i = 0; i < nex.length; i++) nexpr[i] = expr[nex[i]].expr[0];
      return new FTNotIndex(new FTIntersection(pex, nex, nexpr));
    }

    // [SG] is never the case..
    //if(pex.length == 0) return new FTUnion(nex, true, expr);

    if(nex.length == 0) return pex.length == 1 ?
        expr[pex[0]] : new FTUnion(pex, false, expr);
    
    return new FTUnion(gen(), true, expr);
  }
  
  /**
   * Generate sequence for nex.length > 0 && pex.length > 0.
   * @return sequence
   */
  private int[] gen() {
    final int[] r = new int[expr.length];
    for(int i = 0; i < expr.length; i++) r[i] = i;
    return r;
  }
}
