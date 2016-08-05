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
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.SimSolverBuilder;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.SolverUser;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver.EventType;
import com.github.rinde.rinsim.central.rt.RtSimSolverBuilder;
import com.github.rinde.rinsim.central.rt.RtSolverUser;
import com.github.rinde.rinsim.central.rt.RtStAdapters;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.VehicleState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 *
 * @author Rinde van Lon
 */
public final class RtSolverRoutePlanner extends AbstractRoutePlanner
    implements RtSolverUser {

  Deque<Parcel> route;
  Optional<PDPRoadModel> pdpRoadModel;
  Optional<RtSimSolver> simSolver;
  private final RealtimeSolver solver;

  RtSolverRoutePlanner(RealtimeSolver s) {
    route = newLinkedList();
    solver = s;
    simSolver = Optional.absent();
    pdpRoadModel = Optional.absent();
  }

  @Override
  protected void afterInit() {
    pdpRoadModel = Optional.of((PDPRoadModel) roadModel.get());
  }

  @Override
  protected void doUpdate(Set<Parcel> onMap, long time) {
    if (onMap.isEmpty()
      && pdpModel.get().getContents(vehicle.get()).isEmpty()) {
      route.clear();
    } else {
      final Set<Parcel> toRemove = new LinkedHashSet<Parcel>();
      // remove all parcels which are no longer assigned to this routeplanner
      for (final Parcel p : route) {
        if (!onMap.contains(p)
          && !pdpModel.get().getParcelState(p).isPickedUp()
          && !pdpModel.get().getParcelState(p).isTransitionState()) {
          toRemove.add(p);
        }
      }

      LOGGER.trace("route {}", route);
      route.removeAll(toRemove);
      LOGGER.trace("to remove: {}", toRemove);

      @Nullable
      final Parcel destination =
        pdpRoadModel.get().getDestinationToParcel(vehicle.get());

      if (pdpModel.get().getVehicleState(vehicle.get()) != VehicleState.IDLE
        || destination != null) {
        // vehicle is picking up or delivering -> make sure that first item in
        // route is the parcel that is being serviced
        final Parcel next = destination == null
          ? pdpModel.get().getVehicleActionInfo(vehicle.get()).getParcel()
          : destination;
        if (!route.peek().equals(next)) {
          // remove first occurrence
          route.removeFirstOccurrence(next);
          route.addFirst(next);
        }
      }

      final Iterable<Parcel> newRoute =
        RouteFollowingVehicle.delayAdjuster().adjust(route,
          (RouteFollowingVehicle) vehicle.get());

      route.clear();
      Iterables.addAll(route, newRoute);

      final GlobalStateObject gso = simSolver.get().getCurrentState(
        SolveArgs.create()
          .useParcels(onMap)
          .fixRoutes()
          .useCurrentRoutes(ImmutableList.of(ImmutableList.copyOf(route))));

      final Optional<Parcel> dest = gso.getVehicles().get(0).getDestination();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("destination {}", dest);
        if (dest.isPresent()) {
          LOGGER.trace("parcel state {}",
            pdpModel.get().getParcelState(dest.get()));
        }
      }

      simSolver.get().solve(gso);
    }
  }

  @Override
  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Override
  public Optional<Parcel> current() {
    return Optional.fromNullable(route.peek());
  }

  @Override
  public Optional<ImmutableList<Parcel>> currentRoute() {
    if (route.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(ImmutableList.copyOf(route));
  }

  @Override
  protected void nextImpl(long time) {
    route.poll();
  }

  @Override
  public void setSolverProvider(RtSimSolverBuilder builder) {
    simSolver = Optional.of(builder.setVehicles(vehicle.asSet()).build(solver));
    final RtSolverRoutePlanner rp = this;
    simSolver.get().getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        route = newLinkedList(simSolver.get().getCurrentSchedule().get(0));
        LOGGER.trace("Computed new route for {}: {}.", vehicle.get(), route);
        rp.dispatchChangeEvent();
      }
    }, EventType.NEW_SCHEDULE);
  }

  public static StochasticSupplier<RoutePlanner> supplier(
      StochasticSupplier<? extends RealtimeSolver> solver) {
    return new RtSup(solver);
  }

  public static StochasticSupplier<RoutePlanner> simulatedTimeSupplier(
      final StochasticSupplier<? extends Solver> solver) {
    return new StSup(solver);
  }

  static String toStringHelper(String methodName, Object obj) {
    return Joiner.on("").join(RtSolverRoutePlanner.class.getSimpleName(),
      ".", methodName, "(", obj, ")");
  }

  static class StSolverRoutePlanner extends ForwardingRoutePlanner
      implements SolverUser {
    RtSolverRoutePlanner delegate;
    SolverUser stAdapter;

    StSolverRoutePlanner(RtSolverRoutePlanner deleg) {
      delegate = deleg;
      stAdapter = RtStAdapters.toSimTime(deleg);
    }

    @Override
    protected RtSolverRoutePlanner delegate() {
      return delegate;
    }

    @Override
    public void setSolverProvider(SimSolverBuilder builder) {
      stAdapter.setSolverProvider(builder);
    }

    @Override
    public String toString() {
      return "ST" + delegate().toString();
    }
  }

  static final class StSup implements StochasticSupplier<RoutePlanner> {
    final StochasticSupplier<? extends Solver> solver;

    StSup(StochasticSupplier<? extends Solver> s) {
      solver = s;
    }

    @Override
    public RoutePlanner get(long seed) {
      return new StSolverRoutePlanner(new RtSolverRoutePlanner(
        RtStAdapters.toRealtime(solver.get(seed))));
    }

    @Override
    public String toString() {
      return toStringHelper("simulatedTimeSupplier", solver);
    }
  }

  static final class RtSup implements StochasticSupplier<RoutePlanner> {
    final StochasticSupplier<? extends RealtimeSolver> solver;

    RtSup(StochasticSupplier<? extends RealtimeSolver> s) {
      solver = s;
    }

    @Override
    public RoutePlanner get(long seed) {
      return new RtSolverRoutePlanner(solver.get(seed));
    }

    @Override
    public String toString() {
      return toStringHelper("supplier", solver);
    }
  }

}
