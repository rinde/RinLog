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
package com.github.rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import java.util.Iterator;

import com.github.rinde.opt.localsearch.Insertions;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjects;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * An implementation of a cheapest insertion heuristic.
 * @author Rinde van Lon
 */
public class CheapestInsertionHeuristic implements Solver {

  private final ObjectiveFunction objectiveFunction;

  /**
   * Creates a new instance.
   * @param objFunc The objective function used to calculate the cost of a
   *          schedule.
   */
  public CheapestInsertionHeuristic(ObjectiveFunction objFunc) {
    objectiveFunction = objFunc;
  }

  @Override
  public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
      throws InterruptedException {
    return decomposed(state, objectiveFunction);
  }

  /**
   * Static method variant of cheapest insertion heuristic.
   * @param state The state that specifies the problem to be solved. All
   *          unassigned parcels will be inserted using the cheapest insertion
   *          cost heuristic.
   * @param objFunc The objective function to use to determine the cost.
   * @return A list of routes, one for every vehicle in the GlobalStateObject.
   * @throws InterruptedException When the computation is interrupted.
   */
  public static ImmutableList<ImmutableList<Parcel>> solve(
      GlobalStateObject state, ObjectiveFunction objFunc)
          throws InterruptedException {
    return decomposed(state, objFunc);
  }

  static ImmutableList<ImmutableList<Parcel>> createSchedule(
      GlobalStateObject state) {
    final ImmutableList.Builder<ImmutableList<Parcel>> b = ImmutableList
      .builder();
    for (final VehicleStateObject vso : state.getVehicles()) {
      if (vso.getRoute().isPresent()) {
        b.add(vso.getRoute().get());
      } else {
        b.add(ImmutableList.<Parcel>of());
      }
    }
    return b.build();
  }

  static ImmutableList<Double> decomposedCost(GlobalStateObject state,
      ImmutableList<ImmutableList<Parcel>> schedule,
      ObjectiveFunction objFunc) {
    final ImmutableList.Builder<Double> builder = ImmutableList.builder();
    for (int i = 0; i < schedule.size(); i++) {
      builder.add(objFunc.computeCost(Solvers.computeStats(
        state.withSingleVehicle(i), ImmutableList.of(schedule.get(i)))));
    }
    return builder.build();
  }

  static ImmutableList<ImmutableList<Parcel>> decomposed(
      GlobalStateObject state, ObjectiveFunction objFunc)
          throws InterruptedException {
    ImmutableList<ImmutableList<Parcel>> schedule = createSchedule(state);
    ImmutableList<Double> costs = decomposedCost(state, schedule, objFunc);
    final ImmutableSet<Parcel> newParcels =
      GlobalStateObjects.unassignedParcels(state);
    // all new parcels need to be inserted in the plan
    for (final Parcel p : newParcels) {
      double cheapestInsertion = Double.POSITIVE_INFINITY;
      ImmutableList<Parcel> cheapestRoute = null;
      double cheapestRouteCost = 0;
      int cheapestRouteIndex = -1;

      for (int i = 0; i < state.getVehicles().size(); i++) {
        final int startIndex = state.getVehicles().get(i).getDestination()
          .isPresent() ? 1 : 0;

        final Iterator<ImmutableList<Parcel>> insertions = Insertions
          .insertionsIterator(schedule.get(i), p, startIndex, 2);

        while (insertions.hasNext()) {
          if (Thread.interrupted()) {
            throw new InterruptedException();
          }

          final ImmutableList<Parcel> r = insertions.next();
          final double absCost = objFunc.computeCost(Solvers
            .computeStats(state.withSingleVehicle(i), ImmutableList.of(r)));

          final double insertionCost = absCost - costs.get(i);
          if (insertionCost < cheapestInsertion) {
            cheapestInsertion = insertionCost;
            cheapestRoute = r;
            cheapestRouteIndex = i;
            cheapestRouteCost = absCost;
          }
        }
      }
      schedule = modifySchedule(schedule, verifyNotNull(cheapestRoute),
        cheapestRouteIndex);
      costs = modifyCosts(costs, cheapestRouteCost, cheapestRouteIndex);
    }
    return schedule;
  }

  static ImmutableList<Double> modifyCosts(ImmutableList<Double> costs,
      double newCost, int index) {
    return ImmutableList.<Double>builder().addAll(costs.subList(0, index))
      .add(newCost).addAll(costs.subList(index + 1, costs.size())).build();
  }

  // replaces one route
  static <T> ImmutableList<ImmutableList<T>> modifySchedule(
      ImmutableList<ImmutableList<T>> originalSchedule,
      ImmutableList<T> vehicleSchedule, int vehicleIndex) {
    checkArgument(vehicleIndex >= 0 && vehicleIndex < originalSchedule.size(),
      "Vehicle index must be >= 0 && < %s, it is %s.",
      originalSchedule.size(), vehicleIndex);
    final ImmutableList.Builder<ImmutableList<T>> builder = ImmutableList
      .builder();
    builder.addAll(originalSchedule.subList(0, vehicleIndex));
    builder.add(vehicleSchedule);
    builder.addAll(originalSchedule.subList(vehicleIndex + 1,
      originalSchedule.size()));
    return builder.build();
  }

  static <T> ImmutableList<ImmutableList<T>> createEmptySchedule(
      int numVehicles) {
    final ImmutableList.Builder<ImmutableList<T>> builder = ImmutableList
      .builder();
    for (int i = 0; i < numVehicles; i++) {
      builder.add(ImmutableList.<T>of());
    }
    return builder.build();
  }

  /**
   * @param objFunc The objective function used to calculate the cost of a
   *          schedule.
   * @return A {@link StochasticSupplier} that supplies
   *         {@link CheapestInsertionHeuristic} instances.
   */
  public static StochasticSupplier<Solver> supplier(
      final ObjectiveFunction objFunc) {
    return new StochasticSuppliers.AbstractStochasticSupplier<Solver>() {
      private static final long serialVersionUID = 992219257352250656L;

      @Override
      public Solver get(long seed) {
        return new CheapestInsertionHeuristic(objFunc);
      }

      @Override
      public String toString() {
        return "CIH(" + objFunc + ")";
      }
    };
  }

}
