/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

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

import rinde.logistics.pdptw.solver.MultiVehicleHeuristicSolver;
import rinde.sim.core.Simulator;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.TimeLapseFactory;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.pdp.Vehicle;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.core.pdptw.ParcelDTO;
import rinde.sim.core.pdptw.VehicleDTO;
import rinde.sim.pdptw.central.arrays.RandomMVArraysSolver;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauTestUtil;
import rinde.sim.scenario.AddParcelEvent;
import rinde.sim.scenario.AddVehicleEvent;
import rinde.sim.scenario.TimedEvent;
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.TimeWindow;

import com.google.common.collect.ImmutableSet;

/**
 * Tests all known implementations of the {@link RoutePlanner} interface.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
@RunWith(Parameterized.class)
public class RoutePlannerTest {
  protected final StochasticSupplier<RoutePlanner> supplier;
  protected RoutePlanner routePlanner;
  protected DynamicPDPTWProblem problem;
  protected RoadModel roadModel;
  protected PDPModel pdpModel;
  protected Simulator simulator;
  protected DefaultVehicle truck;

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

    problem = ExperimentTest.init(scen, new TestConfiguration(), 123, false);
    simulator = problem.getSimulator();
    roadModel = simulator.getModelProvider().getModel(RoadModel.class);
    pdpModel = simulator.getModelProvider().getModel(PDPModel.class);
    simulator.register(routePlanner);
    simulator.tick();

    assertEquals(1, roadModel.getObjectsOfType(Vehicle.class).size());
    truck = roadModel.getObjectsOfType(DefaultVehicle.class).iterator().next();

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

    final Collection<DefaultParcel> onMap = roadModel
        .getObjectsOfType(DefaultParcel.class);
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

    final Collection<DefaultParcel> empty = ImmutableSet.of();
    final Collection<DefaultParcel> singleCargo = ImmutableSet
        .of((DefaultParcel) pdpModel.getContents(truck).iterator().next());
    final DefaultParcel mapParcel = roadModel
        .getObjectsOfType(DefaultParcel.class).iterator().next();
    final Collection<DefaultParcel> singleOnMap = ImmutableSet.of(mapParcel);

    routePlanner.update(empty, 0);
    assertFalse(routePlanner.prev().isPresent());

    assertEquals(1, singleOnMap.size());
    assertEquals(1, singleCargo.size());

    // first deliver all parcels in cargo such that cargo is empty
    final Iterator<Parcel> it = pdpModel.getContents(truck).iterator();
    long time = 0;
    while (it.hasNext()) {
      final Parcel cur = it.next();
      while (!roadModel.getPosition(truck).equals(cur.getDestination())) {
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

    final Collection<DefaultParcel> s1 = ImmutableSet.of();
    routePlanner.update(s1, 0);
    assertFalse(routePlanner.current().isPresent());
    assertFalse(routePlanner.currentRoute().isPresent());
    assertFalse(routePlanner.hasNext());
    assertTrue(routePlanner.getHistory().isEmpty());
    assertFalse(routePlanner.prev().isPresent());

    final Collection<DefaultParcel> onMap = roadModel
        .getObjectsOfType(DefaultParcel.class);
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
    final ParcelDTO dto = new ParcelDTO(/* */
        new Point(rng.nextDouble(), rng.nextDouble()),/* start pos */
        new Point(rng.nextDouble(), rng.nextDouble()),/* dest pos */
        new TimeWindow(0, 100000),/* pickup tw */
        new TimeWindow(0, 100000),/* deliver tw */
        0,/* needed capacity */
        -1,/* order arrival time */
        3000,/* pickup duration */
        3000 /* delivery duration */);

    return new DefaultParcel(dto);
  }

  AddParcelEvent newParcelEvent(Point origin, Point destination) {
    return new AddParcelEvent(new ParcelDTO(origin, destination,
        new TimeWindow(0, 3600000), new TimeWindow(1800000, 5400000), 0, -1,
        300000, 300000));
  }

  AddParcelEvent newParcelEvent(Point origin, Point destination,
      TimeWindow pickup, TimeWindow delivery) {
    return new AddParcelEvent(new ParcelDTO(origin, destination, pickup,
        delivery, 0, -1, 300000, 300000));
  }

  class TestConfiguration extends DefaultMASConfiguration {

    @Override
    public Creator<AddVehicleEvent> getVehicleCreator() {
      return new Creator<AddVehicleEvent>() {
        @Override
        public boolean create(Simulator sim, AddVehicleEvent event) {
          return sim.register(new TestTruck(event.vehicleDTO));
        }
      };
    }
  }

  class TestTruck extends DefaultVehicle {
    public TestTruck(VehicleDTO dto) {
      super(dto);
    }

    // don't do anything
    @Override
    protected void tickImpl(TimeLapse time) {}
  }
}
