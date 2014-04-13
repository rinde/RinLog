package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
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

import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.sim.core.graph.Point;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SolveArgs;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRngs;
import rinde.sim.util.SupplierRngs.AbstractSupplierRng;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * {@link SolverBidder} that uses a negotiation phase for exchanging parcels.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class NegotiatingBidder extends SolverBidder {

  private static final Comparator<TruckDist> TRUCK_DIST_COMPARATOR = new TruckDistComparator();
  private static final Function<TruckDist, Truck> TRUCK_DIST_TO_TRUCK = new ToTruckFunc();

  /**
   * This heuristic determines the property on which the selection of
   * negotiators is done.
   * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
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

  private final Solver negotiationSolver;
  private final int negotiators;
  private final SelectNegotiatorsHeuristic heuristic;

  /**
   * Create a new instance.
   * @param objFunc The objective function to use for optimization.
   * @param s1 The solver used for computing bids.
   * @param s2 The solver used for the negotiation process.
   * @param numOfNegotiators The number of parties to include in the negotiation
   *          process (including itself), must be <code>>=2</code>.
   * @param h The heuristic to use for selecting negotiators.
   */
  protected NegotiatingBidder(ObjectiveFunction objFunc, Solver s1, Solver s2,
      int numOfNegotiators, SelectNegotiatorsHeuristic h) {
    super(objFunc, s1);
    negotiationSolver = s2;
    checkArgument(numOfNegotiators >= 2);
    negotiators = numOfNegotiators;
    heuristic = h;
  }

  private List<Truck> findTrucks() {
    final Point reference = convertToPos((Truck) vehicle.get());
    final List<TruckDist> pos = newArrayList(Collections2.transform(roadModel
        .get().getObjectsOfType(Truck.class), new ToTruckDistFunc(reference)));

    checkState(
        pos.size() >= negotiators,
        "There are not enough vehicles in the system to hold a %s-party negotiation, there are only %s vehicle(s).",
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
      final DefaultParcel firstDestination = t.getRoute().iterator().next();
      if (pdpModel.get().getParcelState(firstDestination).isPickedUp()) {
        p = firstDestination.dto.destinationLocation;
      } else {
        p = firstDestination.dto.pickupLocation;
      }
    }
    return p;
  }

  @Override
  public void receiveParcel(DefaultParcel p) {
    final List<Truck> trucks = findTrucks();
    final Set<DefaultParcel> ps = newLinkedHashSet();
    ps.add(p);
    for (final Truck t : trucks) {
      ps.addAll(((NegotiatingBidder) t.getCommunicator()).assignedParcels);
    }

    final Set<DefaultParcel> availableParcels = newLinkedHashSet(ps);
    for (final Truck truck : trucks) {
      for (final DefaultParcel dp : truck.getRoute()) {
        if (!pdpModel.get().getParcelState(dp).isPickedUp()) {
          availableParcels.add(dp);
        }
      }
    }

    final ImmutableList.Builder<ImmutableList<DefaultParcel>> currentRoutes = ImmutableList
        .<ImmutableList<DefaultParcel>> builder();
    for (final Truck t : trucks) {
      currentRoutes.add(ImmutableList.copyOf(t.getRoute()));
    }

    final List<Queue<DefaultParcel>> routes = Solvers
        .solverBuilder(negotiationSolver)
        .with(trucks)
        .with(pdpModel.get())
        .with(roadModel.get())
        .with(simulator.get())
        .build()
        .solve(
            SolveArgs.create().useCurrentRoutes(currentRoutes.build())
                .useParcels(availableParcels));

    final List<DefaultParcel> list = newArrayList();
    for (int i = 0; i < trucks.size(); i++) {
      final Queue<DefaultParcel> route = routes.get(i);
      ((SolverRoutePlanner) trucks.get(i).getRoutePlanner()).changeRoute(route);
      trucks.get(i).setRoute(route);
      ((NegotiatingBidder) trucks.get(i).getCommunicator()).assignedParcels
          .clear();

      final Set<DefaultParcel> newAssignedParcels = newLinkedHashSet(route);
      newAssignedParcels.retainAll(ps);
      list.addAll(newAssignedParcels);
      ((NegotiatingBidder) trucks.get(i).getCommunicator()).assignedParcels
          .addAll(newAssignedParcels);

      final List<DefaultParcel> l = newArrayList(route);
      checkArgument(!newAssignedParcels.retainAll(route), "", l,
          newAssignedParcels);

      // FIXME update all trucks by dispatching events? or is this not needed???
    }
    checkArgument(list.size() == ps.size());
    checkArgument(newLinkedHashSet(list).equals(ps));
  }

  /**
   * Create a supplier that creates new instances.
   * @param objFunc The objective function to use for optimization.
   * @param bidderSolverSupplier The solver used for computing bids.
   * @param negoSolverSupplier The solver used for the negotiation process.
   * @param numOfNegotiators The number of parties to include in the negotiation
   *          process (including itself), must be <code>>=2</code>.
   * @param heuristic The heuristic to use for selecting negotiators.
   * @return The new supplier.
   */
  public static SupplierRng<NegotiatingBidder> supplier(
      final ObjectiveFunction objFunc,
      final SupplierRng<? extends Solver> bidderSolverSupplier,
      final SupplierRng<? extends Solver> negoSolverSupplier,
      final int numOfNegotiators,
      final SelectNegotiatorsHeuristic heuristic) {
    return new SupplierRngs.AbstractSupplierRng<NegotiatingBidder>() {
      @Override
      public NegotiatingBidder get(long seed) {
        return new NegotiatingBidder(objFunc, bidderSolverSupplier.get(seed),
            negoSolverSupplier.get(seed), numOfNegotiators, heuristic);
      }

      @Override
      public String toString() {
        return Joiner.on('-').join(
            Arrays.<Object> asList(super.toString(),
                bidderSolverSupplier.toString(), negoSolverSupplier.toString(),
                numOfNegotiators, heuristic.toString().replaceAll("_", "-")));
      }
    };
  }

  static class ToTruckFunc implements Function<TruckDist, Truck> {
    @Override
    @Nullable
    public Truck apply(@Nullable TruckDist input) {
      if (input == null) {
        throw new IllegalArgumentException("Null input is not allowed.");
      }
      return input.truck;
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

  private static class TruckDistComparator implements Comparator<TruckDist> {
    TruckDistComparator() {}

    @Override
    public int compare(TruckDist o1, TruckDist o2) {
      return Double.compare(o1.distance, o2.distance);
    }
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
