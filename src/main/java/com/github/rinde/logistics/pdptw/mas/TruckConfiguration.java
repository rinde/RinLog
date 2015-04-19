/*
 * Copyright (C) 2013-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.mas;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.Model;
import com.github.rinde.rinsim.core.model.ModelBuilder;
import com.github.rinde.rinsim.core.pdptw.VehicleDTO;
import com.github.rinde.rinsim.experiment.DefaultMASConfiguration;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem.Creator;
import com.github.rinde.rinsim.scenario.AddVehicleEvent;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * A {@link com.github.rinde.rinsim.experiment.MASConfiguration} that configures
 * a simulation to use a {@link Truck} instance as vehicle.
 * @author Rinde van Lon
 */
public class TruckConfiguration extends DefaultMASConfiguration {
  private static final long serialVersionUID = -824149700034664324L;

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
  protected final ImmutableList<ModelBuilder<?, ?>> mSuppliers;

  /**
   * Instantiate a new configuration.
   * @param routePlannerSupplier {@link #rpSupplier}.
   * @param communicatorSupplier {@link #cSupplier}.
   * @param modelSuppliers {@link #mSuppliers}.
   */
  @SuppressWarnings("unchecked")
  public TruckConfiguration(
    StochasticSupplier<? extends RoutePlanner> routePlannerSupplier,
    StochasticSupplier<? extends Communicator> communicatorSupplier,
    ImmutableList<? extends StochasticSupplier<? extends Model<?>>> modelSuppliers) {
    rpSupplier = routePlannerSupplier;
    cSupplier = communicatorSupplier;
    mSuppliers = (ImmutableList<ModelBuilder<?, ?>>) modelSuppliers;
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
        sim.register(createTruck(event.vehicleDTO, rp, c));
        return true;
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
  public ImmutableList<? extends ModelBuilder<?, ?>> getModels() {
    return mSuppliers;
  }

  @Override
  public String toString() {
    return Joiner.on("-").join(rpSupplier, cSupplier, mSuppliers.toArray());
  }
}
