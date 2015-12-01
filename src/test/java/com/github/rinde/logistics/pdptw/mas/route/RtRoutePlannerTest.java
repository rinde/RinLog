/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import static com.google.common.truth.Truth.assertThat;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.logistics.pdptw.mas.TruckFactory.DefaultTruckFactory;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel.AuctionEvent;
import com.github.rinde.logistics.pdptw.mas.comm.DoubleBid;
import com.github.rinde.logistics.pdptw.mas.comm.RtSolverBidder;
import com.github.rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSolverModel;
import com.github.rinde.rinsim.central.rt.SleepySolver;
import com.github.rinde.rinsim.central.rt.SolverToRealtimeAdapter;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.Model.AbstractModelVoid;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.core.model.time.TimeModel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.Experiment.SimArgs;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.experiment.PostProcessor;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.AddDepotEvent;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.scenario.Scenario;
import com.github.rinde.rinsim.scenario.StopConditions;
import com.github.rinde.rinsim.scenario.TimeOutEvent;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 *
 * @author Rinde van Lon
 */
public class RtRoutePlannerTest {

  @SuppressWarnings("null")
  Scenario scenario;

  @Before
  public void setUp() {
    scenario = Scenario.builder()
        .addEvent(AddDepotEvent.create(-1, new Point(5, 5)))
        .addEvent(AddVehicleEvent.create(-1, VehicleDTO.builder()
            .availabilityTimeWindow(TimeWindow.create(0, 1500)).build()))
        .addEvent(AddVehicleEvent.create(-1, VehicleDTO.builder()
            .availabilityTimeWindow(TimeWindow.create(0, 1500)).build()))
        .addEvent(AddParcelEvent.create(
          Parcel.builder(new Point(0, 0), new Point(1, 0))
              .orderAnnounceTime(300)
              .pickupTimeWindow(TimeWindow.create(300, 3000))
              .buildDTO()))
        .addEvent(AddParcelEvent.create(
          Parcel.builder(new Point(0, 0), new Point(1, 0))
              .orderAnnounceTime(1500)
              .pickupTimeWindow(TimeWindow.create(1500, 30000))
              .buildDTO()))
        .addEvent(AddParcelEvent.create(
          Parcel.builder(new Point(1, 1), new Point(2, 2))
              .orderAnnounceTime(1500)
              .pickupTimeWindow(TimeWindow.create(1500, 30000))
              .buildDTO()))
        .addEvent(TimeOutEvent.create(1500))
        .addModel(PDPRoadModel.builder(RoadModelBuilders.plane()))
        .addModel(DefaultPDPModel.builder())
        .addModel(TimeModel.builder().withRealTime().withTickLength(100L))
        .setStopCondition(StopConditions.limitedTime(60000))
        .build();
  }

  @Test
  public void test() {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    final StochasticSupplier<RealtimeSolver> rtSolverSup =
      SolverToRealtimeAdapter.create(
        SleepySolver.create(500, CheapestInsertionHeuristic.supplier(objFunc)));

    Experiment.build(objFunc)
        .addScenario(scenario)
        .withThreads(1)
        .addConfiguration(MASConfiguration.pdptwBuilder()
            .addModel(RtSolverModel.builder())
            .addModel(AuctionCommModel.builder(DoubleBid.class))
            .addModel(AuctionCommModelLogger.builder())
            .addEventHandler(AddVehicleEvent.class,
              DefaultTruckFactory.builder()
                  .setRoutePlanner(RtSolverRoutePlanner.supplier(rtSolverSup))
                  .setCommunicator(
                    RtSolverBidder.supplier(objFunc, rtSolverSup))
                  .setRouteAdjuster(RouteFollowingVehicle.delayAdjuster())
                  .setLazyComputation(false)
                  .build())
            .build())
        .usePostProcessor(new PostProcessor<Object>() {
          @Override
          public Object collectResults(Simulator sim, SimArgs args) {
            final AuctionCommModelLogger logger =
              sim.getModelProvider().getModel(AuctionCommModelLogger.class);

            System.out.println(logger.events);
            assertThat(logger.events.keySet().size()).isEqualTo(3);
            for (final Entry<Parcel, Collection<Enum<?>>> entry : logger.events
                .asMap().entrySet()) {

              assertThat(entry.getValue())
                  .containsExactly(AuctionCommModel.EventType.START_AUCTION,
                    AuctionCommModel.EventType.FINISH_AUCTION)
                  .inOrder();

            }

            final RoadModel rm =
              sim.getModelProvider().getModel(RoadModel.class);

            final Set<Truck> trucks = rm.getObjectsOfType(Truck.class);

            for (final Truck t : trucks) {
              System.out.println(t.getRoute());
            }

            return new Object();
          }

          @Override
          public FailureStrategy handleFailure(
              Exception e, Simulator sim, SimArgs args) {
            return FailureStrategy.ABORT_EXPERIMENT_RUN;
          }
        })
        .perform();
  }

  static class AuctionCommModelLogger extends AbstractModelVoid {
    final ListMultimap<Parcel, Enum<?>> events;

    AuctionCommModelLogger(AuctionCommModel model) {
      events = ArrayListMultimap.create();

      model.getEventAPI().addListener(new Listener() {
        @Override
        public void handleEvent(Event e) {
          final AuctionEvent ae = (AuctionEvent) e;
          events.put(ae.getParcel(), e.getEventType());
        }
      }, AuctionCommModel.EventType.values());
    }

    static Builder builder() {
      return new AutoValue_RtRoutePlannerTest_AuctionCommModelLogger_Builder();
    }

    @AutoValue
    static class Builder
        extends AbstractModelBuilder<AuctionCommModelLogger, Void>
        implements Serializable {

      Builder() {
        setDependencies(AuctionCommModel.class);
      }

      @Override
      public AuctionCommModelLogger build(
          DependencyProvider dependencyProvider) {

        final AuctionCommModel model =
          dependencyProvider.get(AuctionCommModel.class);

        return new AuctionCommModelLogger(model);
      }
    }
  }

}
