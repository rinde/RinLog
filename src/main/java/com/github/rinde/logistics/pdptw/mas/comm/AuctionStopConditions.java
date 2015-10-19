/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Set;

/**
 *
 * @author Rinde van Lon
 */
public final class AuctionStopConditions {

  private AuctionStopConditions() {}

  @SuppressWarnings("unchecked")
  public static <T extends Bid<T>> AuctionStopCondition<T> allBidders() {
    return AllBidders.INSTANCE;
  }

  enum AllBidders implements AuctionStopCondition {
    INSTANCE {

      @Override
      public boolean apply(Set bids, int potentialBidders,
          long auctionStartTime, long currentTime) {
        checkArgument(bids.size() <= potentialBidders,
          "There are too many bids: %s.", bids);
        return bids.size() == potentialBidders;
      }

    }
  }
}
