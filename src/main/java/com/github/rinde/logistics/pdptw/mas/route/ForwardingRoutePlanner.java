/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import java.util.List;
import java.util.Set;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.road.RoadModel;
import com.github.rinde.rinsim.event.EventAPI;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Forwarding decorator implementation of {@link RoutePlanner}. Note: the
 * delegate instance can be leaked via dispatching of events.
 * @author Rinde van Lon
 */
public abstract class ForwardingRoutePlanner implements RoutePlanner {

  /**
   * @return The delegate that is decorated.
   */
  protected abstract RoutePlanner delegate();

  @Override
  public void init(RoadModel rm, PDPModel pm, Vehicle dv) {
    delegate().init(rm, pm, dv);
  }

  @Override
  public void update(Set<Parcel> onMap, long time) {
    delegate().update(onMap, time);
  }

  @Override
  public Optional<Parcel> current() {
    return delegate().current();
  }

  @Override
  public Optional<ImmutableList<Parcel>> currentRoute() {
    return delegate().currentRoute();
  }

  @Override
  public Optional<Parcel> next(long time) {
    return delegate().next(time);
  }

  @Override
  public Optional<Parcel> prev() {
    return delegate().prev();
  }

  @Override
  public List<Parcel> getHistory() {
    return delegate().getHistory();
  }

  @Override
  public boolean hasNext() {
    return delegate().hasNext();
  }

  @Override
  public EventAPI getEventAPI() {
    return delegate().getEventAPI();
  }
}
