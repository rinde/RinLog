package rinde.logistics.pdptw.mas;

import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.common.VehicleDTO;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.util.SupplierRng;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class TruckConfiguration extends DefaultMASConfiguration {
  protected final SupplierRng<? extends RoutePlanner> rpSupplier;
  protected final SupplierRng<? extends Communicator> cSupplier;
  protected final ImmutableList<? extends SupplierRng<? extends Model<?>>> mSuppliers;

  public TruckConfiguration(
      SupplierRng<? extends RoutePlanner> routePlannerSupplier,
      SupplierRng<? extends Communicator> communicatorSupplier,
      ImmutableList<? extends SupplierRng<? extends Model<?>>> modelSuppliers) {
    rpSupplier = routePlannerSupplier;
    cSupplier = communicatorSupplier;
    mSuppliers = modelSuppliers;
  }

  @Override
  public Creator<AddVehicleEvent> getVehicleCreator() {
    return new Creator<AddVehicleEvent>() {
      @Override
      public boolean create(Simulator sim, AddVehicleEvent event) {
        final RoutePlanner rp = rpSupplier.get(sim.getRandomGenerator()
            .nextLong());
        final Communicator c = cSupplier.get(sim.getRandomGenerator()
            .nextLong());
        return sim.register(createTruck(event.vehicleDTO, rp, c));
      }
    };
  }

  /**
   * Factory method that can be overridden by subclasses that want to use their
   * own {@link Truck} implementation.
   * @param dto
   * @param rp
   * @param c
   * @return
   */
  protected Truck createTruck(VehicleDTO dto, RoutePlanner rp, Communicator c) {
    return new Truck(dto, rp, c);
  }

  @Override
  public ImmutableList<? extends SupplierRng<? extends Model<?>>> getModels() {
    return mSuppliers;
  }

  @Override
  public String toString() {
    return rpSupplier + "-" + cSupplier + "-" + Joiner.on("-").join(mSuppliers);
  }
}
