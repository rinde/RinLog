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
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.rinde.logistics.pdptw.mas.TestTruck.TestTruckFactory;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.DoubleBid;
import com.github.rinde.logistics.pdptw.mas.comm.TestBidder;
import com.github.rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import com.github.rinde.rinsim.central.SimSolverBuilder;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.central.SolverUser;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.SimulatorUser;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.ParcelDTO;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.EventAPI;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.experiment.ExperimentTestUtil;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.fsm.StateMachine.StateMachineEvent;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.scenario.gendreau06.GendreauTestUtil;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon
 *
 */
public class SingleTruckTest {

  protected Simulator simulator;
  protected RoadModel roadModel;
  protected PDPModel pdpModel;
  protected TestTruck truck;
  protected static Parcel.Builder builder;

  @BeforeClass
  public static void setUp() {
    builder = Parcel
      .builder(new Point(1, 1), new Point(3, 3))
      .pickupTimeWindow(TimeWindow.create(1, 60000))
      .deliveryTimeWindow(TimeWindow.create(1, 60000))
      .neededCapacity(0)
      .orderAnnounceTime(1L)
      .pickupDuration(1000L)
      .deliveryDuration(3000L);
  }

  static Parcel.Builder setCommonProperties(Parcel.Builder b) {
    return b
      .pickupTimeWindow(TimeWindow.create(1, 60000))
      .deliveryTimeWindow(TimeWindow.create(1, 60000))
      .neededCapacity(0)
      .orderAnnounceTime(1L)
      .pickupDuration(3000L)
      .deliveryDuration(3000L);
  }

  // should be called in beginning of every test
  public void setUp(List<ParcelDTO> parcels, int trucks,
      @Nullable StochasticSupplier<? extends RoutePlanner> rp) {
    final List<AddParcelEvent> events = newArrayList();
    for (final ParcelDTO p : parcels) {
      events.add(AddParcelEvent.create(p));
    }
    final Gendreau06Scenario scen =
      GendreauTestUtil.createWithTrucks(events, trucks);

    if (rp == null) {
      rp = RandomRoutePlanner.supplier();
    }

    final MASConfiguration randomRandom = MASConfiguration.pdptwBuilder()
      .addEventHandler(AddVehicleEvent.class,
        TestTruckFactory.testBuilder()
          .setRoutePlanner(DebugRoutePlanner.supplier(rp))
          .setCommunicator(TestBidder.supplier())
          .setLazyComputation(false)
          .build())
      .addEventHandler(AddParcelEvent.class, AddParcelEvent.namedHandler())
      .addModel(AuctionCommModel.builder(DoubleBid.class))
      .addModel(SolverModel.builder())
      .build();

    simulator = ExperimentTestUtil.init(scen, randomRandom, 123);

    roadModel = simulator.getModelProvider().getModel(RoadModel.class);
    pdpModel = simulator.getModelProvider().getModel(PDPModel.class);
    simulator.getModelProvider().getModel(SolverModel.class);
    assertNotNull(roadModel);
    assertNotNull(pdpModel);
    assertEquals(0, simulator.getCurrentTime());
    simulator.tick();
    // check that there are no more (other) vehicles
    assertEquals(trucks, roadModel.getObjectsOfType(Vehicle.class).size());
    // check that the vehicle is of type truck
    assertEquals(trucks, roadModel.getObjectsOfType(Truck.class).size());
    // make sure there are no parcels yet
    assertTrue(roadModel.getObjectsOfType(Parcel.class).isEmpty());

    truck = roadModel.getObjectsOfType(TestTruck.class).iterator().next();

    assertNotNull(truck);
    assertEquals(1000, simulator.getCurrentTime());
  }

  @SuppressWarnings("null")
  @After
  public void tearDown() {
    // to avoid accidental reuse of objects
    simulator = null;
    roadModel = null;
    pdpModel = null;
    truck = null;
  }

  @Test
  public void oneParcel() {
    final ParcelDTO parcel1dto = builder.toString("ONE_PARCEL").buildDTO();

    setUp(asList(parcel1dto), 1, null);

    assertThat(truck.getState()).isEqualTo(truck.waitState());
    assertThat(truck.getDTO().getStartPosition()).isEqualTo(
      roadModel.getPosition(truck));

    simulator.tick();
    assertThat(roadModel.getObjectsOfType(Parcel.class)).hasSize(1);
    final Parcel parcel1 = roadModel.getObjectsOfType(Parcel.class).iterator()
      .next();
    assertThat(parcel1.getDto()).isEqualTo(parcel1dto);
    assertEquals(ParcelState.AVAILABLE, pdpModel.getParcelState(parcel1));
    assertEquals(truck.getState(), truck.gotoState());
    assertFalse(truck.getDTO().getStartPosition().equals(roadModel
      .getPosition(truck)));
    final Parcel cur2 = truck.getRoute().iterator().next();
    assertEquals(parcel1dto, cur2.getDto());

    assertThat(truck.getRoute()).containsExactly(parcel1);

    // move to pickup
    while (truck.getState() == truck.gotoState()) {
      assertEquals(ParcelState.AVAILABLE, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertEquals(truck.getState(), truck.serviceState());
    assertEquals(ParcelState.PICKING_UP, pdpModel.getParcelState(parcel1));
    assertEquals(parcel1dto.getPickupLocation(), roadModel.getPosition(truck));

    // pickup
    while (truck.getState() == truck.serviceState()) {
      assertEquals(parcel1dto.getPickupLocation(),
        roadModel.getPosition(truck));
      assertThat(pdpModel.getParcelState(parcel1))
        .isEqualTo(ParcelState.PICKING_UP);
      simulator.tick();
    }
    assertThat(pdpModel.getParcelState(parcel1))
      .isEqualTo(ParcelState.IN_CARGO);
    assertThat(pdpModel.getContents(truck)).containsExactly(parcel1);
    assertThat(truck.getState()).isEqualTo(truck.gotoState());

    // move to delivery
    while (truck.getState() == truck.gotoState()) {
      assertEquals(ParcelState.IN_CARGO, pdpModel.getParcelState(parcel1));
      assertEquals(new LinkedHashSet<Parcel>(asList(parcel1)),
        pdpModel.getContents(truck));
      simulator.tick();
    }
    assertEquals(truck.getState(), truck.serviceState());
    assertEquals(parcel1dto.getDeliveryLocation(),
      roadModel.getPosition(truck));
    assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));

    // deliver
    while (truck.getState() == truck.serviceState()) {
      assertEquals(parcel1dto.getDeliveryLocation(),
        roadModel.getPosition(truck));
      assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertEquals(ParcelState.DELIVERED, pdpModel.getParcelState(parcel1));
    assertTrue(pdpModel.getContents(truck).isEmpty());
    assertEquals(truck.getState(), truck.waitState());

    while (truck.getState() == truck.waitState()
      && !roadModel.getPosition(truck)
        .equals(truck.getDTO().getStartPosition())) {
      simulator.tick();
    }
    assertEquals(truck.getState(), truck.waitState());
    assertEquals(truck.getDTO().getStartPosition(),
      roadModel.getPosition(truck));
  }

  @Test
  public void twoParcels() {
    final ParcelDTO parcel1dto = builder.buildDTO();
    final ParcelDTO parcel2dto = builder.buildDTO();
    final ParcelDTO parcel3dto = builder.buildDTO();
    setUp(asList(parcel1dto, parcel2dto, parcel3dto), 2, null);
    simulator.start();
  }

  /**
   * Tests that the truck updates its route/assignment at the earliest possible
   * time after the CHANGE event is received.
   */
  @Test
  public void intermediateChange() {
    final ParcelDTO parcel1dto = builder.buildDTO();
    final ParcelDTO parcel2dto = builder.buildDTO();
    final ParcelDTO parcel3dto = builder.buildDTO();

    setUp(asList(parcel1dto, parcel2dto, parcel3dto), 1,
      SolverRoutePlanner.supplierWithoutCurrentRoutes(
        MultiVehicleHeuristicSolver.supplier(50, 100)));

    final List<Event> events = new ArrayList<>();
    truck.getStateMachine().getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        events.add(e);
      }
    }, StateMachineEvent.STATE_TRANSITION);

    assertEquals(truck.waitState(), truck.getState());

    simulator.tick();
    assertEquals(truck.gotoState(), truck.getState());

    ((TestBidder) truck.getCommunicator()).removeAll();
    final int before = ((DebugRoutePlanner) truck.getRoutePlanner())
      .getUpdateCount();

    while (truck.gotoState() == truck.getState()) {
      simulator.tick();
    }
    simulator.tick();
    final int after = ((DebugRoutePlanner) truck.getRoutePlanner())
      .getUpdateCount();
    assertEquals(before, after);
  }

  /**
   * Tests that the truck updates its route/assignment at the earliest possible
   * time after the CHANGE event is received but not earlier.
   */
  @Test
  public void intermediateChange2() {
    final ParcelDTO parcel1dto = setCommonProperties(
      Parcel.builder(new Point(0, 0), new Point(5, 0))).buildDTO();
    final ParcelDTO parcel2dto = setCommonProperties(
      Parcel.builder(new Point(0, 1), new Point(5, 1))).buildDTO();
    setUp(asList(parcel1dto, parcel2dto), 1, null);
    final DebugRoutePlanner drp = (DebugRoutePlanner) truck.getRoutePlanner();
    assertEquals(0, drp.getUpdateCount());
    simulator.tick();

    assertEquals(2, drp.getUpdateCount());
    assertThat(truck.getState()).isEqualTo(truck.gotoState());

    // introduce new parcel
    final ParcelDTO parcel3dto = setCommonProperties(
      Parcel.builder(new Point(0, 2), new Point(5, 2))).buildDTO();
    simulator.register(new Parcel(parcel3dto));
    // goto
    while (truck.getState().equals(truck.gotoState())) {
      simulator.tick();
    }
    assertEquals(3, drp.getUpdateCount());
    // introduce new parcel
    final ParcelDTO parcel4dto = setCommonProperties(
      Parcel.builder(new Point(0, 3), new Point(5, 3))).buildDTO();
    simulator.register(new Parcel(parcel4dto));
    // service
    while (truck.getState().equals(truck.serviceState())) {
      assertEquals(4, drp.getUpdateCount());
      simulator.tick();
    }

    assertEquals(4,
      ((DebugRoutePlanner) truck.getRoutePlanner()).getUpdateCount());
  }

  static class DebugRoutePlanner implements RoutePlanner, SimulatorUser,
      SolverUser {
    private final RoutePlanner delegate;
    private int updateCounter;

    public DebugRoutePlanner(RoutePlanner rp) {
      delegate = rp;
      updateCounter = 0;
    }

    @Override
    public void init(RoadModel rm, PDPModel pm, Vehicle dv) {
      delegate.init(rm, pm, dv);
    }

    @Override
    public void update(Set<Parcel> onMap, long time) {
      delegate.update(onMap, time);
      updateCounter++;
    }

    public int getUpdateCount() {
      return updateCounter;
    }

    @Override
    public Optional<Parcel> current() {
      return delegate.current();
    }

    @Override
    public Optional<ImmutableList<Parcel>> currentRoute() {
      return delegate.currentRoute();
    }

    @Override
    public Optional<Parcel> next(long time) {
      return delegate.next(time);
    }

    @Override
    public Optional<Parcel> prev() {
      return delegate.prev();
    }

    @Override
    public List<Parcel> getHistory() {
      return delegate.getHistory();
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public String toString() {
      return "DebugRoutePlanner";
    }

    public static StochasticSupplier<RoutePlanner> supplier(
        final StochasticSupplier<? extends RoutePlanner> rp) {
      return new StochasticSuppliers.AbstractStochasticSupplier<RoutePlanner>() {
        @Override
        public RoutePlanner get(long seed) {
          return new DebugRoutePlanner(rp.get(seed));
        }
      };
    }

    @Override
    public void setSimulator(SimulatorAPI api) {
      if (delegate instanceof SimulatorUser) {
        ((SimulatorUser) delegate).setSimulator(api);
      }
    }

    @Override
    public void setSolverProvider(SimSolverBuilder b) {
      if (delegate instanceof SolverUser) {
        ((SolverUser) delegate).setSolverProvider(b);
      }
    }

    @Override
    public EventAPI getEventAPI() {
      return delegate.getEventAPI();
    }
  }
}
