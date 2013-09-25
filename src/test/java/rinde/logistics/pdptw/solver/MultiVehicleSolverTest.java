/**
 * 
 */
package rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.util.List;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;

import rinde.sim.core.graph.Point;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.central.Central.SolverCreator;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.arrays.ArraysSolverDebugger;
import rinde.sim.pdptw.central.arrays.ArraysSolverDebugger.MVASDebugger;
import rinde.sim.pdptw.central.arrays.ArraysSolverValidator;
import rinde.sim.pdptw.central.arrays.ArraysSolvers;
import rinde.sim.pdptw.central.arrays.MultiVehicleSolverAdapter;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauTestUtil;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;

import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class MultiVehicleSolverTest {

  @Test
  public void test() {
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

    final Gendreau06ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
    final ExperimentResults er = Experiment.build(objFunc)
        .addScenario(scenario)
        // .showGui()
        .addConfigurator(Central.solverConfigurator(new TestSolverCreator()))
        .perform();

    System.out.println("Sim obj: "
        + objFunc.computeCost(er.results.get(0).stats));
  }

  static class TestSolverCreator implements SolverCreator {

    @Override
    public Solver create(long seed) {
      return new TestSolver();
    }
  }

  static class TestSolver implements Solver {

    private final Solver solver;
    private final MVASDebugger mvhSolver;

    TestSolver() {
      mvhSolver = ArraysSolverDebugger.wrap(new MultiVehicleHeuristicSolver(
          new MersenneTwister(123), 10, 100), false);
      solver = SolverValidator.wrap(new MultiVehicleSolverAdapter(
          ArraysSolverValidator.wrap(mvhSolver), SI.SECOND));
    }

    @Override
    public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
      System.out.println(state);

      final ImmutableList<ImmutableList<ParcelDTO>> routes = solver
          .solve(state);

      checkArgument(mvhSolver.getOutputs().size() == 1);

      System.out.println("total obj: "
          + ArraysSolvers.computeTotalObjectiveValue(mvhSolver
              .getOutputs().get(0), SI.SECOND, NonSI.MINUTE));

      System.out.println(routes);
      return routes;
    }
  }

}
