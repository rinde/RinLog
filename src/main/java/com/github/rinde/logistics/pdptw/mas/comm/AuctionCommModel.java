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
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.commons.math3.random.RandomAdaptor;
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

/**
 * A communication model that supports auctions.
 * @author Rinde van Lon
 * @param <T> The type of bid.
 */
public class AuctionCommModel<T extends Bid<T>>
    extends AbstractCommModel<Bidder<T>>
    implements TickListener {

  final long maxAuctionDurationMs;
  final Map<Parcel, ParcelAuctioneer> parcelAuctioneerMap;
  final AuctionStopCondition<T> stopCondition;
  final EventDispatcher eventDispatcher;
  @Nullable
  final RealtimeClockController clock;
  final AtomicInteger numAuctions;
  @Nullable
  final Random rng;

  AuctionCommModel(AuctionStopCondition<T> sc, Clock c, long maxAuctDurMs,
      @Nullable RandomGenerator r) {
    stopCondition = sc;
    parcelAuctioneerMap = new LinkedHashMap<>();
    maxAuctionDurationMs = maxAuctDurMs;
    rng = r == null ? null : new RandomAdaptor(r);

    eventDispatcher = new EventDispatcher(EventType.values());
    if (c instanceof RealtimeClockController) {
      clock = (RealtimeClockController) c;
    } else {
      clock = null;
    }

    numAuctions = new AtomicInteger();
  }

  public EventAPI getEventAPI() {
    return eventDispatcher.getPublicEventAPI();
  }

  public int getNumParcels() {
    return parcelAuctioneerMap.size();
  }

  public int getNumberOfOngoingAuctions() {
    return numAuctions.get();
  }

  public int getNumUnsuccesfulAuctions() {
    int total = 0;
    for (final ParcelAuctioneer pa : parcelAuctioneerMap.values()) {
      total += pa.unsuccessfulAuctions;
    }
    return total;
  }

  public int getNumAuctions() {
    int total = 0;
    for (final ParcelAuctioneer pa : parcelAuctioneerMap.values()) {
      total += pa.auctions;
    }
    return total;
  }

  public int getNumFailedAuctions() {
    int total = 0;
    for (final ParcelAuctioneer pa : parcelAuctioneerMap.values()) {
      total += pa.failedAuctions;
    }
    return total;
  }

  @Override
  protected void receiveParcel(Parcel p, long time) {
    checkState(!communicators.isEmpty(), "there are no bidders..");

    final ParcelAuctioneer auctioneer = new ParcelAuctioneer(p);
    parcelAuctioneerMap.put(p, auctioneer);
    auctioneer.initialAuction(time);
    auctioneer.update(time);
  }

  @Override
  public void tick(TimeLapse timeLapse) {}

  @Override
  public void afterTick(TimeLapse timeLapse) {
    int numOfOngoingAuctions = 0;
    for (final ParcelAuctioneer pa : parcelAuctioneerMap.values()) {
      pa.update(timeLapse.getStartTime());
      if (!pa.hasWinner()) {
        numOfOngoingAuctions++;
      }
    }
    if (numOfOngoingAuctions > 0 || numAuctions.get() > 0) {
      checkRealtime();
    }

    numAuctions.set(numOfOngoingAuctions);
  }

  void checkRealtime() {
    if (clock != null) {
      checkState(clock.getClockMode() == ClockMode.REAL_TIME,
        "Clock must be in real-time mode, but is in %s mode.",
        clock.getClockMode());
      // make sure we stay in rt
      LOGGER.debug("Check real time -> stay in real time.");
      clock.switchToRealTime();
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
   * @param type The type of {@link Bid}.
   * @param <T> The type of {@link Bid}.
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
    int unsuccessfulAuctions;
    int failedAuctions;
    long lastUnsuccessfulAuctionTime;
    long lastAuctionAttemptTime;

    ParcelAuctioneer(Parcel p) {
      parcel = p;
      bids = Collections.synchronizedSet(new LinkedHashSet<T>());
      winner = Optional.absent();
      initiator = Optional.absent();
      callback = Optional.absent();
      lastUnsuccessfulAuctionTime = -1L;
      lastAuctionAttemptTime = p.getOrderAnnounceTime();
    }

    void initialAuction(long time) {
      LOGGER.trace("{} *** Start auction at {} for {}. ***", this, time,
        parcel);
      synchronized (bids) {
        checkRealtime();
        auctionStartTime = time;
        auctions++;
      }

      eventDispatcher.dispatchEvent(
        new AuctionEvent(EventType.START_AUCTION, parcel, this, time));
      auction(time, null);
    }

    void auction(long time, @Nullable Bidder<T> currentOwner) {
      LOGGER.trace("{} auction({},{})", this, time, currentOwner);
      if (rng != null) {
        Collections.shuffle(communicators, rng);
      }
      for (final Bidder<T> b : communicators) {
        if (b != null && b != currentOwner) {
          b.callForBids(this, parcel, time);
        }
      }
    }

    @Override
    public boolean hasWinner() {
      synchronized (bids) {
        return winner.isPresent();
      }
    }

    void update(long time) {
      if (hasWinner()) {
        return;
      }

      boolean notify = false;
      synchronized (bids) {
        checkRealtime();
        if (time - auctionStartTime > maxAuctionDurationMs) {
          throw new IllegalStateException(
            "Auction duration for " + parcel + " exceeded threshold of "
              + maxAuctionDurationMs + " ms. Current time: " + time + ".");
        }

        if (stopCondition.apply(Collections.unmodifiableSet(bids),
          communicators.size(), auctionStartTime, time)) {
          notify = true;
          LOGGER.trace(
            "{} >>>> {} end of auction for {}, received {} bids, duration {} "
              + "<<<<",
            this, time, parcel, bids.size(), time - auctionStartTime);
          checkState(!bids.isEmpty(),
            "No bids received (yet), cannot end auction. StopCondition: %s.",
            stopCondition);

          // end of auction, choose winner
          final T winningBid = Collections.min(bids);
          LOGGER.trace("{} Winning bid : {}", this, winningBid);
          if (initiator.isPresent()) {
            LOGGER.trace("{} > reference bid {}", this, bids.iterator().next());
          }

          winner = Optional.of(winningBid.getBidder());

          if (initiator.isPresent()
            && winningBid.getBidder().equals(initiator.get())) {
            LOGGER.info("{} Auction for {} had no success.", this, parcel);
            unsuccessfulAuctions++;
            // nothing changes
            initiator = Optional.absent();
            lastUnsuccessfulAuctionTime = time;
          } else {
            lastUnsuccessfulAuctionTime = -1L;
            boolean success = true;
            if (initiator.isPresent()) {
              LOGGER.info("{} Release {} from {}.", this, parcel,
                initiator.get());
              success = initiator.get().releaseParcel(parcel);
              initiator = Optional.absent();
            }
            // if parcel could not be released, the parcel will not be
            // transferred (the auction will complete without effect)
            if (success) {
              LOGGER.info(
                "{} Auction completed successfully, transfer {} to {}.",
                this, parcel, winner.get());
              winner.get().receiveParcel(this, parcel, auctionStartTime);
            } else {
              failedAuctions++;
            }
          }
          if (clock != null) {
            // this is called to prevent the clock from switching to simulated
            // time because the winner also needs to do some computation itself.
            LOGGER.debug(
              "{} End of auction -> switch to (or stay in) real time", this);
            clock.switchToRealTime();
          }
        }
      }

      if (notify) {
        // notify all bidders
        for (final Bidder<T> bidder : communicators) {
          bidder.endOfAuction(this, parcel, auctionStartTime);
        }
        // notify anybody else interested in auctions
        final AuctionEvent ev =
          new AuctionEvent(EventType.FINISH_AUCTION, parcel, this, time,
            bids.size());

        eventDispatcher.dispatchEvent(ev);
        if (callback.isPresent()) {
          callback.get().handleEvent(ev);
          callback = Optional.absent();
        }
      }
    }

    @Override
    public void auctionParcel(Bidder<T> currentOwner, long time,
        T bidToBeat, Listener cb) {
      LOGGER.trace(
        "{} *** Start RE-auction at {} for {}. Prev auctions: {}. Current "
          + "owner: {} ***",
        this, time, parcel, auctions, currentOwner);
      LOGGER.trace("{} > base line bid: {}", this, bidToBeat);
      lastAuctionAttemptTime = time;
      synchronized (bids) {
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

        callback = Optional.of(cb);
        winner = Optional.absent();
        bids.clear();
        bids.add(bidToBeat);
        initiator = Optional.of(currentOwner);

        auctionStartTime = time;
        auctions++;
      }

      auction(time, currentOwner);
      eventDispatcher.dispatchEvent(
        new AuctionEvent(EventType.START_RE_AUCTION, parcel, this, time));
    }

    @Override
    public void submit(T bid) {
      LOGGER.trace("{} Receive bid for {}, bid: {}.", this, parcel, bid);
      checkArgument(bid.getParcel().equals(parcel));

      synchronized (bids) {
        // if a winner is already present, the auction is over. if the auction
        // times do not match, it means a new auction is taking place while the
        // submitted bid is for a previously held auction.
        if (!winner.isPresent() && bid.getTimeOfAuction() == auctionStartTime) {
          bids.add(bid);
        } else {
          LOGGER.info("{} Ignoring bid {}, winner {}, auctionStartTime {}",
            this,
            bid, winner, auctionStartTime);
        }
      }
    }

    @Override
    public Bidder<T> getWinner() {
      synchronized (bids) {
        checkState(winner.isPresent());
        return winner.get();
      }
    }

    @Override
    public long getLastUnsuccessTime() {
      return lastUnsuccessfulAuctionTime;
    }

    @Override
    public long getLastAttemptTime() {
      return lastAuctionAttemptTime;
    }

    @Override
    public String toString() {
      return "{Auctioneer for " + parcel.toString() + "}";
    }
  }

  /**
   * Builder for creating {@link AuctionCommModel}.
   * @author Rinde van Lon
   * @param <T> The type of bid to create.
   */
  @AutoValue
  public abstract static class Builder<T extends Bid<T>>
      extends AbstractModelBuilder<AuctionCommModel<T>, Bidder<?>> {
    private static final long serialVersionUID = 9020465403959292485L;
    private static final long DEFAULT_MAX_AUCTION_DURATION_MS = 10 * 60 * 1000L;
    private static final boolean DEFAULT_CFB_SHUFFLING = true;

    Builder() {
      setDependencies(Clock.class, RandomProvider.class);
      setProvidingTypes(AuctionCommModel.class);
    }

    abstract AuctionStopCondition<T> getStopCondition();

    abstract long getMaxAuctionDuration();

    abstract boolean getCfbShuffling();

    public Builder<T> withStopCondition(AuctionStopCondition stopCondition) {
      return new AutoValue_AuctionCommModel_Builder<>(stopCondition,
        getMaxAuctionDuration(), getCfbShuffling());
    }

    /**
     * Change the maximum duration of an auction. If an auction takes longer
     * than this limit, an {@link IllegalStateException} will be thrown. This is
     * used to detect situations where auctions have not completed normally,
     * usually this is the result of a programming error. This limit should
     * always be (significantly) higher than the maximum auction limit as
     * defined in an {@link AuctionStopCondition}.
     * @param durationMs The duration in milliseconds. The default is
     *          <code>600000</code> (10 minutes), only positive values are
     *          allowed.
     * @return A new builder instance with the duration property changed.
     */
    public Builder<T> withMaxAuctionDuration(long durationMs) {
      checkArgument(durationMs > 0, "Only positive durations are allowed.");
      return new AutoValue_AuctionCommModel_Builder<>(getStopCondition(),
        durationMs, getCfbShuffling());
    }

    /**
     * Call for bids (cfb) shuffling is the shuffling of the list of recipients
     * of call for bid messages. When enabled the list of recipients is shuffled
     * before every auction, when disabled the list of recipients has the same
     * order throughout the simulation.
     * @param shuffle <code>true</code> to enable cfb shuffling (default),
     *          <code>false</code> to disable cfb shuffling.
     * @return A new builder instance with the cfb shuffling property changed.
     */
    public Builder<T> withCfbShuffling(boolean shuffle) {
      return new AutoValue_AuctionCommModel_Builder<>(getStopCondition(),
        getMaxAuctionDuration(), shuffle);
    }

    @Override
    public AuctionCommModel<T> build(DependencyProvider dependencyProvider) {
      final Clock clock = dependencyProvider.get(Clock.class);
      @Nullable
      RandomGenerator rng = null;
      if (getCfbShuffling()) {
        rng = dependencyProvider.get(RandomProvider.class).newInstance();
      }
      return new AuctionCommModel<T>(getStopCondition(), clock,
        getMaxAuctionDuration(), rng);
    }

    static <T extends Bid<T>> Builder<T> create() {
      return new AutoValue_AuctionCommModel_Builder<T>(
        AuctionStopConditions.<T>allBidders(), DEFAULT_MAX_AUCTION_DURATION_MS,
        DEFAULT_CFB_SHUFFLING);
    }
  }
}
