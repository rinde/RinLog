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
package com.github.rinde.logistics.pdptw.mas.comm;

import java.util.Collection;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.event.Listener;

/**
 * Interface of communications. Facade for communication system. acts on behalve
 * of an 'agent'. Implementations of this are added to and 'live' on a truck.
 * Via the communicator a truck receives updates about the environment with
 * regard to {@link Parcel}s.
 *
 * @author Rinde van Lon
 */
public interface Communicator {

  /**
   * Event type for {@link Communicator}.
   */
  public enum CommunicatorEventType {
    /**
     * Indicates that the communicator received information indicating an
     * environment change which caused the task assignment to change.
     */
    CHANGE;
  }

  /**
   * Initializes the communicator for one specific {@link Vehicle} in a
   * {@link RoadModel} and a {@link PDPModel}.
   * @param rm The {@link RoadModel} which the truck is on.
   * @param pm The {@link PDPModel} which manages the truck.
   * @param v The {@link Vehicle} for which routes will be planned.
   */
  void init(RoadModel rm, PDPModel pm, Vehicle v);

  /**
   * Add the {@link Listener} to this {@link Communicator}. The listener should
   * from now receive all {@link CommunicatorEventType#CHANGE} events.
   * @param l The listener to add.
   */
  void addUpdateListener(Listener l);

  /**
   * Indicates that the truck is waiting for this parcel to become available. Is
   * used for intention spreading.
   * @param p The parcel.
   */
  void waitFor(Parcel p);

  /**
   * This communicator lays a claim on the specified {@link Parcel}, this means
   * that this communicator is from now on exclusively responsible for the
   * servicing of this parcel.
   * @param p The parcel to claim.
   */
  void claim(Parcel p);

  /**
   * Releases the claim made by {@link #claim(Parcel)}.
   * @param p The parcel to release the claim for.
   */
  void unclaim(Parcel p);

  /**
   * Indicates that the previously claimed parcels are done and are therefore
   * removed from the {@link #getParcels()} and {@link #getClaimedParcels()}.
   */
  void done();

  /**
   * This method may only return {@link Parcel}s which are not yet picked up.
   * All returned parcels are assigned to this communicator, however, this
   * assignment is not guaranteed to be exclusive.
   * @return All parcels which this communicator may handle.
   */
  Collection<Parcel> getParcels();

  /**
   * @return Parcels which are claimed by this communicator and are not yet
   *         'done', see {@link #done()}.
   */
  Collection<Parcel> getClaimedParcels();
}
