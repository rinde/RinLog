package rinde.logistics.pdptw.solver;

import static org.junit.Assert.assertEquals;
import static rinde.logistics.pdptw.solver.CheapestInsertionHeuristic.modifySchedule;

import java.io.IOException;

import org.junit.Test;

import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;

import com.google.common.collect.ImmutableList;

public class CheapestInsertionHeuristicTest {

  String A = "A", B = "B", C = "C";

  @Test
  public void test() throws IOException {
    final ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
    // try test in RinLog?
    final ExperimentResults er = Experiment
        .build(objFunc)
        .addScenario(
            Gendreau06Parser.parse(
                "files/scenarios/gendreau06/req_rapide_1_240_24", 10))
        .addConfiguration(
            Central.solverConfiguration(SolverValidator
                .wrap(CheapestInsertionHeuristic.supplier(objFunc)))).repeat(3)
        .withThreads(2).perform();
    for (int i = 0; i < er.results.size(); i++) {
      assertEquals(979.898336, objFunc.computeCost(er.results.get(i).stats),
          0.0001);
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void modifyScheduleTest() {
    final ImmutableList<ImmutableList<String>> schedule = schedule(r(A), r(B));
    assertEquals(schedule(r(C), r(B)),
        modifySchedule(schedule, ImmutableList.of(C), 0));
    assertEquals(schedule(r(A), r(C)),
        modifySchedule(schedule, ImmutableList.of(C), 1));
  }

  @SuppressWarnings("unchecked")
  @Test(expected = IllegalArgumentException.class)
  public void modifyScheduleArgFail1() {
    modifySchedule(schedule(r(A), r(B)), r(C), 2);
  }

  @SuppressWarnings("unchecked")
  @Test(expected = IllegalArgumentException.class)
  public void modifyScheduleArgFail2() {
    modifySchedule(schedule(r(A), r(B)), r(C), -1);
  }

  static ImmutableList<String> r(String... s) {
    return ImmutableList.copyOf(s);
  }

  static ImmutableList<ImmutableList<String>> schedule(
      ImmutableList<String>... s) {
    return ImmutableList.copyOf(s);
  }
}
