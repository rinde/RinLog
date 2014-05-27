package rinde.logistics.pdptw.solver;

import rinde.opt.localsearch.Swaps;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.GlobalStateObject.VehicleStateObject;
import rinde.sim.pdptw.central.Solver;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.StochasticSuppliers;
import rinde.sim.util.StochasticSuppliers.AbstractStochasticSupplier;

import com.google.common.collect.ImmutableList;

/**
 * Implementation of 2-opt local search algorithm. It is a decorator for another
 * {@link Solver}, it cannot be used directly since it relies on a complete
 * schedule (i.e. all parcels must be assigned to a vehicle). For more
 * information about the algorithm see
 * {@link Swaps#opt2(ImmutableList, ImmutableList, Object, rinde.opt.localsearch.RouteEvaluator)}
 * .
 * 
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class Opt2 implements Solver {

  final Solver delegate;
  final ParcelRouteEvaluator evaluator;

  /**
   * Creates a new instance that decorates the specified {@link Solver} and uses
   * the specified {@link ObjectiveFunction} to compute the cost of a swap.
   * @param deleg The solver to decorate.
   * @param objFunc The {@link ObjectiveFunction} to use for cost computation.
   */
  public Opt2(Solver deleg, ObjectiveFunction objFunc) {
    delegate = deleg;
    evaluator = new ParcelRouteEvaluator(objFunc);
  }

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    final ImmutableList<ImmutableList<ParcelDTO>> schedule = delegate
        .solve(state);
    final ImmutableList.Builder<Integer> indexBuilder = ImmutableList.builder();
    for (final VehicleStateObject vso : state.vehicles) {
      indexBuilder.add(vso.destination == null ? 0 : 1);
    }
    return Swaps.opt2(schedule, indexBuilder.build(), state, evaluator);
  }

  /**
   * Decorates the specified {@link Solver} supplier with {@link Opt2}.
   * @param delegate The solver to decorate.
   * @param objFunc The objective function to use.
   * @return A supplier that creates instances of a solver decorated with
   *         {@link Opt2}.
   */
  public static StochasticSupplier<Solver> supplier(
      final StochasticSupplier<Solver> delegate, final ObjectiveFunction objFunc) {
    return new StochasticSuppliers.AbstractStochasticSupplier<Solver>() {
      @Override
      public Solver get(long seed) {
        return new Opt2(delegate.get(seed), objFunc);
      }
    };
  }
}
