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

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.fsm.State;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link Truck} specifically for when diversion is allowed.
 * @author Rinde van Lon
 */
public class DiversionTruckTest {

  @SuppressWarnings("null")
  PDPRoadModel rm;
  @SuppressWarnings("null")
  PDPModel pm;
  @SuppressWarnings("null")
  Parcel p1, p2, p3, p4, p5;
  @SuppressWarnings("null")
  TestTruck truck;
  @SuppressWarnings("null")
  Communicator communicator;
  @SuppressWarnings("null")
  RoutePlanner routePlanner;
  @SuppressWarnings("null")
  Simulator sim;

  /**
   * Sets up the simulator with models and initializes truck and parcels.
   */
  @Before
  public void setUp() {

    sim = Simulator
      .builder()
      .addModel(
        PDPRoadModel.builder(
          RoadModelBuilders.plane()
          .withMinPoint(new Point(0, 0))
          .withMaxPoint(new Point(5, 5))
          .withMaxSpeed(50d)
          )
          .withAllowVehicleDiversion(true)
      )
      .addModel(
        DefaultPDPModel.builder().withTimeWindowPolicy(
          TimeWindowPolicies.TARDY_ALLOWED)
      )
      .build();
    final Depot dp = new Depot(new Point(2, 2));

    rm = sim.getModelProvider().getModel(PDPRoadModel.class);
    pm = sim.getModelProvider().getModel(PDPModel.class);

    sim.register(dp);
    p1 = new Parcel(Parcel.builder(new Point(1, 1), new Point(2, 2))
      .buildDTO());
    p2 = new Parcel(Parcel.builder(new Point(2, 4), new Point(1, 2))
      .buildDTO());
    p3 = new Parcel(Parcel
      .builder(new Point(.99, 0), new Point(1, 2))
      .pickupTimeWindow(
        new TimeWindow((60 * 60 * 1000) + 10, 2 * 60 * 60 * 1000)).buildDTO());
    p4 = new Parcel(Parcel.builder(new Point(0, 0), new Point(1, 2))
      .buildDTO());
    p5 = new Parcel(Parcel.builder(new Point(2, 0), new Point(1, 2))
      .pickupDuration(1001).buildDTO());
    sim.register(p1);
    sim.register(p2);
    sim.register(p3);
    sim.register(p4);
    sim.register(p5);

    routePlanner = mock(RoutePlanner.class);
    communicator = mock(Communicator.class);
    final VehicleDTO dto = VehicleDTO.builder()
      .startPosition(new Point(0, 0))
      .speed(30d)
      .capacity(100)
      .availabilityTimeWindow(TimeWindow.ALWAYS)
      .build();

    truck = new TestTruck(dto, routePlanner, communicator);
    sim.register(truck);
    routePlannerGotoNowhere();
    assertEquals(truck.waitState(), truck.getState());

    assertEquals(ParcelState.AVAILABLE, pm.getParcelState(p1));
    assertEquals(ParcelState.AVAILABLE, pm.getParcelState(p2));
    assertEquals(ParcelState.ANNOUNCED, pm.getParcelState(p3));

  }

  /**
   * Change destination of truck in wait state.
   */
  @Test
  public void waitStateDiversion() {
    final InOrder inOrder = inOrder(communicator);
    // attempt to go nowhere, should result in no change
    sim.tick();
    assertEquals(truck.waitState(), truck.getState());
    assertTrue(truck.getRoute().isEmpty());

    // attempt to go to available parcel, should result in going there
    routePlannerGoto(p1);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p1, truck.getRoute().iterator().next());
    inOrder.verify(communicator).claim(p1);

    // make sure that parcel is in cargo and truck becomes idle again
    while (!pm.getParcelState(p1).isPickedUp()) {
      sim.tick();
    }
    assertEquals(truck.waitState(), truck.getState());
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p1));
    assertTrue(pm.getContents(truck).contains(p1));

    // attempt to go to a parcel in cargo while vehicle is in wait state
    routePlannerGoto(p1);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p1, truck.getRoute().iterator().next());

    verify(communicator, times(1)).claim(p1);
    verify(communicator, times(0)).unclaim(p1);
    verify(communicator, times(0)).claim(p2);
    verify(communicator, times(0)).unclaim(p2);
  }

  /**
   * Change truck destination while in goto state.
   */
  @Test
  public void gotoStateDiversion() {
    final InOrder inOrder = inOrder(communicator);

    // make sure to be in goto state first
    routePlannerGoto(p1);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p1, truck.getRoute().iterator().next());
    inOrder.verify(communicator).claim(p1);

    // divert to other available parcel
    routePlannerGoto(p2);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p2, truck.getRoute().iterator().next());
    inOrder.verify(communicator).unclaim(p1);
    inOrder.verify(communicator).claim(p2);

    // divert to no destination, truck should become idle
    routePlannerGotoNowhere();
    sim.tick();
    assertEquals(truck.waitState(), truck.getState());
    assertTrue(truck.getRoute().isEmpty());
    inOrder.verify(communicator).unclaim(p2);

    // go back to goto state
    routePlannerGoto(p2);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p2, truck.getRoute().iterator().next());
    inOrder.verify(communicator).claim(p2);

    // make sure that parcel p2 is in cargo and truck becomes idle again
    while (!pm.getParcelState(p2).isPickedUp()) {
      sim.tick();
    }
    assertEquals(truck.waitState(), truck.getState());
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p2));
    assertTrue(pm.getContents(truck).contains(p2));
    assertTrue(truck.getRoute().isEmpty());
    verify(communicator, times(1)).claim(p1);
    verify(communicator, times(1)).unclaim(p1);
    verify(communicator, times(2)).claim(p2);
    verify(communicator, times(1)).unclaim(p2);

    // go to p1
    routePlannerGoto(p1);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p1, truck.getRoute().iterator().next());
    inOrder.verify(communicator).claim(p1);

    // now divert to p2 which is in cargo. p1 should be unclaimed but p2 should
    // not be claimed since it is already in cargo.
    routePlannerGoto(p2);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p2, truck.getRoute().iterator().next());
    inOrder.verify(communicator).unclaim(p1);

    verify(communicator, times(2)).claim(p1);
    verify(communicator, times(2)).unclaim(p1);
    verify(communicator, times(2)).claim(p2);
    verify(communicator, times(1)).unclaim(p2);

    // now divert back to p1. p1 should be claimed but p2 should not be
    // unclaimed since it is already in cargo.
    routePlannerGoto(p1);
    sim.tick();
    assertEquals(truck.gotoState(), truck.getState());
    assertEquals(p1, truck.getRoute().iterator().next());
    inOrder.verify(communicator).claim(p1);
    verify(communicator, times(3)).claim(p1);
    verify(communicator, times(2)).unclaim(p1);
    verify(communicator, times(2)).claim(p2);
    verify(communicator, times(1)).unclaim(p2);
  }

  /**
   * From WaitAtService state, goto nowhere.
   */
  @Test
  public void waitAtServiceDiversionToNowhere() {
    final InOrder inOrder = inOrder(communicator);
    gotoWaitAtServiceStateP3();
    inOrder.verify(communicator).claim(p3);

    // divert to nowhere
    routePlannerGotoNowhere();
    sim.tick();
    assertEquals(truck.waitState(), truck.getState());
    inOrder.verify(communicator).unclaim(p3);

    verify(communicator, times(1)).claim(p3);
    verify(communicator, times(1)).unclaim(p3);
  }

  /**
   * While in WaitAtService state divert to available parcel.
   */
  @Test
  public void waitAtServiceDiversionToAvailable() {
    final InOrder inOrder = inOrder(communicator);
    gotoWaitAtServiceStateP3();
    inOrder.verify(communicator).claim(p3);

    // divert to p1
    routePlannerGoto(p1);
    sim.tick();
    inOrder.verify(communicator).unclaim(p3);
    inOrder.verify(communicator).claim(p1);

    verify(communicator, times(1)).claim(p3);
    verify(communicator, times(1)).unclaim(p3);
    verify(communicator, times(1)).claim(p1);
  }

  /**
   * While in WaitAtService state divert to a parcel which is in cargo.
   */
  @Test
  public void waitAtServiceDiversionToInCargo() {
    final InOrder inOrder = inOrder(communicator);
    routePlannerGoto(p4);
    sim.tick();
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p4));
    assertTrue(pm.getContents(truck).contains(p4));
    inOrder.verify(communicator).claim(p4);

    gotoWaitAtServiceStateP3();
    inOrder.verify(communicator).claim(p3);

    // divert to p4 which is in cargo
    routePlannerGoto(p4);
    sim.tick();
    inOrder.verify(communicator).unclaim(p3);

    verify(communicator, times(1)).claim(p3);
    verify(communicator, times(1)).unclaim(p3);
    verify(communicator, times(1)).claim(p4);
    verify(communicator, times(0)).unclaim(p4);
  }

  /**
   * While in Service state divert to nowhere.
   */
  @Test
  public void serviceStateDivertToNowhere() {
    final InOrder inOrder = inOrder(communicator);
    gotoServiceStateP5();
    inOrder.verify(communicator).claim(p5);

    routePlannerGotoNowhere();
    assertEquals(ParcelState.PICKING_UP, pm.getParcelState(p5));
    sim.tick();
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p5));
    assertTrue(truck.getRoute().isEmpty());
    verify(communicator, times(1)).claim(p5);
    verify(communicator, times(0)).unclaim(p5);
  }

  /**
   * While in Service state divert to available parcel.
   */
  @Test
  public void serviceStateDivertToAvailableParcel() {
    final InOrder inOrder = inOrder(communicator);
    gotoServiceStateP5();
    inOrder.verify(communicator).claim(p5);

    routePlannerGoto(p1);
    assertEquals(ParcelState.PICKING_UP, pm.getParcelState(p5));
    sim.tick();
    inOrder.verify(communicator).claim(p1);
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p5));
    assertEquals(p1, truck.getRoute().iterator().next());
    verify(communicator, times(1)).claim(p5);
    verify(communicator, times(0)).unclaim(p5);
    verify(communicator, times(1)).claim(p1);
    verify(communicator, times(0)).unclaim(p1);
  }

  /**
   * While in Service state divert to a parcel which is in cargo.
   */
  @Test
  public void serviceStateDivertToParcelInCargo() {
    final InOrder inOrder = inOrder(communicator);
    // pickup p4
    routePlannerGoto(p4);
    sim.tick();
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p4));
    assertTrue(pm.getContents(truck).contains(p4));
    inOrder.verify(communicator).claim(p4);

    // service p5
    gotoServiceStateP5();
    assertEquals(ParcelState.PICKING_UP, pm.getParcelState(p5));
    inOrder.verify(communicator).claim(p5);

    // now divert to p4 while servicing p5. p5 will continue to be picked up, p4
    // will be next destination.
    routePlannerGoto(p4);
    sim.tick();
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(p5));
    assertEquals(p4, truck.getRoute().iterator().next());

    verify(communicator, times(1)).claim(p5);
    verify(communicator, times(0)).unclaim(p5);
    verify(communicator, times(1)).claim(p4);
    verify(communicator, times(0)).unclaim(p4);
  }

  private void routePlannerGotoNowhere() {
    when(routePlanner.current()).thenReturn(Optional.<Parcel> absent());
    when(routePlanner.currentRoute()).thenReturn(
      Optional.of(ImmutableList.<Parcel> of()));
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  private void routePlannerGoto(Parcel dp) {
    when(routePlanner.current()).thenReturn(Optional.of(dp));
    when(routePlanner.currentRoute()).thenReturn(
      Optional.of(ImmutableList.of(dp)));
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  private void gotoWaitAtServiceStateP3() {
    // allow some actions to be done beforehand
    checkState(sim.getCurrentTime() <= 1000);
    routePlannerGoto(p3);
    sim.tick();
    assertEquals(truck.waitState(), truck.getState());

    while (truck.waitState().equals(truck.getState())
      || truck.gotoState().equals(truck.getState())) {
      sim.tick();
    }
    assertEquals(truck.waitForServiceState(), truck.getState());
  }

  private void gotoServiceStateP5() {
    routePlannerGoto(p5);
    while (!pm.getParcelState(p5).isTransitionState()) {
      sim.tick();
    }
    assertEquals(truck.serviceState(), truck.getState());
  }

  class TestTruck extends Truck {
    public TestTruck(VehicleDTO pDto, RoutePlanner rp, Communicator c) {
      super(pDto, rp, c);
    }

    public State<StateEvent, RouteFollowingVehicle> getState() {
      return stateMachine.getCurrentState();
    }

    public State<StateEvent, RouteFollowingVehicle> waitState() {
      return waitState;
    }

    public State<StateEvent, RouteFollowingVehicle> gotoState() {
      return gotoState;
    }

    public State<StateEvent, RouteFollowingVehicle> waitForServiceState() {
      return waitForServiceState;
    }

    public State<StateEvent, RouteFollowingVehicle> serviceState() {
      return serviceState;
    }
  }
}
