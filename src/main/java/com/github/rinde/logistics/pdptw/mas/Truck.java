/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.github.rinde.rinsim.event.EventAPI;
import com.github.rinde.rinsim.event.EventDispatcher;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.fsm.StateMachine.StateMachineEvent;
import com.github.rinde.rinsim.fsm.StateMachine.StateTransitionEvent;
import com.github.rinde.rinsim.geom.GeomHeuristic;
import com.github.rinde.rinsim.geom.GeomHeuristics;
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
  private final AtomicBoolean routePlannerChanged;
  private final boolean lazyRouteComputing;
  private final EventDispatcher eventDispatcher;
  private final Event routeChangedEvent;

  /**
   * Create a new Truck using the specified {@link RoutePlanner} and
   * {@link Communicator}.
   * @param pDto The truck properties.
   * @param rp The route planner used.
   * @param c The communicator used.
   * @param ra Route adjuster.
   * @param lazyRouteComp If true, lazy route computing is on.
   */
  public Truck(VehicleDTO pDto, RoutePlanner rp, Communicator c,
      RouteAdjuster ra, boolean lazyRouteComp) {
    this(pDto, rp, c, ra, lazyRouteComp, GeomHeuristics.euclidean());
  }

  /**
   * Create a new Truck using the specified {@link RoutePlanner} and
   * {@link Communicator}.
   * @param pDto The truck properties.
   * @param rp The route planner used.
   * @param c The communicator used.
   * @param ra Route adjuster.
   * @param lazyRouteComp If true, lazy route computing is on.
   * @param geomHeuristic The heuristic used for route resolution.
   */
  public Truck(VehicleDTO pDto, RoutePlanner rp, Communicator c,
      RouteAdjuster ra, boolean lazyRouteComp, GeomHeuristic geomHeuristic) {
    super(pDto, true, ra, geomHeuristic);
    routePlanner = rp;
    communicator = c;
    communicator.addUpdateListener(this);
    routePlanner.getEventAPI().addListener(new Listener() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void handleEvent(Event e) {
        LOGGER.trace("routeplanner computed a new route, update route");
        routePlannerChanged.set(true);
      }
    }, RoutePlannerEventType.CHANGE);
    stateMachine.getEventAPI().addListener(this,
      StateMachineEvent.STATE_TRANSITION);
    lazyRouteComputing = lazyRouteComp;
    routePlannerChanged = new AtomicBoolean();
    eventDispatcher = new EventDispatcher(TruckEvent.values());
    routeChangedEvent = new Event(TruckEvent.ROUTE_CHANGE, this);
    LOGGER.trace("Truck constructor, {}, {}, {}, {}.", rp, c, ra,
      lazyRouteComp);

  }

  public enum TruckEvent {
    ROUTE_CHANGE
  }

  @Override
  public void initRoadPDP(RoadModel pRoadModel, PDPModel pPdpModel) {
    super.initRoadPDP(pRoadModel, pPdpModel);
    routePlanner.init(pRoadModel, pPdpModel, this);
    communicator.init(pRoadModel, pPdpModel, this);
  }

  @Override
  protected void preTick(TimeLapse time) {
    boolean updated = false;
    if (stateMachine.stateIs(waitState)) {
      if (changed) {
        updateAssignmentAndRoutePlanner();
      } else if (getRoute().isEmpty() && routePlanner.current().isPresent()) {
        updated = true;
        updateRoute();
      }
    } else if (changed && isDiversionAllowed()
      && !stateMachine.stateIs(serviceState)) {
      updateAssignmentAndRoutePlanner();
    }

    if (!updated) {
      checkRoutePlanner();
    }
  }

  void checkRoutePlanner() {
    if (routePlannerChanged.getAndSet(false)) {
      updateRoute();
    }
  }

  /**
   * Updates the {@link RoutePlanner} with the assignment of the
   * {@link Communicator}.
   */
  protected void updateAssignmentAndRoutePlanner() {
    LOGGER.trace("{} updateAssignmentAndRoutePlanner()", this);
    changed = false;

    routePlanner.update(communicator.getParcels(), getCurrentTime());
    final Optional<Parcel> cur = routePlanner.current();
    if (cur.isPresent()) {
      communicator.waitFor(cur.get());
    }
  }

  public EventAPI getEventAPI() {
    return eventDispatcher.getPublicEventAPI();
  }

  /**
   * Updates the route based on the {@link RoutePlanner}.
   */
  protected void updateRoute() {
    LOGGER.trace("{} updateRoute() {}", this, communicator.getParcels());

    final Collection<Parcel> curRoute = getRoute();

    if (routePlanner.current().isPresent()) {
      LOGGER.trace("{}", routePlanner.currentRoute());
      setRoute(routePlanner.currentRoute().get());
      if (getRoute().isEmpty()
        && !routePlanner.currentRoute().get().isEmpty()) {
        updateAssignmentAndRoutePlanner();
      }
    } else {
      setRoute(new LinkedList<Parcel>());
    }

    if (!curRoute.equals(getRoute())) {
      eventDispatcher.dispatchEvent(routeChangedEvent);
    }
  }

  @Override
  public void handleEvent(Event e) {
    LOGGER.trace("{} - Event: {}. Route:{}.", this, e, getRoute());
    // checkRoutePlanner();

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
        final Parcel prev = gotoState.getPreviousDestination();
        if (communicator.getClaimedParcels().contains(prev)) {
          communicator.unclaim(prev);
        } else {
          LOGGER.warn("Cannot unclaim {} because it wasn't claimed.", prev);
        }
      }

      if (event.trigger == DefaultEvent.GOTO
        || event.trigger == DefaultEvent.REROUTE) {
        final Parcel cur = getRoute().iterator().next();
        if (!getPDPModel().getParcelState(cur).isPickedUp()) {
          LOGGER.trace("{} claim:{}", this, cur);

          if (communicator.getParcels().contains(cur)) {
            communicator.claim(cur);
          } else {
            LOGGER.warn("Attempt to visit parcel that is not assigned to me.");
            final List<Parcel> currentRoute = new ArrayList<>(getRoute());
            currentRoute.removeAll(Collections.singleton(cur));
            setRoute(currentRoute);
            LOGGER.warn("Removed parcel from route:{}.", cur);
          }
        }
      } else if (event.trigger == DefaultEvent.DONE) {
        communicator.done();
        if (changed) {
          updateAssignmentAndRoutePlanner();
        } else {
          routePlanner.next(getCurrentTimeLapse().getTime());
        }
      }

      if ((event.newState == waitState
        || isDiversionAllowed() && event.newState != serviceState)
        && changed) {
        updateAssignmentAndRoutePlanner();
      }
    }

    checkRoutePlanner();
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
    } catch (final IllegalArgumentException e) {
      // may not be supported
      LOGGER.info(e.getMessage());
    }
    try {
      api.register(routePlanner);
    } catch (final IllegalArgumentException e) {
      // may not be supported
      LOGGER.info(e.getMessage());
    }
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
