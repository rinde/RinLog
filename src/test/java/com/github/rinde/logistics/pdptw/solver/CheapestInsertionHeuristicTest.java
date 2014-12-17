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
package com.github.rinde.logistics.pdptw.solver;

import static com.github.rinde.logistics.pdptw.solver.CheapestInsertionHeuristic.modifyCosts;
import static com.github.rinde.logistics.pdptw.solver.CheapestInsertionHeuristic.modifySchedule;
import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

import com.github.rinde.rinsim.central.Central;
import com.github.rinde.rinsim.central.SolverValidator;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.ExperimentResults;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.google.common.collect.ImmutableList;

public class CheapestInsertionHeuristicTest {

  String A = "A", B = "B", C = "C";

  /**
   * Tests whether the insertion heuristic keeps giving the same result on an
   * entire scenario.
   */
  @Test
  public void consistency() {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    // try test in RinLog?
    final ExperimentResults er = Experiment
        .build(objFunc)
        .addScenario(
            Gendreau06Parser.parse(new File(
                "files/scenarios/gendreau06/req_rapide_1_240_24")))
        .addConfiguration(
            Central.solverConfiguration(SolverValidator
                .wrap(CheapestInsertionHeuristic.supplier(objFunc))))
        .repeat(3)
        .withThreads(3)
        .perform();
    for (int i = 0; i < er.results.size(); i++) {
      assertEquals(979.898336,
          objFunc.computeCost(er.results.asList().get(i).stats),
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

  @Test
  public void modifiyCostsTest() {

    final ImmutableList<Double> result = modifyCosts(
        ImmutableList.of(1d, 2d, 3d, 4d), 8d, 2);
    assertEquals(ImmutableList.of(1d, 2d, 8d, 4d), result);
  }

  static ImmutableList<String> r(String... s) {
    return ImmutableList.copyOf(s);
  }

  static ImmutableList<ImmutableList<String>> schedule(
      ImmutableList<String>... s) {
    return ImmutableList.copyOf(s);
  }
}
