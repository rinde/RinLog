package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Collection;
import java.util.Queue;
import java.util.Set;

import rinde.logistics.pdptw.mas.Truck;
import rinde.sim.core.SimulatorAPI;
import rinde.sim.core.SimulatorUser;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SVSolverHandle;
import rinde.sim.pdptw.central.Solvers.StateContext;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class SolverBidder extends AbstractBidder implements SimulatorUser {

  private final ObjectiveFunction objectiveFunction;
  private final Solver solver;
  private Optional<SVSolverHandle> solverHandle;
  private Optional<SimulatorAPI> simulator;

  public SolverBidder(ObjectiveFunction objFunc, Solver s) {
    objectiveFunction = objFunc;
    solver = s;
    solverHandle = Optional.absent();
    simulator = Optional.absent();
  }

  @Override
  public double getBidFor(DefaultParcel p, long time) {
    final Set<DefaultParcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(p);
    final Collection<DefaultParcel> currentRoute = ((Truck) vehicle.get())
        .getRoute();
    final ImmutableList<ParcelDTO> dtoRoute = Solvers.toDtoList(currentRoute);
    final StateContext context = solverHandle.get().convert(parcels, null);
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
        context.state, ImmutableList.of(dtoRoute)));

    // make sure that all parcels in the route are always in the available
    // parcel list when needed. This is needed to satisfy the solver.
    for (final DefaultParcel dp : currentRoute) {
      if (!pdpModel.get().getParcelState(dp).isPickedUp()) {
        parcels.add(dp);
      }
    }

    final Queue<DefaultParcel> newRoute = solverHandle.get().solve(parcels,
        ImmutableList.copyOf(currentRoute));

    final double newCost = objectiveFunction.computeCost(Solvers.computeStats(
        context.state, ImmutableList.of(Solvers.toDtoList(newRoute))));

    return newCost - baseline;
  }

  @Override
  protected void afterInit() {
    initSolver();
  }

  @Override
  public void setSimulator(SimulatorAPI api) {
    simulator = Optional.of(api);
    initSolver();
  }

  private void initSolver() {
    if (simulator.isPresent() && roadModel.isPresent()
        && !solverHandle.isPresent()) {
      solverHandle = Optional.of(Solvers.singleVehicleSolver(solver,
          roadModel.get(), pdpModel.get(), simulator.get(), vehicle.get()));
    }
  }

  /**
   * Creates a new {@link SolverBidder} supplier.
   * @param objFunc The objective function to use.
   * @param solverSupplier The solver to use.
   * @return A supplier of {@link SolverBidder} instances.
   */
  public static SupplierRng<SolverBidder> supplier(
      final ObjectiveFunction objFunc,
      final SupplierRng<? extends Solver> solverSupplier) {
    return new DefaultSupplierRng<SolverBidder>() {
      @Override
      public SolverBidder get(long seed) {
        return new SolverBidder(objFunc, solverSupplier.get(seed));
      }

      @Override
      public String toString() {
        return super.toString() + "-" + solverSupplier.toString();
      }
    };
  }
}
