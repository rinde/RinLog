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
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.unmodifiableList;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.EventAPI;
import com.github.rinde.rinsim.event.EventDispatcher;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A partial {@link RoutePlanner} implementation, it already implements much of
 * the common required behaviors. Subclasses only need to concentrate on the
 * route planning itself.
 * @author Rinde van Lon
 */
public abstract class AbstractRoutePlanner implements RoutePlanner {
  /**
   * Logger.
   */
  protected static final Logger LOGGER = LoggerFactory
      .getLogger(AbstractRoutePlanner.class);

  /**
   * Reference to the {@link RoadModel}.
   */
  protected Optional<RoadModel> roadModel;

  /**
   * Reference to the {@link PDPModel}.
   */
  protected Optional<PDPModel> pdpModel;

  /**
   * Reference to the {@link Vehicle} that this planner is responsible for.
   */
  protected Optional<Vehicle> vehicle;

  /**
   * Indicates that this route planner has been updated (via a call to
   * {@link #update(Collection, long)}) at least once.
   */
  protected boolean updated;

  protected final EventDispatcher eventDispatcher;

  private final List<Parcel> history;
  private boolean initialized;

  /**
   * New abstract route planner.
   */
  protected AbstractRoutePlanner() {
    history = newArrayList();
    roadModel = Optional.absent();
    pdpModel = Optional.absent();
    vehicle = Optional.absent();
    eventDispatcher = new EventDispatcher(RoutePlannerEventType.CHANGE);
  }

  @Override
  public final void init(RoadModel rm, PDPModel pm, Vehicle dv) {
    LOGGER.info("init {}", dv);
    checkState(!isInitialized(), "init shoud be called only once");
    initialized = true;
    roadModel = Optional.of(rm);
    pdpModel = Optional.of(pm);
    vehicle = Optional.of(dv);
    afterInit();
  }

  @Override
  public final void update(Set<Parcel> onMap, long time) {
    checkIsInitialized();
    LOGGER.info("update {} {} size {}", vehicle.get(), time, onMap.size());
    updated = true;
    doUpdate(onMap, time);
    LOGGER.info("currentRoute {}", currentRoute());
  }

  @Override
  public final Optional<Parcel> next(long time) {
    checkIsInitialized();
    LOGGER.info("next {} {}", vehicle.get(), time);
    checkState(updated,
      "RoutePlanner should be udpated before it can be used, see update()");
    if (current().isPresent()) {
      history.add(current().get());
    }
    nextImpl(time);
    LOGGER.info("next after {}", currentRoute());
    return current();
  }

  @Override
  public Optional<ImmutableList<Parcel>> currentRoute() {
    if (current().isPresent()) {
      return Optional.of(ImmutableList.of(current().get()));
    }
    return Optional.absent();
  }

  @Override
  public Optional<Parcel> prev() {
    if (history.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(history.get(history.size() - 1));
  }

  protected void dispatchChangeEvent() {
    eventDispatcher.dispatchEvent(
      new Event(RoutePlannerEventType.CHANGE, this));
  }

  @Override
  public List<Parcel> getHistory() {
    return unmodifiableList(history);
  }

  /**
   * Should implement functionality of {@link #update(Set, long)} according to
   * the interface. It can be assumed that the method is allowed to be called
   * (i.e. the route planner is initialized). A
   * {@link RoutePlanner.RoutePlannerEventType#CHANGE} event should be
   * dispatched as soon as updating of the route is done.
   * @param onMap A collection of parcels which currently reside on the map.
   * @param time The current simulation time, this may be relevant for some
   *          route planners that want to take time windows into account.
   */
  protected abstract void doUpdate(Set<Parcel> onMap, long time);

  /**
   * Should implement functionality of {@link #next(long)} according to the
   * interface. It can be assumed that the method is allowed to be called (i.e.
   * the route planner is initialized and has been updated at least once).
   * @param time The current time.
   */
  protected abstract void nextImpl(long time);

  /**
   * This method can optionally be overridden to execute additional code right
   * after {@link #init(RoadModel, PDPModel, Vehicle)} is called.
   */
  protected void afterInit() {}

  /**
   * Checks if {@link #isInitialized()} returns <code>true</code>, throws an
   * {@link IllegalStateException} otherwise.
   */
  protected final void checkIsInitialized() {
    checkState(isInitialized(),
      "RoutePlanner should be initialized before it can be used, see init()");
  }

  /**
   * @return <code>true</code> if the route planner is already initialized,
   *         <code>false</code> otherwise.
   */
  protected final boolean isInitialized() {
    return initialized;
  }

  /**
   * @return <code>true</code> if the route planner has been updated at least
   *         once, <code>false</code> otherwise.
   */
  protected final boolean isUpdated() {
    return updated;
  }

  @Override
  public final EventAPI getEventAPI() {
    return eventDispatcher.getPublicEventAPI();
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .addValue(Integer.toHexString(hashCode()))
        .toString();
  }
}
