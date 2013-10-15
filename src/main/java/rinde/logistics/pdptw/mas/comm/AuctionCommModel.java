/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;

import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

/**
 * A communication model that supports auctions.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class AuctionCommModel extends AbstractCommModel<Bidder> {

  /**
   * New instance.
   */
  public AuctionCommModel() {}

  @Override
  protected void receiveParcel(DefaultParcel p, long time) {
    checkState(!communicators.isEmpty(), "there are no bidders..");
    final Iterator<Bidder> it = communicators.iterator();
    Bidder bestBidder = it.next();
    // if there are no other bidders, there is no need to organize an
    // auction at all (mainly used in test cases)
    if (it.hasNext()) {
      double bestValue = bestBidder.getBidFor(p, time);

      while (it.hasNext()) {
        final Bidder cur = it.next();
        final double curValue = cur.getBidFor(p, time);
        if (curValue < bestValue) {
          bestValue = curValue;
          bestBidder = cur;
        }
      }
    }
    bestBidder.receiveParcel(p);
  }

  public static SupplierRng<AuctionCommModel> supplier() {
    return new DefaultSupplierRng<AuctionCommModel>() {
      @Override
      public AuctionCommModel get(long seed) {
        return new AuctionCommModel();
      }
    };
  }
}
