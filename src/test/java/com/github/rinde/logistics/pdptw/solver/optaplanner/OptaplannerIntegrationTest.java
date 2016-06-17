/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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
package com.github.rinde.logistics.pdptw.solver.optaplanner;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.github.rinde.rinsim.central.Central;
import com.github.rinde.rinsim.central.rt.RtCentral;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.Experiment.SimulationResult;
import com.github.rinde.rinsim.experiment.ExperimentResults;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.experiment.PostProcessors;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.RouteRenderer;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.pdptw.common.TimeLinePanel;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.PDPModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;

/**
 *
 * @author Rinde van Lon
 */
public class OptaplannerIntegrationTest {

  @Test
  public void testSimulatedTime() {
    Experiment.builder()
      .withThreads(1)
      .addConfiguration(
        MASConfiguration.builder(Central
          .solverConfiguration(
            OptaplannerSolvers.builder()
              .withValidated(true)
              .withObjectiveFunction(Gendreau06ObjectiveFunction.instance(30d))
              .withUnimprovedMsLimit(1L)
              .withName("test")
              .buildSolverSupplier()))
          .addEventHandler(AddParcelEvent.class, AddParcelEvent.namedHandler())
          .build())
      .addScenarios(Gendreau06Parser.parser().addFile(new File(
        "files/scenarios/gendreau06/req_rapide_1_240_24"))
        .offline()
        .parse())
      .showGui(View.builder()
        .with(PlaneRoadModelRenderer.builder())
        .with(RoadUserRenderer.builder())
        .with(RouteRenderer.builder())
        .with(TimeLinePanel.builder())
        .withAutoPlay())
      .showGui(false)
      .perform();
  }

  @Test
  public void testRealtime() {

    final ExperimentResults results =
      Experiment.builder()
        .withThreads(1)
        .addConfiguration(
          MASConfiguration.builder(
            RtCentral.solverConfiguration(
              OptaplannerSolvers.builder()
                .withValidated(true)
                .withObjectiveFunction(Gendreau06ObjectiveFunction.instance())
                .withUnimprovedMsLimit(5000L)
                .withName("test")
                .buildRealtimeSolverSupplier(),
              ""))
            .addEventHandler(AddParcelEvent.class,
              AddParcelEvent.namedHandler())
            .build())
        .addScenarios(
          Gendreau06Parser.parser()
            .addFile(
              new File("files/scenarios/gendreau06/req_rapide_1_240_24"))
            .realtime()
            .setNumParcels(5)
            .parse())
        .usePostProcessor(PostProcessors
          .statisticsPostProcessor(Gendreau06ObjectiveFunction.instance()))
        .showGui(View.builder()
          .with(PlaneRoadModelRenderer.builder())
          .with(PDPModelRenderer.builder().withDestinationLines())
          // .with(RoadUserRenderer.builder().withToStringLabel())
          .with(RouteRenderer.builder())
          .with(TimeLinePanel.builder())
          .withSpeedUp(30)
          .withAutoPlay())
        .showGui(false)
        .perform();

    final StatisticsDTO stats =
      (StatisticsDTO) results.getResults().iterator().next().getResultObject();

    System.out.println(
      Gendreau06ObjectiveFunction.instance().printHumanReadableFormat(stats));
  }

  @Test
  public void testDeterminism() {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    final ExperimentResults results =
      Experiment.builder()
        .withThreads(3)
        .repeatSeed(3)
        .addConfiguration(
          MASConfiguration.builder(
            Central.solverConfiguration(
              OptaplannerSolvers.builder()
                .withValidated(true)
                .withCheapestInsertionSolver()
                .withObjectiveFunction(objFunc)
                .buildSolverSupplier(),
              ""))
            .build())
        .addConfiguration(
          MASConfiguration.builder(
            Central.solverConfiguration(
              OptaplannerSolvers.builder()
                .withValidated(true)
                .withFirstFitDecreasingSolver()
                .withObjectiveFunction(objFunc)
                .buildSolverSupplier(),
              ""))
            .build())
        .addConfiguration(
          MASConfiguration.builder(
            Central.solverConfiguration(
              OptaplannerSolvers.builder()
                .withValidated(true)
                .withFirstFitDecreasingWithTabuSolver()
                .withObjectiveFunction(objFunc)
                .withUnimprovedStepCountLimit(100)
                .buildSolverSupplier(),
              ""))
            .build())
        .addScenarios(
          Gendreau06Parser.parser()
            .addFile(new File("files/scenarios/gendreau06/req_rapide_1_240_24"))
            .offline()
            .setNumParcels(15)
            .parse())
        .usePostProcessor(PostProcessors.statisticsPostProcessor(objFunc))
        .perform();

    final Map<MASConfiguration, StatisticsDTO> resultsMap =
      new LinkedHashMap<>();
    for (final SimulationResult sr : results.getResults()) {
      final MASConfiguration config = sr.getSimArgs().getMasConfig();
      final StatisticsDTO stats = (StatisticsDTO) sr.getResultObject();
      if (resultsMap.containsKey(config)) {
        assertThat(stats).isEqualTo(resultsMap.get(config));
      } else {
        resultsMap.put(config, stats);
      }
    }
  }
}
