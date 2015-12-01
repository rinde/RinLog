/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;

import javax.annotation.Nullable;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle.RouteAdjuster;
import com.github.rinde.rinsim.scenario.TimedEventHandler;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.auto.value.AutoValue;

/**
 * @author Rinde van Lon
 *
 */
public interface TruckFactory
    extends TimedEventHandler<AddVehicleEvent>, Serializable {

  /**
   * @return Supplier for {@link RoutePlanner} instances, it supplies a new
   *         instance for <i>every</i> {@link Truck}.
   */
  StochasticSupplier<? extends RoutePlanner> getRoutePlanner();

  /**
   * @return Supplier for {@link Communicator} instances, it supplies a new
   *         instance for <i>every</i> {@link Truck}.
   */
  StochasticSupplier<? extends Communicator> getCommunicator();

  RouteAdjuster getRouteAdjuster();

  boolean getLazyComputation();

  @AutoValue
  public abstract static class DefaultTruckFactory implements TruckFactory {

    private static final long serialVersionUID = -5872180422731872369L;

    DefaultTruckFactory() {}

    @Override
    public void handleTimedEvent(AddVehicleEvent event,
        SimulatorAPI simulator) {
      final RoutePlanner rp =
        getRoutePlanner().get(simulator.getRandomGenerator()
            .nextLong());
      final Communicator c =
        getCommunicator().get(simulator.getRandomGenerator()
            .nextLong());
      simulator.register(new Truck(event.getVehicleDTO(), rp, c,
          getRouteAdjuster(), getLazyComputation()));
    }

    public static Builder builder() {
      return new Builder();
    }
  }

  public static class Builder {
    @Nullable
    StochasticSupplier<? extends RoutePlanner> routePlanner;
    @Nullable
    StochasticSupplier<? extends Communicator> communicator;
    RouteAdjuster routeAdjuster;
    boolean lazyComputation;

    Builder() {
      routePlanner = null;
      communicator = null;
      routeAdjuster = RouteFollowingVehicle.nopAdjuster();
      lazyComputation = true;
    }

    public Builder setRoutePlanner(
        StochasticSupplier<? extends RoutePlanner> rp) {
      routePlanner = rp;
      return this;
    }

    public Builder setCommunicator(
        StochasticSupplier<? extends Communicator> c) {
      communicator = c;
      return this;
    }

    public Builder setRouteAdjuster(RouteAdjuster ra) {
      routeAdjuster = ra;
      return this;
    }

    public Builder setLazyComputation(boolean lc) {
      lazyComputation = lc;
      return this;
    }

    public TruckFactory build() {
      checkArgument(routePlanner != null, "A route planner must be specified.");
      checkArgument(communicator != null, "A communicator must be specified.");
      return new AutoValue_TruckFactory_DefaultTruckFactory(routePlanner,
          communicator, routeAdjuster, lazyComputation);
    }
  }
}
