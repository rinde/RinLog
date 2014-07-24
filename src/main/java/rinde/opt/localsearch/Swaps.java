package rinde.opt.localsearch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.random.RandomAdaptor;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.opt.localsearch.Insertions.InsertionIndexGenerator;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;

/**
 * Class for swap algorithms. Currently supports two variants of 2-opt:
 * <ul>
 * <li>Breadth-first 2-opt search:
 * {@link #bfsOpt2(ImmutableList, ImmutableList, Object, RouteEvaluator)}.</li>
 * <li>Depth-first 2-opt search:
 * {@link #dfsOpt2(ImmutableList, ImmutableList, Object, RouteEvaluator, RandomGenerator)}
 * .</li>
 * </ul>
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class Swaps {

  private Swaps() {}

  /**
   * 2-opt local search procedure for schedules. Performs breadth-first search
   * in 2-swap space, picks <i>best swap</i> and uses that as starting point for
   * next iteration. Stops as soon as there is no improving swap anymore.
   * @param schedule The schedule to improve.
   * @param startIndices Indices indicating which part of the schedule can be
   *          modified. <code>startIndices[j] = n</code> indicates that
   *          <code>schedule[j][n]</code> can be modified but
   *          <code>schedule[j][n-1]</code> not.
   * @param context The context to the schedule, used by the evaluator to
   *          compute the cost of a swap.
   * @param evaluator {@link RouteEvaluator} that can compute the cost of a
   *          single route.
   * @param <C> The context type.
   * @param <T> The route item type (i.e. the locations that are part of a
   *          route).
   * @return An improved schedule (or the input schedule if no improvement could
   *         be made).
   */
  public static <C, T> ImmutableList<ImmutableList<T>> bfsOpt2(
      ImmutableList<ImmutableList<T>> schedule,
      ImmutableList<Integer> startIndices, C context,
      RouteEvaluator<C, T> evaluator) {
    return opt2(schedule, startIndices, context, evaluator, false,
        Optional.<RandomGenerator> absent());
  }

  /**
   * 2-opt local search procedure for schedules. Performs depth-first search in
   * 2-swap space, picks <i>first improving</i> (from random ordering of swaps)
   * swap and uses that as starting point for next iteration. Stops as soon as
   * there is no improving swap anymore.
   * @param schedule The schedule to improve.
   * @param startIndices Indices indicating which part of the schedule can be
   *          modified. <code>startIndices[j] = n</code> indicates that
   *          <code>schedule[j][n]</code> can be modified but
   *          <code>schedule[j][n-1]</code> not.
   * @param context The context to the schedule, used by the evaluator to
   *          compute the cost of a swap.
   * @param evaluator {@link RouteEvaluator} that can compute the cost of a
   *          single route.
   * @param rng The random number generator that is used to randomize the
   *          ordering of the swaps.
   * @param <C> The context type.
   * @param <T> The route item type (i.e. the locations that are part of a
   *          route).
   * @return An improved schedule (or the input schedule if no improvement could
   *         be made).
   */
  public static <C, T> ImmutableList<ImmutableList<T>> dfsOpt2(
      ImmutableList<ImmutableList<T>> schedule,
      ImmutableList<Integer> startIndices, C context,
      RouteEvaluator<C, T> evaluator, RandomGenerator rng) {
    return opt2(schedule, startIndices, context, evaluator, true,
        Optional.of(rng));
  }

  static <C, T> ImmutableList<ImmutableList<T>> opt2(
      ImmutableList<ImmutableList<T>> schedule,
      ImmutableList<Integer> startIndices, C context,
      RouteEvaluator<C, T> evaluator, boolean depthFirst,
      Optional<RandomGenerator> rng) {

    checkArgument(schedule.size() == startIndices.size());

    final Schedule<C, T> baseSchedule = Schedule.create(context, schedule,
        startIndices, evaluator);

    final Map<ImmutableList<T>, Double> routeCostCache = newLinkedHashMap();
    for (int i = 0; i < baseSchedule.routes.size(); i++) {
      routeCostCache.put(baseSchedule.routes.get(i),
          baseSchedule.objectiveValues.get(i));
    }

    Schedule<C, T> bestSchedule = baseSchedule;
    boolean isImproving = true;
    while (isImproving) {
      isImproving = false;

      final Schedule<C, T> curBest = bestSchedule;
      Iterator<Swap<T>> it = swapIterator(curBest);
      if (depthFirst) {
        // randomize ordering of swaps
        final List<Swap<T>> swaps = newArrayList(it);
        Collections.shuffle(swaps, new RandomAdaptor(rng.get()));
        it = swaps.iterator();
      }

      while (it.hasNext()) {
        final Swap<T> swapOperation = it.next();
        final Optional<Schedule<C, T>> newSchedule = Swaps.swap(curBest,
            swapOperation,
            bestSchedule.objectiveValue - curBest.objectiveValue,
            routeCostCache);

        if (newSchedule.isPresent()) {
          isImproving = true;
          bestSchedule = newSchedule.get();
          if (depthFirst) {
            // first improving swap is chosen as new starting point (depth
            // first).
            break;
          }
        }
      }
    }
    return bestSchedule.routes;
  }

  static <C, T> Iterator<Swap<T>> swapIterator(Schedule<C, T> schedule) {
    final ImmutableList.Builder<Iterator<Swap<T>>> iteratorBuilder = ImmutableList
        .builder();
    final Set<T> seen = newLinkedHashSet();
    for (int i = 0; i < schedule.routes.size(); i++) {
      final ImmutableList<T> row = schedule.routes.get(i);
      for (int j = 0; j < row.size(); j++) {
        final T t = row.get(j);
        if (j >= schedule.startIndices.get(i) && !seen.contains(t)) {
          iteratorBuilder.add(oneItemSwapIterator(schedule,
              schedule.startIndices, t, i));
        }
        seen.add(t);
      }
    }
    return Iterators.concat(iteratorBuilder.build().iterator());
  }

  static <C, T> Iterator<Swap<T>> oneItemSwapIterator(Schedule<C, T> schedule,
      ImmutableList<Integer> startIndices, T item, int fromRow) {
    final ImmutableList<Integer> indices = indices(
        schedule.routes.get(fromRow), item);
    final ImmutableList.Builder<Iterator<Swap<T>>> iteratorBuilder = ImmutableList
        .builder();

    Range<Integer> range;
    if (indices.size() == 1) {
      range = Range.closedOpen(fromRow, fromRow + 1);
    } else {
      range = Range.closedOpen(0, schedule.routes.size());
    }

    for (int i = range.lowerEndpoint(); i < range.upperEndpoint(); i++) {
      int rowSize = schedule.routes.get(i).size();
      if (fromRow == i) {
        rowSize -= indices.size();
      }
      Iterator<ImmutableList<Integer>> it = new InsertionIndexGenerator(
          indices.size(), rowSize, startIndices.get(i));
      // filter out swaps that have existing result
      if (fromRow == i) {
        it = Iterators.filter(it, Predicates.not(Predicates.equalTo(indices)));
      }
      iteratorBuilder.add(Iterators.transform(it, new IndexToSwapTransform<T>(
          item, fromRow, i)));
    }
    return Iterators.concat(iteratorBuilder.build().iterator());
  }

  static <C, T> Optional<Schedule<C, T>> swap(Schedule<C, T> s, Swap<T> swap,
      double threshold) {
    return swap(s, swap, threshold,
        new LinkedHashMap<ImmutableList<T>, Double>());
  }

  /**
   * Swap an item from <code>fromRow</code> to <code>toRow</code>. All
   * occurrences are removed from <code>fromRow</code> and will be added in
   * <code>toRow</code> at the specified indices. The modified schedule is only
   * returned if it improves over the specified <code>threshold</code> value.
   * The quality of a schedule is determined by its {@link Schedule#evaluator}.
   * 
   * @param s The schedule to perform the swap on.
   * @param itemToSwap The item to swap.
   * @param fromRow The originating row of the item.
   * @param toRow The destination row for the item.
   * @param insertionIndices The indices where the item will be inserted in the
   *          new row. The number of indices must equal the number of
   *          occurrences of item in the <code>fromRow</code>. If
   *          <code>fromRow == toRow</code> the insertion indices point to the
   *          indices of the row <b>without</b> the original item in it.
   * @param threshold The threshold value which decides whether a schedule is
   *          returned.
   * @return The swapped schedule if the cost of the new schedule is better
   *         (lower) than the threshold, {@link Optional#absent()} otherwise.
   */
  static <C, T> Optional<Schedule<C, T>> swap(Schedule<C, T> s, Swap<T> swap,
      double threshold, Map<ImmutableList<T>, Double> cache) {

    checkArgument(swap.fromRow >= 0 && swap.fromRow < s.routes.size(),
        "fromRow must be >= 0 and < %s, it is %s.", s.routes.size(),
        swap.fromRow);
    checkArgument(swap.toRow >= 0 && swap.toRow < s.routes.size(),
        "toRow must be >= 0 and < %s, it is %s.", s.routes.size(), swap.toRow);

    if (swap.fromRow == swap.toRow) {
      // 1. swap within same vehicle
      // compute cost of original ordering
      // compute cost of new ordering
      final double originalCost = s.objectiveValues.get(swap.fromRow);
      final ImmutableList<T> newRoute = inListSwap(s.routes.get(swap.fromRow),
          swap.toIndices, swap.item);

      final double newCost = computeCost(s, swap.fromRow, newRoute, cache);
      final double diff = newCost - originalCost;

      if (diff < threshold) {
        // it improves
        final ImmutableList<ImmutableList<T>> newRoutes = replace(s.routes,
            ImmutableList.of(swap.fromRow), ImmutableList.of(newRoute));
        final double newObjectiveValue = s.objectiveValue + diff;
        final ImmutableList<Double> newObjectiveValues = replace(
            s.objectiveValues, ImmutableList.of(swap.fromRow),
            ImmutableList.of(newCost));
        return Optional
            .of(Schedule.create(s.context, newRoutes, s.startIndices,
                newObjectiveValues, newObjectiveValue, s.evaluator));
      } else {
        return Optional.absent();
      }
    } else {
      // 2. swap between vehicles

      // compute cost of removal from original vehicle
      final double originalCostA = s.objectiveValues.get(swap.fromRow);
      final ImmutableList<T> newRouteA = ImmutableList.copyOf(filter(
          s.routes.get(swap.fromRow), not(equalTo(swap.item))));
      final int itemCount = s.routes.get(swap.fromRow).size()
          - newRouteA.size();
      checkArgument(
          itemCount > 0,
          "The item (%s) is not in row %s, hence it cannot be swapped to another row.",
          swap.item, swap.fromRow);
      checkArgument(
          itemCount == swap.toIndices.size(),
          "The number of occurences in the fromRow (%s) should equal the number of insertion indices (%s).",
          itemCount, swap.toIndices.size());

      final double newCostA = computeCost(s, swap.fromRow, newRouteA, cache);
      final double diffA = newCostA - originalCostA;

      // compute cost of insertion in new vehicle
      final double originalCostB = s.objectiveValues.get(swap.toRow);
      final ImmutableList<T> newRouteB = Insertions.insert(
          s.routes.get(swap.toRow), swap.toIndices, swap.item);

      final double newCostB = computeCost(s, swap.toRow, newRouteB, cache);
      final double diffB = newCostB - originalCostB;

      final double diff = diffA + diffB;
      if (diff < threshold) {
        final ImmutableList<Integer> rows = ImmutableList.of(swap.fromRow,
            swap.toRow);
        final ImmutableList<ImmutableList<T>> newRoutes = replace(s.routes,
            rows, ImmutableList.of(newRouteA, newRouteB));
        final double newObjectiveValue = s.objectiveValue + diff;
        final ImmutableList<Double> newObjectiveValues = replace(
            s.objectiveValues, rows, ImmutableList.of(newCostA, newCostB));

        return Optional
            .of(Schedule.create(s.context, newRoutes, s.startIndices,
                newObjectiveValues, newObjectiveValue, s.evaluator));
      } else {
        return Optional.absent();
      }
    }
  }

  static <C, T> double computeCost(Schedule<C, T> s, int row,
      ImmutableList<T> newRoute, Map<ImmutableList<T>, Double> cache) {
    if (cache.containsKey(newRoute)) {
      return cache.get(newRoute);
    }
    final double newCost = s.evaluator.computeCost(s.context, row, newRoute);
    cache.put(newRoute, newCost);
    return newCost;
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
  static <T> ImmutableList<T> inListSwap(ImmutableList<T> originalList,
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
  static <T> ImmutableList<Integer> removeAll(List<T> list, T item) {
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

  static <T> ImmutableList<Integer> indices(List<T> list, T item) {
    final ImmutableList.Builder<Integer> builder = ImmutableList.builder();
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).equals(item)) {
        builder.add(i);
      }
    }
    return builder.build();
  }

  static <T> ImmutableList<T> replace(ImmutableList<T> list,
      ImmutableList<Integer> indices, ImmutableList<T> elements) {
    checkArgument(indices.size() == elements.size(),
        "Number of indices (%s) must equal number of elements (%s).",
        indices.size(), elements.size());
    final List<T> newL = newArrayList(list);
    for (int i = 0; i < indices.size(); i++) {
      newL.set(indices.get(i), elements.get(i));
    }
    return ImmutableList.copyOf(newL);
  }

  static class Swap<T> {
    final T item;
    final int fromRow;
    final int toRow;
    final ImmutableList<Integer> toIndices;

    Swap(T i, int from, int to, ImmutableList<Integer> toInd) {
      item = i;
      fromRow = from;
      toRow = to;
      toIndices = toInd;
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this).add("item", item)
          .add("fromRow", fromRow).add("toRow", toRow)
          .add("toIndices", toIndices).toString();
    }
  }

  static class IndexToSwapTransform<T> implements
      Function<ImmutableList<Integer>, Swap<T>> {
    private final T item;
    private final int fromRow;
    private final int toRow;

    IndexToSwapTransform(T it, int from, int to) {
      item = it;
      fromRow = from;
      toRow = to;
    }

    @Override
    public Swap<T> apply(ImmutableList<Integer> input) {
      return new Swap<T>(item, fromRow, toRow, input);
    }
  }
}
