package rinde.opt.localsearch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import rinde.opt.localsearch.Insertions.Insertion;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class Swaps {

  private Swaps() {}

  public interface Evaluator<C, T> {

    double eval(C context, int routeIndex, ImmutableList<T> newRoute);

  }

  static class Swap<T> {
    final T item;
    final int fromRow;
    final Insertion insertion;

    Swap(T i, int from, Insertion ins) {
      item = i;
      fromRow = from;
      insertion = ins;
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this).add("item", item)
          .add("fromRow", fromRow).add("insertion", insertion).toString();
    }
  }

  static class SwapIterator<T> implements Iterator<Swap<T>> {

    final Set<T> seen = newLinkedHashSet();
    final Iterator<Iterator<Insertion>> iterators;
    final Iterator<Integer> rows;
    final Iterator<T> items;

    Iterator<Insertion> current;

    private int currentRow;
    private T currentItem;

    public SwapIterator(Schedule<?, T> schedule,
        ImmutableList<Integer> startIndices) {
      final ImmutableList.Builder<Iterator<Insertion>> builder = ImmutableList
          .builder();
      final ImmutableList.Builder<T> itemsB = ImmutableList.builder();
      final ImmutableList.Builder<Integer> rowsB = ImmutableList.builder();

      for (int i = 0; i < schedule.routes.size(); i++) {

        final ImmutableList<T> row = schedule.routes.get(i);
        for (final T t : row) {
          if (!seen.contains(t)) {
            seen.add(t);

            // FIXME find way to elegantly avoid already existing insertions
            // perhaps by providing a list of exceptions to Insertions.iterator?
            final int occurrences = Collections.frequency(row, t);

            itemsB.add(t);
            rowsB.add(i);
            builder.add(Insertions.iterator(schedule, i, occurrences,
                startIndices));
          }
        }
      }
      iterators = builder.build().iterator();
      items = itemsB.build().iterator();
      rows = rowsB.build().iterator();
      if (iterators.hasNext()) {
        current = iterators.next();
        currentRow = rows.next();
        currentItem = items.next();
      }

    }

    @Override
    public boolean hasNext() {
      return iterators.hasNext() || current.hasNext();
    }

    @Override
    public Swap<T> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      if (!current.hasNext()) {
        current = iterators.next();
        currentRow = rows.next();
        currentItem = items.next();
      }

      final Insertion insertion = current.next();

      return new Swap<T>(currentItem, currentRow, insertion);
    }

    @Deprecated
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  static <C, T> Iterator<Swap<T>> generate(Schedule<C, T> schedule,
      ImmutableList<Integer> startIndices) {

    return new SwapIterator<T>(schedule, startIndices);
    // for each T, find # occurrences, insertion points
    // compute number of possible insertions in other places

    // generate insertions for an entire schedule

    // Insertions.insertionsIterator(list, item, startIndex, numOfInsertions)

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
  static <C, T> Optional<Schedule<C, T>> swap(Schedule<C, T> s, T itemToSwap,
      int fromRow, int toRow, ImmutableList<Integer> insertionIndices,
      double threshold) {

    checkArgument(fromRow >= 0 && fromRow < s.routes.size(),
        "fromRow must be >= 0 and < %s, it is %s.", s.routes.size(), fromRow);
    checkArgument(toRow >= 0 && toRow < s.routes.size(),
        "toRow must be >= 0 and < %s, it is %s.", s.routes.size(), toRow);

    if (fromRow == toRow) {
      // 1. swap within same vehicle
      // compute cost of original ordering
      // compute cost of new ordering
      final double originalCost = s.objectiveValues.get(fromRow);
      final ImmutableList<T> newRoute = inListSwap(s.routes.get(fromRow),
          insertionIndices, itemToSwap);
      final double newCost = s.evaluator.eval(s.context, fromRow, newRoute);

      final double diff = newCost - originalCost;

      if (diff < threshold) {
        // it improves
        final ImmutableList<ImmutableList<T>> newRoutes = replace(s.routes,
            ImmutableList.of(fromRow), ImmutableList.of(newRoute));
        final double newObjectiveValue = s.objectiveValue + diff;
        final ImmutableList<Double> newObjectiveValues = replace(
            s.objectiveValues, ImmutableList.of(fromRow),
            ImmutableList.of(newCost));
        return Optional.of(Schedule.create(s.context, newRoutes,
            newObjectiveValues, newObjectiveValue, s.evaluator));
      } else {
        return Optional.absent();
      }
    } else {
      // 2. swap between vehicles

      // compute cost of removal from original vehicle
      final double originalCostA = s.objectiveValues.get(fromRow);
      final ImmutableList<T> newRouteA = ImmutableList.copyOf(filter(
          s.routes.get(fromRow), not(equalTo(itemToSwap))));
      final int itemCount = s.routes.get(fromRow).size() - newRouteA.size();
      checkArgument(
          itemCount > 0,
          "The item (%s) is not in row %s, hence it cannot be swapped to another row.",
          itemToSwap, fromRow);
      checkArgument(
          itemCount == insertionIndices.size(),
          "The number of occurences in the fromRow (%s) should equal the number of insertion indices (%s).",
          itemCount, insertionIndices.size());

      final double newCostA = s.evaluator.eval(s.context, fromRow, newRouteA);
      final double diffA = newCostA - originalCostA;

      // compute cost of insertion in new vehicle
      final double originalCostB = s.objectiveValues.get(toRow);
      final ImmutableList<T> newRouteB = Insertions.insert(s.routes.get(toRow),
          insertionIndices, itemToSwap);
      final double newCostB = s.evaluator.eval(s.context, toRow, newRouteB);
      final double diffB = newCostB - originalCostB;

      final double diff = diffA + diffB;
      if (diff < threshold) {
        final ImmutableList<Integer> rows = ImmutableList.of(fromRow, toRow);
        final ImmutableList<ImmutableList<T>> newRoutes = replace(s.routes,
            rows, ImmutableList.of(newRouteA, newRouteB));
        final double newObjectiveValue = s.objectiveValue + diff;
        final ImmutableList<Double> newObjectiveValues = replace(
            s.objectiveValues, rows, ImmutableList.of(newCostA, newCostB));

        return Optional.of(Schedule.create(s.context, newRoutes,
            newObjectiveValues, newObjectiveValue, s.evaluator));
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
}
