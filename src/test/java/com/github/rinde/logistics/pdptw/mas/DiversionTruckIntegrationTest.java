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

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.logistics.pdptw.mas.TruckFactory.DefaultTruckFactory;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.DoubleBid;
import com.github.rinde.logistics.pdptw.mas.comm.SolverBidder;
import com.github.rinde.logistics.pdptw.mas.route.GotoClosestRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.rinsim.central.RandomSolver;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.scenario.gendreau06.GendreauProblemClass;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

@RunWith(Parameterized.class)
public class DiversionTruckIntegrationTest {

  static final ObjectiveFunction GENDREAU_OBJ_FUNC = Gendreau06ObjectiveFunction
      .instance();

  final MASConfiguration config;
  final ObjectiveFunction objectiveFunction;
  final int numTrucks;

  public DiversionTruckIntegrationTest(MASConfiguration tc,
      ObjectiveFunction objFunc, int numT) {
    config = tc;
    objectiveFunction = objFunc;
    numTrucks = numT;
  }

  @Parameters
  public static Collection<Object[]> configs() {

    final ImmutableList<StochasticSupplier<? extends RoutePlanner>> routePlanners =
      ImmutableList
          .<StochasticSupplier<? extends RoutePlanner>>of(
            GotoClosestRoutePlanner.supplier(),
            RandomRoutePlanner.supplier(),
            SolverRoutePlanner.supplier(RandomSolver.supplier()));

    final ImmutableList<StochasticSupplier<? extends Communicator>> communicators =
      ImmutableList
          .<StochasticSupplier<? extends Communicator>>of(SolverBidder
              .supplier(GENDREAU_OBJ_FUNC, RandomSolver.supplier()));

    final ImmutableList<Integer> numTrucks = ImmutableList
        .of(1, 2, 3, 4, 5, 10, 15);

    final List<Object[]> configs = newArrayList();
    for (final StochasticSupplier<? extends RoutePlanner> rp : routePlanners) {
      for (final StochasticSupplier<? extends Communicator> cm : communicators) {
        for (final int i : numTrucks) {
          configs.add(new Object[] {
              MASConfiguration.pdptwBuilder()
                  .addEventHandler(AddVehicleEvent.class,
                    DefaultTruckFactory.builder()
                        .setRoutePlanner(rp)
                        .setCommunicator(cm)
                        .build())
                  .addModel(AuctionCommModel.builder(DoubleBid.class))
                  .addModel(SolverModel.builder())
                  .build(),

              // new TruckConfiguration(rp, cm,
              // ImmutableList.of(AuctionCommModel
              // .supplier())),
              GENDREAU_OBJ_FUNC, i});
        }
      }
    }
    return configs;
  }

  @Test
  public void integration() {
    final Gendreau06Scenario scen = Gendreau06Parser.parse(new File(
        "files/scenarios/gendreau06/req_rapide_1_240_24"));

    final ImmutableList<AddParcelEvent> events = FluentIterable
        .from(scen.getEvents())
        .filter(AddParcelEvent.class)
        .limit(100)
        .toList();

    final List<Gendreau06Scenario> onlineScenarios = Gendreau06Parser
        .parser()
        .allowDiversion()
        .setNumVehicles(numTrucks)
        .addFile(events, "req_rapide_1_240_24")
        .filter(GendreauProblemClass.SHORT_LOW_FREQ)
        .parse();

    final Experiment.Builder builder = Experiment.build(objectiveFunction)
        .withRandomSeed(123)
        .repeat(1)
        .withThreads(1)
        .addScenarios(onlineScenarios);

    builder.addConfiguration(config);
    builder.perform();
  }
}
