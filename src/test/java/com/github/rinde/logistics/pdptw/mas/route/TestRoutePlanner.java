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
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Queue;
import java.util.Set;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.google.common.base.Optional;

/**
 * @author Rinde van Lon
 *
 */
public class TestRoutePlanner extends AbstractRoutePlanner {

  protected final Queue<Parcel> route;

  public TestRoutePlanner() {
    route = newLinkedList();
  }

  @Override
  public Optional<Parcel> current() {
    return Optional.fromNullable(route.peek());
  }

  @Override
  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Override
  protected void doUpdate(Set<Parcel> onMap, long time) {
    final Set<Parcel> inCargo = pdpModel.get().getContents(vehicle.get());
    route.clear();
    route.addAll(onMap);
    route.addAll(inCargo);
    route.addAll(onMap);
  }

  @Override
  protected void nextImpl(long time) {
    route.poll();
  }

  public static StochasticSupplier<TestRoutePlanner> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<TestRoutePlanner>() {
      @Override
      public TestRoutePlanner get(long seed) {
        return new TestRoutePlanner();
      }
    };
  }
}
