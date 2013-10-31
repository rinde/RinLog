package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nullable;

import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPModel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SolveArgs;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class NegotiatingBidder extends SolverBidder {

  public enum SelectNegotiatorsHeuristic {
    VEHICLE_POSITION, FIRST_DESTINATION_POSITION;
  }

  private final Solver negotiationSolver;
  private final int negotiators;
  private final SelectNegotiatorsHeuristic heuristic;

  public NegotiatingBidder(ObjectiveFunction objFunc, Solver s1, Solver s2,
      int numOfNegotiators, SelectNegotiatorsHeuristic h) {
    super(objFunc, s1);
    negotiationSolver = s2;
    checkArgument(numOfNegotiators >= 2);
    negotiators = numOfNegotiators;
    heuristic = h;
  }

  private List<Truck> findTrucks() {
    final Point reference = roadModel.get().getPosition(vehicle.get());
    final List<TruckDist> pos = newArrayList(Collections2.transform(roadModel
        .get().getObjectsOfType(Truck.class),
        new ToTruckDistFunc(roadModel.get(), pdpModel.get(), reference,
            heuristic)));

    Collections.sort(pos);
    final List<Truck> trucks = newArrayList(Lists.transform(pos,
        new ToTruckFunc()).subList(0, negotiators));

    if (!trucks.contains(vehicle.get())) {
      // remove the last one in the list
      trucks.remove(trucks.size() - 1);
      trucks.add((Truck) vehicle.get());
    }
    checkArgument(trucks.contains(vehicle.get()));
    return trucks;
  }

  static class ToTruckFunc implements Function<TruckDist, Truck> {
    @Override
    @Nullable
    public Truck apply(@Nullable TruckDist input) {
      checkArgument(input != null);
      return input.truck;
    }
  }

  private static class ToTruckDistFunc implements Function<Truck, TruckDist> {
    private final RoadModel roadModel;
    private final PDPModel pdpModel;
    private final Point reference;
    private final SelectNegotiatorsHeuristic heuristic;

    ToTruckDistFunc(RoadModel rm, PDPModel pm, Point ref,
        SelectNegotiatorsHeuristic h) {
      roadModel = rm;
      pdpModel = pm;
      reference = ref;
      heuristic = h;
    }

    @Override
    @Nullable
    public TruckDist apply(@Nullable Truck t) {
      checkArgument(t != null);
      Point p;
      if (t.getRoute().isEmpty()
          || heuristic == SelectNegotiatorsHeuristic.VEHICLE_POSITION) {
        p = roadModel.getPosition(t);
      } else {
        final DefaultParcel firstDestination = t.getRoute().iterator().next();
        if (pdpModel.getParcelState(firstDestination).isPickedUp()) {
          p = firstDestination.dto.destinationLocation;
        } else {
          p = firstDestination.dto.pickupLocation;
        }
      }
      return new TruckDist(t, Point.distance(p, reference));
    }
  }

  private static class TruckDist implements Comparable<TruckDist> {
    final Truck truck;
    final double distance;

    TruckDist(Truck t, double d) {
      truck = t;
      distance = d;
    }

    @Override
    public int compareTo(TruckDist o) {
      return Double.compare(distance, o.distance);
    }
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
    }
    checkArgument(list.size() == ps.size());
    checkArgument(newLinkedHashSet(list).equals(ps));
  }

  public static SupplierRng<NegotiatingBidder> supplier(
      final ObjectiveFunction objFunc,
      final SupplierRng<? extends Solver> bidderSolverSupplier,
      final SupplierRng<? extends Solver> negoSolverSupplier,
      final int numOfNegotiators, final SelectNegotiatorsHeuristic heuristic) {
    return new DefaultSupplierRng<NegotiatingBidder>() {
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
                numOfNegotiators, heuristic));
      }
    };
  }
}
