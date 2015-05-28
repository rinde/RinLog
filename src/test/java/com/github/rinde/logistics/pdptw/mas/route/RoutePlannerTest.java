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
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.central.arrays.RandomMVArraysSolver;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.core.model.time.TimeLapseFactory;
import com.github.rinde.rinsim.experiment.ExperimentTest;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.TimedEvent;
import com.github.rinde.rinsim.scenario.TimedEventHandler;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.scenario.gendreau06.GendreauTestUtil;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.collect.ImmutableSet;

/**
 * Tests all known implementations of the {@link RoutePlanner} interface.
 * @author Rinde van Lon
 */
@RunWith(Parameterized.class)
public class RoutePlannerTest {
  protected final StochasticSupplier<RoutePlanner> supplier;
  protected RoutePlanner routePlanner;
  protected RoadModel roadModel;
  protected PDPModel pdpModel;
  protected Simulator simulator;
  protected Vehicle truck;

  public RoutePlannerTest(StochasticSupplier<RoutePlanner> rp) {
    supplier = rp;
  }

  @Parameters
  public static Collection<Object[]> configs() {
    return Arrays
      .asList(new Object[][] {
          { RandomRoutePlanner.supplier() },
          { SolverRoutePlanner.supplier(MultiVehicleHeuristicSolver.supplier(
            50, 100)) },
          { SolverRoutePlanner.supplier(RandomMVArraysSolver.solverSupplier()) },
          { GotoClosestRoutePlanner.supplier() },
          { TestRoutePlanner.supplier() } });
  }

  @Before
  public void setUp() {
    routePlanner = supplier.get(123);
    final int numOnMap = 10;
    final int numInCargo = 10;

    final RandomGenerator rng = new MersenneTwister(123);
    final List<TimedEvent> events = newLinkedList();
    for (int i = 0; i < numOnMap; i++) {
      events.add(newParcelEvent(
        new Point(rng.nextDouble() * 5, rng.nextDouble() * 5),
        new Point(rng.nextDouble() * 5, rng.nextDouble() * 5)));
    }
    final Gendreau06Scenario scen = GendreauTestUtil.create(events);

    final MASConfiguration config = MASConfiguration.pdptwBuilder()
      .addEventHandler(AddVehicleEvent.class, TestTruckHandler.INSTANCE)
      .addModel(SolverModel.builder())
      .build();

    simulator = ExperimentTest.init(scen, config, 123, false);
    roadModel = simulator.getModelProvider().getModel(RoadModel.class);
    pdpModel = simulator.getModelProvider().getModel(PDPModel.class);
    if (routePlanner instanceof SolverRoutePlanner) {
      simulator.register(routePlanner);
    }
    simulator.tick();

    assertEquals(1, roadModel.getObjectsOfType(Vehicle.class).size());
    truck = roadModel.getObjectsOfType(Vehicle.class).iterator().next();

    for (int i = 0; i < numInCargo; i++) {
      final Parcel p = createParcel(rng);
      pdpModel.register(p);
      pdpModel.addParcelIn(truck, p);
    }
  }

  /**
   * Tests whether a complete route is correctly followed.
   */
  @Test
  public void testRouteCompleteness() {
    assertFalse(routePlanner.prev().isPresent());
    assertFalse(routePlanner.current().isPresent());
    assertFalse(routePlanner.currentRoute().isPresent());
    assertFalse(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    if (routePlanner instanceof AbstractRoutePlanner) {
      assertFalse(((AbstractRoutePlanner) routePlanner).isUpdated());
    }

    routePlanner.init(roadModel, pdpModel, truck);

    assertFalse(routePlanner.prev().isPresent());
    assertFalse(routePlanner.current().isPresent());
    assertFalse(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    if (routePlanner instanceof AbstractRoutePlanner) {
      assertFalse(((AbstractRoutePlanner) routePlanner).isUpdated());
    }

    final Collection<Parcel> onMap = roadModel
      .getObjectsOfType(Parcel.class);
    final Collection<Parcel> inCargo = pdpModel.getContents(truck);
    final List<Parcel> visited = newLinkedList();
    routePlanner.update(onMap, 0);

    assertFalse(routePlanner.prev().isPresent());
    assertTrue(routePlanner.current().isPresent());
    assertEquals(routePlanner.current().get(), routePlanner.currentRoute()
      .get().get(0));
    assertTrue(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    if (routePlanner instanceof AbstractRoutePlanner) {
      assertTrue(((AbstractRoutePlanner) routePlanner).isUpdated());
    }

    while (routePlanner.hasNext()) {
      visited.add(routePlanner.current().get());
      assertEquals(
        "current must keep the same value during repeated invocations",
        routePlanner.current(), routePlanner.current());
      routePlanner.next(0);
      assertEquals(visited.get(visited.size() - 1), routePlanner.prev().get());
    }

    assertEquals(visited, routePlanner.getHistory());
    assertFalse(routePlanner.current().isPresent());
    assertFalse(routePlanner.next(0).isPresent());

    assertEquals("total number of stops should equal num locations",
      (onMap.size() * 2) + inCargo.size(), visited.size());

    for (final Parcel p : onMap) {
      assertEquals(2, Collections.frequency(visited, p));
    }
    for (final Parcel p : inCargo) {
      assertEquals(1, Collections.frequency(visited, p));
    }

  }

  @Test
  public void testMultiUpdate() {
    routePlanner.init(roadModel, pdpModel, truck);

    final Collection<Parcel> empty = ImmutableSet.of();
    final Collection<Parcel> singleCargo = ImmutableSet
      .of(pdpModel.getContents(truck).iterator().next());
    final Parcel mapParcel = roadModel
      .getObjectsOfType(Parcel.class).iterator().next();
    final Collection<Parcel> singleOnMap = ImmutableSet.of(mapParcel);

    routePlanner.update(empty, 0);
    assertFalse(routePlanner.prev().isPresent());

    assertEquals(1, singleOnMap.size());
    assertEquals(1, singleCargo.size());

    // first deliver all parcels in cargo such that cargo is empty
    final Iterator<Parcel> it = pdpModel.getContents(truck).iterator();
    long time = 0;
    while (it.hasNext()) {
      final Parcel cur = it.next();
      while (!roadModel.getPosition(truck).equals(cur.getDeliveryLocation())) {
        roadModel
          .moveTo(truck, cur, TimeLapseFactory.create(time, time + 1000));
        time += 1000;
      }
      pdpModel.deliver(truck, cur, TimeLapseFactory.create(time, time + 10000));
      time += 10000;
    }

    routePlanner.update(singleOnMap, 0);
    assertEquals(0, routePlanner.getHistory().size());

    assertEquals(mapParcel, routePlanner.next(0).get());
    assertTrue(routePlanner.hasNext());
    assertFalse(routePlanner.next(0).isPresent());

    assertEquals(asList(mapParcel, mapParcel), routePlanner.getHistory());
  }

  // init can be called only once
  @Test(expected = IllegalStateException.class)
  public void testInitTwice() {
    routePlanner.init(roadModel, pdpModel, truck);
    routePlanner.init(roadModel, pdpModel, truck);
  }

  // init needs to be called before update
  @Test(expected = IllegalStateException.class)
  public void testNotInitializedUpdate() {
    routePlanner.update(null, 0);
  }

  // update needs to be called before next
  @Test(expected = IllegalStateException.class)
  public void testNotInitializedNext() {
    routePlanner.next(0);
  }

  /**
   * Tests update and init with empty truck and no parcels on map.
   */
  @Test
  public void testEmpty() {
    final TestTruck emptyTruck = new TestTruck(VehicleDTO.builder()
      .startPosition(new Point(0, 0))
      .speed(10d)
      .capacity(10)
      .availabilityTimeWindow(new TimeWindow(0, 1))
      .build());
    simulator.register(emptyTruck);

    routePlanner.init(roadModel, pdpModel, emptyTruck);

    final Collection<Parcel> s1 = ImmutableSet.of();
    routePlanner.update(s1, 0);
    assertFalse(routePlanner.current().isPresent());
    assertFalse(routePlanner.currentRoute().isPresent());
    assertFalse(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    assertFalse(routePlanner.prev().isPresent());

    final Collection<Parcel> onMap = roadModel
      .getObjectsOfType(Parcel.class);
    routePlanner.update(onMap, 0);
    assertTrue(routePlanner.current().isPresent());
    assertTrue(routePlanner.currentRoute().isPresent());
    assertTrue(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    assertFalse(routePlanner.prev().isPresent());

    routePlanner.update(s1, 0);
    assertFalse(routePlanner.current().isPresent());
    assertFalse(routePlanner.currentRoute().isPresent());
    assertFalse(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    assertFalse(routePlanner.prev().isPresent());
  }

  static Parcel createParcel(RandomGenerator rng) {
    final ParcelDTO dto = Parcel
      .builder(new Point(rng.nextDouble(), rng.nextDouble()),
        new Point(rng.nextDouble(), rng.nextDouble()))
      .pickupTimeWindow(new TimeWindow(0, 100000))
      .deliveryTimeWindow(new TimeWindow(0, 100000))
      .neededCapacity(0)
      .orderAnnounceTime(-1L)
      .pickupDuration(3000L)
      .deliveryDuration(3000L)
      .buildDTO();
    return new Parcel(dto);
  }

  AddParcelEvent newParcelEvent(Point origin, Point destination) {
    return AddParcelEvent.create(
      Parcel.builder(origin, destination)
        .pickupTimeWindow(new TimeWindow(0, 3600000))
        .deliveryTimeWindow(new TimeWindow(1800000, 5400000))
        .neededCapacity(0)
        .orderAnnounceTime(-1)
        .pickupDuration(3000L)
        .deliveryDuration(3000L)
        .buildDTO());
  }

  AddParcelEvent newParcelEvent(Point origin, Point destination,
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

  enum TestTruckHandler implements TimedEventHandler<AddVehicleEvent> {
    INSTANCE {
      @Override
      public void handleTimedEvent(AddVehicleEvent event, SimulatorAPI simulator) {
        simulator.register(new TestTruck(event.getVehicleDTO()));
      }
    }
  }

  static class TestTruck extends Vehicle {
    public TestTruck(VehicleDTO dto) {
      super(dto);
    }

    // don't do anything
    @Override
    protected void tickImpl(TimeLapse time) {}
  }
}
