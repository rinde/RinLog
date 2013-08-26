/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Collections.unmodifiableSet;

import java.util.Collection;
import java.util.Set;

import rinde.sim.event.Event;
import rinde.sim.event.EventDispatcher;
import rinde.sim.event.Listener;
import rinde.sim.pdptw.common.DefaultParcel;

/**
 * Basic implementation for {@link Bidder}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public abstract class AbstractBidder implements Bidder {

  protected final Set<DefaultParcel> assignedParcels;
  protected final EventDispatcher eventDispatcher;

  /**
   * Initializes bidder.
   */
  public AbstractBidder() {
    assignedParcels = newLinkedHashSet();
    eventDispatcher = new EventDispatcher(CommunicatorEventType.values());
  }

  public void addUpdateListener(Listener l) {
    eventDispatcher.addListener(l, CommunicatorEventType.CHANGE);
  }

  // ignore
  public void waitFor(DefaultParcel p) {}

  public void claim(DefaultParcel p) {
    checkArgument(assignedParcels.contains(p));
    assignedParcels.remove(p);
  }

  public final Collection<DefaultParcel> getParcels() {
    return unmodifiableSet(assignedParcels);
  }

  public void receiveParcel(DefaultParcel p) {
    assignedParcels.add(p);
    eventDispatcher
        .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }
}
