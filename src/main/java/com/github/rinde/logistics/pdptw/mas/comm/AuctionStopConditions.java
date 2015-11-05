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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;

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

  public static <T extends Bid<T>> AuctionStopCondition<T> maxAuctionDuration(
      long maxDuration) {
    return new MaxAuctionDuration<>(maxDuration);
  }

  @SafeVarargs
  public static <T extends Bid<T>> AuctionStopCondition<T> or(
      AuctionStopCondition<T>... auctionStopConditions) {
    return new Or<>(auctionStopConditions);
  }

  static class Or<T extends Bid<T>>
      implements AuctionStopCondition<T> {

    List<AuctionStopCondition<T>> conditions;

    @SafeVarargs
    Or(AuctionStopCondition<T>... conds) {
      conditions = ImmutableList.copyOf(conds);
    }

    @Override
    public boolean apply(Set<T> bids, int potentialBidders,
        long auctionStartTime, long currentTime) {
      for (final AuctionStopCondition<T> cond : conditions) {
        if (cond.apply(bids, potentialBidders, auctionStartTime, currentTime)) {
          return true;
        }
      }
      return false;
    }
  }

  static class MaxAuctionDuration<T extends Bid<T>>
      implements AuctionStopCondition<T> {

    final long maxAuctionDuration;

    MaxAuctionDuration(long maxDuration) {
      maxAuctionDuration = maxDuration;
    }

    @Override
    public boolean apply(Set<T> bids, int potentialBidders,
        long auctionStartTime, long currentTime) {
      return currentTime - auctionStartTime > maxAuctionDuration;
    }

    @Override
    public String toString() {
      return AuctionStopConditions.class.getSimpleName()
          + ".maxAuctionDuration(" + maxAuctionDuration + ")";
    }
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
