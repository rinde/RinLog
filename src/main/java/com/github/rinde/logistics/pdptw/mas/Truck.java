/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner.RoutePlannerEventType;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.SimulatorUser;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.fsm.StateMachine.StateMachineEvent;
import com.github.rinde.rinsim.fsm.StateMachine.StateTransitionEvent;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.google.common.base.Optional;

/**
 * A vehicle entirely controlled by a {@link RoutePlanner} and a
 * {@link Communicator}.
 * @author Rinde van Lon
 */
public class Truck
    extends RouteFollowingVehicle
    implements Listener, SimulatorUser {

  private static final Logger LOGGER = LoggerFactory.getLogger(Truck.class);
  private final RoutePlanner routePlanner;
  private final Communicator communicator;
  private boolean changed;
  private final boolean lazyRouteComputing;

  /**
   * Create a new Truck using the specified {@link RoutePlanner} and
   * {@link Communicator}.
   * @param pDto The truck properties.
   * @param rp The route planner used.
   * @param c The communicator used.
   */
  public Truck(VehicleDTO pDto, RoutePlanner rp, Communicator c,
      RouteAdjuster ra, boolean lazyRouteComp) {
    super(pDto, true, ra);
    routePlanner = rp;
    communicator = c;
    communicator.addUpdateListener(this);
    routePlanner.getEventAPI().addListener(new Listener() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void handleEvent(Event e) {
        LOGGER.trace("routeplanner is done, update route");
        updateRoute();
      }
    }, RoutePlannerEventType.CHANGE);
    stateMachine.getEventAPI().addListener(this,
      StateMachineEvent.STATE_TRANSITION);
    lazyRouteComputing = lazyRouteComp;
    LOGGER.trace("Truck constructor, {}, {}, {}, {}.", rp, c, ra,
      lazyRouteComp);
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
        updateAssignmentAndRoutePlanner();
      } else if (getRoute().isEmpty() && routePlanner.current().isPresent()) {
        updateRoute();
      }
    } else if (changed && isDiversionAllowed()
        && !stateMachine.stateIs(serviceState)) {
      updateAssignmentAndRoutePlanner();
    }
  }

  /**
   * Updates the {@link RoutePlanner} with the assignment of the
   * {@link Communicator}.
   */
  protected void updateAssignmentAndRoutePlanner() {
    changed = false;

    routePlanner.update(communicator.getParcels(), getCurrentTime().getTime());
    final Optional<Parcel> cur = routePlanner.current();
    if (cur.isPresent()) {
      communicator.waitFor(cur.get());
    }
  }

  /**
   * Updates the route based on the {@link RoutePlanner}.
   */
  protected void updateRoute() {
    if (routePlanner.current().isPresent()) {
      setRoute(routePlanner.currentRoute().get());
    } else {
      setRoute(new LinkedList<Parcel>());
    }
  }

  @Override
  public void handleEvent(Event e) {
    LOGGER.trace("{} - Event: {}", this, e);
    if (e.getEventType() == CommunicatorEventType.CHANGE) {
      changed = true;
      if (!lazyRouteComputing) {
        updateAssignmentAndRoutePlanner();
      }
    } else {
      // we know this is safe since it can only be one type of event
      @SuppressWarnings("unchecked")
      final StateTransitionEvent<StateEvent, RouteFollowingVehicle> event =
        (StateTransitionEvent<StateEvent, RouteFollowingVehicle>) e;

      // when diverting -> unclaim previous
      if ((event.trigger == DefaultEvent.REROUTE
          || event.trigger == DefaultEvent.NOGO)
          && !getPDPModel().getParcelState(gotoState.getPreviousDestination())
              .isPickedUp()) {
        communicator.unclaim(gotoState.getPreviousDestination());
      }

      if (event.trigger == DefaultEvent.GOTO
          || event.trigger == DefaultEvent.REROUTE) {
        final Parcel cur = getRoute().iterator().next();
        if (!getPDPModel().getParcelState(cur).isPickedUp()) {
          LOGGER.trace("{} claim:{}", this, cur);
          communicator.claim(cur);
        }
      } else if (event.trigger == DefaultEvent.DONE) {
        communicator.done();
        if (changed) {
          updateAssignmentAndRoutePlanner();
        } else {
          routePlanner.next(getCurrentTime().getTime());
        }
      }

      if ((event.newState == waitState
          || (isDiversionAllowed() && event.newState != serviceState))
          && changed) {
        updateAssignmentAndRoutePlanner();
      }
    }
  }

  /**
   * @return The {@link Communicator} of this {@link Truck}.
   */
  public Communicator getCommunicator() {
    return communicator;
  }

  /**
   * @return The {@link RoutePlanner} of this {@link Truck}.
   */
  public RoutePlanner getRoutePlanner() {
    return routePlanner;
  }

  @Override
  public void setSimulator(SimulatorAPI api) {
    try {
      api.register(communicator);
    } catch (final IllegalArgumentException e) {}
    try {
      api.register(routePlanner);
    } catch (final IllegalArgumentException e) {}
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .addValue(Integer.toHexString(hashCode()))
        .add("rp", routePlanner)
        .add("c", communicator)
        .toString();
  }
}
