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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.google.common.base.Optional;

/**
 * A {@link RoutePlanner} implementation that lets a vehicle go to its closest
 * destination.
 * @author Rinde van Lon
 */
public class GotoClosestRoutePlanner extends AbstractRoutePlanner {

  Comparator<Parcel> comp;

  private Optional<Parcel> current;
  private final List<Parcel> parcels;

  /**
   * New instance.
   */
  public GotoClosestRoutePlanner() {
    comp = new ClosestDistanceComparator();
    current = Optional.absent();
    parcels = newArrayList();
  }

  @Override
  protected final void doUpdate(Collection<Parcel> onMap, long time) {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    final Collection<Parcel> inCargo = Collections.checkedCollection(
      (Collection) pdpModel.get().getContents(vehicle.get()),
      Parcel.class);
    parcels.clear();
    parcels.addAll(onMap);
    parcels.addAll(onMap);
    parcels.addAll(inCargo);
    updateCurrent();
  }

  private void updateCurrent() {
    if (parcels.isEmpty()) {
      current = Optional.absent();
    } else {
      current = Optional.of(Collections.min(parcels, comp));
    }
  }

  @Override
  protected void nextImpl(long time) {
    if (current.isPresent()) {
      parcels.remove(current.get());
    }
    updateCurrent();
  }

  @Override
  public Optional<Parcel> current() {
    return current;
  }

  @Override
  public boolean hasNext() {
    return !parcels.isEmpty();
  }

  /**
   * @return A {@link StochasticSupplier} that supplies
   *         {@link GotoClosestRoutePlanner} instances.
   */
  public static StochasticSupplier<GotoClosestRoutePlanner> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<GotoClosestRoutePlanner>() {
      private static final long serialVersionUID = 1701618808844264668L;

      @Override
      public GotoClosestRoutePlanner get(long seed) {
        return new GotoClosestRoutePlanner();
      }
    };
  }

  static Point getPos(Parcel parcel, PDPModel model) {
    if (model.getParcelState(parcel).isPickedUp()) {
      return parcel.getDto().getDeliveryLocation();
    }
    return parcel.getDto().getPickupLocation();
  }

  class ClosestDistanceComparator implements Comparator<Parcel> {
    @Override
    public int compare(@Nullable Parcel arg0,
      @Nullable Parcel arg1) {
      final Point cur = roadModel.get().getPosition(vehicle.get());
      final Point p0 = getPos(checkNotNull(arg0), pdpModel.get());
      final Point p1 = getPos(checkNotNull(arg1), pdpModel.get());
      return Double.compare(Point.distance(cur, p0), Point.distance(cur, p1));
    }
  }
}
