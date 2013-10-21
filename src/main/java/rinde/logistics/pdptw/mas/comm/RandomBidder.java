/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

/**
 * A {@link Bidder} implementation that creates random bids.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class RandomBidder extends AbstractBidder {

  private final RandomGenerator rng;

  /**
   * Create a random bidder using the specified random seed.
   * @param seed The random seed.
   */
  public RandomBidder(long seed) {
    rng = new MersenneTwister(seed);
  }

  @Override
  public double getBidFor(DefaultParcel p, long time) {
    return rng.nextDouble();
  }

  /**
   * @return A {@link SupplierRng} that supplies {@link RandomBidder} instances.
   */
  public static SupplierRng<RandomBidder> supplier() {
    return new DefaultSupplierRng<RandomBidder>() {
      @Override
      public RandomBidder get(long seed) {
        return new RandomBidder(seed);
      }
    };
  }
}
