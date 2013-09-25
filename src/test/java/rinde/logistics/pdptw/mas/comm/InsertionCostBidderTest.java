/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class InsertionCostBidderTest {

  @Test
  public void testPlusOneInsertions() {
    final String a = "A";
    final String b = "B";
    final String c = "C";
    final String z = "Z";

    List<ImmutableList<String>> strings = InsertionCostBidder.plusOneInsertions(
        ImmutableList.<String> of(), z);
    assertEquals(1, strings.size());
    assertEquals(asList(z), strings.get(0));

    strings = InsertionCostBidder.plusOneInsertions(ImmutableList.of(a), z);
    assertEquals(2, strings.size());
    assertEquals(asList(z, a), strings.get(0));
    assertEquals(asList(a, z), strings.get(1));

    strings = InsertionCostBidder.plusOneInsertions(ImmutableList.of(a, b), z);
    assertEquals(3, strings.size());
    assertEquals(asList(z, a, b), strings.get(0));
    assertEquals(asList(a, z, b), strings.get(1));
    assertEquals(asList(a, b, z), strings.get(2));

    strings = InsertionCostBidder.plusOneInsertions(ImmutableList.of(a, b, c), z);
    assertEquals(4, strings.size());
    assertEquals(asList(z, a, b, c), strings.get(0));
    assertEquals(asList(a, z, b, c), strings.get(1));
    assertEquals(asList(a, b, z, c), strings.get(2));
    assertEquals(asList(a, b, c, z), strings.get(3));

  }
}
