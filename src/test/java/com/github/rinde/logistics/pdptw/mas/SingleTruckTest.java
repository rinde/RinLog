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
package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.TestBidder;
import com.github.rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.SimulatorUser;
import com.github.rinde.rinsim.core.model.Model;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.DefaultVehicle;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.fsm.AbstractState;
import com.github.rinde.rinsim.fsm.State;
import com.github.rinde.rinsim.fsm.StateMachine;
import com.github.rinde.rinsim.fsm.StateMachine.StateMachineEvent;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.pdptw.experiment.ExperimentTest;
import com.github.rinde.rinsim.pdptw.experiment.MASConfiguration;
import com.github.rinde.rinsim.scenario.AddParcelEvent;
import com.github.rinde.rinsim.scenario.TimedEvent;
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

  protected DynamicPDPTWProblem prob;
  protected Simulator simulator;
  protected RoadModel roadModel;
  protected PDPModel pdpModel;
  protected TestTruck truck;
  protected static ParcelDTO.Builder builder;

  @BeforeClass
  public static void setUp() {
    builder = ParcelDTO
        .builder(new Point(1, 1), new Point(3, 3))
        .pickupTimeWindow(new TimeWindow(1, 60000))
        .deliveryTimeWindow(new TimeWindow(1, 60000))
        .neededCapacity(0)
        .orderAnnounceTime(1L)
        .pickupDuration(1000L)
        .deliveryDuration(3000L);
  }

  // should be called in beginning of every test
  public void setUp(List<ParcelDTO> parcels, int trucks,
      @Nullable StochasticSupplier<? extends RoutePlanner> rp) {
    final Collection<TimedEvent> events = newArrayList();
    for (final ParcelDTO p : parcels) {
      events.add(new AddParcelEvent(p));
    }
    final Gendreau06Scenario scen = GendreauTestUtil.create(events, trucks);

    if (rp == null) {
      rp = RandomRoutePlanner.supplier();
    }

    final MASConfiguration randomRandom = new TestTruckConfiguration(
        DebugRoutePlanner.supplier(rp), TestBidder.supplier(),
        ImmutableList.of(AuctionCommModel.supplier()));

    prob = ExperimentTest.init(scen, randomRandom, 123, false);
    simulator = prob.getSimulator();
    roadModel = simulator.getModelProvider().getModel(RoadModel.class);
    pdpModel = simulator.getModelProvider().getModel(PDPModel.class);
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

  @After
  public void tearDown() {
    // to avoid accidental reuse of objects
    prob = null;
    simulator = null;
    roadModel = null;
    pdpModel = null;
    truck = null;
  }

  @Test
  public void oneParcel() {
    final ParcelDTO parcel1dto = builder.build();

    setUp(asList(parcel1dto), 1, null);

    assertEquals(truck.getStateMachine().getCurrentState(),
        truck.getWaitState());
    assertEquals(truck.getDTO().startPosition, roadModel.getPosition(truck));

    simulator.tick();
    assertEquals(1, roadModel.getObjectsOfType(Parcel.class).size());
    final Parcel parcel1 = roadModel.getObjectsOfType(Parcel.class).iterator()
        .next();
    assertEquals(ParcelState.AVAILABLE, pdpModel.getParcelState(parcel1));
    assertEquals(truck.getState(), truck.getGotoState());
    assertFalse(truck.getDTO().startPosition.equals(roadModel
        .getPosition(truck)));
    final DefaultParcel cur2 = truck.getRoute().iterator().next();
    assertEquals(parcel1dto, cur2.dto);

    // move to pickup
    while (truck.getState() == truck.getGotoState()) {
      assertEquals(ParcelState.AVAILABLE, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertEquals(truck.getState(), truck.getServiceState());
    assertEquals(ParcelState.PICKING_UP, pdpModel.getParcelState(parcel1));
    assertEquals(parcel1dto.pickupLocation, roadModel.getPosition(truck));

    // pickup
    while (truck.getState() == truck.getServiceState()) {
      assertEquals(parcel1dto.pickupLocation, roadModel.getPosition(truck));
      assertEquals(ParcelState.PICKING_UP, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertEquals(truck.getWaitState(), truck.getState());
    assertEquals(ParcelState.IN_CARGO, pdpModel.getParcelState(parcel1));
    assertEquals(new LinkedHashSet<Parcel>(asList(parcel1)),
        pdpModel.getContents(truck));

    simulator.tick();
    assertEquals(truck.getGotoState(), truck.getState());

    // move to delivery
    while (truck.getState() == truck.getGotoState()) {
      assertEquals(ParcelState.IN_CARGO, pdpModel.getParcelState(parcel1));
      assertEquals(new LinkedHashSet<Parcel>(asList(parcel1)),
          pdpModel.getContents(truck));
      simulator.tick();
    }
    assertEquals(truck.getState(), truck.getServiceState());
    assertEquals(parcel1dto.deliveryLocation, roadModel.getPosition(truck));
    assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));

    // deliver
    while (truck.getState() == truck.getServiceState()) {
      assertEquals(parcel1dto.deliveryLocation, roadModel.getPosition(truck));
      assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertEquals(ParcelState.DELIVERED, pdpModel.getParcelState(parcel1));
    assertTrue(pdpModel.getContents(truck).isEmpty());
    assertEquals(truck.getState(), truck.getWaitState());

    while (truck.getState() == truck.getWaitState()
        && !roadModel.getPosition(truck).equals(truck.getDTO().startPosition)) {
      simulator.tick();
    }
    assertEquals(truck.getState(), truck.getWaitState());
    assertEquals(truck.getDTO().startPosition, roadModel.getPosition(truck));
  }

  @Test
  public void twoParcels() {
    final ParcelDTO parcel1dto = builder.build();
    final ParcelDTO parcel2dto = builder.build();
    final ParcelDTO parcel3dto = builder.build();
    setUp(asList(parcel1dto, parcel2dto, parcel3dto), 2, null);
    simulator.start();
  }

  /**
   * Tests that the truck updates its route/assignment at the earliest possible
   * time after the CHANGE event is received.
   */
  @Test
  public void intermediateChange() {
    final ParcelDTO parcel1dto = builder.build();
    final ParcelDTO parcel2dto = builder.build();
    final ParcelDTO parcel3dto = builder.build();

    setUp(asList(parcel1dto, parcel2dto, parcel3dto), 1,
        SolverRoutePlanner
            .supplierWithoutCurrentRoutes(MultiVehicleHeuristicSolver.supplier(
                50, 100)));

    final List<Event> events = newArrayList();
    truck.getStateMachine().getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        events.add(e);
      }
    }, StateMachineEvent.STATE_TRANSITION);

    assertEquals(truck.getWaitState(), truck.getState());

    simulator.tick();
    assertEquals(truck.getGotoState(), truck.getState());

    ((TestBidder) truck.getCommunicator()).removeAll();
    final int before = ((DebugRoutePlanner) truck.getRoutePlanner())
        .getUpdateCount();

    while (truck.getGotoState() == truck.getState()) {
      simulator.tick();
    }
    simulator.tick();
    final int after = ((DebugRoutePlanner) truck.getRoutePlanner())
        .getUpdateCount();
    assertEquals(before + 1, after);
  }

  /**
   * Tests that the truck updates its route/assignment at the earliest possible
   * time after the CHANGE event is received but not earlier.
   */
  @Test
  public void intermediateChange2() {
    final ParcelDTO parcel1dto = builder.build();
    final ParcelDTO parcel2dto = builder.build();
    setUp(asList(parcel1dto, parcel2dto), 1, null);

    final DebugRoutePlanner drp = (DebugRoutePlanner) truck.getRoutePlanner();
    assertEquals(0, drp.getUpdateCount());
    simulator.tick();
    assertEquals(1, drp.getUpdateCount());

    // introduce new parcel
    final ParcelDTO parcel3dto = builder.build();
    simulator.register(new DefaultParcel(parcel3dto));

    // goto
    while (truck.getState().equals(truck.getGotoState())) {
      simulator.tick();
    }
    assertEquals(1, drp.getUpdateCount());
    // introduce new parcel
    final ParcelDTO parcel4dto = builder.build();
    simulator.register(new DefaultParcel(parcel4dto));
    // service
    while (truck.getState().equals(truck.getServiceState())) {
      assertEquals(1, drp.getUpdateCount());
      simulator.tick();
    }

    assertEquals(2,
        ((DebugRoutePlanner) truck.getRoutePlanner()).getUpdateCount());
  }

  static class TestTruckConfiguration extends TruckConfiguration {
    TestTruckConfiguration(
        StochasticSupplier<? extends RoutePlanner> routePlannerSupplier,
        StochasticSupplier<? extends Communicator> communicatorSupplier,
        ImmutableList<? extends StochasticSupplier<? extends Model<?>>> modelSuppliers) {
      super(routePlannerSupplier, communicatorSupplier, modelSuppliers);
    }

    @Override
    protected Truck createTruck(VehicleDTO dto, RoutePlanner rp, Communicator c) {
      return new TestTruck(dto, rp, c);
    }
  }

  static class TestTruck extends Truck {

    TestTruck(VehicleDTO pDto, RoutePlanner rp, Communicator c) {
      super(pDto, rp, c);
    }

    public StateMachine<StateEvent, RouteFollowingVehicle> getStateMachine() {
      return stateMachine;
    }

    public State<StateEvent, RouteFollowingVehicle> getState() {
      return getStateMachine().getCurrentState();
    }

    public AbstractState<StateEvent, RouteFollowingVehicle> getWaitState() {
      return waitState;
    }

    public AbstractState<StateEvent, RouteFollowingVehicle> getGotoState() {
      return gotoState;
    }

    public AbstractState<StateEvent, RouteFollowingVehicle> getServiceState() {
      return serviceState;
    }

    @Override
    public Collection<DefaultParcel> getRoute() {
      return super.getRoute();
    }
  }

  static class DebugRoutePlanner implements RoutePlanner, SimulatorUser {
    private final RoutePlanner delegate;
    private int updateCounter;

    public DebugRoutePlanner(RoutePlanner rp) {
      delegate = rp;
      updateCounter = 0;
    }

    @Override
    public void init(RoadModel rm, PDPModel pm, DefaultVehicle dv) {
      delegate.init(rm, pm, dv);
    }

    @Override
    public void update(Collection<DefaultParcel> onMap, long time) {
      delegate.update(onMap, time);
      updateCounter++;
    }

    public int getUpdateCount() {
      return updateCounter;
    }

    @Override
    public Optional<DefaultParcel> current() {
      return delegate.current();
    }

    @Override
    public Optional<ImmutableList<DefaultParcel>> currentRoute() {
      return delegate.currentRoute();
    }

    @Override
    public Optional<DefaultParcel> next(long time) {
      return delegate.next(time);
    }

    @Override
    public Optional<DefaultParcel> prev() {
      return delegate.prev();
    }

    @Override
    public List<DefaultParcel> getHistory() {
      return delegate.getHistory();
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
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
  }
}
