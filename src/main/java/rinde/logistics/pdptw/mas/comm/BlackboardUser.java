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
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Optional;

/**
 * This {@link Communicator} implementation allows communication via a
 * blackboard system. It requires the {@link BlackboardCommModel}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class BlackboardUser implements Communicator {

  private Optional<BlackboardCommModel> bcModel;
  private final EventDispatcher eventDispatcher;

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

  @Override
  public void waitFor(DefaultParcel p) {}

  /**
   * Lay a claim on the specified {@link DefaultParcel}.
   * @param p The parcel to claim.
   */
  @Override
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

  @Override
  public void addUpdateListener(Listener l) {
    eventDispatcher.addListener(l, CommunicatorEventType.CHANGE);
  }

  @Override
  public Collection<DefaultParcel> getParcels() {
    return bcModel.get().getUnclaimedParcels();
  }

  // not needed
  @Override
  public void init(RoadModel rm, PDPModel pm, DefaultVehicle v) {}

  /**
   * @return A {@link SupplierRng} that supplies {@link BlackboardUser}
   *         instances.
   */
  public static SupplierRng<BlackboardUser> supplier() {
    return new DefaultSupplierRng<BlackboardUser>() {
      @Override
      public BlackboardUser get(long seed) {
        return new BlackboardUser();
      }
    };
  }

  @Override
  public void unclaim(DefaultParcel p) {
    throw new UnsupportedOperationException(
        "diversion is not yet supported in this class");
  }

  @Override
  public Collection<DefaultParcel> getClaimedParcels() {
    throw new UnsupportedOperationException();
  }
}
