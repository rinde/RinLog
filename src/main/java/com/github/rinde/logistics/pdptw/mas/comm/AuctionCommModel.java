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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.rand.RandomProvider;
import com.github.rinde.rinsim.core.model.time.Clock;
import com.github.rinde.rinsim.core.model.time.RealtimeClockController;
import com.github.rinde.rinsim.core.model.time.RealtimeClockController.ClockMode;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.EventAPI;
import com.github.rinde.rinsim.event.EventDispatcher;
import com.github.rinde.rinsim.event.Listener;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
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

  final EventDispatcher eventDispatcher;
  @Nullable
  final RealtimeClockController clock;

  AuctionCommModel(RandomGenerator r, AuctionStopCondition<T> sc, Clock c) {
    rng = r;
    stopCondition = sc;
    bidMap = LinkedHashMultimap.create();

    eventDispatcher = new EventDispatcher(EventType.values());
    if (c instanceof RealtimeClockController) {
      clock = (RealtimeClockController) c;
    } else {
      clock = null;
    }
  }

  public EventAPI getEventAPI() {
    return eventDispatcher.getPublicEventAPI();
  }

  @Override
  protected void receiveParcel(Parcel p, long time) {
    checkState(!communicators.isEmpty(), "there are no bidders..");

    final ParcelAuctioneer auctioneer = new ParcelAuctioneer(p);
    bidMap.put(p, auctioneer);
    auctioneer.initialAuction(time);
    auctioneer.update(time);
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

  @Override
  public <U> U get(Class<U> clazz) {
    if (clazz.isAssignableFrom(AuctionCommModel.class)) {
      return clazz.cast(this);
    }
    throw new IllegalArgumentException(
        AuctionCommModel.class.getSimpleName() + " does not support " + clazz);
  }

  /**
   * @return A new {@link Builder} instance.
   */
  public static <T extends Bid<T>> Builder<T> builder(Class<T> type) {
    return Builder.<T>create();
  }

  public enum EventType {
    START_AUCTION, FINISH_AUCTION, START_RE_AUCTION
  }

  public static class AuctionEvent extends Event {
    private final Parcel parcel;
    private final long time;
    private final Optional<Bidder<?>> winner;
    private final int receivedBids;

    protected AuctionEvent(Enum<?> type, Parcel p, Auctioneer a, long t) {
      this(type, p, a, t, -1);
    }

    protected AuctionEvent(Enum<?> type, Parcel p, Auctioneer a, long t,
        int numBids) {
      super(type);
      parcel = p;
      time = t;
      receivedBids = numBids;
      if (type == EventType.FINISH_AUCTION) {
        winner = Optional.<Bidder<?>>of(a.getWinner());
      } else {
        winner = Optional.absent();
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(AuctionEvent.class)
          .add("type", getEventType())
          .add("parcel", parcel)
          .add("winner", winner)
          .add("time", time)
          .toString();
    }

    /**
     * @return the parcel
     */
    public Parcel getParcel() {
      return parcel;
    }

    public int getNumBids() {
      return receivedBids;
    }

    public Optional<Bidder<?>> getWinner() {
      return winner;
    }

    public long getTime() {
      return time;
    }
  }

  class ParcelAuctioneer implements Auctioneer<T> {
    final Parcel parcel;
    final Set<T> bids;
    Optional<Bidder<T>> winner;
    Optional<Bidder<T>> initiator;
    long auctionStartTime;
    int auctions;

    Optional<Listener> callback;

    ParcelAuctioneer(Parcel p) {
      parcel = p;
      bids = Collections.synchronizedSet(new LinkedHashSet<T>());
      winner = Optional.absent();
      initiator = Optional.absent();
      callback = Optional.absent();
    }

    void initialAuction(long time) {
      LOGGER.trace("*** Start auction at {} for {}. ***", time, parcel);
      checkRealtime();
      eventDispatcher.dispatchEvent(
        new AuctionEvent(EventType.START_AUCTION, parcel, this, time));
      auctionStartTime = time;
      auctions++;

      for (final Bidder<T> b : communicators) {
        b.callForBids(this, parcel, time);
      }
    }

    void update(long time) {
      if (!winner.isPresent()) {
        checkRealtime();
      }

      synchronized (bids) {
        if (!winner.isPresent() && stopCondition.apply(
          Collections.unmodifiableSet(bids), communicators.size(),
          auctionStartTime, time)) {

          LOGGER.trace(
            ">>>> {} end of auction for {}, received {} bids, duration {} <<<<",
            time, parcel, bids.size(), time - auctionStartTime);
          checkState(!bids.isEmpty(),
            "No bids received (yet), cannot end auction.");

          for (final Bidder<T> bidder : communicators) {
            bidder.endOfAuction(this, parcel, auctionStartTime);
          }

          // end of auction, choose winner
          final T winningBid = Collections.min(bids);
          LOGGER.trace("Winning bid : {}", winningBid);

          winner = Optional.of(winningBid.getBidder());

          final AuctionEvent ev =
            new AuctionEvent(EventType.FINISH_AUCTION, parcel, this, time,
                bids.size());

          eventDispatcher.dispatchEvent(ev);
          if (callback.isPresent()) {
            callback.get().handleEvent(ev);
            callback = Optional.absent();
          }

          if (initiator.isPresent()
              && winningBid.getBidder().equals(initiator.get())) {
            // nothing changes
            initiator = Optional.absent();
          } else {
            if (initiator.isPresent()) {
              initiator.get().releaseParcel(parcel);
              initiator = Optional.absent();
            }
            winner.get().receiveParcel(this, parcel, auctionStartTime);
          }
          if (clock != null) {
            // this is called to prevent the clock from switching to simulated
            // time because the winner also needs to do some computation itself.
            LOGGER.trace("switchToRealTime");
            clock.switchToRealTime();
          }
        }
      }
    }

    void checkRealtime() {
      if (clock != null) {
        checkState(clock.getClockMode() == ClockMode.REAL_TIME,
          "Clock must be in real-time mode, but is in %s mode.",
          clock.getClockMode());
        // make sure we stay in rt
        LOGGER.trace("switchToRealTime");
        clock.switchToRealTime();
      }
    }

    @Override
    public void auctionParcel(Bidder<T> currentOwner, long time,
        T bidToBeat, Listener cb) {
      LOGGER.trace("*** Start RE-auction at {} for {}. Prev auctions: {} ***",
        time, parcel, auctions);
      checkRealtime();

      checkNotNull(currentOwner);
      checkArgument(time >= 0L);
      checkNotNull(bidToBeat);
      checkNotNull(cb);
      checkState(winner.isPresent());
      checkState(!callback.isPresent());
      checkArgument(bidToBeat.getParcel().equals(parcel));
      checkArgument(bidToBeat.getBidder().equals(currentOwner));
      checkArgument(winner.get().equals(currentOwner),
        "A reauction can only be initiated by the previous winner (%s), "
            + "found %s. Parcel: %s.",
        winner.get(), currentOwner, parcel);

      eventDispatcher.dispatchEvent(
        new AuctionEvent(EventType.START_RE_AUCTION, parcel, this, time));

      callback = Optional.of(cb);
      winner = Optional.absent();
      bids.clear();
      bids.add(bidToBeat);
      initiator = Optional.of(currentOwner);

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
      LOGGER.trace("Receive bid for {}, bid: {}.", parcel, bid);
      checkArgument(bid.getParcel().equals(parcel));
      // if a winner is already present, the auction is over. if the auction
      // times do not match, it means a new auction is taking place while the
      // submitted bid is for a previously held auction.
      if (!winner.isPresent() && bid.getTimeOfAuction() == auctionStartTime) {
        bids.add(bid);
      } else {
        LOGGER.warn("Ignoring bid {}, winner {}, auctionStartTime {}", bid,
          winner, auctionStartTime);
      }
    }

    @Override
    public Bidder<T> getWinner() {
      checkState(winner.isPresent());
      return winner.get();
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
      setDependencies(RandomProvider.class, Clock.class);
      setProvidingTypes(AuctionCommModel.class);
    }

    public abstract AuctionStopCondition<T> getStopCondition();

    public Builder<T> withStopCondition(AuctionStopCondition stopCondition) {
      return new AutoValue_AuctionCommModel_Builder<T>(stopCondition);
    }

    @Override
    public AuctionCommModel<T> build(DependencyProvider dependencyProvider) {
      final RandomGenerator r = dependencyProvider.get(RandomProvider.class)
          .newInstance();
      final Clock clock = dependencyProvider.get(Clock.class);
      return new AuctionCommModel<T>(r, getStopCondition(), clock);
    }

    static <T extends Bid<T>> Builder<T> create() {
      return new AutoValue_AuctionCommModel_Builder<T>(
          AuctionStopConditions.<T>allBidders());
    }
  }
}
