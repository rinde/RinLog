/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;

/**
 * A {@link Bidder} implementation that creates random bids.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class RandomBidder extends AbstractBidder {

  protected final RandomGenerator rng;

  /**
   * Create a random bidder using the specified random seed.
   * @param seed The random seed.
   */
  public RandomBidder(long seed) {
    rng = new MersenneTwister(seed);
  }

  public double getBidFor(DefaultParcel p, long time) {
    return rng.nextDouble();
  }

  // not needed
  public void init(RoadModel rm, PDPModel pm, DefaultVehicle v) {}
}
