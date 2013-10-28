/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Queue;

import rinde.sim.core.SimulatorAPI;
import rinde.sim.core.SimulatorUser;
import rinde.sim.pdptw.central.Solver;
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

  /**
   * Create a route planner that uses the specified {@link Solver} to compute
   * the best route.
   * @param s {@link Solver} used for route planning.
   */
  public SolverRoutePlanner(Solver s) {
    solver = s;
    route = newLinkedList();
    solverHandle = Optional.absent();
    simulator = Optional.absent();
  }

  public void changeRoute(Queue<? extends DefaultParcel> r) {
    updated = true;
    route = newLinkedList(r);
  }

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    if (onMap.isEmpty() && pdpModel.get().getContents(vehicle.get()).isEmpty()) {
      route.clear();
    } else {
      route = solverHandle
          .get()
          .solve(
              SolveArgs
                  .create()
                  .useCurrentRoutes(
                      ImmutableList.of(ImmutableList.copyOf(route)))
                  .useParcels(onMap)).get(0);
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
   * @param solverSupplier A {@link SupplierRng} that supplies the
   *          {@link Solver} that will be used in the {@link SolverRoutePlanner}
   *          .
   * @return A {@link SupplierRng} that supplies {@link SolverRoutePlanner}
   *         instances.
   */
  public static SupplierRng<SolverRoutePlanner> supplier(
      final SupplierRng<? extends Solver> solverSupplier) {
    return new DefaultSupplierRng<SolverRoutePlanner>() {
      @Override
      public SolverRoutePlanner get(long seed) {
        return new SolverRoutePlanner(solverSupplier.get(seed));
      }

      @Override
      public String toString() {
        return super.toString() + "-" + solverSupplier.toString();
      }
    };
  }
}
