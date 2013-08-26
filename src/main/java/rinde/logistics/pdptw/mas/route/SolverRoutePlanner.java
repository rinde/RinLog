/**
 * 
 */
package rinde.logistics.pdptw.mas.route;

import static com.google.common.collect.Lists.newLinkedList;

import java.util.Collection;
import java.util.Queue;

import javax.annotation.Nullable;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.central.arrays.SolutionObject;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.PDPRoadModel;

/**
 * A {@link RoutePlanner} implementation that uses a {@link Solver} that
 * computes a complete route each time {@link #update(Collection, long)} is
 * called.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class SolverRoutePlanner extends AbstractRoutePlanner {

  protected final Solver solver;
  protected Queue<? extends DefaultParcel> route;
  @Nullable
  protected SolutionObject solutionObject;

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
    route = Solvers
        .solve(solver, (PDPRoadModel) roadModel.get(), pdpModel.get(), vehicle
            .get(), onMap, time, SI.MILLI(SI.SECOND), NonSI.KILOMETERS_PER_HOUR, SI.KILOMETER);
  }

  public boolean hasNext() {
    return !route.isEmpty();
  }

  @Nullable
  public DefaultParcel current() {
    return route.peek();
  }

  @Override
  protected void nextImpl(long time) {
    route.poll();
  }
}
