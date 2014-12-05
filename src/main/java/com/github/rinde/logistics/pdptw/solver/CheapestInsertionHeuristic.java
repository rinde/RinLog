/*
 * Copyright (C) 2013-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
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
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Iterator;
import java.util.Set;

import com.github.rinde.opt.localsearch.Insertions;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * An implementation of a cheapest insertion heuristic.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
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

  static ImmutableSet<ParcelDTO> unassignedParcels(GlobalStateObject state) {
    final Set<ParcelDTO> set = newLinkedHashSet(state.availableParcels);
    for (final VehicleStateObject vso : state.vehicles) {
      if (vso.route.isPresent()) {
        set.removeAll(vso.route.get());
      }
    }
    return ImmutableSet.copyOf(set);
  }

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    return decomposed(state);
  }

  static ImmutableList<ImmutableList<ParcelDTO>> createSchedule(
      GlobalStateObject state) {
    final ImmutableList.Builder<ImmutableList<ParcelDTO>> b = ImmutableList
        .builder();
    for (final VehicleStateObject vso : state.vehicles) {
      if (vso.route.isPresent()) {
        b.add(vso.route.get());
      } else {
        b.add(ImmutableList.<ParcelDTO> of());
      }
    }
    return b.build();
  }

  ImmutableList<Double> decomposedCost(GlobalStateObject state,
      ImmutableList<ImmutableList<ParcelDTO>> schedule) {
    final ImmutableList.Builder<Double> builder = ImmutableList.builder();
    for (int i = 0; i < schedule.size(); i++) {
      builder.add(objectiveFunction.computeCost(Solvers.computeStats(
          state.withSingleVehicle(i), ImmutableList.of(schedule.get(i)))));
    }
    return builder.build();
  }

  @SuppressWarnings("null")
  ImmutableList<ImmutableList<ParcelDTO>> decomposed(GlobalStateObject state) {
    ImmutableList<ImmutableList<ParcelDTO>> schedule = createSchedule(state);
    ImmutableList<Double> costs = decomposedCost(state, schedule);
    final ImmutableSet<ParcelDTO> newParcels = unassignedParcels(state);
    // all new parcels need to be inserted in the plan
    for (final ParcelDTO p : newParcels) {
      double cheapestInsertion = Double.POSITIVE_INFINITY;
      ImmutableList<ParcelDTO> cheapestRoute = null;
      double cheapestRouteCost = 0;
      int cheapestRouteIndex = -1;

      for (int i = 0; i < state.vehicles.size(); i++) {
        final int startIndex = state.vehicles.get(i).destination == null ? 0
            : 1;
        final Iterator<ImmutableList<ParcelDTO>> insertions = Insertions
            .insertionsIterator(schedule.get(i), p, startIndex, 2);

        while (insertions.hasNext()) {
          final ImmutableList<ParcelDTO> r = insertions.next();
          final double absCost = objectiveFunction.computeCost(Solvers
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
      schedule = modifySchedule(schedule, cheapestRoute, cheapestRouteIndex);
      costs = modifyCosts(costs, cheapestRouteCost, cheapestRouteIndex);
    }
    return schedule;
  }

  static ImmutableList<Double> modifyCosts(ImmutableList<Double> costs,
      double newCost, int index) {
    return ImmutableList.<Double> builder().addAll(costs.subList(0, index))
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

  static <T> ImmutableList<ImmutableList<T>> createEmptySchedule(int numVehicles) {
    final ImmutableList.Builder<ImmutableList<T>> builder = ImmutableList
        .builder();
    for (int i = 0; i < numVehicles; i++) {
      builder.add(ImmutableList.<T> of());
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
    };
  }

}
