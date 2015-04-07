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
package com.github.rinde.logistics.pdptw.mas.comm;

import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;

/**
 * {@link Bidder} implementation that adds some methods for easier testing.
 * @author Rinde van Lon 
 */
public class TestBidder extends RandomBidder {

  /**
   * @param seed Seed for random bidder.
   */
  public TestBidder(long seed) {
    super(seed);
  }

  /**
   * Clears assignedParcels.
   */
  public void removeAll() {
    assignedParcels.clear();
    eventDispatcher
        .dispatchEvent(new Event(CommunicatorEventType.CHANGE, this));
  }

  /**
   * @return A {@link StochasticSupplier} that supplies {@link RandomBidder} instances.
   */
  public static StochasticSupplier<RandomBidder> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<RandomBidder>() {
      @Override
      public TestBidder get(long seed) {
        return new TestBidder(seed);
      }
    };
  }
}
