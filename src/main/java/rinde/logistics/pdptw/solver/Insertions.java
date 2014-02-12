package rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;

/**
 * Utilities for creation insertions.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public final class Insertions {

  private Insertions() {}

  /**
   * Creates a list of lists, each list contains the insertion of
   * <code>item</code> at a different position in the list.
   * @param list The original list.
   * @param item The item to be inserted.
   * @return A list of lists of size <code>n+1</code>.
   */
  public static <T> ImmutableList<ImmutableList<T>> plusOneInsertions(
      ImmutableList<T> list, T item) {
    return plusOneInsertions(list, item, 0);
  }

  /**
   * Creates a list of lists, each list contains the insertion of
   * <code>item</code> at a different position in the list. Only creates
   * insertions starting at <code>startIndex</code>.
   * @param list The original list.
   * @param item The item to be inserted.
   * @param startIndex Must be >= 0 && <= list size.
   * @return A list of lists of size <code>(n+1)-startIndex</code>.
   */
  public static <T> ImmutableList<ImmutableList<T>> plusOneInsertions(
      ImmutableList<T> list, T item, int startIndex) {
    checkArgument(startIndex >= 0 && startIndex <= list.size(),
        "startIndex must be >= 0 and <= %s (list size), it is %s.",
        list.size(), startIndex);
    final ImmutableList.Builder<ImmutableList<T>> inserted = ImmutableList
        .builder();
    for (int i = startIndex; i < list.size() + 1; i++) {
      final ImmutableList<T> firstHalf = list.subList(0, i);
      final ImmutableList<T> secondHalf = list.subList(i, list.size());
      inserted.add(ImmutableList.<T> builder().addAll(firstHalf).add(item)
          .addAll(secondHalf).build());
    }
    return inserted.build();
  }

  /**
   * Creates a list of lists, each list contains two insertions of
   * <code>item</code> at different positions in the list. Only creates
   * insertions starting at <code>startIndex</code>.
   * @param list The original list.
   * @param item The item to be inserted twice.
   * @param startIndex Must be >= 0 && <= list size.
   * @return A list of lists.
   */
  public static <T> ImmutableList<ImmutableList<T>> plusTwoInsertions(
      ImmutableList<T> list, T item, int startIndex) {
    final ImmutableList<ImmutableList<T>> plusOneInsertions = plusOneInsertions(
        list, item, startIndex);
    final ImmutableList.Builder<ImmutableList<T>> plusTwoInsertions = ImmutableList
        .builder();
    for (final ImmutableList<T> l : plusOneInsertions) {
      plusTwoInsertions.addAll(plusOneInsertions(l, item, l.indexOf(item) + 1));
    }
    return plusTwoInsertions.build();
  }

}
