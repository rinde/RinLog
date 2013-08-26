/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.unmodifiableList;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.DefaultVehicle;

/**
 * A partial {@link RoutePlanner} implementation, it already implements much of
 * the common required behaviors. Subclasses only need to concentrate on the
 * route planning itself.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public abstract class AbstractRoutePlanner implements RoutePlanner {

  private final List<DefaultParcel> history;
  private boolean initialized;
  private boolean updated;

  @Nullable
  protected RoadModel roadModel;
  @Nullable
  protected PDPModel pdpModel;
  @Nullable
  protected DefaultVehicle vehicle;

  protected AbstractRoutePlanner() {
    history = newArrayList();
  }

  public void init(RoadModel rm, PDPModel pm, DefaultVehicle dv) {
    checkState(!isInitialized(), "init shoud be called only once");
    initialized = true;
    roadModel = rm;
    pdpModel = pm;
    vehicle = dv;
  }

  public final void update(Collection<DefaultParcel> onMap, long time) {
    checkState(isInitialized(), "RoutePlanner should be initialized before it can be used, see init()");
    updated = true;
    doUpdate(onMap, time);
  }

  /**
   * Should implement functionality of
   * {@link #update(Collection, Collection, long)} according to the interface.
   * It can be assumed that hte method is allowed to be called (i.e. the route
   * planner is initialized).
   * @param onMap A collection of parcels which currently reside on the map.
   * @param inCargo A collection of parcels which currently reside in the
   *          truck's cargo.
   * @param time The current simulation time, this may be relevant for some
   *          routeplanners that want to take time windows into account.
   * @see #doUpdate(Collection, Collection, long)
   */
  protected abstract void doUpdate(Collection<DefaultParcel> onMap, long time);

  @Nullable
  public final DefaultParcel next(long time) {
    checkState(isInitialized(), "RoutePlanner should be initialized before it can be used, see init()");
    checkState(updated, "RoutePlanner should be udpated before it can be used, see update()");
    history.add(current());
    nextImpl(time);
    return current();
  }

  /**
   * Should implement functionality of {@link #next(long)} according to the
   * interface. It can be assumed that the method is allowed to be called (i.e.
   * the route planner is initialized and has been updated at least once).
   * @param time The current time.
   */
  protected abstract void nextImpl(long time);

  @Nullable
  public DefaultParcel prev() {
    if (history.isEmpty()) {
      return null;
    }
    return history.get(history.size() - 1);
  }

  public List<DefaultParcel> getHistory() {
    return unmodifiableList(history);
  }

  /**
   * @return <code>true</code> if the routeplanner is already initialized,
   *         <code>false</code> otherwise.
   */
  protected boolean isInitialized() {
    return initialized;
  }

  /**
   * @return <code>true</code> if the routeplanner has been updated at least
   *         once, <code>false</code> otherwise.
   */
  protected boolean isUpdated() {
    return updated;
  }

}
