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
import rinde.sim.pdptw.central.Solvers.SVSolverHandle;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.PDPRoadModel;

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
  private Optional<SVSolverHandle> solverHandle;
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

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    route = solverHandle.get().solve(onMap);
  }

  @Override
  public void setSimulator(SimulatorAPI api) {
    simulator = Optional.of(api);
    initSolver();
  }

  private void initSolver() {
    if (!solverHandle.isPresent() && isInitialized() && simulator.isPresent()) {
      solverHandle = Optional.of(Solvers.singleVehicleSolver(solver,
          (PDPRoadModel) roadModel.get(), pdpModel.get(), simulator.get(),
          vehicle.get()));
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
}
