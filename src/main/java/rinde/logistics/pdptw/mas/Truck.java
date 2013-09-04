/**
 * 
 */
package rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkState;

import java.util.Set;

import javax.annotation.Nullable;

import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.event.Event;
import rinde.sim.event.Listener;
import rinde.sim.pdptw.common.DefaultDepot;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;
import rinde.sim.pdptw.common.VehicleDTO;
import rinde.sim.util.fsm.AbstractState;
import rinde.sim.util.fsm.StateMachine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class Truck extends DefaultVehicle implements Listener {

  private enum TruckEvent {
    DONE;
  }

  @VisibleForTesting
  final StateMachine<TruckEvent, Truck> stateMachine;
  private final RoutePlanner routePlanner;
  private final Communicator communicator;
  private TimeLapse currentTime;
  private boolean changed;

  private Optional<DefaultDepot> depot;

  /**
   * Create a new Truck using the specified {@link RoutePlanner} and
   * {@link Communicator}.
   * @param pDto The truck properties.
   * @param rp The route planner used.
   * @param c The communicator used.
   */
  public Truck(VehicleDTO pDto, RoutePlanner rp, Communicator c) {
    super(pDto);
    routePlanner = rp;
    communicator = c;
    communicator.addUpdateListener(this);
    depot = Optional.absent();

    final AbstractTruckState wait = new Wait();
    final AbstractTruckState go = new Goto();
    final AbstractTruckState service = new Service();
    stateMachine = StateMachine.create(wait)/* */
    .addTransition(wait, TruckEvent.DONE, go)/* */
    .addTransition(go, TruckEvent.DONE, service)/* */
    .addTransition(service, TruckEvent.DONE, wait)/* */
    .build();
  }

  @Override
  public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
    super.initRoadPDP(pRoadModel, pPdpModel);

    final RoadModel rm = roadModel.get();
    final PDPModel pm = pdpModel.get();

    routePlanner.init(rm, pm, this);
    communicator.init(rm, pm, this);

    final Set<DefaultDepot> depots = rm.getObjectsOfType(DefaultDepot.class);
    checkState(depots.size() == 1,
        "This truck can only deal with problems with a single depot.");
    depot = Optional.of(depots.iterator().next());
  }

  @Override
  protected void tickImpl(TimeLapse time) {
    currentTime = time;
    stateMachine.handle(this);
  }

  protected boolean isTooEarly(Parcel p) {
    final boolean isPickup = pdpModel.get().getParcelState(p) != ParcelState.IN_CARGO;
    final Point loc = isPickup ? ((DefaultParcel) p).dto.pickupLocation : p
        .getDestination();
    final long travelTime = (long) ((Point.distance(loc, roadModel.get()
        .getPosition(this)) / 30d) * 3600000d);
    long timeUntilAvailable = (isPickup ? p.getPickupTimeWindow().begin : p
        .getDeliveryTimeWindow().begin) - currentTime.getStartTime();

    final long remainder = timeUntilAvailable % currentTime.getTimeStep();
    if (remainder > 0) {
      timeUntilAvailable += currentTime.getTimeStep() - remainder;
    }
    return timeUntilAvailable - travelTime > 0;
  }

  protected boolean isEndOfDay() {
    return currentTime.hasTimeLeft()
        && currentTime.getTime() > dto.availabilityTimeWindow.end
            - ((Point.distance(roadModel.get().getPosition(this),
                dto.startPosition) / getSpeed()) * 3600000);
  }

  abstract class AbstractTruckState extends AbstractState<TruckEvent, Truck> {}

  class Wait extends AbstractTruckState {
    @Nullable
    @Override
    public TruckEvent handle(TruckEvent event, Truck context) {
      if (changed) {
        changed = false;
        routePlanner.update(communicator.getParcels(), currentTime.getTime());
        communicator.waitFor(routePlanner.current().get());
      }

      final Optional<DefaultParcel> cur = routePlanner.current();
      if (cur.isPresent() && !isTooEarly(cur.get())) {
        return TruckEvent.DONE;
      }

      if (!cur.isPresent() && isEndOfDay()
          && !roadModel.get().equalPosition(context, depot.get())) {
        roadModel.get().moveTo(context, depot.get(), context.currentTime);
      }
      return null;
    }
  }

  class Goto extends AbstractTruckState {

    Optional<DefaultParcel> cur = Optional.absent();

    @Override
    public void onEntry(TruckEvent event, Truck context) {
      cur = routePlanner.current();
      if (pdpModel.get().getParcelState(cur.get()) != ParcelState.IN_CARGO) {
        communicator.claim(cur.get());
      }
    }

    @Nullable
    @Override
    public TruckEvent handle(TruckEvent event, Truck context) {
      // move to service location
      roadModel.get().moveTo(context, cur.get(), currentTime);
      if (roadModel.get().equalPosition(context, cur.get())) {
        return TruckEvent.DONE;
      }
      return null;
    }
  }

  class Service extends AbstractTruckState {
    @Override
    public void onEntry(TruckEvent event, Truck context) {
      final Parcel cur = routePlanner.current().get();
      pdpModel.get().service(context, cur, currentTime);
      routePlanner.next(currentTime.getTime());
    }

    @Nullable
    @Override
    public TruckEvent handle(TruckEvent event, Truck context) {
      if (context.currentTime.hasTimeLeft()) {
        return TruckEvent.DONE;
      }
      return null;
    }
  }

  @Override
  public void handleEvent(@SuppressWarnings("null") Event e) {
    if (e.getEventType() == CommunicatorEventType.CHANGE) {
      changed = true;
    }
  }

}
