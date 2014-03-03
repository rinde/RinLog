/**
 * 
 */
package rinde.opt.localsearch;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static rinde.opt.localsearch.Insertions.insert;
import static rinde.opt.localsearch.Insertions.insertions;
import static rinde.opt.localsearch.Insertions.insertionsIterator;
import static rinde.opt.localsearch.Insertions.iterator;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

import rinde.opt.localsearch.Insertions.Insertion;
import rinde.opt.localsearch.Insertions.InsertionIndexGenerator;
import rinde.opt.localsearch.SwapsTest.SortDirection;
import rinde.opt.localsearch.SwapsTest.StringListEvaluator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class InsertionsTest {

  static final String A = "A";
  static final String B = "B";
  static final String C = "C";
  static final String D = "D";
  static final String Z = "Z";

  /**
   * Tests whether all insertions are created.
   */
  @Test
  public void oneInsertions() {
    List<ImmutableList<String>> strings = insertions(
        ImmutableList.<String> of(), Z, 0, 1);
    assertEquals(1, strings.size());
    assertEquals(asList(Z), strings.get(0));

    strings = insertions(ImmutableList.of(A), Z, 0, 1);
    assertEquals(2, strings.size());
    assertEquals(asList(Z, A), strings.get(0));
    assertEquals(asList(A, Z), strings.get(1));

    strings = insertions(ImmutableList.of(A, B), Z, 0, 1);
    assertEquals(3, strings.size());
    assertEquals(asList(Z, A, B), strings.get(0));
    assertEquals(asList(A, Z, B), strings.get(1));
    assertEquals(asList(A, B, Z), strings.get(2));

    strings = insertions(ImmutableList.of(A, B, C), Z, 0, 1);
    assertEquals(4, strings.size());
    assertEquals(asList(Z, A, B, C), strings.get(0));
    assertEquals(asList(A, Z, B, C), strings.get(1));
    assertEquals(asList(A, B, Z, C), strings.get(2));
    assertEquals(asList(A, B, C, Z), strings.get(3));
  }

  /**
   * Tests whether all two insertions are created.
   */
  @Test
  public void twoInsertions() {
    List<ImmutableList<String>> strings = insertions(ImmutableList.of(A, B, C),
        Z, 0, 2);
    assertEquals(10, strings.size());
    assertEquals(asList(Z, Z, A, B, C), strings.get(0));
    assertEquals(asList(Z, A, Z, B, C), strings.get(1));
    assertEquals(asList(Z, A, B, Z, C), strings.get(2));
    assertEquals(asList(Z, A, B, C, Z), strings.get(3));

    assertEquals(asList(A, Z, Z, B, C), strings.get(4));
    assertEquals(asList(A, Z, B, Z, C), strings.get(5));
    assertEquals(asList(A, Z, B, C, Z), strings.get(6));

    assertEquals(asList(A, B, Z, Z, C), strings.get(7));
    assertEquals(asList(A, B, Z, C, Z), strings.get(8));

    assertEquals(asList(A, B, C, Z, Z), strings.get(9));

    assertEquals(strings.size(), newHashSet(strings).size());

    // test empty
    strings = insertions(ImmutableList.<String> of(), Z, 0, 2);
    assertEquals(1, strings.size());
    assertEquals(asList(Z, Z), strings.get(0));
  }

  /**
   * Checks four simultaneous insertions.
   */
  @Test
  public void fourInsertionsTest() {
    final List<ImmutableList<String>> strings = insertions(
        InsertionsTest.list(A, B, C, D), Z, 2, 4);

    assertEquals(15, strings.size());
    assertEquals(15, ImmutableSet.copyOf(strings).size());

    assertEquals(asList(A, B, Z, Z, Z, Z, C, D), strings.get(0));
    assertEquals(asList(A, B, Z, Z, Z, C, Z, D), strings.get(1));
    assertEquals(asList(A, B, Z, Z, Z, C, D, Z), strings.get(2));

    assertEquals(asList(A, B, Z, Z, C, Z, Z, D), strings.get(3));
    assertEquals(asList(A, B, Z, Z, C, Z, D, Z), strings.get(4));
    assertEquals(asList(A, B, Z, Z, C, D, Z, Z), strings.get(5));

    assertEquals(asList(A, B, Z, C, Z, Z, Z, D), strings.get(6));
    assertEquals(asList(A, B, Z, C, Z, Z, D, Z), strings.get(7));
    assertEquals(asList(A, B, Z, C, Z, D, Z, Z), strings.get(8));
    assertEquals(asList(A, B, Z, C, D, Z, Z, Z), strings.get(9));

    assertEquals(asList(A, B, C, Z, Z, Z, Z, D), strings.get(10));
    assertEquals(asList(A, B, C, Z, Z, Z, D, Z), strings.get(11));
    assertEquals(asList(A, B, C, Z, Z, D, Z, Z), strings.get(12));
    assertEquals(asList(A, B, C, Z, D, Z, Z, Z), strings.get(13));
    assertEquals(asList(A, B, C, D, Z, Z, Z, Z), strings.get(14));
  }

  /**
   * Generates several large insertions.
   */
  @Test
  public void nInsertionsTest() {
    for (int i = 0; i < 20; i++) {
      final List<ImmutableList<String>> strings = insertions(
          InsertionsTest.list(A, B, C, D), Z, 0, 1 + i);
      final long size = Insertions.multichoose(5, 1 + i);
      // check for correct size
      assertEquals(size, strings.size());
      // check for uniqueness of items
      assertEquals(size, ImmutableSet.copyOf(strings).size());
    }
  }

  /**
   * Tests illegal negative start index.
   */
  @Test(expected = IllegalArgumentException.class)
  public void illegalStartIndex1() {
    insertions(InsertionsTest.list(A, B), Z, -1, 1);
  }

  /**
   * Tests illegal start index higher than list size.
   */
  @Test(expected = IllegalArgumentException.class)
  public void illegalStartIndex2() {
    insertions(InsertionsTest.list(A, B), Z, 3, 1);
  }

  /**
   * Tests non-positive number of insertions fail.
   */
  @Test(expected = IllegalArgumentException.class)
  public void illegalNumOfInsertions() {
    insertions(InsertionsTest.list(A, B), Z, 0, 0);
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test(expected = UnsupportedOperationException.class)
  public void iteratorRemove() {
    insertionsIterator(InsertionsTest.list(A, B), Z, 0, 1).remove();
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test
  public void iteratorNextFail() {
    final Iterator<ImmutableList<String>> it = insertionsIterator(
        InsertionsTest.list(A, B), Z, 0, 1);
    it.next();
    it.next();
    it.next();

    boolean fail = false;
    try {
      it.next();
    } catch (final NoSuchElementException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test(expected = UnsupportedOperationException.class)
  public void insertionIndexGeneratorRemoveFail() {
    ((Iterator<ImmutableList<Integer>>) new InsertionIndexGenerator(1, 0, 0))
        .remove();
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test
  public void insertionIndexGeneratorNextFail() {
    final Iterator<ImmutableList<Integer>> iig = new InsertionIndexGenerator(1,
        0, 0);
    iig.next();
    boolean fail = false;
    try {
      iig.next();
    } catch (final NoSuchElementException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  /**
   * Test for several insertion combinations.
   */
  @Test
  public void insertTest() {
    assertEquals(InsertionsTest.list(A, C, B, C),
        insert(InsertionsTest.list(A, B), InsertionsTest.list(1, 2), C));
    assertEquals(InsertionsTest.list(A, C, B, C, C, C),
        insert(InsertionsTest.list(A, B), InsertionsTest.list(1, 2, 2, 2), C));

    assertEquals(InsertionsTest.list(C, C, C),
        insert(InsertionsTest.list(), InsertionsTest.list(0, 0, 0), C));
    assertEquals(
        InsertionsTest.list(D, A, D, B, D, C, D),
        insert(InsertionsTest.list(A, B, C), InsertionsTest.list(0, 1, 2, 3), D));

    assertEquals(InsertionsTest.list(A, B, C, D),
        insert(InsertionsTest.list(A, B, C), InsertionsTest.list(3), D));

    assertEquals(InsertionsTest.list(A, B, C),
        insert(InsertionsTest.list(B, C), InsertionsTest.list(0), A));
    assertEquals(InsertionsTest.list(B, A, C),
        insert(InsertionsTest.list(B, C), InsertionsTest.list(1), A));
    assertEquals(InsertionsTest.list(B, C, A),
        insert(InsertionsTest.list(B, C), InsertionsTest.list(2), A));
  }

  /**
   * Illegal index: negative.
   */
  @Test(expected = IllegalArgumentException.class)
  public void insertNegativeIndex() {
    insert(InsertionsTest.list(A, C), InsertionsTest.list(0, 1, 2, -1), B);
  }

  /**
   * Illegal index: too large.
   */
  @Test(expected = IllegalArgumentException.class)
  public void insertTooLargeIndex() {
    insert(InsertionsTest.list(A, C), InsertionsTest.list(0, 1, 2, 3), B);
  }

  /**
   * Illegal indices order, it should be ascending.
   */
  @Test(expected = IllegalArgumentException.class)
  public void insertNotAscendingIndices() {
    insert(InsertionsTest.list(A, C), InsertionsTest.list(0, 1, 2, 0), B);
  }

  /**
   * Illegal input: no indices.
   */
  @Test(expected = IllegalArgumentException.class)
  public void insertNoIndices() {
    insert(InsertionsTest.list(A, B, C), ImmutableList.<Integer> of(), D);
  }

  @Test
  public void scheduleIteratorTest() {
    final Schedule<SortDirection, String> s = Schedule.create(
        SortDirection.ASCENDING, list(list(A, B), list(C, D)),
        new StringListEvaluator());

    final List<Insertion> insertions = ImmutableList.copyOf(iterator(s,
        new Insertion(0, list(1)), list(0, 2)));
    assertEquals(3, insertions.size());
    assertEquals(new Insertion(0, ImmutableList.of(0)), insertions.get(0));
    assertEquals(new Insertion(0, ImmutableList.of(2)), insertions.get(1));
    assertEquals(new Insertion(1, ImmutableList.of(2)), insertions.get(2));

  }

  /**
   * Short hand for creating immutable lists.
   * @param items The items of the list.
   * @return An {@link ImmutableList}.
   */
  public static <T> ImmutableList<T> list(T... items) {
    return ImmutableList.copyOf(items);
  }

}
