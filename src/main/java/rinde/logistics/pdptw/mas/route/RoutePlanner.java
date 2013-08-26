/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import rinde.logistics.pdptw.mas.Truck;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;

/**
 * This is a route planner interface. It is unusual in the sense that it reveals
 * its future destinations one hop at a time. The future destinations are hidden
 * deliberatly, they may not be known at a certain time. This allows a more
 * flexible routeplanner, internally the future hops may change repeatedly. The
 * routeplanner is always coupled to one {@link Truck}, the routeplanner plans
 * its routes for this truck instance.
 * <p>
 * <b>Initialization</b> {@link RoutePlanner} instances should be initialized in
 * a uniform manner. The methods should be called in the following order:
 * <ol>
 * <li>constructor</li>
 * <li>{@link #init(RoadModel, PDPModel, DefaultVehicle)}</li>
 * <li>{@link #update(Collection, long)}</li>
 * </ol>
 * Once these methods are called in this order, {@link #next(long)} may be
 * called. {@link #update(Collection, long)} may be called more than once. All
 * implementations of this interface should throw {@link IllegalStateException}s
 * when these methods are called in an incorrect order (as defined by the test
 * class).
 * <p>
 * <b>Usage</b> Once the initialization is complete, {@link #current()} will
 * return the parcel that should be visited next (if there are any parcels of
 * course). The implementation should guarantee that any subsequent invocations
 * of {@link #current()} should always return the same value. That is, unless
 * one of the two <i>modifying</i> methods are called:
 * <ul>
 * <li>{@link #update(Collection, long)}</li>
 * <li>{@link #next(long)}</li>
 * </ul>
 * Each time one of these is called the route <i>may</i> change. When
 * {@link #next(long)} is called, the value of {@link #current()} is saved into
 * its history (accesible via {@link #getHistory()}) and accesible via
 * {@link #prev()}.
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public interface RoutePlanner {

  /**
   * Initializes the routeplanner for one specific {@link DefaultVehicle} in a
   * {@link RoadModel} and a {@link PDPModel}.
   * @param rm The {@link RoadModel} which the truck is on.
   * @param pm The {@link PDPModel} which manages the truck.
   * @param dv The {@link DefaultVehicle} for which routes will be planned.
   */
  void init(RoadModel rm, PDPModel pm, DefaultVehicle dv);

  /**
   * Indicates a change in data (or sets it initially), this should update the
   * route. This is one of the <i>modifying</i> methods (see
   * {@link RoutePlanner} for more information). This method must be called
   * <i>after</i> {@link #init(RoadModel, PDPModel, DefaultVehicle)} and should
   * be called <i>before</i> any calls to {@link #next(long)}.
   * <p>
   * <b>Implementations of this method should treat the incoming collections as
   * immutable.</b>
   * @param onMap A collection of parcels which currently reside on the map.
   *          Note: this may be a <i>subset</i> of all parcels available.
   * @param inCargo A collection of parcels which currently reside in the
   *          truck's cargo.
   * @param time The current simulation time, this may be relevant for some
   *          routeplanners that want to take time windows into account.
   */
  void update(Collection<DefaultParcel> onMap, long time);

  /**
   * Should return the current parcel (the parcel that should be visited next).
   * Subsequent calls should always return the same destination (no
   * recomputation should be done). Only when one of the <i>modifying</i>
   * methods is called, {@link #update(Collection, long)} or {@link #next(long)}
   * , this method may return a different value.
   * @return The current parcel that should be visited next, returns
   *         <code>null</code> when there are no parcels to go to.
   */
  @Nullable
  DefaultParcel current();

  /**
   * Indicates that the current location has been visited. Computes the next
   * parcel to visit. This is one of the <i>modifying</i> methods (see
   * {@link RoutePlanner} for more information). When called, the value of
   * {@link #current()} is saved into the history (see {@link #getHistory()})
   * and accesible via {@link #prev()}. This method will return the new value of
   * {@link #current()}.
   * @param time The current simulation time.
   * @return The new current parcel or <code>null</code> if there are no parcels
   *         to visit.
   */
  @Nullable
  DefaultParcel next(long time);

  /**
   * @return The value of {@link #current()} right before the last invocation of
   *         {@link #next(long)}. This is always the last value in the list
   *         returned by {@link #getHistory()}. Returns <code>null</code> if
   *         {@link #next(long)} has never been called.
   */
  @Nullable
  DefaultParcel prev();

  /**
   * @return A list of all visited parcels. A parcel is 'visited' when
   *         {@link #next(long)} is called.
   */
  List<DefaultParcel> getHistory();

  /**
   * @return <code>false</code> if the next invocation of {@link #next(long)}
   *         will return <code>null</code>, returns <code>true</code> otherwise.
   */
  boolean hasNext();
}
