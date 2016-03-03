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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolver.EventType;
import com.github.rinde.rinsim.central.rt.RtSimSolver.SolverEvent;
import com.github.rinde.rinsim.central.rt.RtSimSolverBuilder;
import com.github.rinde.rinsim.central.rt.RtSolverModel;
import com.github.rinde.rinsim.central.rt.RtSolverUser;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.event.Event;
import com.github.rinde.rinsim.event.EventAPI;
import com.github.rinde.rinsim.event.Listener;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.StatisticsDTO;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Queues;

/**
 *
 * @author Rinde van Lon
 */
public class RtSolverBidder
    extends AbstractBidder<DoubleBid>
    implements RtSolverUser, TickListener {

  // 5 minutes
  private static final long MAX_LOSING_TIME = 5 * 60 * 1000;

  final ObjectiveFunction objectiveFunction;
  Optional<RtSimSolver> solverHandle;
  final Queue<CallForBids> cfbQueue;
  Listener currentListener;
  Map<Parcel, Auctioneer<DoubleBid>> parcelAuctioneers;

  AtomicBoolean reauctioning;
  AtomicBoolean computing;

  final BidFunction bidFunction;
  long lastAuctionWinTime;

  private final RealtimeSolver solver;

  RtSolverBidder(ObjectiveFunction objFunc, RealtimeSolver s,
      BidFunction bidFunc) {
    super(SetFactories.synchronizedFactory(SetFactories.linkedHashSet()));
    objectiveFunction = objFunc;
    solver = s;
    solverHandle = Optional.absent();
    bidFunction = bidFunc;
    cfbQueue = Queues.synchronizedQueue(new LinkedList<CallForBids>());
    parcelAuctioneers = new LinkedHashMap<>();
    reauctioning = new AtomicBoolean();
    computing = new AtomicBoolean();
  }

  @Override
  public void callForBids(final Auctioneer<DoubleBid> auctioneer,
      final Parcel parcel, final long time) {
    LOGGER.trace("{} receive callForBids {} {} {}", this, auctioneer, parcel,
      time);
    cfbQueue.add(CallForBids.create(auctioneer, parcel, time));
    parcelAuctioneers.put(parcel, auctioneer);

    // avoid multiple bids at the same time
    checkState(solverHandle.isPresent(),
      "A %s could not be obtained, probably missing a %s.",
      RtSimSolver.class.getSimpleName(),
      RtSolverModel.class.getSimpleName());
    next();
  }

  @Override
  public void afterInit() {
    super.afterInit();
    ((Truck) vehicle.get()).getEventAPI().addListener(new Listener() {
      @Override
      public void handleEvent(Event e) {
        LOGGER.trace("{} Route change -> reauction", vehicle.get());
        reauction();
      }
    }, Truck.TruckEvent.ROUTE_CHANGE);
  }

  @Override
  public void endOfAuction(Auctioneer<DoubleBid> auctioneer, Parcel parcel,
      long time) {
    final CallForBids endedAuction =
      CallForBids.create(auctioneer, parcel, time);

    // we have won
    if (equals(auctioneer.getWinner())) {
      lastAuctionWinTime = time;
    }

    synchronized (solverHandle.get().getLock()) {
      synchronized (computing) {
        if (computing.get()) {
          // if current computation is about this auction -> cancel it
          if (endedAuction.equals(cfbQueue.peek())) {
            LOGGER.trace("{} cancel computation", this);

            computing.set(false);
            final EventAPI ev = solverHandle.get().getEventAPI();

            // in some cases the listener is already removed because it was
            // called
            // before it could be removed, we can safely ignore this
            if (ev.containsListener(currentListener, EventType.DONE)) {
              ev.removeListener(currentListener, EventType.DONE);
            }
            solverHandle.get().cancel();
          }
          cfbQueue.remove(endedAuction);
          next();
        }
      }
    }

    if (!equals(auctioneer.getWinner())
      && time - lastAuctionWinTime > MAX_LOSING_TIME
      && !assignedParcels.isEmpty()) {
      LOGGER.trace("{} We haven't won an auction for a while -> reauction",
        this);
      // we haven't won an auction for a while
      reauction();
    }
  }

  void next() {
    synchronized (computing) {
      if (!cfbQueue.isEmpty() && !computing.get()) {
        computeBid(cfbQueue.peek());
      }
    }
  }

  void computeBid(final CallForBids cfb) {
    checkState(!cfb.getAuctioneer().hasWinner());
    checkState(!computing.getAndSet(true));
    LOGGER.trace("{} Start computing bid {}", this, cfb);
    final Set<Parcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(cfb.getParcel());
    final ImmutableList<Parcel> currentRoute =
      ImmutableList.copyOf(((Truck) vehicle.get()).getRoute());

    final GlobalStateObject state = solverHandle.get().getCurrentState(
      SolveArgs.create()
        .useCurrentRoutes(ImmutableList.of(currentRoute))
        .fixRoutes()
        .useParcels(parcels));
    final double baseline = objectiveFunction.computeCost(
      Solvers.computeStats(state, ImmutableList.of(currentRoute)));

    final EventAPI ev = solverHandle.get().getEventAPI();
    final RtSolverBidder bidder = this;
    currentListener = new Listener() {
      boolean exec;

      // this is called to notify us of the newly computed schedule
      @Override
      public void handleEvent(Event e) {
        synchronized (computing) {
          final SolverEvent event = (SolverEvent) e;
          checkState(cfbQueue.peek().equals(cfb));
          checkArgument(event.hasScheduleAndState(),
            "Solver was terminated before it found a solution.");

          checkState(!exec, "%s handleEvent was already called.", bidder);
          checkState(ev.containsListener(this, EventType.DONE));
          ev.removeListener(this, EventType.DONE);
          exec = true;

          // check if we receive the callback of the expected computation, (with
          // the correct state). If this is not the case, the callback was
          // probably already done before the listener could be removed. This
          // can be safely ignored.
          if (!event.getState().equals(state)) {
            return;
          }

          // submit bid using baseline
          final ImmutableList<ImmutableList<Parcel>> schedule =
            solverHandle.get().getCurrentSchedule();
          final double newCost = objectiveFunction.computeCost(
            Solvers.computeStats(state, schedule));

          LOGGER.trace("{} Computed new bid: baseline {}, newcost {}", bidder,
            baseline, newCost);

          final double bidValue =
            bidFunction.computeBidValue(currentRoute.size() + 2,
              newCost - baseline);
          cfb.getAuctioneer().submit(DoubleBid.create(
            cfb.getTime(), bidder, cfb.getParcel(), bidValue));

          cfbQueue.poll();
          checkState(computing.getAndSet(false));
        }
      }

      @Override
      public String toString() {
        return cfb.getParcel() + "-auction-listener-" + bidder;
      }
    };
    // add callback to solver, such that we get the newly computed schedule as
    // soon as it is done computing (note, we are NOT interested in intermediary
    // schedules since we can only propose one bid, therefore we wait for the
    // best schedule).
    solverHandle.get().getEventAPI()
      .addListener(currentListener, EventType.DONE);

    LOGGER.trace("{} Compute new bid, currentRoute {}, parcels {}.", this,
      currentRoute, parcels);
    solverHandle.get().solve(state);
  }

  @Override
  public void receiveParcel(Auctioneer<DoubleBid> auctioneer, Parcel p,
      long auctionStartTime) {
    LOGGER.trace("{} RECEIVE PARCEL {} {} {}", this, auctioneer, p,
      auctionStartTime);

    super.receiveParcel(auctioneer, p, auctionStartTime);
    checkArgument(auctioneer.getWinner().equals(this));
  }

  @SuppressWarnings({"null", "unused"})
  void reauction() {
    if (assignedParcels.isEmpty()) {
      return;
    }
    LOGGER.trace("{} Considering a reauction, assignedParcels: {}.", this,
      assignedParcels.size());
    final ImmutableList<Parcel> currentRoute = ImmutableList
      .copyOf(((Truck) vehicle.get()).getRoute());
    final GlobalStateObject state = solverHandle.get().getCurrentState(
      SolveArgs.create().noCurrentRoutes().useParcels(assignedParcels));
    final StatisticsDTO stats =
      Solvers.computeStats(state, ImmutableList.of(currentRoute));

    final Parcel lastReceivedParcel = Iterables.getLast(assignedParcels);

    if (!reauctioning.get()) {
      // find all swappable parcels, a parcel can be swapped if it is not yet in
      // cargo (it must occur twice in route for that)
      // TODO filter out parcels that will be visited within several seconds
      // (length of auction)
      final Multiset<Parcel> routeMultiset =
        LinkedHashMultiset.create(currentRoute);
      final Set<Parcel> swappableParcels = new LinkedHashSet<>();
      for (final Parcel ap : assignedParcels) {
        if (!pdpModel.get().getParcelState(ap).isPickedUp()
          && !pdpModel.get().getParcelState(ap).isTransitionState()
          && !state.getVehicles().get(0).getDestination().asSet()
            .contains(ap)
          && !ap.equals(lastReceivedParcel)) {
          swappableParcels.add(ap);
        }
      }

      final double baseline = objectiveFunction.computeCost(stats);
      double lowestCost = baseline;
      @Nullable
      Parcel toSwap = null;

      LOGGER.trace("Compute cost of swapping");
      for (final Parcel sp : swappableParcels) {
        final List<Parcel> newRoute = new ArrayList<>();
        newRoute.addAll(currentRoute);
        newRoute.removeAll(Collections.singleton(sp));
        final double cost = objectiveFunction.computeCost(
          Solvers.computeStats(state,
            ImmutableList.of(ImmutableList.copyOf(newRoute))));
        if (cost < lowestCost) {
          lowestCost = cost;
          toSwap = sp;
        }
      }

      // we have found the most expensive parcel in the route, that is, removing
      // this parcel from the route will yield the greatest cost reduction.
      if (toSwap != null
        && !reauctioning.get()
        && !toSwap.equals(lastReceivedParcel)) {
        reauctioning.set(true);
        LOGGER.trace("Found most expensive parcel for reauction: {}.", toSwap);

        // try to reauction
        final Auctioneer<DoubleBid> auct = parcelAuctioneers.get(toSwap);

        final double bidValue = bidFunction.computeBidValue(currentRoute.size(),
          lowestCost - baseline);
        final DoubleBid initialBid = DoubleBid.create(state.getTime(), this,
          toSwap, bidValue);

        auct.auctionParcel(this, state.getTime(), initialBid,
          new Listener() {
            @Override
            public void handleEvent(Event e) {
              reauctioning.set(false);
            }
          });
      }
    }
  }

  @Override
  public boolean releaseParcel(Parcel p) {
    LOGGER.trace("{} RELEASE PARCEL {}", this, p);
    // remove the parcel from the route immediately to avoid going there
    final List<Parcel> currentRoute =
      new ArrayList<>(((Truck) vehicle.get()).getRoute());
    if (currentRoute.contains(p)) {
      final List<Parcel> original = new ArrayList<>(currentRoute);
      LOGGER.trace(" > remove parcel from route: {}", currentRoute);
      currentRoute.removeAll(Collections.singleton(p));

      final Truck truck = (Truck) vehicle.get();
      truck.setRoute(currentRoute);
      if (truck.getRoute().contains(p)) {
        LOGGER.warn("Could not release parcel, cancelling auction.");
        // set back original route
        truck.setRoute(original);
        return false;
      }
    }
    return super.releaseParcel(p);
  }

  @Override
  public void tick(TimeLapse timeLapse) {
    next();
  }

  @Override
  public void afterTick(TimeLapse timeLapse) {
    next();
  }

  @Override
  public void setSolverProvider(RtSimSolverBuilder builder) {
    solverHandle = Optional.of(builder.setVehicles(vehicle.asSet())
      .build(solver));
  }

  public static StochasticSupplier<RtSolverBidder> supplier(
      ObjectiveFunction objFunc,
      StochasticSupplier<? extends RealtimeSolver> solverSupplier,
      BidFunction bidFunction) {
    return new AutoValue_RtSolverBidder_Sup(objFunc, solverSupplier,
      bidFunction);
  }

  public interface BidFunction {
    double computeBidValue(int numLocations, double additionalCost);
  }

  public enum BidFunctions implements BidFunction {

    PLAIN {
      @Override
      public double computeBidValue(int numLocations, double additionalCost) {
        return additionalCost;
      }
    },
    BALANCED {
      @Override
      public double computeBidValue(int numLocations, double additionalCost) {
        return additionalCost + numLocations * additionalCost;
      }
    },
    BALANCED_LOW {
      @Override
      public double computeBidValue(int numLocations, double additionalCost) {
        return additionalCost + numLocations / MUL * additionalCost;
      }
    },
    BALANCED_HIGH {
      @Override
      public double computeBidValue(int numLocations, double additionalCost) {
        return additionalCost + MUL * numLocations * additionalCost;
      }
    };
    static final double MUL = 10d;
  }

  @AutoValue
  abstract static class CallForBids {

    CallForBids() {}

    abstract Auctioneer<DoubleBid> getAuctioneer();

    abstract Parcel getParcel();

    abstract long getTime();

    static CallForBids create(Auctioneer<DoubleBid> auctioneer, Parcel parcel,
        long time) {
      return new AutoValue_RtSolverBidder_CallForBids(auctioneer, parcel, time);
    }
  }

  @AutoValue
  abstract static class Sup implements StochasticSupplier<RtSolverBidder> {

    Sup() {}

    abstract ObjectiveFunction getObjectiveFunction();

    abstract StochasticSupplier<? extends RealtimeSolver> getSolverSupplier();

    abstract BidFunction getBidFunction();

    @Override
    public RtSolverBidder get(long seed) {
      return new RtSolverBidder(getObjectiveFunction(),
        getSolverSupplier().get(seed), getBidFunction());
    }

    @Override
    public String toString() {
      return RtSolverBidder.class.getSimpleName() + ".supplier(..)";
    }
  }
}
