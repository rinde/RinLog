package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.logistics.pdptw.mas.TruckConfiguration;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.Bidder;
import com.github.rinde.logistics.pdptw.mas.comm.RandomBidder;
import com.github.rinde.logistics.pdptw.mas.comm.SolverBidder;
import com.github.rinde.logistics.pdptw.mas.comm.CommunicationIntegrationTest.CommTestModel;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType;
import com.github.rinde.logistics.pdptw.mas.route.AbstractRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import com.github.rinde.rinsim.central.RandomSolver;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.DefaultVehicle;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.experiment.ExperimentTest;
import com.github.rinde.rinsim.pdptw.experiment.MASConfiguration;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.scenario.AddParcelEvent;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.TimeWindow;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Tests for the auction mechanism.
 * @author Rinde van Lon 
 */
@RunWith(Parameterized.class)
public class AuctionTest {
  AddParcelEvent ape1, ape2;

  final StochasticSupplier<Bidder> bidderSupplier;

  /**
   * @param b The {@link Bidder} under test.
   */
  @SuppressWarnings("null")
  public AuctionTest(StochasticSupplier<Bidder> b) {
    bidderSupplier = b;
  }

  @Parameters
  public static Collection<Object[]> configs() {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    return ImmutableList.of(
        // new Object[] { RandomBidder.supplier() },
        new Object[] { SolverBidder.supplier(objFunc,
            RandomSolver.supplier()) });
  }

  /**
   * 
   */
  @Before
  public void setUp() {
    ape1 = new AddParcelEvent(ParcelDTO
        .builder(new Point(1, 1), new Point(1, 4))
        .pickupTimeWindow(new TimeWindow(218300, 10 * 60000))
        .deliveryTimeWindow(new TimeWindow(0, 20 * 60000))
        .serviceDuration(5000)
        .orderAnnounceTime(-1)
        .build());

    ape2 = new AddParcelEvent(ParcelDTO
        .builder(new Point(4, 1), new Point(4, 4))
        .pickupTimeWindow(new TimeWindow(0, 10 * 60000))
        .deliveryTimeWindow(new TimeWindow(0, 20 * 60000))
        .serviceDuration(5000)
        .orderAnnounceTime(-1)
        .build());
  }

  /**
   * Tests an auction scenario.
   */
  @Test
  public void test() {
    final MASConfiguration configuration = new TruckConfiguration(
        FixedRoutePlanner.supplier(), bidderSupplier,
        ImmutableList.of(AuctionCommModel.supplier(), CommTestModel.supplier()));
    final Gendreau06Scenario scen = Gendreau06Parser.parser()
        .allowDiversion()
        .setNumVehicles(1)
        .addFile(ImmutableList.of(ape1, ape2), "req_rapide_1_240_24")
        .parse().get(0);

    final DynamicPDPTWProblem problem = ExperimentTest.init(scen,
        configuration, 123, false);
    final Simulator sim = problem.getSimulator();
    sim.tick();

    final RoadModel rm = Optional.fromNullable(
        problem.getSimulator().getModelProvider().getModel(RoadModel.class))
        .get();

    final PDPModel pm = Optional.fromNullable(
        problem.getSimulator().getModelProvider().getModel(PDPModel.class))
        .get();

    final Set<Truck> trucks = rm.getObjectsOfType(Truck.class);

    final Truck truck = trucks.iterator().next();
    assertEquals(1, trucks.size());

    final Set<DefaultParcel> parcels = rm.getObjectsOfType(DefaultParcel.class);
    assertEquals(2, parcels.size());
    final Iterator<DefaultParcel> it = parcels.iterator();
    final DefaultParcel dp1 = it.next();
    assertEquals(ape1.parcelDTO, dp1.dto);
    final DefaultParcel dp2 = it.next();
    assertEquals(ape2.parcelDTO, dp2.dto);

    final FixedRoutePlanner routePlanner = (FixedRoutePlanner) truck
        .getRoutePlanner();

    final Bidder bidder = (Bidder) truck.getCommunicator();

    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertTrue(bidder.getClaimedParcels().isEmpty());

    // set initial destination
    routePlanner.current = Optional.fromNullable(dp1);
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
    sim.tick();

    assertThat(truck.getRoute().iterator().next(), is(dp1));
    assertThat(rm.getDestination(truck), is(dp1.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));

    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertEquals(1, bidder.getClaimedParcels().size());
    assertTrue(bidder.getClaimedParcels().contains(dp1));

    // change destination
    routePlanner.current = Optional.fromNullable(dp2);
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
    sim.tick();

    assertThat(truck.getRoute().iterator().next(), is(dp2));
    assertThat(rm.getDestination(truck), is(dp2.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));
    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertEquals(1, bidder.getClaimedParcels().size());
    assertTrue(bidder.getClaimedParcels().contains(dp2));

    // change destination again, now back to first
    routePlanner.current = Optional.fromNullable(dp1);
    truck.handleEvent(new Event(CommunicatorEventType.CHANGE, this));
    sim.tick();

    assertThat(truck.getRoute().iterator().next(), is(dp1));
    assertThat(rm.getDestination(truck), is(dp1.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));

    while (!pm.getParcelState(dp1).isPickedUp()) {
      sim.tick();
    }
    routePlanner.current = Optional.fromNullable(dp2);
    while (rm.containsObject(dp2) && !rm.equalPosition(truck, dp2)) {
      sim.tick();
    }
  }

  @Test
  public void swapParcelTest() {
    final MASConfiguration configuration = new TruckConfiguration(
        RandomRoutePlanner.supplier(), bidderSupplier,
        ImmutableList.of(AuctionCommModel.supplier(), CommTestModel.supplier()));
    final Gendreau06Scenario scen = Gendreau06Parser.parser()
        .allowDiversion()
        .setNumVehicles(2)
        .addFile(ImmutableList.of(ape1, ape2), "req_rapide_1_240_24")
        .parse().get(0);

    final DynamicPDPTWProblem problem = ExperimentTest.init(scen,
        configuration, 123, false);
    final Simulator sim = problem.getSimulator();
    sim.tick();

    final RoadModel rm = Optional.fromNullable(
        problem.getSimulator().getModelProvider().getModel(RoadModel.class))
        .get();

    final PDPModel pm = Optional.fromNullable(
        problem.getSimulator().getModelProvider().getModel(PDPModel.class))
        .get();

    final AuctionCommModel cm = Optional.fromNullable(
        problem.getSimulator().getModelProvider()
            .getModel(AuctionCommModel.class))
        .get();

    final List<Truck> trucks = newArrayList(rm.getObjectsOfType(Truck.class));
    final Truck truck1 = trucks.get(0);
    final Truck truck2 = trucks.get(1);
    assertNotEquals(truck1, truck2);
    assertEquals(2, trucks.size());

    final Bidder bidder1 = (Bidder) truck1.getCommunicator();
    final Bidder bidder2 = (Bidder) truck2.getCommunicator();
    sim.tick();

    final DefaultParcel newParcel = new DefaultParcel(ParcelDTO.builder(
        new Point(0, 0), new Point(3, 4)).build());

    // SWAP a parcel to another truck
    final DefaultParcel parcelToSwap = truck1.getCommunicator().getParcels()
        .iterator().next();
    bidder1.releaseParcel(parcelToSwap);
    bidder2.receiveParcel(parcelToSwap);
    System.out.println(truck1.getRoute());
    System.out.println(truck2.getRoute());

    sim.tick();
    System.out.println("swapped parcel: " + parcelToSwap);
    System.out.println(truck1.getRoute());
    System.out.println(truck2.getRoute());

    System.out.println("GOGOGOGO");
    while (!pm.getParcelState(parcelToSwap).isPickedUp()) {
      sim.tick();
    }
    assertEquals(ParcelState.IN_CARGO, pm.getParcelState(parcelToSwap));
    assertTrue(pm.getContents(truck2).contains(parcelToSwap));

    sim.register(newParcel);
    // cm.receiveParcel(newParcel, sim.getCurrentTime());
    sim.tick();
  }

  /**
   * Illegal claim.
   */
  @Test(expected = IllegalArgumentException.class)
  public void claimFail1() {
    new RandomBidder(123).claim(new DefaultParcel(ape1.parcelDTO));
  }

  /**
   * Tests whether two consecutive calls to claim() throws an exception.
   */
  @Test
  public void claimFail2() {
    final RandomBidder rb = new RandomBidder(123);
    final DefaultParcel dp = new DefaultParcel(ape1.parcelDTO);
    final PDPModel pm = mock(PDPModel.class);
    rb.init(mock(PDPRoadModel.class), pm, mock(DefaultVehicle.class));
    when(pm.getParcelState(dp)).thenReturn(ParcelState.AVAILABLE);
    rb.receiveParcel(dp);
    rb.claim(dp);
    boolean fail = false;
    try {
      rb.claim(dp);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  /**
   * Illegal unclaim.
   */
  @Test(expected = IllegalArgumentException.class)
  public void unclaimFail1() {
    new RandomBidder(123).unclaim(new DefaultParcel(ape1.parcelDTO));
  }

  /**
   * Illegal unclaim.
   */
  @Test(expected = IllegalArgumentException.class)
  public void unclaimFail2() {
    final RandomBidder rb = new RandomBidder(123);
    final DefaultParcel dp = new DefaultParcel(ape1.parcelDTO);
    rb.receiveParcel(dp);
    rb.unclaim(dp);
  }

  static class FixedRoutePlanner extends AbstractRoutePlanner {
    Optional<DefaultParcel> current;

    FixedRoutePlanner() {
      current = Optional.absent();
    }

    @Override
    public Optional<DefaultParcel> current() {
      return current;
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    protected void doUpdate(Collection<DefaultParcel> onMap, long time) {}

    @Override
    protected void nextImpl(long time) {
      current = Optional.absent();
    }

    static StochasticSupplier<FixedRoutePlanner> supplier() {
      return new StochasticSuppliers.AbstractStochasticSupplier<FixedRoutePlanner>() {
        @Override
        public FixedRoutePlanner get(long seed) {
          return new FixedRoutePlanner();
        }
      };
    }
  }
}
