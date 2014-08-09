package rinde.logistics.pdptw.mas;

import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.model.Model;
import rinde.sim.core.pdptw.VehicleDTO;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.scenario.AddVehicleEvent;
import rinde.sim.util.StochasticSupplier;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * A {@link rinde.sim.pdptw.experiment.MASConfiguration} that configures a
 * simulation to use a {@link Truck} instance as vehicle.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class TruckConfiguration extends DefaultMASConfiguration {
  /**
   * Supplier for {@link RoutePlanner} instances, it supplies a new instance for
   * <i>every</i> {@link Truck}.
   */
  protected final StochasticSupplier<? extends RoutePlanner> rpSupplier;

  /**
   * Supplier for {@link Communicator} instances, it supplies a new instance for
   * <i>every</i> {@link Truck}.
   */
  protected final StochasticSupplier<? extends Communicator> cSupplier;

  /**
   * Suppliers for {@link Model}s, for each model a new instance is created for
   * each simulation.
   */
  protected final ImmutableList<? extends StochasticSupplier<? extends Model<?>>> mSuppliers;

  /**
   * Instantiate a new configuration.
   * @param routePlannerSupplier {@link #rpSupplier}.
   * @param communicatorSupplier {@link #cSupplier}.
   * @param modelSuppliers {@link #mSuppliers}.
   */
  public TruckConfiguration(
      StochasticSupplier<? extends RoutePlanner> routePlannerSupplier,
      StochasticSupplier<? extends Communicator> communicatorSupplier,
      ImmutableList<? extends StochasticSupplier<? extends Model<?>>> modelSuppliers) {
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
   * @param dto The {@link VehicleDTO} containing the vehicle information.
   * @param rp The {@link RoutePlanner} to use in the truck.
   * @param c The {@link Communicator} to use in the truck.
   * @return The newly created truck.
   */
  protected Truck createTruck(VehicleDTO dto, RoutePlanner rp, Communicator c) {
    return new Truck(dto, rp, c);
  }

  @Override
  public ImmutableList<? extends StochasticSupplier<? extends Model<?>>> getModels() {
    return mSuppliers;
  }

  @Override
  public String toString() {
    return Joiner.on("-").join(rpSupplier, cSupplier, mSuppliers.toArray());
  }
}
