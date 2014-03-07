package rinde.logistics.pdptw.mas.comm;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.junit.Test;

import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.TruckConfiguration;
import rinde.logistics.pdptw.mas.comm.CommTest.CommTestModel;
import rinde.logistics.pdptw.mas.route.AbstractRoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;
import rinde.sim.util.TimeWindow;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class AuctionTest {

  @Test
  public void test() {

    final MASConfiguration configuration = new TruckConfiguration(
        FixedRoutePlanner.supplier(), RandomBidder.supplier(),
        ImmutableList.of(AuctionCommModel.supplier(), CommTestModel.supplier()));

    final AddParcelEvent ape1 = new AddParcelEvent(new ParcelDTO(
        new Point(1, 1), new Point(1, 4), new TimeWindow(218300, 10 * 60000),
        new TimeWindow(0, 20 * 60000), 0, -1, 5000, 5000));

    final AddParcelEvent ape2 = new AddParcelEvent(new ParcelDTO(
        new Point(4, 1), new Point(4, 4), new TimeWindow(0, 10 * 60000),
        new TimeWindow(0, 20 * 60000), 0, -1, 5000, 5000));

    final Gendreau06Scenario scen = Gendreau06Parser.parser().allowDiversion()
        .setNumVehicles(1)
        .addFile(ImmutableList.of(ape1, ape2), "req_rapide_1_240_24").parse()
        .get(0);

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

    final AbstractBidder bidder = (AbstractBidder) truck.getCommunicator();

    assertEquals(2, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertTrue(bidder.getClaimedParcels().isEmpty());

    // set initial destination
    routePlanner.current = Optional.fromNullable(dp1);
    System.out.println("before");
    sim.tick();
    System.out.println("#1");

    assertThat(truck.getRoute().iterator().next(), is(dp1));
    assertThat(rm.getDestination(truck), is(dp1.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));

    assertEquals(1, bidder.getParcels().size());
    assertFalse(bidder.getParcels().contains(dp1));
    assertTrue(bidder.getParcels().contains(dp2));
    assertEquals(1, bidder.getClaimedParcels().size());
    assertTrue(bidder.getClaimedParcels().contains(dp1));

    // change destination
    routePlanner.current = Optional.fromNullable(dp2);
    sim.tick();
    System.out.println("#2");

    assertThat(truck.getRoute().iterator().next(), is(dp2));
    assertThat(rm.getDestination(truck), is(dp2.dto.pickupLocation));
    assertThat(rm.getPosition(truck), is(not(truck.getDTO().startPosition)));
    assertEquals(1, bidder.getParcels().size());
    assertTrue(bidder.getParcels().contains(dp1));
    assertFalse(bidder.getParcels().contains(dp2));
    assertEquals(1, bidder.getClaimedParcels().size());
    assertTrue(bidder.getClaimedParcels().contains(dp2));

    // change destination again, now back to first
    routePlanner.current = Optional.fromNullable(dp1);
    sim.tick();
    System.out.println("#3");

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

    System.out.println(sim.getCurrentTime());
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

    static SupplierRng<FixedRoutePlanner> supplier() {
      return new DefaultSupplierRng<FixedRoutePlanner>() {
        @Override
        public FixedRoutePlanner get(long seed) {
          return new FixedRoutePlanner();
        }
      };
    }

  }

}
