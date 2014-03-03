package rinde.opt.localsearch;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.apache.commons.math3.util.ArithmeticUtils;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;

/**
 * Utilities for creation insertions.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class Insertions {

  private Insertions() {}

  static class Insertion {
    public final int row;
    public final ImmutableList<Integer> insertionIndices;

    Insertion(int r, ImmutableList<Integer> indices) {
      row = r;
      insertionIndices = indices;
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this).add("row", row)
          .add("insertionIndices", insertionIndices).toString();
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(row, insertionIndices);
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other == null) {
        return false;
      }
      if (getClass() != other.getClass()) {
        return false;
      }
      final Insertion ins = (Insertion) other;
      return Objects.equal(row, ins.row)
          && Objects.equal(insertionIndices, ins.insertionIndices);
    }
  }

  static <C, T> Iterator<Insertion> iterator(Schedule<C, T> schedule,
      Insertion insertion, ImmutableList<Integer> startIndices) {
    final List<Iterator<Insertion>> iterators = newArrayList();
    for (int i = 0; i < schedule.routes.size(); i++) {
      final InsertionGenerator ig = new InsertionGenerator(i,
          startIndices.get(i), new InsertionIndexGenerator(
              insertion.insertionIndices.size(), schedule.routes.get(i).size(),
              startIndices.get(i)));
      if (i == insertion.row) {
        iterators.add(Iterators.filter(ig,
            Predicates.not(Predicates.equalTo(insertion))));
      } else {
        iterators.add(ig);
      }
    }
    return Iterators.concat(iterators.iterator());
  }

  // adapter for InsertionIndexGenerator
  static class InsertionGenerator implements Iterator<Insertion> {

    final int row;
    final int startIndex;
    final InsertionIndexGenerator indexGenerator;

    InsertionGenerator(int r, int si, InsertionIndexGenerator iig) {
      row = r;
      startIndex = si;
      indexGenerator = iig;
    }

    @Override
    public boolean hasNext() {
      return indexGenerator.hasNext();
    }

    @Override
    public Insertion next() {
      return new Insertion(row, indexGenerator.next());
    }

    @Deprecated
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Creates an {@link Iterator} for a list of lists, each list contains a
   * specified number of insertions of <code>item</code> at a different position
   * in the list. Only creates insertions starting at <code>startIndex</code>.
   * @param list The original list.
   * @param item The item to be inserted.
   * @param startIndex Must be >= 0 && <= list size.
   * @param numOfInsertions The number of times <code>item</code> is inserted.
   * @return Iterator producing a list of lists of size
   *         <code>(n+1)-startIndex</code>.
   */
  public static <T> Iterator<ImmutableList<T>> insertionsIterator(
      ImmutableList<T> list, T item, int startIndex, int numOfInsertions) {
    checkArgument(startIndex >= 0 && startIndex <= list.size(),
        "startIndex must be >= 0 and <= %s (list size), it is %s.",
        list.size(), startIndex);
    checkArgument(numOfInsertions > 0, "numOfInsertions must be positive.");
    return new InsertionsIterator<T>(list, item, startIndex, numOfInsertions);
  }

  /**
   * Creates a list of lists, each list contains a specified number of
   * insertions of <code>item</code> at a different position in the list. Only
   * creates insertions starting at <code>startIndex</code>.
   * @param list The original list.
   * @param item The item to be inserted.
   * @param startIndex Must be >= 0 && <= list size.
   * @param numOfInsertions The number of times <code>item</code> is inserted.
   * @return A list containing all insertions.
   */
  public static <T> ImmutableList<ImmutableList<T>> insertions(
      ImmutableList<T> list, T item, int startIndex, int numOfInsertions) {
    return ImmutableList.copyOf(insertionsIterator(list, item, startIndex,
        numOfInsertions));
  }

  /**
   * Calculates the number of <code>k</code> sized multisubsets that can be
   * formed in a set of size <code>n</code>. See <a
   * href="https://en.wikipedia.org/wiki/Combination#
   * Number_of_combinations_with_repetition">Wikipedia</a> for a description.
   * 
   * @param n The size of the set to create subsets from.
   * @param k The size of the multisubsets.
   * @return The number of multisubsets.
   */
  static long multichoose(int n, int k) {
    return ArithmeticUtils.binomialCoefficient(n + k - 1, k);
  }

  /**
   * Inserts <code>item</code> in the specified indices in the
   * <code>originalList</code>.
   * @param originalList The list which will be inserted by <code>item</code>.
   * @param insertionIndices List of insertion indices in ascending order.
   * @param item The item to insert.
   * @return A list based on the original list but inserted with item in the
   *         specified places.
   */
  public static <T> ImmutableList<T> insert(List<T> originalList,
      List<Integer> insertionIndices, T item) {
    // TODO this method should be reused in the rest of the code
    checkArgument(!insertionIndices.isEmpty(),
        "At least one insertion index must be defined.");
    int prev = 0;
    final ImmutableList.Builder<T> builder = ImmutableList.<T> builder();
    for (int i = 0; i < insertionIndices.size(); i++) {
      final int cur = insertionIndices.get(i);
      checkArgument(
          cur >= 0 && cur <= originalList.size(),
          "The specified indices must be >= 0 and <= %s (list size), it is %s.",
          originalList.size(), cur);
      checkArgument(cur >= prev,
          "The specified indices must be in ascending order. Received %s.",
          insertionIndices);
      builder.addAll(originalList.subList(prev, cur));
      builder.add(item);
      prev = cur;
    }
    builder.addAll(originalList.subList(prev, originalList.size()));
    return builder.build();
  }

  static class InsertionsIterator<T> implements Iterator<ImmutableList<T>> {
    private final InsertionIndexGenerator indexIterator;
    private final ImmutableList<T> originalList;
    private final T item;
    private final int startIndex;

    InsertionsIterator(ImmutableList<T> l, T it, int si, int nrOfInsertions) {
      originalList = l;
      startIndex = si;
      item = it;
      indexIterator = new InsertionIndexGenerator(nrOfInsertions, l.size(),
          startIndex);
    }

    @Override
    public boolean hasNext() {
      return indexIterator.hasNext();
    }

    @Override
    public ImmutableList<T> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final List<Integer> insertionIndices = indexIterator.next();
      int prev = 0;
      final ImmutableList.Builder<T> builder = ImmutableList.<T> builder();
      for (int i = 0; i < insertionIndices.size(); i++) {
        final int cur = insertionIndices.get(i);
        builder.addAll(originalList.subList(prev, cur));
        builder.add(item);
        prev = cur;
      }
      builder.addAll(originalList.subList(prev, originalList.size()));
      return builder.build();
    }

    @Deprecated
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  static class InsertionIndexGenerator implements
      Iterator<ImmutableList<Integer>> {
    private final int[] insertionPositions;
    private final int originalListSize;
    private final long length;
    private int index = 0;

    InsertionIndexGenerator(int numOfInsertions, int listSize, int startIndex) {
      insertionPositions = new int[numOfInsertions];
      for (int i = 0; i < insertionPositions.length; i++) {
        insertionPositions[i] = startIndex;
      }
      originalListSize = listSize;
      length = multichoose(listSize + 1 - startIndex, numOfInsertions);
    }

    @Override
    public boolean hasNext() {
      return index < length;
    }

    @Override
    public ImmutableList<Integer> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (index > 0) {
        for (int i = 0; i < insertionPositions.length; i++) {
          if (insertionPositions[i] == originalListSize) {
            insertionPositions[i - 1]++;
            for (int j = i; j < insertionPositions.length; j++) {
              insertionPositions[j] = insertionPositions[i - 1];
            }
            break;
          } else if (i == insertionPositions.length - 1) {
            insertionPositions[i]++;
          }
        }
      }
      index++;
      return ImmutableList.copyOf(Ints.asList(insertionPositions));
    }

    @Deprecated
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

}
