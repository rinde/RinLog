package com.github.rinde.logistics.pdptw.mas.comm;

import java.util.Collection;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.DefaultVehicle;
import com.github.rinde.rinsim.event.Listener;

/**
 * Interface of communications. Facade for communication system. acts on behalve
 * of an 'agent'. Implementations of this are added to and 'live' on a truck.
 * Via the communicator a truck receives updates about the environment with
 * regard to {@link DefaultParcel}s.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
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
   * Initializes the communicator for one specific {@link DefaultVehicle} in a
   * {@link RoadModel} and a {@link PDPModel}.
   * @param rm The {@link RoadModel} which the truck is on.
   * @param pm The {@link PDPModel} which manages the truck.
   * @param v The {@link DefaultVehicle} for which routes will be planned.
   */
  void init(RoadModel rm, PDPModel pm, DefaultVehicle v);

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
  void waitFor(DefaultParcel p);

  /**
   * This communicator lays a claim on the specified {@link DefaultParcel}, this
   * means that this communicator is from now on exclusively responsible for the
   * servicing of this parcel.
   * @param p The parcel to claim.
   */
  void claim(DefaultParcel p);

  /**
   * Releases the claim made by {@link #claim(DefaultParcel)}.
   * @param p The parcel to release the claim for.
   */
  void unclaim(DefaultParcel p);

  /**
   * Indicates that the previously claimed parcels are done and are therefore
   * removed from the {@link #getParcels()} and {@link #getClaimedParcels()}.
   */
  void done();

  /**
   * This method may only return {@link DefaultParcel}s which are not yet picked
   * up. All returned parcels are assigned to this communicator, however, this
   * assignment is not guaranteed to be exclusive.
   * @return All parcels which this communicator may handle.
   */
  Collection<DefaultParcel> getParcels();

  /**
   * @return Parcels which are claimed by this communicator and are not yet
   *         'done', see {@link #done()}.
   */
  Collection<DefaultParcel> getClaimedParcels();
}
