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
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public final class AuctionStopConditions {
  private static final String R_BRACE = ")";
  private static final String COMMA = ",";

  private AuctionStopConditions() {}

  @SuppressWarnings("unchecked")
  public static <T extends Bid<T>> AuctionStopCondition<T> allBidders() {
    return Conditions.ALL_BIDDERS;
  }

  public static <T extends Bid<T>> AuctionStopCondition<T> maxAuctionDuration(
      long maxDuration) {
    return new MaxAuctionDuration<>(maxDuration);
  }

  public static <T extends Bid<T>> AuctionStopCondition<T> atLeastNumBids(
      int numberOfBids) {
    return new AtLeastNumBidders<>(numberOfBids);
  }

  @SafeVarargs
  public static <T extends Bid<T>> AuctionStopCondition<T> or(
      AuctionStopCondition<T>... auctionStopConditions) {
    return new Or<>(auctionStopConditions);
  }

  @SafeVarargs
  public static <T extends Bid<T>> AuctionStopCondition<T> and(
      AuctionStopCondition<T>... auctionStopConditions) {
    return new And<>(auctionStopConditions);
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

    @Override
    public String toString() {
      return new StringBuilder(AuctionStopConditions.class.getSimpleName())
        .append(".or(")
        .append(Joiner.on(COMMA).join(conditions).toString())
        .append(R_BRACE).toString();
    }
  }

  static class And<T extends Bid<T>>
      implements AuctionStopCondition<T> {
    List<AuctionStopCondition<T>> conditions;

    @SafeVarargs
    And(AuctionStopCondition<T>... conds) {
      conditions = ImmutableList.copyOf(conds);
    }

    @Override
    public boolean apply(Set<T> bids, int potentialBidders,
        long auctionStartTime, long currentTime) {
      for (final AuctionStopCondition<T> cond : conditions) {
        if (!cond.apply(bids, potentialBidders, auctionStartTime,
          currentTime)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public String toString() {
      return new StringBuilder(AuctionStopConditions.class.getSimpleName())
        .append(".and(")
        .append(Joiner.on(COMMA).join(conditions).toString())
        .append(R_BRACE).toString();
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
        + ".maxAuctionDuration(" + maxAuctionDuration + R_BRACE;
    }
  }

  static class AtLeastNumBidders<T extends Bid<T>>
      implements AuctionStopCondition<T> {
    final int numBidders;

    AtLeastNumBidders(int num) {
      checkArgument(num > 0);
      numBidders = num;
    }

    @Override
    public boolean apply(Set<T> bids, int potentialBidders,
        long auctionStartTime, long currentTime) {
      return bids.size() >= numBidders;
    }

    @Override
    public String toString() {
      return AuctionStopConditions.class.getSimpleName() + ".atLeastNumBids("
        + numBidders + R_BRACE;
    }
  }

  enum Conditions implements AuctionStopCondition {
    ALL_BIDDERS {
      @Override
      public boolean apply(Set bids, int potentialBidders,
          long auctionStartTime, long currentTime) {
        checkArgument(bids.size() <= potentialBidders,
          "There are too many bids, expected %s, found %s: %s.",
          potentialBidders, bids.size(), bids);
        return bids.size() == potentialBidders;
      }

      @Override
      public String toString() {
        return AuctionStopConditions.class.getSimpleName() + ".allBidders()";
      }
    }
  }
}
