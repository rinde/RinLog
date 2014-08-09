package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.graph.Point;
import com.github.rinde.rinsim.core.model.pdp.DefaultPDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import com.github.rinde.rinsim.core.model.road.PlaneRoadModel;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.pdptw.common.DefaultDepot;
import com.github.rinde.rinsim.pdptw.common.DefaultParcel;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.util.TimeWindow;
import com.github.rinde.rinsim.util.fsm.State;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link Truck} specifically for when diversion is allowed.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class DiversionTruckTest {

  @SuppressWarnings("null")
  PDPRoadModel rm;
  @SuppressWarnings("null")
  PDPModel pm;
  @SuppressWarnings("null")
  DefaultParcel p1, p2, p3, p4, p5;
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
    rm = new PDPRoadModel(new PlaneRoadModel(new Point(0, 0), new Point(5, 5),
        50d), true);
    pm = new DefaultPDPModel(TimeWindowPolicies.TARDY_ALLOWED);

    sim = new Simulator(new MersenneTwister(123), Measure.valueOf(1000L,
        SI.MILLI(SI.SECOND)));
    sim.register(rm);
    sim.register(pm);
    sim.configure();
    final DefaultDepot dp = new DefaultDepot(new Point(2, 2));

    sim.register(dp);
    p1 = new DefaultParcel(ParcelDTO.builder(new Point(1, 1), new Point(2, 2))
        .build());
    p2 = new DefaultParcel(ParcelDTO.builder(new Point(2, 4), new Point(1, 2))
        .build());
    p3 = new DefaultParcel(ParcelDTO
        .builder(new Point(.99, 0), new Point(1, 2))
        .pickupTimeWindow(
            new TimeWindow((60 * 60 * 1000) + 10, 2 * 60 * 60 * 1000)).build());
    p4 = new DefaultParcel(ParcelDTO.builder(new Point(0, 0), new Point(1, 2))
        .build());
    p5 = new DefaultParcel(ParcelDTO.builder(new Point(2, 0), new Point(1, 2))
        .pickupDuration(1001).build());
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
    when(routePlanner.current()).thenReturn(Optional.<DefaultParcel> absent());
    when(routePlanner.currentRoute()).thenReturn(
        Optional.of(ImmutableList.<DefaultParcel> of()));
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  private void routePlannerGoto(DefaultParcel dp) {
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
