package rinde.logistics.pdptw.mas.comm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.List;
import java.util.Queue;
import java.util.Set;

import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.sim.core.model.road.RoadModels;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SolveArgs;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.collect.ImmutableList;

public class NegotiatingBidder extends SolverBidder {

  private final Solver negotiationSolver;

  public NegotiatingBidder(ObjectiveFunction objFunc, Solver s1, Solver s2) {
    super(objFunc, s1);
    negotiationSolver = s2;
  }

  @Override
  public void receiveParcel(DefaultParcel p) {
    final List<Truck> trucks = RoadModels.findClosestObjects(roadModel.get()
        .getPosition(vehicle.get()), roadModel.get(), Truck.class, 2);

    if (trucks.get(0) != vehicle.get() && trucks.get(1) != vehicle.get()) {
      trucks.remove(1);
      trucks.add((Truck) vehicle.get());
    }

    final Set<DefaultParcel> ps = newLinkedHashSet();
    ps.add(p);
    ps.addAll(assignedParcels);

    final Truck other = trucks.get(0) == vehicle.get() ? trucks.get(1) : trucks
        .get(0);
    ps.addAll(((NegotiatingBidder) other.getCommunicator()).assignedParcels);

    checkArgument(
        trucks.get(0) == vehicle.get() || trucks.get(1) == vehicle.get(),
        "this: %s, others: %s", vehicle.get(), trucks);

    final Set<DefaultParcel> availableParcels = newLinkedHashSet(ps);
    for (final Truck truck : trucks) {
      for (final DefaultParcel dp : truck.getRoute()) {
        if (!pdpModel.get().getParcelState(dp).isPickedUp()) {
          availableParcels.add(dp);
        }
      }
    }

    final ImmutableList<ImmutableList<DefaultParcel>> currentRoutes = ImmutableList
        .<ImmutableList<DefaultParcel>> builder()
        .add(ImmutableList.copyOf(trucks.get(0).getRoute()))
        .add(ImmutableList.copyOf(trucks.get(1).getRoute())).build();

    final List<Queue<DefaultParcel>> routes = Solvers
        .solverBuilder(negotiationSolver)
        .with(trucks)
        .with(pdpModel.get())
        .with(roadModel.get())
        .with(simulator.get())
        .build()
        .solve(
            SolveArgs.create().useCurrentRoutes(currentRoutes)
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
      final SupplierRng<? extends Solver> negoSolverSupplier) {
    return new DefaultSupplierRng<NegotiatingBidder>() {
      @Override
      public NegotiatingBidder get(long seed) {
        return new NegotiatingBidder(objFunc, bidderSolverSupplier.get(seed),
            negoSolverSupplier.get(seed));
      }

      @Override
      public String toString() {
        return super.toString() + "-" + bidderSolverSupplier.toString() + "-"
            + negoSolverSupplier.toString();
      }
    };
  }
}
