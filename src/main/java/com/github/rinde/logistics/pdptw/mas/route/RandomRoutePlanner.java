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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomAdaptor;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.google.common.base.Optional;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

/**
 * A {@link RoutePlanner} implementation that creates random routes.
 * @author Rinde van Lon
 */
public class RandomRoutePlanner extends AbstractRoutePlanner {

  private final Multiset<Parcel> assignedParcels;
  private Optional<Parcel> current;
  private final Random rng;

  /**
   * Creates a random route planner using the specified random seed.
   * @param seed The random seed.
   */
  public RandomRoutePlanner(long seed) {
    LOGGER.info("constructor {}", seed);
    assignedParcels = LinkedHashMultiset.create();
    current = Optional.absent();
    rng = new RandomAdaptor(new MersenneTwister(seed));
  }

  @Override
  protected final void doUpdate(Collection<Parcel> onMap, long time) {
    final Set<Parcel> inCargo = pdpModel.get().getContents(vehicle.get());
    assignedParcels.clear();
    for (final Parcel dp : onMap) {
      assignedParcels.add(dp, 2);
    }
    assignedParcels.addAll(inCargo);
    updateCurrent();
  }

  private void updateCurrent() {
    if (assignedParcels.isEmpty()) {
      current = Optional.absent();
    } else {
      final List<Parcel> list = newArrayList(assignedParcels
          .elementSet());
      current = Optional.of(list.get(rng.nextInt(list.size())));
    }
    dispatchChangeEvent();
  }

  @Override
  public final void nextImpl(long time) {
    LOGGER.trace("current {}", current);
    if (current.isPresent()) {
      checkArgument(assignedParcels.remove(current.get()));
    }
    updateCurrent();
  }

  @Override
  public final boolean hasNext() {
    return !assignedParcels.isEmpty();
  }

  @Override
  public final Optional<Parcel> current() {
    return current;
  }

  /**
   * @return A {@link StochasticSupplier} that supplies
   *         {@link RandomRoutePlanner} instances.
   */
  public static StochasticSupplier<RandomRoutePlanner> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<RandomRoutePlanner>() {
      private static final long serialVersionUID = 1701618808844264668L;

      @Override
      public RandomRoutePlanner get(long seed) {
        return new RandomRoutePlanner(seed);
      }
    };
  }
}
