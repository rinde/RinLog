package rinde.logistics.pdptw.mas.comm;

import rinde.sim.event.Event;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

public class TestBidder extends RandomBidder {

  public TestBidder(long seed) {
    super(seed);
  }

  public void removeAll() {
    assignedParcels.clear();
    eventDispatcher
        .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  /**
   * @return A {@link SupplierRng} that supplies {@link RandomBidder} instances.
   */
  public static SupplierRng<RandomBidder> supplier() {
    return new DefaultSupplierRng<RandomBidder>() {
      @Override
      public TestBidder get(long seed) {
        return new TestBidder(seed);
      }
    };
  }

}
