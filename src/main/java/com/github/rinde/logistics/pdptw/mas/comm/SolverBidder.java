/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import java.util.Queue;
import java.util.Set;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.SimSolver;
import com.github.rinde.rinsim.central.SimSolverBuilder;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.central.SolverUser;
import com.github.rinde.rinsim.central.SolverValidator;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.Solvers.StateContext;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A {@link Bidder} that uses a {@link Solver} for computing the bid value.
 * @author Rinde van Lon
 */
public class SolverBidder extends AbstractBidder<DoubleBid>
    implements SolverUser {

  private final ObjectiveFunction objectiveFunction;
  private final Solver solver;
  private Optional<SimSolver> solverHandle;

  /**
   * Creates a new bidder using the specified solver and objective function.
   * @param objFunc The {@link ObjectiveFunction} to use to calculate the bid
   *          value.
   * @param s The solver used to compute the (near) optimal schedule when
   *          calculating a bid.
   */
  public SolverBidder(ObjectiveFunction objFunc, Solver s) {
    objectiveFunction = objFunc;
    solver = s;
    solverHandle = Optional.absent();
  }

  @Override
  public void callForBids(Auctioneer<DoubleBid> auctioneer, Parcel p,
      long time) {
    LOGGER.info("{} callForBids {}", this, p);
    checkState(solverHandle.isPresent(),
      "A %s could not be obtained, probably missing a %s.",
      Solver.class.getSimpleName(),
      SolverModel.class.getSimpleName());

    final Set<Parcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(p);
    final ImmutableList<Parcel> currentRoute = ImmutableList
        .copyOf(((Truck) vehicle.get()).getRoute());
    LOGGER.trace(" > currentRoute {}", currentRoute);
    final StateContext context = solverHandle.get().convert(
      SolveArgs.create().noCurrentRoutes().useParcels(parcels));
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
      context.state, ImmutableList.of(currentRoute)));

    // make sure that all parcels in the route are always in the available
    // parcel list when needed. This is needed to satisfy the solver.
    for (final Parcel dp : currentRoute) {
      if (!pdpModel.get().getParcelState(dp).isPickedUp()) {
        parcels.add(dp);
      }
    }

    // check whether the RoutePlanner produces routes compatible with the solver
    final SolveArgs args = SolveArgs.create().useParcels(parcels)
        .useCurrentRoutes(ImmutableList.of(currentRoute));
    try {
      final GlobalStateObject gso = solverHandle.get().convert(args).state;
      SolverValidator.checkRoute(gso.getVehicles().get(0), 0);
    } catch (final IllegalArgumentException e) {
      args.noCurrentRoutes();
    }
    // if the route is not compatible, don't use routes at all
    final Queue<Parcel> newRoute = solverHandle.get().solve(args).get(0);
    final double newCost = objectiveFunction.computeCost(Solvers.computeStats(
      context.state, ImmutableList.of(ImmutableList.copyOf(newRoute))));

    auctioneer.submit(DoubleBid.create(time, this, p, newCost - baseline));
  }

  @Override
  public void endOfAuction(Auctioneer<DoubleBid> auctioneer, Parcel p,
      long time) {}

  @Override
  public void setSolverProvider(SimSolverBuilder builder) {
    solverHandle = Optional.of(builder.setVehicle(vehicle.get()).build(solver));
  }

  /**
   * Creates a new {@link SolverBidder} supplier.
   * @param objFunc The objective function to use.
   * @param solverSupplier The solver to use.
   * @return A supplier of {@link SolverBidder} instances.
   */
  public static StochasticSupplier<SolverBidder> supplier(
      final ObjectiveFunction objFunc,
      final StochasticSupplier<? extends Solver> solverSupplier) {
    return new AbstractStochasticSupplier<SolverBidder>() {
      private static final long serialVersionUID = -3290309520168516504L;

      @Override
      public SolverBidder get(long seed) {
        return new SolverBidder(objFunc, solverSupplier.get(seed));
      }

      @Override
      public String toString() {
        return super.toString() + "-" + solverSupplier.toString();
      }
    };
  }
}
