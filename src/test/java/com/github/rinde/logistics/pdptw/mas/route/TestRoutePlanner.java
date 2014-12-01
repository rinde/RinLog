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
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;

import com.github.rinde.logistics.pdptw.mas.route.AbstractRoutePlanner;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.base.Optional;

/**
 * @author Rinde van Lon 
 * 
 */
public class TestRoutePlanner extends AbstractRoutePlanner {

  protected final Queue<DefaultParcel> route;

  public TestRoutePlanner() {
    route = newLinkedList();
  }

  @Override
  public Optional<DefaultParcel> current() {
    return Optional.fromNullable(route.peek());
  }

  @Override
  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    final Collection<DefaultParcel> inCargo = Collections.checkedCollection(
        (Collection) pdpModel.get().getContents(vehicle.get()),
        DefaultParcel.class);
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
