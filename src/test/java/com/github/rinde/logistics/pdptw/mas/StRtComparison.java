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
package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Paths;

import org.junit.Test;

import com.github.rinde.logistics.pdptw.mas.TruckFactory.DefaultTruckFactory;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionPanel;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionStopConditions;
import com.github.rinde.logistics.pdptw.mas.comm.Bidder;
import com.github.rinde.logistics.pdptw.mas.comm.DoubleBid;
import com.github.rinde.logistics.pdptw.mas.comm.RtSolverBidder;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RtSolverRoutePlanner;
import com.github.rinde.rinsim.central.RandomSolver;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSolverModel;
import com.github.rinde.rinsim.central.rt.RtStAdapters;
import com.github.rinde.rinsim.core.model.ModelBuilder;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.ExperimentResults;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.experiment.PostProcessors;
import com.github.rinde.rinsim.io.FileProvider;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.RoutePanel;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.pdptw.common.TimeLinePanel;
import com.github.rinde.rinsim.scenario.ScenarioConverters;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.PDPModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.base.Functions;

/**
 *
 * @author Rinde van Lon
 */
public class StRtComparison {

  static final ObjectiveFunction OBJ_FUNC =
    Gendreau06ObjectiveFunction.instance();

  @Test
  public void test() {
    final StochasticSupplier<Solver> randomSolver = RandomSolver.supplier();
    final StochasticSupplier<RealtimeSolver> rtRandomSolver =
      RtStAdapters.toRealtime(randomSolver);

    final StochasticSupplier<Bidder<DoubleBid>> stBidder =
      RtSolverBidder.simulatedTimeBuilder(OBJ_FUNC, randomSolver);
    final StochasticSupplier<Bidder<DoubleBid>> rtBidder =
      RtSolverBidder.realtimeBuilder(OBJ_FUNC, rtRandomSolver);

    final StochasticSupplier<RoutePlanner> rtRoutePlanner =
      RtSolverRoutePlanner.supplier(rtRandomSolver);
    final StochasticSupplier<RoutePlanner> stRoutePlanner =
      RtSolverRoutePlanner.simulatedTimeSupplier(randomSolver);

    final FileProvider.Builder fileProviderBuilder = FileProvider.builder()
      .add(Paths.get("files/scenarios/gendreau06"))
      .filter("glob:**req_rapide**");

    final View.Builder gui = View.builder()
      .withAutoPlay()
      .with(PlaneRoadModelRenderer.builder())
      .with(PDPModelRenderer.builder())
      .with(AuctionPanel.builder())
      .with(RoutePanel.builder())
      .with(TimeLinePanel.builder());

    final ModelBuilder<?, ?> auctionModel =
      AuctionCommModel.builder(DoubleBid.class)
        .withStopCondition(
          AuctionStopConditions.and(
            AuctionStopConditions.<DoubleBid>atLeastNumBids(2),
            AuctionStopConditions.<DoubleBid>or(
              AuctionStopConditions.<DoubleBid>allBidders(),
              AuctionStopConditions.<DoubleBid>maxAuctionDuration(5000))))
        .withMaxAuctionDuration(30 * 60 * 1000L);

    final Experiment.Builder stExperiment = Experiment.builder()
      .addScenarios(fileProviderBuilder)
      .setScenarioReader(Gendreau06Parser.reader())
      .usePostProcessor(PostProcessors.statisticsPostProcessor(OBJ_FUNC))
      // .showGui(gui)
      .withThreads(1)
      .addConfiguration(
        MASConfiguration.pdptwBuilder()
          .addEventHandler(AddVehicleEvent.class,
            DefaultTruckFactory.builder()
              .setRoutePlanner(stRoutePlanner)
              .setCommunicator(stBidder)
              .setLazyComputation(false)
              .build())
          .addModel(auctionModel)
          .addModel(SolverModel.builder())
          .build());

    final Experiment.Builder rtExperiment = Experiment.builder()
      .addScenarios(fileProviderBuilder)
      .setScenarioReader(Functions.compose(
        ScenarioConverters.toRealtime(),
        Gendreau06Parser.reader()))
      .usePostProcessor(PostProcessors.statisticsPostProcessor(OBJ_FUNC))
      .showGui(gui)
      .showGui(false)
      .withThreads(1)
      .addConfiguration(
        MASConfiguration.pdptwBuilder()
          .addEventHandler(AddVehicleEvent.class,
            DefaultTruckFactory.builder()
              .setRoutePlanner(rtRoutePlanner)
              .setCommunicator(rtBidder)
              .build())
          .addModel(auctionModel)
          .addModel(RtSolverModel.builder())
          .build());

    final ExperimentResults stResults = stExperiment.perform();
    final ExperimentResults rtResults = rtExperiment.perform();

    assertThat(stResults.getResults()).hasSize(1);
    assertThat(rtResults.getResults()).hasSize(1);

    final StatisticsDTO stStats =
      (StatisticsDTO) stResults.getResults().iterator().next()
        .getResultObject();
    final StatisticsDTO rtStats =
      (StatisticsDTO) rtResults.getResults().iterator().next()
        .getResultObject();

    System.out.println(stStats);
    System.out.println(rtStats);

    assertThat(stStats).isEqualTo(rtStats);

  }

}
