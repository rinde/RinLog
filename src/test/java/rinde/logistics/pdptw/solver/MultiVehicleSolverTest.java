/**
 * 
 */
package rinde.logistics.pdptw.solver;

import static java.util.Arrays.asList;

import java.math.RoundingMode;
import java.util.Arrays;

import org.junit.Test;

import rinde.sim.core.graph.Point;
import rinde.sim.pdptw.central.arrays.ArraysSolvers;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class MultiVehicleSolverTest {

  @Test
  public void test() {
    final Point p0 = new Point(0, 0);
    final Point p1 = new Point(10, 0);
    final Point p2 = new Point(10, 10);
    final Point p3 = new Point(0, 10);

    final int[][] travelTime = ArraysSolvers.toTravelTimeMatrix(
        asList(p0, p1, p2, p3), 1.5, RoundingMode.CEILING);

    System.out.println(Arrays.deepToString(travelTime));

    // ArraysSolverValidator
    // .validateInputs(travelTime, releaseDates, dueDates, servicePairs,
    // serviceTimes, vehicleTravelTimes, inventories,
    // remainingServiceTimes);
  }
}
