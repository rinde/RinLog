package com.github.rinde.logistics.pdptw.mas.comm;

import com.github.rinde.logistics.pdptw.mas.comm.Bidder;
import com.github.rinde.logistics.pdptw.mas.comm.RandomBidder;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;

/**
 * {@link Bidder} implementation that adds some methods for easier testing.
 * @author Rinde van Lon 
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
