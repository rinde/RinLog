/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
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

import java.io.Serializable;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.scenario.TimedEventHandler;
import com.github.rinde.rinsim.util.StochasticSupplier;

/**
 * @author Rinde van Lon
 *
 */
public class VehicleHandler implements TimedEventHandler<AddVehicleEvent>,
  Serializable {

  private static final long serialVersionUID = 3802558880510520740L;

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
   * Instantiate a new configuration.
   * @param rp {@link #rpSupplier}.
   * @param c {@link #cSupplier}.
   */
  public VehicleHandler(StochasticSupplier<? extends RoutePlanner> rp,
    StochasticSupplier<? extends Communicator> c) {
    rpSupplier = rp;
    cSupplier = c;
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
  public void handleTimedEvent(AddVehicleEvent event, SimulatorAPI simulator) {
    final RoutePlanner rp = rpSupplier.get(simulator.getRandomGenerator()
      .nextLong());
    final Communicator c = cSupplier.get(simulator.getRandomGenerator()
      .nextLong());
    simulator.register(createTruck(event.getVehicleDTO(), rp, c));
  }

}
