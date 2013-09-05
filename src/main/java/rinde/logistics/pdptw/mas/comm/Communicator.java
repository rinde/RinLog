package rinde.logistics.pdptw.mas.comm;

import java.util.Collection;

import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.event.Listener;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;

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
     * environment change.
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
   * Indicates that the truck is going to this parcel (this is final!).
   * @param p The parcel.
   */
  void claim(DefaultParcel p);

  /**
   * This method may only return {@link DefaultParcel}s which are not yet picked
   * up.
   * @return All parcels which this communicator may handle.
   */
  Collection<DefaultParcel> getParcels();

}
