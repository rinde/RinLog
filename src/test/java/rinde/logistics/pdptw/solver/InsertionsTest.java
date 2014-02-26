/**
 * 
 */
package rinde.logistics.pdptw.solver;

import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static rinde.logistics.pdptw.solver.Insertions.insertions;
import static rinde.logistics.pdptw.solver.Insertions.insertionsIterator;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

import rinde.logistics.pdptw.solver.Insertions.InsertionIndexGenerator;

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
        ImmutableList.of(A, B, C, D), Z, 2, 4);

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
          ImmutableList.of(A, B, C, D), Z, 0, 1 + i);
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
    insertions(ImmutableList.of(A, B), Z, -1, 1);
  }

  /**
   * Tests illegal start index higher than list size.
   */
  @Test(expected = IllegalArgumentException.class)
  public void illegalStartIndex2() {
    insertions(ImmutableList.of(A, B), Z, 3, 1);
  }

  /**
   * Tests non-positive number of insertions fail.
   */
  @Test(expected = IllegalArgumentException.class)
  public void illegalNumOfInsertions() {
    insertions(ImmutableList.of(A, B), Z, 0, 0);
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test(expected = UnsupportedOperationException.class)
  public void iteratorRemove() {
    insertionsIterator(ImmutableList.of(A, B), Z, 0, 1).remove();
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test
  public void iteratorNextFail() {
    final Iterator<ImmutableList<String>> it = insertionsIterator(
        ImmutableList.of(A, B), Z, 0, 1);
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
    ((Iterator<List<Integer>>) new InsertionIndexGenerator(1, 0)).remove();
  }

  /**
   * Tests correct failure of iterator remove method.
   */
  @Test
  public void insertionIndexGeneratorNextFail() {
    final Iterator<List<Integer>> iig = new InsertionIndexGenerator(1, 0);

    iig.next();
    boolean fail = false;
    try {
      iig.next();
    } catch (final NoSuchElementException e) {
      fail = true;
    }
    assertTrue(fail);
  }

}
