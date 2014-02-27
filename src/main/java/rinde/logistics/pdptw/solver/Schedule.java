package rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Iterator;
import java.util.List;

import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class Schedule {

  private final GlobalStateObject state;
  private final ImmutableList<ImmutableList<ParcelDTO>> routes;
  private final ImmutableList<Double> objectiveValues;
  private final ObjectiveFunction objectiveFunction;
  private final double objectiveValue;

  public Schedule(GlobalStateObject s,
      ImmutableList<ImmutableList<ParcelDTO>> r, ImmutableList<Double> ovs,
      ObjectiveFunction of, double ov) {

    state = s;
    routes = r;
    objectiveValues = ovs;
    objectiveFunction = of;
    objectiveValue = ov;
  }

  // input for a swap: parcel + vehicle + new_vehicle + new_insertion_locations
  // output: obj value increase
  Optional<Schedule> swap(ParcelDTO parcelToSwap, int vehicle, int newVehicle,
      ImmutableList<Integer> insertionIndices) {

    if (vehicle == newVehicle) {
      // 1. swap within same vehicle
      // compute cost of original ordering
      // compute cost of new ordering
      // return new - original
      final double originalCost = objectiveValues.get(vehicle);

      final ImmutableList<ParcelDTO> newRoute = inListSwap(routes.get(vehicle),
          insertionIndices, parcelToSwap);
      final double newCost = objectiveFunction.computeCost(Solvers
          .computeStats(state.withSingleVehicle(vehicle),
              ImmutableList.of(newRoute)));

      final double diff = newCost - originalCost;

      if (diff < 0) {
        // it improves
        // return Optional.of(new Schedule());
        return Optional.absent();
      } else {
        return Optional.absent();
      }
    } else {
      // 2. swap between vehicles

      // compute cost of removal from original vehicle
      final double originalCostA = objectiveValues.get(vehicle);
      final ImmutableList<ParcelDTO> newRouteA = ImmutableList.copyOf(filter(
          routes.get(vehicle), not(equalTo(parcelToSwap))));
      final double newCostA = objectiveFunction.computeCost(Solvers
          .computeStats(state.withSingleVehicle(vehicle),
              ImmutableList.of(newRouteA)));
      final double diffA = newCostA - originalCostA;

      // compute cost of insertion in new vehicle
      final double originalCostB = objectiveValues.get(newVehicle);
      final ImmutableList<ParcelDTO> newRouteB = Insertions.insert(
          routes.get(newVehicle), insertionIndices, parcelToSwap);
      final double newCostB = objectiveFunction.computeCost(Solvers
          .computeStats(state.withSingleVehicle(newVehicle),
              ImmutableList.of(newRouteB)));

      final double diffB = newCostB - originalCostB;

      final double diff = diffA + diffB;

      if (diff < 0) {
        // return Optional.of(new Schedule());
        return Optional.absent();
      } else {
        return Optional.absent();
      }
    }
  }

  /**
   * Moves the occurrences of <code>item</code> to their new positions. This
   * does not change the relative ordering of any other items in the list.
   * @param originalList The original list that will be swapped.
   * @param insertionIndices The indices where item should be inserted relative
   *          to the positions of the <code>originalList</code> <b>without</b>
   *          <code>item</code>. The number of indices must equal the number of
   *          occurrences of item in the original list.
   * @param item The item to swap.
   * @return The swapped list.
   * @throws IllegalArgumentException if an attempt is made to move the item to
   *           the previous location(s), this would have no effect and is
   *           therefore considered a bug.
   */
  public static <T> ImmutableList<T> inListSwap(ImmutableList<T> originalList,
      ImmutableList<Integer> insertionIndices, T item) {
    checkArgument(!originalList.isEmpty(), "The list may not be empty.");
    final List<T> newList = newArrayList(originalList);
    final List<Integer> indices = removeAll(newList, item);
    checkArgument(
        newList.size() == originalList.size() - insertionIndices.size(),
        "The number of occurrences (%s) of item should equal the number of insertionIndices (%s), original list: %s, item %s, insertionIndices %s.",
        indices.size(), insertionIndices.size(), originalList, item,
        insertionIndices);
    checkArgument(
        !indices.equals(insertionIndices),
        "Attempt to move the item to exactly the same locations as the input. Indices in original list %s, insertion indices %s.",
        indices, insertionIndices);
    return Insertions.insert(newList, insertionIndices, item);
  }

  /**
   * Removes all items from list and returns the indices of the removed items.
   * @param list The list to remove items from.
   * @param item The item to remove from the list.
   * @return The indices of the removed items, or an empty list if the item was
   *         not found in list.
   */
  public static <T> ImmutableList<Integer> removeAll(List<T> list, T item) {
    final Iterator<T> it = list.iterator();
    final ImmutableList.Builder<Integer> builder = ImmutableList.builder();
    int i = 0;
    while (it.hasNext()) {
      if (it.next().equals(item)) {
        it.remove();
        builder.add(i);
      }
      i++;
    }
    return builder.build();
  }
}
