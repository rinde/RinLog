/*
 * Copyright (C) 2013-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.opt.localsearch;

import static com.github.rinde.opt.localsearch.InsertionsTest.list;
import static com.github.rinde.opt.localsearch.Swaps.inListSwap;
import static com.github.rinde.opt.localsearch.Swaps.removeAll;
import static com.github.rinde.opt.localsearch.Swaps.replace;
import static com.github.rinde.opt.localsearch.Swaps.swap;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.reverseOrder;
import static java.util.Collections.sort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.github.rinde.opt.localsearch.RouteEvaluator;
import com.github.rinde.opt.localsearch.Schedule;
import com.github.rinde.opt.localsearch.Swaps;
import com.github.rinde.opt.localsearch.Swaps.Swap;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Test of {@link Swaps}.
 * @author Rinde van Lon 
 */
public class SwapsTest {

  static final String A = "A";
  static final String B = "B";
  static final String C = "C";
  static final String D = "D";
  static final String E = "E";
  static final String F = "F";
  static final String G = "G";

  @SuppressWarnings("null")
  Schedule<SortDirection, String> schedule;

  enum SortDirection {
    ASCENDING, DESCENDING;
  }

  /**
   * Creates a schedule for testing.
   */
  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    schedule = Schedule.create(SortDirection.ASCENDING,
        list(list(G, D, D, G), list(A, C, B, F, E, F, A, B)), list(0, 0),
        new StringListEvaluator());
  }

  /**
   * Tests whether swap iterator produces correct swaps.
   */
  @Test
  public void generateTest() {
    final Schedule<SortDirection, String> s = Schedule.create(
        SortDirection.DESCENDING, list(list(A, A, B, E), list(C, D)),
        list(0, 0), new StringListEvaluator());

    final Iterator<Swap<String>> it = Swaps.swapIterator(s);
    while (it.hasNext()) {
      final Swap<String> swapOperation = it.next();
      final ImmutableList<ImmutableList<String>> routes = Swaps.swap(s,
          swapOperation, 100).get().routes;
    }
  }

  /**
   * Evaluator providing an objective function for sorting strings in ascending
   * or descending order.
   * @author Rinde van Lon 
   */
  static class StringListEvaluator implements
      RouteEvaluator<SortDirection, String> {
    @Override
    public double computeCost(SortDirection context, int routeIndex,
        ImmutableList<String> newRoute) {
      final List<String> expected = newArrayList(newRoute);
      if (context == SortDirection.DESCENDING) {
        sort(expected, reverseOrder());
      } else {
        sort(expected);
      }
      double error = 0;
      for (int i = 0; i < expected.size(); i++) {
        final int foundAtIndex = newRoute.indexOf(expected.get(i));
        error += Math.abs(i - foundAtIndex);
      }
      return error;
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this).toString();
    }
  }

  /**
   * Tests for swapping of one item at a time.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void singleSwapTest() {
    final Schedule<SortDirection, String> s = Schedule.create(
        SortDirection.ASCENDING, list(list(A, C, B), list(D)), list(0, 0),
        new StringListEvaluator());

    assertFalse(swap(s, new Swap<String>(B, 0, 0, list(0)), 0d).isPresent());
    assertFalse(swap(s, new Swap<String>(B, 0, 1, list(1)), 0d).isPresent());
    final Schedule<SortDirection, String> swap1 = swap(s,
        new Swap<String>(B, 0, 1, list(0)), 0d).get();
    assertEquals(s.context, swap1.context);
    assertEquals(s.evaluator, swap1.evaluator);
    assertEquals(list(list(A, C), list(B, D)), swap1.routes);
    assertEquals(list(0d, 0d), swap1.objectiveValues);
    assertEquals(0, swap1.objectiveValue, 0.00001);

    final Schedule<SortDirection, String> swap2 = swap(s,
        new Swap<String>(B, 0, 0, list(1)), 0d).get();
    assertEquals(s.context, swap2.context);
    assertEquals(s.evaluator, swap2.evaluator);
    assertEquals(list(list(A, B, C), list(D)), swap2.routes);
    assertEquals(list(0d, 0d), swap2.objectiveValues);
    assertEquals(0, swap2.objectiveValue, 0.00001);

  }

  /**
   * Swap two (the same) items at once.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void doubleSwapTest() {
    final Schedule<SortDirection, String> s = Schedule.create(
        SortDirection.ASCENDING,
        list(list(G, D, D, G), list(A, C, B, F, E, F, A, B)), list(0, 0),
        new StringListEvaluator());
    assertEquals(s.objectiveValues.size(), s.routes.size());
    assertFalse(s.toString().isEmpty());

    final Schedule<SortDirection, String> swap1 = swap(s,
        new Swap<String>(A, 1, 0, list(1, 3)), 10).get();
    assertEquals(s.context, swap1.context);
    assertEquals(s.evaluator, swap1.evaluator);
    assertEquals(list(list(G, A, D, D, A, G), list(C, B, F, E, F, B)),
        swap1.routes);
    assertEquals(list(11d, 8d), swap1.objectiveValues);
    assertEquals(s.objectiveValue, swap1.objectiveValue, 0.00001);

    // within list
    assertEquals(list(list(G, D, D, G), list(A, A, C, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 1, list(0, 0)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(A, C, A, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 1, list(0, 1)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(C, A, A, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 1, list(1, 1)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(C, A, B, A, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 1, list(1, 2)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(C, A, B, F, A, E, F, B)),
        swap(s, new Swap<String>(A, 1, 1, list(1, 3)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(C, A, B, F, E, A, F, B)),
        swap(s, new Swap<String>(A, 1, 1, list(1, 4)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(C, A, B, F, E, F, A, B)),
        swap(s, new Swap<String>(A, 1, 1, list(1, 5)), 10).get().routes);
    assertEquals(list(list(G, D, D, G), list(C, A, B, F, E, F, B, A)),
        swap(s, new Swap<String>(A, 1, 1, list(1, 6)), 10).get().routes);

    // to other list
    assertEquals(list(list(A, A, G, D, D, G), list(C, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 0, list(0, 0)), 10).get().routes);
    assertEquals(list(list(A, G, A, D, D, G), list(C, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 0, list(0, 1)), 10).get().routes);
    assertEquals(list(list(A, G, D, A, D, G), list(C, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 0, list(0, 2)), 10).get().routes);
    assertEquals(list(list(A, G, D, D, A, G), list(C, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 0, list(0, 3)), 10).get().routes);
    assertEquals(list(list(A, G, D, D, G, A), list(C, B, F, E, F, B)),
        swap(s, new Swap<String>(A, 1, 0, list(0, 4)), 10).get().routes);
  }

  /**
   * fromRow can not be negative.
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapNegativeFromRow() {
    swap(schedule, new Swap<String>(A, -1, 1, list(1)), 0d);
  }

  /**
   * fromRow can not be too large.
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapToLargeFromRow() {
    swap(schedule, new Swap<String>(A, 2, 1, list(1)), 0d);
  }

  /**
   * toRow can not be negative.
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapNegativeToRow() {
    swap(schedule, new Swap<String>(A, 1, -1, list(1)), 0d);
  }

  /**
   * toRow can not be too large.
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapTooLargeToRow() {
    swap(schedule, new Swap<String>(A, 1, 2, list(1)), 0d);
  }

  /**
   * A is not in row 0, hence it cannot be swapped.
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapWrongRow() {
    swap(schedule, new Swap<String>(A, 0, 1, list(1)), 0d);
  }

  /**
   * There are two occurrences of A in row 1, therefore there should be 2
   * indices.
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapIncorrectIndicesSize() {
    swap(schedule, new Swap<String>(A, 1, 0, list(1)), 0d);
  }

  /**
   * Cannot move A to index -1 (does not exist).
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapToNegativeIndices() {
    swap(schedule, new Swap<String>(A, 1, 0, list(1, -1)), 0d);
  }

  /**
   * Cannot move A to index 8 (does not exist).
   */
  @Test(expected = IllegalArgumentException.class)
  public void swapToTooLargeIndices() {
    swap(schedule, new Swap<String>(A, 1, 0, list(1, 8)), 0d);
  }

  /**
   * Several tests for the inListSwap method.
   */
  @Test
  public void inListSwapTest() {
    assertEquals(InsertionsTest.list(A, C, B),
        inListSwap(InsertionsTest.list(A, B, C), list(2), B));

    assertEquals(
        InsertionsTest.list(D, A, B, C, D, D),
        inListSwap(InsertionsTest.list(A, B, C, D, D, D),
            InsertionsTest.list(0, 3, 3), D));

    boolean fail = false;
    try {
      inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(0), A);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
    assertEquals(InsertionsTest.list(B, A, C),
        inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(1), A));
    assertEquals(InsertionsTest.list(B, C, A),
        inListSwap(InsertionsTest.list(A, B, C), InsertionsTest.list(2), A));
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

  /**
   * Test replace with valid inputs.
   */
  @Test
  public void replaceTest() {
    assertEquals(list(4, 5, 6),
        replace(list(1, 2, 3), list(0, 1, 2), list(4, 5, 6)));
    assertEquals(list(), replace(list(), ImmutableList.<Integer> of(), list()));
  }

  /**
   * Number of indices must equal number of elements.
   */
  @Test(expected = IllegalArgumentException.class)
  public void replaceInvalidTest() {
    replace(list(1), list(0, 0), list(2));
  }

}
