package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Queue;
import java.util.Set;

import rinde.logistics.pdptw.mas.Truck;
import rinde.sim.core.SimulatorAPI;
import rinde.sim.core.SimulatorUser;
import rinde.sim.core.pdptw.ParcelDTO;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SimulationSolver;
import rinde.sim.pdptw.central.Solvers.SolveArgs;
import rinde.sim.pdptw.central.Solvers.StateContext;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.StochasticSuppliers;
import rinde.sim.util.StochasticSuppliers.AbstractStochasticSupplier;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A {@link Bidder} that uses a {@link Solver} for computing the bid value.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class SolverBidder extends AbstractBidder implements SimulatorUser {

  /**
   * A reference to the simulator.
   */
  protected Optional<SimulatorAPI> simulator;

  private final ObjectiveFunction objectiveFunction;
  private final Solver solver;
  private Optional<SimulationSolver> solverHandle;

  /**
   * Creates a new bidder using the specified solver and objective function.
   * @param objFunc The {@link ObjectiveFunction} to use to calculate the bid
   *          value.
   * @param s The solver used to compute the (near) optimal schedule when
   *          calculating a bid.
   */
  public SolverBidder(ObjectiveFunction objFunc, Solver s) {
    objectiveFunction = objFunc;
    solver = s;
    solverHandle = Optional.absent();
    simulator = Optional.absent();
  }

  @Override
  public double getBidFor(DefaultParcel p, long time) {
    LOGGER.info("{} getBidFor {}", this, p);
    final Set<DefaultParcel> parcels = newLinkedHashSet(assignedParcels);
    parcels.add(p);
    final ImmutableList<DefaultParcel> currentRoute = ImmutableList
        .copyOf(((Truck) vehicle.get()).getRoute());
    LOGGER.trace(" > currentRoute {}", currentRoute);
    final ImmutableList<ParcelDTO> dtoRoute = Solvers.toDtoList(currentRoute);
    final StateContext context = solverHandle.get().convert(
        SolveArgs.create().noCurrentRoutes().useParcels(parcels));
    final double baseline = objectiveFunction.computeCost(Solvers.computeStats(
        context.state, ImmutableList.of(dtoRoute)));

    // make sure that all parcels in the route are always in the available
    // parcel list when needed. This is needed to satisfy the solver.
    for (final DefaultParcel dp : currentRoute) {
      if (!pdpModel.get().getParcelState(dp).isPickedUp()) {
        parcels.add(dp);
      }
    }

    // check whether the RoutePlanner produces routes compatible with the solver
    final SolveArgs args = SolveArgs.create().useParcels(parcels)
        .useCurrentRoutes(ImmutableList.of(currentRoute));
    try {
      final GlobalStateObject gso = solverHandle.get().convert(args).state;
      SolverValidator.checkRoute(gso.vehicles.get(0), 0);
    } catch (final IllegalArgumentException e) {
      args.noCurrentRoutes();
    }
    // if the route is not compatible, don't use routes at all
    final Queue<DefaultParcel> newRoute = solverHandle.get().solve(args).get(0);
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
      solverHandle = Optional.of(Solvers.solverBuilder(solver)
          .with(roadModel.get())
          .with(pdpModel.get())
          .with(simulator.get())
          .with(vehicle.get())
          .buildSingle());

    }
  }

  /**
   * Creates a new {@link SolverBidder} supplier.
   * @param objFunc The objective function to use.
   * @param solverSupplier The solver to use.
   * @return A supplier of {@link SolverBidder} instances.
   */
  public static StochasticSupplier<SolverBidder> supplier(
      final ObjectiveFunction objFunc,
      final StochasticSupplier<? extends Solver> solverSupplier) {
    return new StochasticSuppliers.AbstractStochasticSupplier<SolverBidder>() {
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
