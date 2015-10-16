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
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.rand.RandomProvider;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

/**
 * A communication model that supports auctions.
 * @author Rinde van Lon
 */
public class AuctionCommModel<T extends Bid<T>>
    extends AbstractCommModel<Bidder<T>>
    implements TickListener {
  private final RandomGenerator rng;

  final SetMultimap<Parcel, ParcelAuctioneer> bidMap;
  final AuctionStopCondition<T> stopCondition;

  AuctionCommModel(RandomGenerator r, AuctionStopCondition<T> sc) {
    rng = r;
    stopCondition = sc;
    bidMap = LinkedHashMultimap.create();
  }

  @Override
  protected void receiveParcel(Parcel p, long time) {
    checkState(!communicators.isEmpty(), "there are no bidders..");

    final ParcelAuctioneer auctioneer = new ParcelAuctioneer(p);
    bidMap.put(p, auctioneer);
    auctioneer.initialAuction(time);
    auctioneer.update(time);

    // final Iterator<Bidder> it = communicators.iterator();
    //
    // final List<Bidder> bestBidders = newArrayList();
    // bestBidders.add(it.next());
    //
    // // if there are no other bidders, there is no need to organize an
    // // auction at all (mainly used in test cases)
    // if (it.hasNext()) {
    // double bestValue = bestBidders.get(0).getBidFor(p, time);
    // while (it.hasNext()) {
    // final Bidder cur = it.next();
    // final double curValue = cur.getBidFor(p, time);
    // if (curValue < bestValue) {
    // bestValue = curValue;
    // bestBidders.clear();
    // bestBidders.add(cur);
    // } else if (Math.abs(curValue - bestValue) < TOLERANCE) {
    // bestBidders.add(cur);
    // }
    // }
    // }
    //
    // if (bestBidders.size() > 1) {
    // bestBidders.get(rng.nextInt(bestBidders.size())).receiveParcel(p);
    // } else {
    // bestBidders.get(0).receiveParcel(p);
    // }
  }

  @Override
  public void tick(TimeLapse timeLapse) {

  }

  @Override
  public void afterTick(TimeLapse timeLapse) {
    for (final ParcelAuctioneer pa : bidMap.values()) {
      pa.update(timeLapse.getStartTime());
    }
  }

  /**
   * @return A new {@link Builder} instance.
   */
  public static <T extends Bid<T>> Builder<T> builder(
      Class<T> type) {
    return Builder.<T>create();
  }

  class ParcelAuctioneer implements Auctioneer<T> {
    final Parcel parcel;
    final Set<T> bids;
    Optional<Bidder<T>> winner;
    long auctionStartTime;
    int auctions;

    ParcelAuctioneer(Parcel p) {
      parcel = p;
      bids = new LinkedHashSet<>();
      winner = Optional.absent();
    }

    void initialAuction(long time) {
      auctionStartTime = time;

      for (final Bidder<T> b : communicators) {
        b.callForBids(this, parcel, time);
      }
    }

    void update(long time) {
      if (!winner.isPresent() && stopCondition.apply(
        Collections.unmodifiableSet(bids), communicators.size(),
        auctionStartTime, time)) {
        // end of auction, choose winner
        final T winningBid = Collections.min(bids);
        winner = Optional.of(winningBid.getBidder());
        winner.get().receiveParcel(winningBid.getParcel());
      }
    }

    @Override
    public void auctionParcel(Bidder<T> currentOwner, Parcel p, long time,
        T bidToBeat) {
      checkArgument(winner.get() == currentOwner);
      winner = Optional.absent();

      auctionStartTime = time;
      auctions++;

      for (final Bidder<T> b : communicators) {
        if (b != currentOwner) {
          b.callForBids(this, parcel, time);
        }
      }
    }

    @Override
    public void submit(T bid) {
      // if a winner is already present, the auction is over. if the auction
      // times do not match, it means a new auction is taking place while the
      // submitted bid is for a previously held auction.
      if (!winner.isPresent() && bid.getTimeOfAuction() == auctionStartTime) {
        bids.add(bid);
      }
    }
  }

  /**
   * Builder for creating {@link AuctionCommModel}.
   * @author Rinde van Lon
   */
  @AutoValue
  public abstract static class Builder<T extends Bid<T>>
      extends AbstractModelBuilder<AuctionCommModel<T>, Bidder>
      implements Serializable {

    Builder() {
      setDependencies(RandomProvider.class);
    }

    public abstract AuctionStopCondition<T> getStopCondition();

    @Override
    public AuctionCommModel<T> build(DependencyProvider dependencyProvider) {
      final RandomGenerator r = dependencyProvider.get(RandomProvider.class)
          .newInstance();
      return new AuctionCommModel<T>(r, getStopCondition());
    }

    static <T extends Bid<T>> Builder<T> create() {
      return new AutoValue_AuctionCommModel_Builder<T>(
          AuctionStopConditions.<T>allBidders());
    }
  }
}
