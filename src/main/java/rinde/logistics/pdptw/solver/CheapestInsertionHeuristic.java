package rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.List;
import java.util.Set;

import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.GlobalStateObject.VehicleStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

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

  // operations
  // - convert global state object to single vehicle state object
  // - compute objective value of single vehicle state object
  // -

  // iterate over search neighborhood

  // create efficient datastructure for modifying schedules?
  // should allow partial evaluation? i.e. per vehicle obj value. decomposition

  // convert GlobalStateObject to only include single vehicle

  // store objValue per vehicle/route
  // re-evaluate routes that change, and calculate difference

  // how to know what has changed? track insertions?

  // operations:
  // insertion1
  // insertion2
  // deletion1
  // deletion2
  // swap (deletion and insertion)

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    final ImmutableSet<ParcelDTO> newParcels = unassignedParcels(state);

    // construct schedule
    final ImmutableList.Builder<ImmutableList<ParcelDTO>> b = ImmutableList
        .builder();
    for (final VehicleStateObject vso : state.vehicles) {
      if (vso.route.isPresent()) {
        b.add(vso.route.get());
      } else {
        b.add(ImmutableList.<ParcelDTO> of());
      }
    }
    ImmutableList<ImmutableList<ParcelDTO>> schedule = b.build();

    // all new parcels need to be inserted in the plan
    for (final ParcelDTO p : newParcels) {
      double cheapestInsertion = Double.POSITIVE_INFINITY;
      ImmutableList<ImmutableList<ParcelDTO>> bestSchedule = schedule;
      for (int i = 0; i < state.vehicles.size(); i++) {
        final int startIndex = state.vehicles.get(i).destination == null ? 0
            : 1;
        final List<ImmutableList<ParcelDTO>> insertions = Insertions
            .insertions(schedule.get(i), p, startIndex, 2);

        for (int j = 0; j < insertions.size(); j++) {
          final ImmutableList<ParcelDTO> r = insertions.get(j);
          // compute cost using entire schedule
          final ImmutableList<ImmutableList<ParcelDTO>> newSchedule = modifySchedule(
              schedule, r, i);
          final double cost = objectiveFunction.computeCost(Solvers
              .computeStats(state, newSchedule));
          if (cost < cheapestInsertion) {
            cheapestInsertion = cost;
            bestSchedule = newSchedule;
          }
        }
      }
      schedule = bestSchedule;
    }
    return schedule;
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
    return new DefaultSupplierRng<Solver>() {
      @Override
      public Solver get(long seed) {
        return new CheapestInsertionHeuristic(objFunc);
      }
    };
  }
}
