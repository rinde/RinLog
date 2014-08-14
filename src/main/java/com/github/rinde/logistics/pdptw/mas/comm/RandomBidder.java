/**
 * 
 */
package com.github.rinde.logistics.pdptw.mas.comm;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;

/**
 * A {@link Bidder} implementation that creates random bids.
 * @author Rinde van Lon 
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
   * @return A {@link StochasticSupplier} that supplies {@link RandomBidder} instances.
   */
  public static StochasticSupplier<RandomBidder> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<RandomBidder>() {
      @Override
      public RandomBidder get(long seed) {
        return new RandomBidder(seed);
      }
    };
  }
}
