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
package com.github.rinde.logistics.pdptw.mas.comm;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;

/**
 * A {@link Bidder} implementation that creates random bids.
 * @author Rinde van Lon
 */
public class RandomBidder extends AbstractBidder<DoubleBid> {

  private final RandomGenerator rng;

  /**
   * Create a random bidder using the specified random seed.
   * @param seed The random seed.
   */
  public RandomBidder(long seed) {
    rng = new MersenneTwister(seed);
  }

  @Override
  public void callForBids(Auctioneer<DoubleBid> auctioneer, Parcel p,
      long time) {
    auctioneer.submit(DoubleBid.create(time, this, p, rng.nextDouble()));
  }

  /**
   * @return A {@link StochasticSupplier} that supplies {@link RandomBidder}
   *         instances.
   */
  public static StochasticSupplier<RandomBidder> supplier() {
    return new StochasticSuppliers.AbstractStochasticSupplier<RandomBidder>() {
      private static final long serialVersionUID = 1701618808844264668L;

      @Override
      public RandomBidder get(long seed) {
        return new RandomBidder(seed);
      }
    };
  }

}
