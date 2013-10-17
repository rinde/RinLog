/**
 * 
 */
package rinde.logistics.pdptw.solver;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;

import rinde.sim.core.graph.Point;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.central.DebugSolverCreator;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.arrays.ArraysSolvers;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.common.StatisticsDTO;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauTestUtil;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class MultiVehicleHeuristicSolverTest {

  /**
   * Tests the validity of the {@link MultiVehicleHeuristicSolver}.
   * @throws IOException When file loading fails.
   */
  @Test
  public void testValidity() throws IOException {
    Experiment
        .build(new Gendreau06ObjectiveFunction())
        .addConfiguration(
        // not good settings, but fast!
            Central.solverConfiguration(MultiVehicleHeuristicSolver.supplier(
                50, 100, false, true)))
        .addScenario(
            Gendreau06Parser.parse(
                "files/scenarios/gendreau06/req_rapide_1_240_24", 10))
        .perform();
  }

  /**
   * Tests the correctness of the computation of the objective value of
   * {@link MultiVehicleHeuristicSolver}.
   */
  @Test
  public void testObjectiveValue() {
    final List<TimedEvent> parcels = newArrayList();
    parcels.add(new AddParcelEvent(new ParcelDTO(new Point(1, 1), new Point(2,
        2), new TimeWindow(10, 100000), new TimeWindow(0, 1000000), 0, 10,
        300000, 300000)));
    parcels.add(new AddParcelEvent(new ParcelDTO(new Point(3, 1), new Point(
        0.1, 3), new TimeWindow(10, 100), new TimeWindow(0, 100), 0, 10,
        300000, 300000)));
    parcels.add(new AddParcelEvent(new ParcelDTO(new Point(1, 4.5), new Point(
        2, 5), new TimeWindow(10, 100), new TimeWindow(0, 100), 0, 10, 300000,
        300000)));
    parcels.add(new AddParcelEvent(new ParcelDTO(new Point(1, 5), new Point(0,
        2), new TimeWindow(10, 100), new TimeWindow(0, 100), 0, 10, 300000,
        300000)));

    final Gendreau06Scenario scenario = GendreauTestUtil.create(parcels, 2);
    final DebugSolverCreator dsc = new DebugSolverCreator(
        new MultiVehicleHeuristicSolver(new MersenneTwister(123), 50, 1000,
            false, true), SI.MILLI(SI.SECOND));
    final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
    Experiment.build(objFunc).addScenario(scenario)
        .addConfiguration(Central.solverConfiguration(dsc)).perform();

    for (int i = 0; i < dsc.solver.getInputs().size(); i++) {
      final double arrObjVal = ArraysSolvers
          .computeTotalObjectiveValue(dsc.arraysSolver.getOutputs().get(i)) / 60000d;
      final StatisticsDTO stats = Solvers.computeStats(dsc.solver.getInputs()
          .get(i), dsc.solver.getOutputs().get(i));
      final double objVal = objFunc.computeCost(stats);
      assertEquals(arrObjVal, objVal, 0.01);
    }
  }

}
