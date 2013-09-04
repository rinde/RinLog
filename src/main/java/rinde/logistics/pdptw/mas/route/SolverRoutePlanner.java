/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Queue;

import javax.annotation.Nullable;
import javax.measure.Measure;
import javax.measure.unit.SI;

import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.arrays.SolutionObject;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.PDPRoadModel;

import com.google.common.base.Optional;

/**
 * A {@link RoutePlanner} implementation that uses a {@link Solver} that
 * computes a complete route each time {@link #update(Collection, long)} is
 * called.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class SolverRoutePlanner extends AbstractRoutePlanner {

  private final Solver solver;
  private Queue<? extends DefaultParcel> route;
  @Nullable
  private SolutionObject solutionObject;

  /**
   * Create a route planner that uses the specified {@link Solver} to compute
   * the best route.
   * @param s {@link Solver} used for route planning.
   */
  public SolverRoutePlanner(Solver s) {
    solver = s;
    route = newLinkedList();
  }

  @Override
  protected void doUpdate(Collection<DefaultParcel> onMap, long time) {
    route = Solvers.solve(solver, (PDPRoadModel) roadModel.get(),
        pdpModel.get(), vehicle.get(), onMap,
        Measure.valueOf(time, SI.MILLI(SI.SECOND)));
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
  protected void nextImpl(long time) {
    route.poll();
  }
}
