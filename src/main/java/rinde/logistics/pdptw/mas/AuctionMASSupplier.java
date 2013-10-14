package rinde.logistics.pdptw.mas;

import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.Bidder;
import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.util.SupplierRng;

import com.google.common.collect.ImmutableList;

public class AuctionMASSupplier implements SupplierRng<MASConfiguration> {
  final SupplierRng<? extends RoutePlanner> routePlannerSupplier;
  final SupplierRng<? extends Bidder> bidderSupplier;

  public AuctionMASSupplier(SupplierRng<? extends RoutePlanner> rp,
      SupplierRng<? extends Bidder> b) {
    routePlannerSupplier = rp;
    bidderSupplier = b;
  }

  @Override
  public MASConfiguration get(long seed) {
    return new AuctionMASConfiguration(seed);
  }

  @Override
  public String toString() {
    return "Auction-" + routePlannerSupplier.toString() + "-"
        + bidderSupplier.toString();
  }

  class AuctionMASConfiguration extends DefaultMASConfiguration {
    AuctionMASConfiguration(long seed) {
      super(seed);
    }

    @Override
    public Creator<AddVehicleEvent> getVehicleCreator() {
      return new Creator<AddVehicleEvent>() {
        @Override
        public boolean create(Simulator sim, AddVehicleEvent event) {
          final Communicator c = bidderSupplier.get(rng.nextLong());
          final RoutePlanner rp = routePlannerSupplier.get(rng.nextLong());
          return sim.register(new Truck(event.vehicleDTO, rp, c));
        }
      };
    }

    @Override
    public ImmutableList<? extends Model<?>> getModels() {
      return ImmutableList.of(new AuctionCommModel());
    }
  }
}
