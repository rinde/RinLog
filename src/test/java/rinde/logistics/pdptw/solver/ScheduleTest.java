package rinde.logistics.pdptw.solver;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static rinde.logistics.pdptw.solver.Schedule.inListSwap;
import static rinde.logistics.pdptw.solver.Schedule.removeAll;

import java.util.List;

import org.junit.Test;

public class ScheduleTest {

  static final String A = "A";
  static final String B = "B";
  static final String C = "C";
  static final String D = "D";

  @Test
  public void inListSwapTest() {
    assertEquals(InsertionsTest.list(A, C, B), inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(2), B));

    assertEquals(InsertionsTest.list(D, A, B, C, D, D),
        inListSwap(InsertionsTest.list(A, B, C, D, D, D), InsertionsTest.list(0, 3, 3), D));

    boolean fail = false;
    try {
      inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(0), A);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
    assertEquals(InsertionsTest.list(B, A, C), inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(1), A));
    assertEquals(InsertionsTest.list(B, C, A), inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(2), A));
  }

  /**
   * Test for empty list.
   */
  @Test(expected = IllegalArgumentException.class)
  public void insListSwapEmptyList() {
    inListSwap(InsertionsTest.list(), InsertionsTest.list(1), A);
  }

  /**
   * May not swap such that the result equals the input (a no effect swap).
   */
  @Test(expected = IllegalArgumentException.class)
  public void inListSwapSameLocation() {
    inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(1), B);
  }

  /**
   * Number of occurrences of B (1) should equal number of insertions (2).
   */
  @Test(expected = IllegalArgumentException.class)
  public void inListSwapNonSymmetric1() {
    inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(1, 2), B);
  }

  /**
   * Number of occurrences of B (2) should equal number of insertions (1).
   */
  @Test(expected = IllegalArgumentException.class)
  public void inListSwapNonSymmetric2() {
    inListSwap(InsertionsTest.list(A, B, C, B), InsertionsTest.list(2), B);
  }

  /**
   * Tests the remove all method.
   */
  @Test
  public void removeAllTest() {
    final List<String> list = newArrayList(A, B, C, A, B, C, D);
    assertEquals(7, list.size());
    assertEquals(InsertionsTest.list(2, 5), removeAll(list, C));
    assertEquals(5, list.size());
    assertEquals(InsertionsTest.list(), removeAll(list, C));
    assertEquals(5, list.size());
    assertEquals(InsertionsTest.list(1, 3), removeAll(list, B));
    assertEquals(3, list.size());
    assertEquals(InsertionsTest.list(2), removeAll(list, D));
    assertEquals(2, list.size());
    assertEquals(InsertionsTest.list(0, 1), removeAll(list, A));
    assertTrue(list.isEmpty());

    assertEquals(InsertionsTest.list(), removeAll(newArrayList(), A));
  }

}
