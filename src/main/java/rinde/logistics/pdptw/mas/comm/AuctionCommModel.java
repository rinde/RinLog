/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.StochasticSuppliers;
import rinde.sim.util.StochasticSuppliers.AbstractStochasticSupplier;

/**
 * A communication model that supports auctions.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class AuctionCommModel extends AbstractCommModel<Bidder> {
  private static final double TOLERANCE = .0001;
  private final RandomGenerator rng;

  /**
   * New instance.
   * @param seed The seed to use for the random number generator.
   */
  public AuctionCommModel(long seed) {
    rng = new MersenneTwister(seed);
  }

  @Override
  protected void receiveParcel(DefaultParcel p, long time) {
    checkState(!communicators.isEmpty(), "there are no bidders..");
    final Iterator<Bidder> it = communicators.iterator();
    final List<Bidder> bestBidders = newArrayList();
    bestBidders.add(it.next());

    // if there are no other bidders, there is no need to organize an
    // auction at all (mainly used in test cases)
    if (it.hasNext()) {
      double bestValue = bestBidders.get(0).getBidFor(p, time);
      while (it.hasNext()) {
        final Bidder cur = it.next();
        final double curValue = cur.getBidFor(p, time);
        if (curValue < bestValue) {
          bestValue = curValue;
          bestBidders.clear();
          bestBidders.add(cur);
        } else if (Math.abs(curValue - bestValue) < TOLERANCE) {
          bestBidders.add(cur);
        }
      }
    }

    if (bestBidders.size() > 1) {
      bestBidders.get(rng.nextInt(bestBidders.size())).receiveParcel(p);
    } else {
      bestBidders.get(0).receiveParcel(p);
    }
  }

  /**
   * @return A {@link StochasticSupplier} that supplies {@link AuctionCommModel}
   *         instances.
   */
  public static StochasticSupplier<AuctionCommModel> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<AuctionCommModel>() {
      @Override
      public AuctionCommModel get(long seed) {
        return new AuctionCommModel(seed);
      }
    };
  }
}
