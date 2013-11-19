/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Queue;

import rinde.sim.core.SimulatorAPI;
import rinde.sim.core.SimulatorUser;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.SolverValidator;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.Solvers.SimulationSolver;
import rinde.sim.pdptw.central.Solvers.SolveArgs;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.PDPRoadModel;
import rinde.sim.util.SupplierRng;
import rinde.sim.util.SupplierRng.DefaultSupplierRng;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A {@link RoutePlanner} implementation that uses a {@link Solver} that
 * computes a complete route each time {@link #update(Collection, long)} is
 * called.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class SolverRoutePlanner extends AbstractRoutePlanner implements
    SimulatorUser {

  private final Solver solver;
  private Queue<? extends DefaultParcel> route;
  private Optional<SimulationSolver> solverHandle;
  private Optional<SimulatorAPI> simulator;
  private final boolean reuseCurRoutes;

  /**
   * Create a route planner that uses the specified {@link Solver} to compute
   * the best route.
   * @param s {@link Solver} used for route planning.
   * @param reuseCurrentRoutes Whether to reuse the current routes.
   */
  public SolverRoutePlanner(Solver s, boolean reuseCurrentRoutes) {
    solver = s;
    route = newLinkedList();
    solverHandle = Optional.absent();
    simulator = Optional.absent();
    reuseCurRoutes = reuseCurrentRoutes;
  }

  /**
   * Calling this method overrides the route of this planner. This method has
   * similar effect as {@link #update(Collection, long)} except that no
   * computations are done.
   * @param r The new route.
   */
  public void changeRoute(Queue<? extends DefaultParcel> r) {
    updated = true;
    route = newLinkedList(r);
  }

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    if (onMap.isEmpty() && pdpModel.get().getContents(vehicle.get()).isEmpty()) {
      route.clear();
    } else {
      final SolveArgs args = SolveArgs.create().useParcels(onMap);

      if (reuseCurRoutes) {
        args.useCurrentRoutes(ImmutableList.of(ImmutableList.copyOf(route)));
        try {
          final GlobalStateObject gso = solverHandle.get().convert(args).state;
          SolverValidator.checkRoute(gso.vehicles.get(0), 0);
        } catch (final IllegalArgumentException e) {
          args.noCurrentRoutes();
        }
      }
      route = solverHandle.get().solve(args).get(0);
    }
  }

  @Override
  public void setSimulator(SimulatorAPI api) {
    simulator = Optional.of(api);
    initSolver();
  }

  private void initSolver() {
    if (!solverHandle.isPresent() && isInitialized() && simulator.isPresent()) {
      solverHandle = Optional.of(Solvers.solverBuilder(solver)
          .with((PDPRoadModel) roadModel.get()).with(pdpModel.get())
          .with(simulator.get()).with(vehicle.get()).buildSingle());
    }
  }

  @Override
  protected void afterInit() {
    initSolver();
  }

  @Override
  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Override
  public Optional<DefaultParcel> current() {
    return Optional.fromNullable((DefaultParcel) route.peek());
  }

  @Override
  public Optional<ImmutableList<DefaultParcel>> currentRoute() {
    if (route.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(ImmutableList.copyOf(route));
  }

  @Override
  protected void nextImpl(long time) {
    route.poll();
  }

  /**
   * Supplier for {@link SolverRoutePlanner} that does not reuse the current
   * routes.
   * @param solverSupplier A {@link SupplierRng} that supplies the
   *          {@link Solver} that will be used in the {@link SolverRoutePlanner}
   *          .
   * @return A {@link SupplierRng} that supplies {@link SolverRoutePlanner}
   *         instances.
   */
  public static SupplierRng<SolverRoutePlanner> supplierWithoutCurrentRoutes(
      final SupplierRng<? extends Solver> solverSupplier) {
    return new SRPSupplier(solverSupplier, false);
  }

  /**
   * @param solverSupplier A {@link SupplierRng} that supplies the
   *          {@link Solver} that will be used in the {@link SolverRoutePlanner}
   *          .
   * @return A {@link SupplierRng} that supplies {@link SolverRoutePlanner}
   *         instances.
   */
  public static SupplierRng<SolverRoutePlanner> supplier(
      final SupplierRng<? extends Solver> solverSupplier) {
    return new SRPSupplier(solverSupplier, true);
  }

  private static class SRPSupplier extends
      DefaultSupplierRng<SolverRoutePlanner> {
    final SupplierRng<? extends Solver> solverSupplier;
    final boolean reuseCurrentRoutes;

    SRPSupplier(final SupplierRng<? extends Solver> ss, final boolean rr) {
      solverSupplier = ss;
      reuseCurrentRoutes = rr;
    }

    @Override
    public SolverRoutePlanner get(long seed) {
      return new SolverRoutePlanner(solverSupplier.get(seed),
          reuseCurrentRoutes);
    }

    @Override
    public String toString() {
      return super.toString() + "-" + solverSupplier.toString();
    }
  }
}
