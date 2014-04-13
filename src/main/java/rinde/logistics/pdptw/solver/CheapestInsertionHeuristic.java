package rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Iterator;
import java.util.Set;

import rinde.opt.localsearch.Insertions;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.GlobalStateObject.VehicleStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRngs;
import rinde.sim.util.SupplierRngs.AbstractSupplierRng;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class CheapestInsertionHeuristic implements Solver {

  private final ObjectiveFunction objectiveFunction;

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
   * @return A {@link SupplierRng} that supplies
   *         {@link CheapestInsertionHeuristic} instances.
   */
  public static SupplierRng<Solver> supplier(final ObjectiveFunction objFunc) {
    return new SupplierRngs.AbstractSupplierRng<Solver>() {
      @Override
      public Solver get(long seed) {
        return new CheapestInsertionHeuristic(objFunc);
      }
    };
  }

}
