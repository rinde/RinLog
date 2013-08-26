/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import java.util.Collection;

import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.event.Event;
import rinde.sim.event.EventDispatcher;
import rinde.sim.event.Listener;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;

import com.google.common.base.Optional;

/**
 * This {@link Communicator} implementation allows communication via a
 * blackboard system. It requires the {@link BlackboardCommModel}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class BlackboardUser implements Communicator {

  protected Optional<BlackboardCommModel> bcModel;
  protected final EventDispatcher eventDispatcher;

  /**
   * Constructor.
   */
  public BlackboardUser() {
    eventDispatcher = new EventDispatcher(CommunicatorEventType.values());
    bcModel = Optional.absent();
  }

  /**
   * @param model Injects the {@link BlackboardCommModel}.
   */
  public void init(BlackboardCommModel model) {
    bcModel = Optional.of(model);
  }

  public void waitFor(DefaultParcel p) {}

  /**
   * Lay a claim on the specified {@link DefaultParcel}.
   * @param p The parcel to claim.
   */
  public void claim(DefaultParcel p) {
    // forward call to model
    bcModel.get().claim(this, p);
  }

  /**
   * Notifies this blackboard user of a change in the environment.
   */
  public void update() {
    eventDispatcher
        .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  public void addUpdateListener(Listener l) {
    eventDispatcher.addListener(l, CommunicatorEventType.CHANGE);
  }

  public Collection<DefaultParcel> getParcels() {
    return bcModel.get().getUnclaimedParcels();
  }

  // not needed
  public void init(RoadModel rm, PDPModel pm, DefaultVehicle v) {}

}
