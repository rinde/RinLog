/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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
package com.github.rinde.logistics.pdptw.mas.route;

import org.junit.Before;
import org.junit.Test;

import com.github.rinde.logistics.pdptw.mas.VehicleHandler;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.DoubleBid;
import com.github.rinde.logistics.pdptw.mas.comm.RtSolverBidder;
import com.github.rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSolverModel;
import com.github.rinde.rinsim.central.rt.SleepySolver;
import com.github.rinde.rinsim.central.rt.SolverToRealtimeAdapter;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.time.TimeModel;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.AddDepotEvent;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.scenario.Scenario;
import com.github.rinde.rinsim.scenario.TimeOutEvent;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.TimeWindow;

/**
 *
 * @author Rinde van Lon
 */
public class RtRoutePlannerTest {

  @Before
  public void setUp() {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    final StochasticSupplier<RealtimeSolver> rtSolverSup =
      SolverToRealtimeAdapter.create(
        SleepySolver.create(500, CheapestInsertionHeuristic.supplier(objFunc)));

    final Scenario scenario = Scenario.builder()
        .addEvent(AddDepotEvent.create(-1, new Point(5, 5)))
        .addEvent(AddVehicleEvent.create(-1, VehicleDTO.builder().build()))
        .addEvent(AddVehicleEvent.create(-1, VehicleDTO.builder().build()))
        .addEvent(AddParcelEvent.create(
          Parcel.builder(new Point(0, 0), new Point(1, 0))
              .orderAnnounceTime(300)
              .pickupTimeWindow(TimeWindow.create(400, 3000))
              .buildDTO()))
        .addEvent(AddParcelEvent.create(
          Parcel.builder(new Point(0, 0), new Point(1, 0))
              .orderAnnounceTime(800)
              .pickupTimeWindow(TimeWindow.create(800, 3000))
              .buildDTO()))
        .addEvent(TimeOutEvent.create(1500))
        .addModel(PDPRoadModel.builder(RoadModelBuilders.plane()))
        .addModel(DefaultPDPModel.builder())
        .addModel(TimeModel.builder().withRealTime())
        .build();

    Experiment.build(objFunc)
        .addScenario(scenario)
        .addConfiguration(MASConfiguration.pdptwBuilder()
            .addModel(RtSolverModel.builder())
            .addModel(AuctionCommModel.builder(DoubleBid.class))
            .addEventHandler(AddVehicleEvent.class,
              new VehicleHandler(RtSolverRoutePlanner.supplier(rtSolverSup),
                  RtSolverBidder.supplier(objFunc, rtSolverSup)))
            .build())
        .perform();

  }

  @Test
  public void test() {

  }

}
