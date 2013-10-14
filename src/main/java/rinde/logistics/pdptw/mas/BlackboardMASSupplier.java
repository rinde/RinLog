package rinde.logistics.pdptw.mas;

import rinde.logistics.pdptw.mas.comm.BlackboardCommModel;
import rinde.logistics.pdptw.mas.comm.BlackboardUser;
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

public class BlackboardMASSupplier implements SupplierRng<MASConfiguration> {
  final SupplierRng<? extends RoutePlanner> routePlannerSupplier;

  public BlackboardMASSupplier(SupplierRng<? extends RoutePlanner> rp) {
    routePlannerSupplier = rp;
  }

  @Override
  public MASConfiguration get(long seed) {
    return new BlackboardMASConfiguration(seed);
  }

  @Override
  public String toString() {
    return "Blackboard-" + routePlannerSupplier.toString();
  }

  class BlackboardMASConfiguration extends DefaultMASConfiguration {
    BlackboardMASConfiguration(long seed) {
      super(seed);
    }

    @Override
    public Creator<AddVehicleEvent> getVehicleCreator() {
      return new Creator<AddVehicleEvent>() {
        @Override
        public boolean create(Simulator sim, AddVehicleEvent event) {
          final RoutePlanner rp = routePlannerSupplier.get(rng.nextLong());
          final Communicator c = new BlackboardUser();
          return sim.register(new Truck(event.vehicleDTO, rp, c));
        }
      };
    }

    @Override
    public ImmutableList<? extends Model<?>> getModels() {
      return ImmutableList.of(new BlackboardCommModel());
    }
  }
}
