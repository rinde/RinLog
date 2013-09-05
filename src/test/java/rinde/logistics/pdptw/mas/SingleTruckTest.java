/**
 * 
 */
package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.After;
import org.junit.Test;

import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.comm.RandomBidder;
import rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.pdp.Vehicle;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.common.RouteFollowingVehicle;
import rinde.sim.pdptw.common.VehicleDTO;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.experiment.MASConfigurator;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauTestUtil;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;
import rinde.sim.util.fsm.AbstractState;
import rinde.sim.util.fsm.State;
import rinde.sim.util.fsm.StateMachine;

import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class SingleTruckTest {

  protected DynamicPDPTWProblem prob;
  protected Simulator simulator;
  protected RoadModel roadModel;
  protected PDPModel pdpModel;
  protected TestTruck truck;

  // should be called in beginning of every test
  public void setUp(List<ParcelDTO> parcels, int trucks) {
    final Collection<TimedEvent> events = newArrayList();
    for (final ParcelDTO p : parcels) {
      events.add(new AddParcelEvent(p));
    }
    final Gendreau06Scenario scen = GendreauTestUtil.create(events, trucks);

    prob = ExperimentTest.init(scen, new TestRandomRandom().configure(123),
        false);
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
    final ParcelDTO parcel1dto = new ParcelDTO(new Point(1, 1),
        new Point(3, 3), new TimeWindow(1, 60000), new TimeWindow(1, 60000), 0,
        1, 3000, 3000);

    setUp(asList(parcel1dto), 1);

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
    assertEquals(parcel1dto.destinationLocation, roadModel.getPosition(truck));
    assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));

    // deliver
    while (truck.getState() == truck.getServiceState()) {
      assertEquals(parcel1dto.destinationLocation, roadModel.getPosition(truck));
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
    final ParcelDTO parcel1dto = new ParcelDTO(new Point(1, 1),
        new Point(3, 3), new TimeWindow(1, 60000), new TimeWindow(1, 60000), 0,
        1, 3000, 3000);
    final ParcelDTO parcel2dto = new ParcelDTO(new Point(1, 1),
        new Point(3, 3), new TimeWindow(1, 60000), new TimeWindow(1, 60000), 0,
        1, 3000, 3000);
    final ParcelDTO parcel3dto = new ParcelDTO(new Point(1, 1),
        new Point(3, 3), new TimeWindow(1, 60000), new TimeWindow(1, 60000), 0,
        1, 3000, 3000);

    setUp(asList(parcel1dto, parcel2dto, parcel3dto), 2);

    simulator.start();
  }

  public static class TestTruck extends Truck {

    public TestTruck(VehicleDTO pDto, RoutePlanner rp, Communicator c) {
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

  public static class TestRandomRandom implements MASConfigurator {
    @Override
    public MASConfiguration configure(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);

      return new DefaultMASConfiguration() {
        @Override
        public ImmutableList<? extends Model<?>> getModels() {
          return ImmutableList.of(new AuctionCommModel());
        }

        @Override
        public Creator<AddVehicleEvent> getVehicleCreator() {
          return new Creator<AddVehicleEvent>() {
            @Override
            public boolean create(Simulator sim, AddVehicleEvent event) {
              final Communicator c = new RandomBidder(rng.nextLong());
              sim.register(c);
              return sim.register(new TestTruck(event.vehicleDTO,
                  new RandomRoutePlanner(rng.nextLong()), c));
            }
          };
        }
      };
    }
  }
}
