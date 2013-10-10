/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;

import java.util.List;

import rinde.sim.core.model.Model;
import rinde.sim.core.model.ModelProvider;
import rinde.sim.core.model.ModelReceiver;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.PDPModelEventType;
import rinde.sim.core.model.pdp.PDPModelEvent;
import rinde.sim.event.Event;
import rinde.sim.event.Listener;
import rinde.sim.pdptw.common.DefaultParcel;

/**
 * This class provides a common base for classes that implement a communication
 * strategy between a set of {@link Communicator}s. There are currently two
 * implementations, blackboard communication ({@link BlackboardCommModel}) and
 * auctioning ({@link AuctionCommModel}).
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * @param <T> The type of {@link Communicator} this model expects.
 */
public abstract class AbstractCommModel<T extends Communicator> implements
    ModelReceiver, Model<T> {
  /**
   * The list of registered communicators.
   */
  protected List<T> communicators;

  /**
   * New instance.
   */
  protected AbstractCommModel() {
    communicators = newArrayList();
  }

  @Override
  public void registerModelProvider(ModelProvider mp) {
    final PDPModel pm = mp.getModel(PDPModel.class);
    checkState(pm != null);
    pm.getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        final PDPModelEvent event = ((PDPModelEvent) e);
        checkArgument(event.parcel instanceof DefaultParcel,
            "This class is only compatible with DefaultParcel and subclasses.");
        final DefaultParcel dp = (DefaultParcel) event.parcel;
        receiveParcel(dp, event.time);
      }
    }, PDPModelEventType.NEW_PARCEL);
  }

  /**
   * Subclasses can define their own parcel handling strategy in this method.
   * @param p The new {@link DefaultParcel} that becomes available.
   * @param time The current time.
   */
  protected abstract void receiveParcel(DefaultParcel p, long time);

  @Override
  public boolean register(final T communicator) {
    communicators.add(communicator);
    return true;
  }

  @Override
  public boolean unregister(T element) {
    throw new UnsupportedOperationException();
  }
}
