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

  private final RealtimeSolver solver;
  final ObjectiveFunction objectiveFunction;
  Optional<RtSimSolver> solverHandle;
  final Queue<CallForBids> cfbQueue;
  Listener currentListener;
  Map<Parcel, Auctioneer<DoubleBid>> parcelAuctioneers;

  AtomicBoolean reauctioning;

  RtSolverBidder(ObjectiveFunction objFunc, RealtimeSolver s) {
    objectiveFunction = objFunc;
    solver = s;
    solverHandle = Optional.absent();
    cfbQueue = Queues.synchronizedQueue(new LinkedList<CallForBids>());
    parcelAuctioneers = new LinkedHashMap<>();
    reauctioning = new AtomicBoolean();
  }

  @Override
  public void callForBids(final Auctioneer<DoubleBid> auctioneer,
      final Parcel parcel, final long time) {
    LOGGER.trace("callForBids {} {} {}", auctioneer, parcel, time);
    cfbQueue.add(CallForBids.create(auctioneer, parcel, time));
    parcelAuctioneers.put(parcel, auctioneer);

    // avoid multiple bids at the same time
    checkState(solverHandle.isPresent(),
      "A %s could not be obtained, probably missing a %s.",
      RealtimeSolver.class.getSimpleName(),
      RtSolverModel.class.getSimpleName());

    next();
  }

  @Override
  public void endOfAuction(Auctioneer<DoubleBid> auctioneer, Parcel parcel,
      long time) {
    final CallForBids endedAuction =
      CallForBids.create(auctioneer, parcel, time);

    if (solverHandle.get().isComputing()) {
      // if current computation is about this auction -> cancel it
      if (endedAuction.equals(cfbQueue.peek())) {
        LOGGER.trace("cancel computation");
        solverHandle.get().cancel();
        final EventAPI ev = solverHandle.get().getEventAPI();
        if (ev.containsListener(currentListener, EventType.NEW_SCHEDULE)) {
          ev.removeListener(currentListener, EventType.NEW_SCHEDULE);
        }
      }
      cfbQueue.remove(endedAuction);
      next();
    }
  }

  void next() {
    if (!cfbQueue.isEmpty() && !solverHandle.get().isComputing()) {
      computeBid(cfbQueue.poll());
    }
  }

  void computeBid(final CallForBids cfb) {
    LOGGER.trace("start computing bid {}", cfb);
    final Set<Parcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(cfb.getParcel());
    final ImmutableList<Parcel> currentRoute = ImmutableList
        .copyOf(((Truck) vehicle.get()).getRoute());

    final GlobalStateObject state = solverHandle.get().getCurrentState(
      SolveArgs.create().noCurrentRoutes().useParcels(parcels));
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
      state, ImmutableList.of(currentRoute)));

    final RtSolverBidder bidder = this;
    currentListener = new Listener() {
      boolean exec = false;

      @Override
      public void handleEvent(Event e) {
        if (exec) {
          return;
        }
        exec = true;
        // submit bid using baseline
        final ImmutableList<ImmutableList<Parcel>> schedule =
          solverHandle.get().getCurrentSchedule();
        final double cost =
          objectiveFunction.computeCost(Solvers.computeStats(state, schedule));

        LOGGER.trace("baseline {}, newcost {}", baseline, cost);

        cfb.getAuctioneer().submit(DoubleBid.create(cfb.getTime(), bidder,
          cfb.getParcel(), cost - baseline));
        solverHandle.get().getEventAPI().removeListener(this,
          EventType.NEW_SCHEDULE);
      }
    };
    solverHandle.get().getEventAPI().addListener(currentListener,
      EventType.NEW_SCHEDULE);

    LOGGER.trace("Compute new bid, currentRoute {}, parcels {}.", currentRoute,
      parcels);
    solverHandle.get().solve(SolveArgs.create()
        .useCurrentRoutes(ImmutableList.of(currentRoute))
        .useParcels(parcels));
  }

  @SuppressWarnings("unused")
  @Override
  public void receiveParcel(Auctioneer<DoubleBid> auctioneer, Parcel p,
      long auctionStartTime) {

    LOGGER.trace("{} RECEIVE PARCEL {} {} {}", this, auctioneer, p,
      auctionStartTime);

    super.receiveParcel(auctioneer, p, auctionStartTime);
    checkArgument(auctioneer.getWinner().equals(this));

    final ImmutableList<Parcel> currentRoute = ImmutableList
        .copyOf(((Truck) vehicle.get()).getRoute());
    final GlobalStateObject state = solverHandle.get().getCurrentState(
      SolveArgs.create().noCurrentRoutes().useParcels(assignedParcels));
    final StatisticsDTO stats =
      Solvers.computeStats(state, ImmutableList.of(currentRoute));

    // if there is any tardiness in the current route, lets try to reacution one
    // parcel
    if (!reauctioning.get()
        && (stats.pickupTardiness > 0 || stats.deliveryTardiness > 0)) {

      // find all swappable parcels, a parcel can be swapped if it is not yet in
      // cargo (it must occur twice in route for that)
      // TODO filter out parcels that will be visited within several seconds
      // (length of auction)
      final Multiset<Parcel> routeMultiset =
        LinkedHashMultiset.create(currentRoute);
      final Set<Parcel> swappableParcels = new LinkedHashSet<>();
      for (final Parcel ap : assignedParcels) {
        if (!pdpModel.get().getParcelState(ap).isPickedUp()
            && !pdpModel.get().getParcelState(ap).isTransitionState()) {
          swappableParcels.add(ap);
        }
      }

      final double baseline = objectiveFunction.computeCost(stats);
      double lowestCost = baseline;
      @Nullable
      Parcel toSwap = null;

      LOGGER.trace("Compute cost of swapping");
      for (final Parcel sp : swappableParcels) {
        LOGGER.trace("try to swap {}", sp);
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
      if (toSwap != null) {
        reauctioning.set(true);
        // for (final Map.Entry<Parcel, Auctioneer<DoubleBid>> entry :
        // parcelAuctioneers
        // .entrySet()) {
        //
        // System.out.println(entry.toString());
        // }

        // try to reauction

        final Auctioneer<DoubleBid> auct = parcelAuctioneers.get(toSwap);

        final DoubleBid initialBid = DoubleBid.create(state.getTime(), this,
          toSwap, baseline - lowestCost);
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
  public void releaseParcel(Parcel p) {
    LOGGER.trace("{} RELEASE PARCEL {}", this, p);
    // remove the parcel from the route immediately to avoid going there
    final List<Parcel> currentRoute =
      new ArrayList<>(((Truck) vehicle.get()).getRoute());
    if (currentRoute.contains(p)) {
      LOGGER.trace(" > remove parcel from route: {}", currentRoute);
      currentRoute.removeAll(Collections.singleton(p));
      ((Truck) vehicle.get()).setRoute(currentRoute);

      checkState(!((Truck) vehicle.get()).getRoute().contains(p));

    }
    super.releaseParcel(p);
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
      StochasticSupplier<? extends RealtimeSolver> solverSupplier) {
    return new AutoValue_RtSolverBidder_Sup(objFunc, solverSupplier);
  }

  @AutoValue
  abstract static class CallForBids {

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

    abstract ObjectiveFunction getObjectiveFunction();

    abstract StochasticSupplier<? extends RealtimeSolver> getSolverSupplier();

    @Override
    public RtSolverBidder get(long seed) {
      return new RtSolverBidder(getObjectiveFunction(),
          getSolverSupplier().get(seed));
    }

    @Override
    public String toString() {
      return RtSolverBidder.class.getSimpleName() + ".supplier(..)";
    }
  }
}
