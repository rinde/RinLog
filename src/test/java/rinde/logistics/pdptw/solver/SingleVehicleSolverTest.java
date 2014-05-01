/**
 * 
 */
package rinde.logistics.pdptw.solver;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.comm.RandomBidder;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.Model;
import rinde.sim.pdptw.central.arrays.ArraysSolverDebugger;
import rinde.sim.pdptw.central.arrays.ArraysSolverDebugger.SVASDebugger;
import rinde.sim.pdptw.central.arrays.ArraysSolverValidator;
import rinde.sim.pdptw.central.arrays.SingleVehicleArraysSolver;
import rinde.sim.pdptw.central.arrays.SingleVehicleSolverAdapter;
import rinde.sim.pdptw.central.arrays.SolutionObject;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.common.StatisticsDTO;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauTestUtil;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.TimeWindow;

import com.google.common.collect.ImmutableList;

/**
 * Checks whether the objective as calculated by the simulator via
 * {@link Gendreau06ObjectiveFunction} is 'equal' to the objective as calculated
 * by the {@link SingleVehicleArraysSolver}. Note that due to the fact that the
 * solver works with integers (doubles are rounded up), a discrepancy is
 * expected. In fact, this discrepancy is checked to see if the objective
 * calculated by the {@link SingleVehicleArraysSolver} is always worse compared
 * to the objective calculated by the {@link Gendreau06ObjectiveFunction}.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
@RunWith(Parameterized.class)
public class SingleVehicleSolverTest {

  protected final SingleVehicleArraysSolver solver;

  static final double EPSILON = 0.1;

  public SingleVehicleSolverTest(SingleVehicleArraysSolver solver) {
    this.solver = solver;
  }

  @Parameters
  public static Collection<Object[]> configs() {
    return Arrays.asList(new Object[][] {//
        { new HeuristicSolver(new MersenneTwister(123)) } });
  }

  @Test
  public void test() {
    final Point a = new Point(0, 0);
    final Point b = new Point(5, 5);
    final Point c = new Point(5, 0);
    final Point d = new Point(0, 5);
    final List<TimedEvent> events = newArrayList();
    events.add(newParcelEvent(a, b));
    events.add(newParcelEvent(c, d));

    final Gendreau06Scenario testScen = GendreauTestUtil.create(events);
    final TestConfigurator tc = new TestConfigurator(solver, SI.SECOND);
    final StatisticsDTO stats = ExperimentTest.singleRun(testScen, tc, 123,
        Gendreau06ObjectiveFunction.instance(), false);
    assertEquals(1, tc.debuggers.size());

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    assertTrue("invalid result", objFunc.isValidResult(stats));
    final double simObj = objFunc.computeCost(stats);

    final List<SolutionObject> solObjs = tc.debuggers.get(0).getOutputs();
    assertEquals(1, solObjs.size());
    final double solverObj = solObjs.get(0).objectiveValue / 60.0;

    assertEquals(simObj, solverObj, EPSILON);
    assertTrue(
        "the solver should have a slightly pessimistic view on the world",
        solverObj > simObj);
  }

  /**
   * Scenario with no tardiness.
   */
  @Test
  public void test2() {
    // results in 10 positions -> 5 packages
    final List<Point> points = newArrayList();
    for (double i = 0.5; i <= 5; i += 3) {
      for (double j = .5; j <= 5; j++) {
        if (i % 3 != 1) {
          points.add(new Point(i + (j * 0.1), j + (i * 0.1)));
        }
      }
    }

    final List<TimedEvent> events = newArrayList();
    for (int i = 0; i < points.size() / 2; i++) {
      events.add(newParcelEvent(points.get(i),
          points.get(points.size() - 1 - i)));
    }
    final Gendreau06Scenario testScen = GendreauTestUtil.create(events);
    final TestConfigurator tc = new TestConfigurator(solver, SI.SECOND);
    final StatisticsDTO stats = ExperimentTest.singleRun(testScen, tc, 123,
        Gendreau06ObjectiveFunction.instance(), false);
    assertEquals(1, tc.debuggers.size());

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    assertTrue(objFunc.isValidResult(stats));
    final double simObj = objFunc.computeCost(stats);
    final List<SolutionObject> solObjs = tc.debuggers.get(0).getOutputs();
    assertEquals(1, solObjs.size());

    final double solverObj = solObjs.get(0).objectiveValue / 60.0;
    assertEquals(simObj, solverObj, EPSILON);
    assertTrue(
        "the solver should have a slightly pessimistic view on the world",
        solverObj > simObj);
  }

  /**
   * Scenario where tardiness can not be avoided.
   */
  @Test
  public void test3() {
    // results in 10 positions -> 5 packages
    final List<Point> points = newArrayList();
    for (double i = 0.5; i <= 5; i += 3) {
      for (double j = .5; j <= 5; j++) {
        if (i % 3 != 1) {
          points.add(new Point(i + (j * 0.1), j + (i * 0.1)));
        }
      }
    }

    final List<TimeWindow> timeWindows = newArrayList();
    for (int i = 0; i < 10; i++) {
      final long startTime = i * 600000;
      timeWindows.add(new TimeWindow(startTime, startTime + 5400000));
    }

    final List<TimedEvent> events = newArrayList();
    for (int i = 0; i < points.size() / 2; i++) {
      events.add(newParcelEvent(points.get(i),
          points.get(points.size() - 1 - i), timeWindows.get(i),
          timeWindows.get(points.size() - 1 - i)));
    }

    final Gendreau06Scenario testScen = GendreauTestUtil.create(events);
    final TestConfigurator tc = new TestConfigurator(solver, SI.SECOND);
    final StatisticsDTO stats = ExperimentTest.singleRun(testScen, tc, 123,
        Gendreau06ObjectiveFunction.instance(), false);
    assertEquals(1, tc.debuggers.size());

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    assertTrue(objFunc.isValidResult(stats));
    final double simObj = objFunc.computeCost(stats);

    final List<SolutionObject> solObjs = tc.debuggers.get(0).getOutputs();
    assertEquals(1, solObjs.size());

    final double solverObj = solObjs.get(0).objectiveValue / 60.0;
    assertEquals(simObj, solverObj, EPSILON);
    assertTrue(
        "the solver should have a slightly pessimistic view on the world",
        solverObj > simObj);
  }

  static AddParcelEvent newParcelEvent(Point origin, Point destination) {
    return new AddParcelEvent(new ParcelDTO(origin, destination,
        new TimeWindow(0, 3600000), new TimeWindow(1800000, 5400000), 0, -1,
        300000, 300000));
  }

  static AddParcelEvent newParcelEvent(Point origin, Point destination,
      TimeWindow pickup, TimeWindow delivery) {
    return new AddParcelEvent(new ParcelDTO(origin, destination, pickup,
        delivery, 0, -1, 300000, 300000));
  }

  static class TestConfigurator extends DefaultMASConfiguration {
    final List<SVASDebugger> debuggers;
    final SingleVehicleArraysSolver solver;
    final Unit<Duration> timeUnit;

    public TestConfigurator(SingleVehicleArraysSolver solver,
        Unit<Duration> timeUnit) {
      this.solver = solver;
      this.timeUnit = timeUnit;
      debuggers = newArrayList();
    }

    @Override
    public Creator<AddVehicleEvent> getVehicleCreator() {
      return new Creator<AddVehicleEvent>() {
        @Override
        public boolean create(Simulator sim, AddVehicleEvent event) {
          final Communicator c = new RandomBidder(123);
          sim.register(c);

          final SVASDebugger sd = ArraysSolverDebugger.wrap(
              ArraysSolverValidator.wrap(solver), false);
          debuggers.add(sd);
          return sim.register(new Truck(event.vehicleDTO,
              new SolverRoutePlanner(new SingleVehicleSolverAdapter(sd,
                  timeUnit), true), c));
        }
      };
    }

    @Override
    public ImmutableList<? extends SupplierRng<? extends Model<?>>> getModels() {
      return ImmutableList.of(AuctionCommModel.supplier());
    }
  }

}
