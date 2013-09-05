/**
 * 
 */
package rinde.logistics.pdptw.mas;

import static java.util.Arrays.asList;

import java.util.LinkedList;

import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.event.Event;
import rinde.sim.event.Listener;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.RouteFollowingVehicle;
import rinde.sim.pdptw.common.VehicleDTO;
import rinde.sim.util.fsm.StateMachine.StateMachineEvent;
import rinde.sim.util.fsm.StateMachine.StateTransitionEvent;

import com.google.common.base.Optional;

/**
 * A vehicle entirely controlled by a {@link RoutePlanner} and a
 * {@link Communicator}.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class Truck extends RouteFollowingVehicle implements Listener {

  private final RoutePlanner routePlanner;
  private final Communicator communicator;
  private boolean changed;

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
    stateMachine.getEventAPI().addListener(this,
        StateMachineEvent.STATE_TRANSITION);
  }

  @Override
  public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
    super.initRoadPDP(pRoadModel, pPdpModel);
    routePlanner.init(pRoadModel, pPdpModel, this);
    communicator.init(pRoadModel, pPdpModel, this);
  }

  @Override
  protected void preTick(TimeLapse time) {
    if (stateMachine.stateIs(waitState)) {
      if (changed) {
        changed = false;
        routePlanner.update(communicator.getParcels(), getCurrentTime()
            .getTime());
        final Optional<DefaultParcel> cur = routePlanner.current();
        if (cur.isPresent()) {
          communicator.waitFor(cur.get());
        }
      }

      final Optional<DefaultParcel> cur = routePlanner.current();
      if (cur.isPresent()) {
        setRoute(asList(cur.get()));
      } else {
        setRoute(new LinkedList<DefaultParcel>());
      }
    }
  }

  @Override
  public void handleEvent(Event e) {
    if (e.getEventType() == CommunicatorEventType.CHANGE) {
      changed = true;
    } else {
      // we know this is safe since it can only be one type of event
      @SuppressWarnings("unchecked")
      final StateTransitionEvent<StateEvent, RouteFollowingVehicle> event = (StateTransitionEvent<StateEvent, RouteFollowingVehicle>) e;
      if (event.event == StateEvent.GOTO) {
        final DefaultParcel cur = getRoute().iterator().next();
        if (pdpModel.get().getParcelState(cur) != ParcelState.IN_CARGO) {
          communicator.claim(cur);
        }
      } else if (event.event == StateEvent.DONE) {
        routePlanner.next(getCurrentTime().getTime());
      }
    }
  }
}
