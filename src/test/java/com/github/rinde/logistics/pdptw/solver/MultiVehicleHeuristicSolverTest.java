/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
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

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;

import com.github.rinde.rinsim.central.Central;
import com.github.rinde.rinsim.central.DebugSolverCreator;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.arrays.ArraysSolvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.scenario.TimedEvent;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.scenario.gendreau06.GendreauTestUtil;
import com.github.rinde.rinsim.util.TimeWindow;

/**
 * @author Rinde van Lon
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
      .build(Gendreau06ObjectiveFunction.instance())
      .addConfiguration(
        // not good settings, but fast!
        Central.solverConfiguration(MultiVehicleHeuristicSolver.supplier(
          50, 100, false, true)))
      .addScenario(
        Gendreau06Parser.parse(new File(
          "files/scenarios/gendreau06/req_rapide_1_240_24"))).perform();
  }

  /**
   * Tests the correctness of the computation of the objective value of
   * {@link MultiVehicleHeuristicSolver}.
   */
  @Test
  public void testObjectiveValue() {
    final List<TimedEvent> parcels = newArrayList();
    parcels.add(AddParcelEvent.create(
      Parcel.builder(new Point(1, 1), new Point(2, 2))
        .pickupTimeWindow(new TimeWindow(10, 100000))
        .deliveryTimeWindow(new TimeWindow(0, 1000000))
        .neededCapacity(0)
        .orderAnnounceTime(10L)
        .pickupDuration(300000L)
        .deliveryDuration(300000L)
        .buildDTO()));
    parcels.add(AddParcelEvent.create(
      Parcel.builder(new Point(3, 1), new Point(0.1, 3))
        .pickupTimeWindow(new TimeWindow(10, 100))
        .deliveryTimeWindow(new TimeWindow(0, 100))
        .neededCapacity(0)
        .orderAnnounceTime(10L)
        .pickupDuration(300000L)
        .deliveryDuration(300000L)
        .buildDTO()));
    parcels.add(AddParcelEvent.create(
      Parcel.builder(new Point(1, 4.5), new Point(2, 5))
        .pickupTimeWindow(new TimeWindow(10, 100))
        .deliveryTimeWindow(new TimeWindow(0, 100))
        .neededCapacity(0)
        .orderAnnounceTime(10L)
        .pickupDuration(300000L)
        .deliveryDuration(300000L)
        .buildDTO()));
    parcels.add(AddParcelEvent.create(
      Parcel.builder(new Point(1, 5), new Point(0, 2))
        .pickupTimeWindow(new TimeWindow(10, 100))
        .deliveryTimeWindow(new TimeWindow(0, 100))
        .neededCapacity(0)
        .orderAnnounceTime(10L)
        .pickupDuration(300000L)
        .deliveryDuration(300000L)
        .buildDTO()));

    final Gendreau06Scenario scenario = GendreauTestUtil.createWithTrucks(parcels, 2);
    final DebugSolverCreator dsc = new DebugSolverCreator(
      new MultiVehicleHeuristicSolver(new MersenneTwister(123), 50, 1000,
        false, true), SI.MILLI(SI.SECOND));
    final Gendreau06ObjectiveFunction objFunc = Gendreau06ObjectiveFunction
      .instance();
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
