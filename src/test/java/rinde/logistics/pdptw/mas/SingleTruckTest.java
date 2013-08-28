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

import org.junit.After;
import org.junit.Test;

import rinde.logistics.pdptw.mas.OldExperiments.RandomRandom;
import rinde.logistics.pdptw.mas.Truck.Goto;
import rinde.logistics.pdptw.mas.Truck.Service;
import rinde.logistics.pdptw.mas.Truck.Wait;
import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.PDPModel.ParcelState;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.pdp.Vehicle;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauTestUtil;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.TimeWindow;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class SingleTruckTest {

  protected DynamicPDPTWProblem prob;
  protected Simulator simulator;
  protected RoadModel roadModel;
  protected PDPModel pdpModel;
  protected Truck truck;

  // should be called in beginning of every test
  public void setUp(List<ParcelDTO> parcels, int trucks) {
    final Collection<TimedEvent> events = newArrayList();
    for (final ParcelDTO p : parcels) {
      events.add(new AddParcelEvent(p));
    }
    final Gendreau06Scenario scen = GendreauTestUtil.create(events, trucks);

    prob = GSimulation.init(scen, new RandomRandom(), false);
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

    truck = roadModel.getObjectsOfType(Truck.class).iterator().next();
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

    assertTrue(truck.stateMachine.getCurrentState() instanceof Wait);
    assertEquals(truck.getDTO().startPosition, roadModel.getPosition(truck));

    simulator.tick();
    assertEquals(1, roadModel.getObjectsOfType(Parcel.class).size());
    final Parcel parcel1 = roadModel.getObjectsOfType(Parcel.class).iterator()
        .next();
    assertEquals(ParcelState.AVAILABLE, pdpModel.getParcelState(parcel1));
    assertTrue(truck.stateMachine.getCurrentState() instanceof Goto);
    assertFalse(truck.getDTO().startPosition.equals(roadModel
        .getPosition(truck)));
    final DefaultParcel cur2 = ((Goto) truck.stateMachine.getCurrentState()).cur
        .get();
    assertEquals(parcel1dto, cur2.dto);

    // move to pickup
    while (truck.stateMachine.getCurrentState() instanceof Goto) {
      assertEquals(ParcelState.AVAILABLE, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertTrue(truck.stateMachine.getCurrentState() instanceof Service);
    assertEquals(ParcelState.PICKING_UP, pdpModel.getParcelState(parcel1));
    assertEquals(parcel1dto.pickupLocation, roadModel.getPosition(truck));

    // pickup
    while (truck.stateMachine.getCurrentState() instanceof Service) {
      assertEquals(parcel1dto.pickupLocation, roadModel.getPosition(truck));
      assertEquals(ParcelState.PICKING_UP, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertTrue(truck.stateMachine.getCurrentState() instanceof Goto);
    assertEquals(ParcelState.IN_CARGO, pdpModel.getParcelState(parcel1));
    assertEquals(new LinkedHashSet<Parcel>(asList(parcel1)), pdpModel.getContents(truck));

    // move to delivery
    while (truck.stateMachine.getCurrentState() instanceof Goto) {
      assertEquals(ParcelState.IN_CARGO, pdpModel.getParcelState(parcel1));
      assertEquals(new LinkedHashSet<Parcel>(asList(parcel1)), pdpModel.getContents(truck));
      simulator.tick();
    }
    assertTrue(truck.stateMachine.getCurrentState() instanceof Service);
    assertEquals(parcel1dto.destinationLocation, roadModel.getPosition(truck));
    assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));

    // deliver
    while (truck.stateMachine.getCurrentState() instanceof Service) {
      assertEquals(parcel1dto.destinationLocation, roadModel.getPosition(truck));
      assertEquals(ParcelState.DELIVERING, pdpModel.getParcelState(parcel1));
      simulator.tick();
    }
    assertEquals(ParcelState.DELIVERED, pdpModel.getParcelState(parcel1));
    assertTrue(pdpModel.getContents(truck).isEmpty());
    assertTrue(truck.stateMachine.getCurrentState() instanceof Wait);

    while (truck.stateMachine.getCurrentState() instanceof Wait
        && !roadModel.getPosition(truck).equals(truck.getDTO().startPosition)) {
      simulator.tick();
    }
    assertTrue(truck.stateMachine.getCurrentState() instanceof Wait);
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

}
