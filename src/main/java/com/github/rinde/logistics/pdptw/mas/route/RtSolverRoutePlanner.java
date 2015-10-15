/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import java.util.Collection;
import java.util.Queue;

import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver.EventType;
import com.github.rinde.rinsim.central.rt.RtSimSolverBuilder;
import com.github.rinde.rinsim.central.rt.RtSolverUser;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public final class RtSolverRoutePlanner extends AbstractRoutePlanner
    implements RtSolverUser {

  private Queue<? extends Parcel> route;
  private final RealtimeSolver solver;
  private Optional<RtSimSolver> simSolver;

  RtSolverRoutePlanner(RealtimeSolver s) {
    route = newLinkedList();
    solver = s;
    simSolver = Optional.absent();
  }

  @Override
  protected void doUpdate(Collection<Parcel> onMap, long time) {
    if (onMap.isEmpty()
        && pdpModel.get().getContents(vehicle.get()).isEmpty()) {
      route.clear();
    } else {
      simSolver.get()
          .solve(SolveArgs.create()
              .useParcels(onMap)
              .useCurrentRoutes(ImmutableList.of(ImmutableList.copyOf(route))));
    }
  }

  @Override
  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Override
  public Optional<Parcel> current() {
    return Optional.fromNullable((Parcel) route.peek());
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
    simSolver.get().getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        route = newLinkedList(simSolver.get().getCurrentSchedule().get(0));
      }
    }, EventType.NEW_SCHEDULE);
  }

  public static StochasticSupplier<RoutePlanner> supplier(
      StochasticSupplier<? extends RealtimeSolver> solver) {
    return new Sup(solver);
  }

  static class Sup implements StochasticSupplier<RoutePlanner> {
    StochasticSupplier<? extends RealtimeSolver> solver;

    Sup(StochasticSupplier<? extends RealtimeSolver> s) {
      solver = s;
    }

    @Override
    public RoutePlanner get(long seed) {
      return new RtSolverRoutePlanner(solver.get(seed));
    }

    @Override
    public String toString() {
      return Joiner.on("").join(RtSolverRoutePlanner.class.getSimpleName(),
        ".supplier(", solver, ")");
    }
  }
}
