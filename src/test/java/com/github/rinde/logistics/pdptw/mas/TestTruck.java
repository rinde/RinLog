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

import java.util.Collection;

import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.fsm.State;
import com.github.rinde.rinsim.fsm.StateMachine;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle;
import com.google.auto.value.AutoValue;

/**
 *
 * @author Rinde van Lon
 */
public class TestTruck extends Truck {
  TestTruck(VehicleDTO pDto, RoutePlanner rp, Communicator c,
      RouteAdjuster ra, boolean lazy) {
    super(pDto, rp, c, ra, lazy);
  }

  public State<StateEvent, RouteFollowingVehicle> getState() {
    return stateMachine.getCurrentState();
  }

  public State<StateEvent, RouteFollowingVehicle> waitState() {
    return waitState;
  }

  public State<StateEvent, RouteFollowingVehicle> gotoState() {
    return gotoState;
  }

  public State<StateEvent, RouteFollowingVehicle> waitForServiceState() {
    return waitForServiceState;
  }

  public State<StateEvent, RouteFollowingVehicle> serviceState() {
    return serviceState;
  }

  public StateMachine<StateEvent, RouteFollowingVehicle> getStateMachine() {
    return stateMachine;
  }

  @Override
  public Collection<Parcel> getRoute() {
    return super.getRoute();
  }

  @AutoValue
  public abstract static class TestTruckFactory implements TruckFactory {

    private static final long serialVersionUID = 7021922386391250575L;

    @Override
    public void handleTimedEvent(AddVehicleEvent event,
        SimulatorAPI simulator) {
      final RoutePlanner rp =
        getRoutePlanner().get(simulator.getRandomGenerator()
            .nextLong());
      final Communicator c =
        getCommunicator().get(simulator.getRandomGenerator()
            .nextLong());
      simulator.register(new TestTruck(event.getVehicleDTO(), rp, c,
          getRouteAdjuster(), getLazyComputation()));
    }

    public static Builder testBuilder() {
      return new Builder();
    }

    public static class Builder extends TruckFactory.Builder {
      @Override
      public TestTruckFactory build() {
        return new AutoValue_TestTruck_TestTruckFactory(routePlanner,
            communicator, routeAdjuster, lazyComputation);
      }
    }
  }
}
