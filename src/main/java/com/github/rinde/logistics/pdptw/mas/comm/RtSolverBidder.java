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
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Set;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver.EventType;
import com.github.rinde.rinsim.central.rt.RtSimSolverBuilder;
import com.github.rinde.rinsim.central.rt.RtSolverModel;
import com.github.rinde.rinsim.central.rt.RtSolverUser;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class RtSolverBidder
    extends AbstractBidder<DoubleBid>
    implements RtSolverUser {

  private final RealtimeSolver solver;
  final ObjectiveFunction objectiveFunction;
  Optional<RtSimSolver> solverHandle;

  RtSolverBidder(ObjectiveFunction objFunc, RealtimeSolver s) {
    objectiveFunction = objFunc;
    solver = s;
    solverHandle = Optional.absent();
  }

  @Override
  public void callForBids(final Auctioneer<DoubleBid> auctioneer,
      final Parcel parcel, final long time) {

    // avoid multiple bids at the same time
    checkState(solverHandle.isPresent(),
      "A %s could not be obtained, probably missing a %s.",
      RealtimeSolver.class.getSimpleName(),
      RtSolverModel.class.getSimpleName());

    final Set<Parcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(parcel);
    final ImmutableList<Parcel> currentRoute = ImmutableList
        .copyOf(((Truck) vehicle.get()).getRoute());

    final GlobalStateObject state = solverHandle.get().getCurrentState(
      SolveArgs.create().noCurrentRoutes().useParcels(parcels));
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
      state, ImmutableList.of(currentRoute)));

    final Bidder<DoubleBid> bidder = this;
    final Listener listener = new Listener() {
      @Override
      public void handleEvent(Event e) {
        // submit bid using baseline
        final ImmutableList<ImmutableList<Parcel>> schedule =
          solverHandle.get().getCurrentSchedule();
        final double cost =
          objectiveFunction.computeCost(Solvers.computeStats(state, schedule));
        auctioneer
            .submit(DoubleBid.create(time, bidder, parcel, cost - baseline));
      }
    };

    solverHandle.get().getEventAPI().addListener(listener,
      EventType.NEW_SCHEDULE);

    solverHandle.get().solve(SolveArgs.create()
        .useCurrentRoutes(ImmutableList.of(currentRoute))
        .useParcels(parcels));
  }

  @Override
  public void setSolverProvider(RtSimSolverBuilder builder) {
    solverHandle = Optional.of(builder.setVehicles(vehicle.asSet())
        .build(solver));
  }

  public static StochasticSupplier<RtSolverBidder> supplier(
      ObjectiveFunction objFunc,
      StochasticSupplier<? extends RealtimeSolver> solverSupplier) {
    return new AutoValue_RtSolverBidder_Sup(objFunc, solverSupplier);
  }

  @AutoValue
  abstract static class Sup implements StochasticSupplier<RtSolverBidder> {

    abstract ObjectiveFunction getObjectiveFunction();

    abstract StochasticSupplier<? extends RealtimeSolver> getSolverSupplier();

    @Override
    public RtSolverBidder get(long seed) {
      return new RtSolverBidder(getObjectiveFunction(),
          getSolverSupplier().get(seed));
    }

    @Override
    public String toString() {
      return RtSolverBidder.class.getSimpleName() + ".supplier(..)";
    }
  }
}
