package rinde.logistics.pdptw.mas.comm;

import rinde.sim.event.Event;
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.StochasticSuppliers;
import rinde.sim.util.StochasticSuppliers.AbstractStochasticSupplier;

/**
 * {@link Bidder} implementation that adds some methods for easier testing.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class TestBidder extends RandomBidder {

  /**
   * @param seed Seed for random bidder.
   */
  public TestBidder(long seed) {
    super(seed);
  }

  /**
   * Clears assignedParcels.
   */
  public void removeAll() {
    assignedParcels.clear();
    eventDispatcher
        .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  /**
   * @return A {@link StochasticSupplier} that supplies {@link RandomBidder} instances.
   */
  public static StochasticSupplier<RandomBidder> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<RandomBidder>() {
      @Override
      public TestBidder get(long seed) {
        return new TestBidder(seed);
      }
    };
  }
}
