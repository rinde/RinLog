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
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.rinsim.central.SimSolver;
import com.github.rinde.rinsim.central.SimSolverBuilder;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.Solvers.SolveArgs;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * {@link SolverBidder} that uses a negotiation phase for exchanging parcels.
 * @author Rinde van Lon
 */
public class NegotiatingBidder extends SolverBidder {

  private static final Comparator<TruckDist> TRUCK_DIST_COMPARATOR =
    TruckDistComparator.INSTANCE;
  private static final Function<TruckDist, Truck> TRUCK_DIST_TO_TRUCK =
    ToTruckFunc.INSTANCE;

  /**
   * This heuristic determines the property on which the selection of
   * negotiators is done.
   * @author Rinde van Lon
   */
  public enum SelectNegotiatorsHeuristic {
    /**
     * Select negotiators based on <code>n</code> closed vehicles.
     */
    VEHICLE_POSITION,

    /**
     * Select negotiators based on <code>n</code> vehicles which closest first
     * destinations.
     */
    FIRST_DESTINATION_POSITION;
  }

  Optional<SimSolverBuilder> simSolvBuilder;
  private final Solver negotiationSolver;
  private final int negotiators;
  private final SelectNegotiatorsHeuristic heuristic;

  /**
   * Create a new instance.
   * @param objFunc The objective function to use for optimization.
   * @param s1 The solver used for computing bids.
   * @param s2 The solver used for the negotiation process.
   * @param numOfNegotiators The number of parties to include in the negotiation
   *          process (including itself), must be <code>&ge; 2</code>.
   * @param h The heuristic to use for selecting negotiators.
   */
  protected NegotiatingBidder(ObjectiveFunction objFunc, Solver s1, Solver s2,
      int numOfNegotiators, SelectNegotiatorsHeuristic h) {
    super(objFunc, s1);
    negotiationSolver = s2;
    checkArgument(numOfNegotiators >= 2);
    negotiators = numOfNegotiators;
    heuristic = h;
    simSolvBuilder = Optional.absent();
  }

  private List<Truck> findTrucks() {
    final Point reference = convertToPos((Truck) vehicle.get());
    final List<TruckDist> pos = newArrayList(Collections2.transform(roadModel
        .get().getObjectsOfType(Truck.class),
      new ToTruckDistFunc(reference)));

    checkState(
      pos.size() >= negotiators,
      "There are not enough vehicles in the system to hold a %s-party "
          + "negotiation, there are only %s vehicle(s).",
      negotiators, pos.size());
    Collections.sort(pos, TRUCK_DIST_COMPARATOR);
    final List<Truck> trucks = newArrayList(Lists.transform(pos,
      TRUCK_DIST_TO_TRUCK).subList(0, negotiators));

    if (!trucks.contains(vehicle.get())) {
      // remove the last one in the list
      trucks.remove(trucks.size() - 1);
      trucks.add((Truck) vehicle.get());
    }
    checkArgument(trucks.contains(vehicle.get()));
    return trucks;
  }

  Point convertToPos(Truck t) {
    Point p;
    if (t.getRoute().isEmpty()
        || heuristic == SelectNegotiatorsHeuristic.VEHICLE_POSITION) {
      p = roadModel.get().getPosition(t);
    } else {
      final Parcel firstDestination = t.getRoute().iterator().next();
      if (pdpModel.get().getParcelState(firstDestination).isPickedUp()) {
        p = firstDestination.getDto().getDeliveryLocation();
      } else {
        p = firstDestination.getDto().getPickupLocation();
      }
    }
    return p;
  }

  @Override
  public void receiveParcel(Auctioneer<DoubleBid> auctioneer, Parcel p,
      long auctionStartTime) {
    final List<Truck> trucks = findTrucks();
    final Set<Parcel> ps = newLinkedHashSet();
    ps.add(p);
    for (final Truck t : trucks) {
      ps.addAll(((NegotiatingBidder) t.getCommunicator()).assignedParcels);
    }

    final Set<Parcel> availableParcels = newLinkedHashSet(ps);
    for (final Truck truck : trucks) {
      for (final Parcel dp : truck.getRoute()) {
        if (!pdpModel.get().getParcelState(dp).isPickedUp()) {
          availableParcels.add(dp);
        }
      }
    }

    final ImmutableList.Builder<ImmutableList<Parcel>> currentRoutes =
      ImmutableList
          .<ImmutableList<Parcel>>builder();
    for (final Truck t : trucks) {
      currentRoutes.add(ImmutableList.copyOf(t.getRoute()));
    }

    final SimSolver sol = simSolvBuilder.get()
        .setVehicles(trucks)
        .build(negotiationSolver);
    final List<Queue<Parcel>> routes = sol.solve(SolveArgs.create()
        .useCurrentRoutes(currentRoutes.build())
        .useParcels(availableParcels));

    final List<Parcel> list = newArrayList();
    for (int i = 0; i < trucks.size(); i++) {
      final Queue<Parcel> route = routes.get(i);
      ((SolverRoutePlanner) trucks.get(i).getRoutePlanner()).changeRoute(route);
      trucks.get(i).setRoute(route);
      ((NegotiatingBidder) trucks.get(i).getCommunicator()).assignedParcels
          .clear();

      final Set<Parcel> newAssignedParcels = newLinkedHashSet(route);
      newAssignedParcels.retainAll(ps);
      list.addAll(newAssignedParcels);
      ((NegotiatingBidder) trucks.get(i).getCommunicator()).assignedParcels
          .addAll(newAssignedParcels);

      final List<Parcel> l = newArrayList(route);
      checkArgument(!newAssignedParcels.retainAll(route), "", l,
        newAssignedParcels);

      // FIXME update all trucks by dispatching events? or is this not needed???
    }
    checkArgument(list.size() == ps.size());
    checkArgument(newLinkedHashSet(list).equals(ps));
  }

  @Override
  public void setSolverProvider(SimSolverBuilder builder) {
    super.setSolverProvider(builder);
    simSolvBuilder = Optional.of(builder);
  }

  /**
   * Create a supplier that creates new instances.
   * @param objFunc The objective function to use for optimization.
   * @param bidderSolverSupplier The solver used for computing bids.
   * @param negoSolverSupplier The solver used for the negotiation process.
   * @param numOfNegotiators The number of parties to include in the negotiation
   *          process (including itself), must be <code>&ge; 2</code>.
   * @param heuristic The heuristic to use for selecting negotiators.
   * @return The new supplier.
   */
  public static StochasticSupplier<NegotiatingBidder> supplier(
      final ObjectiveFunction objFunc,
      final StochasticSupplier<? extends Solver> bidderSolverSupplier,
      final StochasticSupplier<? extends Solver> negoSolverSupplier,
      final int numOfNegotiators,
      final SelectNegotiatorsHeuristic heuristic) {
    return new AbstractStochasticSupplier<NegotiatingBidder>() {
      private static final long serialVersionUID = 8739438748665308053L;

      @Override
      public NegotiatingBidder get(long seed) {
        return new NegotiatingBidder(objFunc, bidderSolverSupplier.get(seed),
            negoSolverSupplier.get(seed), numOfNegotiators, heuristic);
      }

      @Override
      public String toString() {
        return Joiner.on('-').join(
          Arrays.<Object>asList(super.toString(),
            bidderSolverSupplier.toString(), negoSolverSupplier.toString(),
            numOfNegotiators, heuristic.toString().replaceAll("_", "-")));
      }
    };
  }

  enum ToTruckFunc implements Function<TruckDist, Truck> {
    INSTANCE {
      @Override
      @Nullable
      public Truck apply(@Nullable TruckDist input) {
        if (input == null) {
          throw new IllegalArgumentException("Null input is not allowed.");
        }
        return input.truck;
      }
    }
  }

  class ToTruckDistFunc implements Function<Truck, TruckDist> {
    private final Point reference;

    ToTruckDistFunc(Point ref) {
      reference = ref;
    }

    @Override
    @Nullable
    public TruckDist apply(@Nullable Truck t) {
      if (t == null) {
        throw new IllegalArgumentException("Null is not allowed.");
      }
      return new TruckDist(t, Point.distance(convertToPos(t), reference));
    }
  }

  private enum TruckDistComparator implements Comparator<TruckDist> {
    INSTANCE {
      @Override
      public int compare(@Nullable TruckDist o1, @Nullable TruckDist o2) {
        return Double.compare(checkNotNull(o1).distance,
          checkNotNull(o2).distance);
      }
    };
  }

  private static class TruckDist {
    final Truck truck;
    final double distance;

    TruckDist(Truck t, double d) {
      truck = t;
      distance = d;
    }
  }
}
