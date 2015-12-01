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
package com.github.rinde.logistics.pdptw.solver;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.measure.quantity.Duration;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.DoubleBid;
import com.github.rinde.logistics.pdptw.mas.comm.RandomBidder;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.central.arrays.ArraysSolverDebugger;
import com.github.rinde.rinsim.central.arrays.ArraysSolverDebugger.SVASDebugger;
import com.github.rinde.rinsim.central.arrays.ArraysSolverValidator;
import com.github.rinde.rinsim.central.arrays.SingleVehicleArraysSolver;
import com.github.rinde.rinsim.central.arrays.SingleVehicleSolverAdapter;
import com.github.rinde.rinsim.central.arrays.SolutionObject;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.experiment.ExperimentTestUtil;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.scenario.TimedEvent;
import com.github.rinde.rinsim.scenario.TimedEventHandler;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.scenario.gendreau06.GendreauTestUtil;
import com.github.rinde.rinsim.util.TimeWindow;

/**
 * Checks whether the objective as calculated by the simulator via
 * {@link Gendreau06ObjectiveFunction} is 'equal' to the objective as calculated
 * by the {@link SingleVehicleArraysSolver}. Note that due to the fact that the
 * solver works with integers (doubles are rounded up), a discrepancy is
 * expected. In fact, this discrepancy is checked to see if the objective
 * calculated by the {@link SingleVehicleArraysSolver} is always worse compared
 * to the objective calculated by the {@link Gendreau06ObjectiveFunction}.
 *
 * @author Rinde van Lon
 */
@RunWith(Parameterized.class)
public class SingleVehicleSolverTest {

  protected final SingleVehicleArraysSolver solver;

  static final double EPSILON = 0.1;

  public SingleVehicleSolverTest(SingleVehicleArraysSolver solver) {
    this.solver = solver;
  }

  @Parameters
  public static Collection<Object[]> configs() {
    return Arrays.asList(new Object[][] {//
        {new HeuristicSolver(new MersenneTwister(123))}});
  }

  @Test
  public void test() {
    final Point a = new Point(0, 0);
    final Point b = new Point(5, 5);
    final Point c = new Point(5, 0);
    final Point d = new Point(0, 5);
    final List<TimedEvent> events = newArrayList();
    events.add(newParcelEvent(a, b));
    events.add(newParcelEvent(c, d));

    final Gendreau06Scenario testScen = GendreauTestUtil.create(events);

    final Creator creator = new Creator(solver, SI.SECOND);
    final MASConfiguration tc = MASConfiguration.pdptwBuilder()
        .addEventHandler(AddVehicleEvent.class, creator)
        .addModel(AuctionCommModel.builder(DoubleBid.class))
        .addModel(SolverModel.builder())
        .build();
    final StatisticsDTO stats = ExperimentTestUtil.singleRun(testScen, tc, 123,
      Gendreau06ObjectiveFunction.instance(), false);
    assertEquals(1, creator.debuggers.size());

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    assertTrue("invalid result", objFunc.isValidResult(stats));
    final double simObj = objFunc.computeCost(stats);

    final List<SolutionObject> solObjs = creator.debuggers.get(0).getOutputs();
    assertEquals(1, solObjs.size());
    final double solverObj = solObjs.get(0).objectiveValue / 60.0;

    assertEquals(simObj, solverObj, EPSILON);
    assertTrue(
      "the solver should have a slightly pessimistic view on the world",
      solverObj > simObj);
  }

  /**
   * Scenario with no tardiness.
   */
  @Test
  public void test2() {
    // results in 10 positions -> 5 packages
    final List<Point> points = newArrayList();
    for (double i = 0.5; i <= 5; i += 3) {
      for (double j = .5; j <= 5; j++) {
        if (i % 3 != 1) {
          points.add(new Point(i + (j * 0.1), j + (i * 0.1)));
        }
      }
    }

    final List<TimedEvent> events = newArrayList();
    for (int i = 0; i < points.size() / 2; i++) {
      events.add(newParcelEvent(points.get(i),
        points.get(points.size() - 1 - i)));
    }
    final Gendreau06Scenario testScen = GendreauTestUtil.create(events);
    final Creator creator = new Creator(solver, SI.SECOND);
    final MASConfiguration tc = MASConfiguration.pdptwBuilder()
        .addEventHandler(AddVehicleEvent.class, creator)
        .addModel(AuctionCommModel.builder(DoubleBid.class))
        .addModel(SolverModel.builder())
        .build();
    final StatisticsDTO stats = ExperimentTestUtil.singleRun(testScen, tc, 123,
      Gendreau06ObjectiveFunction.instance(), false);
    assertEquals(1, creator.debuggers.size());

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    assertTrue(objFunc.isValidResult(stats));
    final double simObj = objFunc.computeCost(stats);
    final List<SolutionObject> solObjs = creator.debuggers.get(0).getOutputs();
    assertEquals(1, solObjs.size());

    final double solverObj = solObjs.get(0).objectiveValue / 60.0;
    assertEquals(simObj, solverObj, EPSILON);
    assertTrue(
      "the solver should have a slightly pessimistic view on the world",
      solverObj > simObj);
  }

  /**
   * Scenario where tardiness can not be avoided.
   */
  @Test
  public void test3() {
    // results in 10 positions -> 5 packages
    final List<Point> points = newArrayList();
    for (double i = 0.5; i <= 5; i += 3) {
      for (double j = .5; j <= 5; j++) {
        if (i % 3 != 1) {
          points.add(new Point(i + (j * 0.1), j + (i * 0.1)));
        }
      }
    }

    final List<TimeWindow> timeWindows = newArrayList();
    for (int i = 0; i < 10; i++) {
      final long startTime = i * 600000;
      timeWindows.add(TimeWindow.create(startTime, startTime + 5400000));
    }

    final List<TimedEvent> events = newArrayList();
    for (int i = 0; i < points.size() / 2; i++) {
      events.add(newParcelEvent(points.get(i),
        points.get(points.size() - 1 - i), timeWindows.get(i),
        timeWindows.get(points.size() - 1 - i)));
    }

    final Gendreau06Scenario testScen = GendreauTestUtil.create(events);

    final Creator creator = new Creator(solver, SI.SECOND);
    final MASConfiguration tc = MASConfiguration.pdptwBuilder()
        .addEventHandler(AddVehicleEvent.class, creator)
        .addModel(AuctionCommModel.builder(DoubleBid.class))
        .addModel(SolverModel.builder())
        .build();
    final StatisticsDTO stats = ExperimentTestUtil.singleRun(testScen, tc, 123,
      Gendreau06ObjectiveFunction.instance(), false);
    assertEquals(1, creator.debuggers.size());

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    assertTrue(objFunc.isValidResult(stats));
    final double simObj = objFunc.computeCost(stats);

    final List<SolutionObject> solObjs = creator.debuggers.get(0).getOutputs();
    assertEquals(1, solObjs.size());

    final double solverObj = solObjs.get(0).objectiveValue / 60.0;
    assertEquals(simObj, solverObj, EPSILON);
    assertTrue(
      "the solver should have a slightly pessimistic view on the world",
      solverObj > simObj);
  }

  static AddParcelEvent newParcelEvent(Point origin, Point destination) {
    return AddParcelEvent.create(
      Parcel.builder(origin, destination)
          .pickupTimeWindow(TimeWindow.create(0, 3600000))
          .deliveryTimeWindow(TimeWindow.create(1800000, 5400000))
          .neededCapacity(0)
          .orderAnnounceTime(-1L)
          .pickupDuration(300000L)
          .deliveryDuration(300000L)
          .buildDTO());
  }

  static AddParcelEvent newParcelEvent(Point origin, Point destination,
      TimeWindow pickup, TimeWindow delivery) {
    return AddParcelEvent.create(
      Parcel.builder(origin, destination)
          .pickupTimeWindow(pickup)
          .deliveryTimeWindow(delivery)
          .neededCapacity(0)
          .orderAnnounceTime(-1L)
          .pickupDuration(300000L)
          .deliveryDuration(300000L)
          .buildDTO());
  }

  static class Creator implements TimedEventHandler<AddVehicleEvent>,
      Serializable {
    private static final long serialVersionUID = 6621784477214246735L;
    final List<SVASDebugger> debuggers;
    final SingleVehicleArraysSolver solver;
    final Unit<Duration> timeUnit;

    Creator(SingleVehicleArraysSolver s, Unit<Duration> tu) {
      solver = s;
      timeUnit = tu;
      debuggers = newArrayList();
    }

    @Override
    public void handleTimedEvent(AddVehicleEvent event,
        SimulatorAPI simulator) {
      final Communicator c = new RandomBidder(123);
      simulator.register(c);

      final SVASDebugger sd = ArraysSolverDebugger.wrap(
        ArraysSolverValidator.wrap(solver), false);
      debuggers.add(sd);
      simulator.register(new Truck(event.getVehicleDTO(),
          new SolverRoutePlanner(new SingleVehicleSolverAdapter(sd, timeUnit),
              true),
          c, RouteFollowingVehicle.nopAdjuster(), true));
    }
  }

  // static class TestConfigurator extends DefaultMASConfiguration {
  // final List<SVASDebugger> debuggers;
  // final SingleVehicleArraysSolver solver;
  // final Unit<Duration> timeUnit;
  //
  // public TestConfigurator(SingleVehicleArraysSolver solver,
  // Unit<Duration> timeUnit) {
  // this.solver = solver;
  // this.timeUnit = timeUnit;
  // debuggers = newArrayList();
  // }
  //
  // @Override
  // public Creator<AddVehicleEvent> getVehicleCreator() {
  // return new Creator<AddVehicleEvent>() {
  // @Override
  // public boolean create(Simulator sim, AddVehicleEvent event) {
  // final Communicator c = new RandomBidder(123);
  // sim.register(c);
  //
  // final SVASDebugger sd = ArraysSolverDebugger.wrap(
  // ArraysSolverValidator.wrap(solver), false);
  // debuggers.add(sd);
  // sim.register(new Truck(event.getVehicleDTO(),
  // new SolverRoutePlanner(new SingleVehicleSolverAdapter(sd,
  // timeUnit), true), c));
  // return true;
  // }
  // };
  // }
  //
  // @Override
  // public ImmutableList<? extends StochasticSupplier<? extends Model<?>>>
  // getModels() {
  // return ImmutableList.of(AuctionCommModel.supplier());
  // }
  // }

}
