package com.github.rinde.logistics.pdptw.solver;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.opt.localsearch.Swaps;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.pdptw.central.GlobalStateObject;
import com.github.rinde.rinsim.pdptw.central.Solver;
import com.github.rinde.rinsim.pdptw.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.collect.ImmutableList;

/**
 * Implementation of 2-opt local search algorithm. It is a decorator for another
 * {@link Solver}, it cannot be used directly since it relies on a complete
 * schedule as input (i.e. all parcels must already be assigned to a vehicle).
 * For more information about the algorithm see
 * {@link Swaps#bfsOpt2(ImmutableList, ImmutableList, Object, com.github.rinde.opt.localsearch.RouteEvaluator)}
 * and
 * {@link Swaps#dfsOpt2(ImmutableList, ImmutableList, Object, com.github.rinde.opt.localsearch.RouteEvaluator, org.apache.commons.math3.random.RandomGenerator)}
 * .
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class Opt2 implements Solver {

  final RandomGenerator rng;
  final Solver delegate;
  final ParcelRouteEvaluator evaluator;
  final boolean depthFirstSearch;

  /**
   * Creates a new instance that decorates the specified {@link Solver} and uses
   * the specified {@link ObjectiveFunction} to compute the cost of a swap.
   * @param seed The seed to use to initialize the random number generator. Note
   *          that this has only an effect when <i>depth first search</i> is
   *          used. Breadth first search is deterministic and does not use the
   *          random number generator.
   * @param deleg The solver to decorate.
   * @param objFunc The {@link ObjectiveFunction} to use for cost computation.
   * @param dfs If true <i>depth first search</i> will be used, otherwise
   *          <i>breadth first search</i> is used.
   */
  public Opt2(long seed, Solver deleg, ObjectiveFunction objFunc, boolean dfs) {
    rng = new MersenneTwister(seed);
    delegate = deleg;
    evaluator = new ParcelRouteEvaluator(objFunc);
    depthFirstSearch = dfs;
  }

  @Override
  public ImmutableList<ImmutableList<ParcelDTO>> solve(GlobalStateObject state) {
    final ImmutableList<ImmutableList<ParcelDTO>> schedule = delegate
        .solve(state);
    final ImmutableList.Builder<Integer> indexBuilder = ImmutableList.builder();
    for (final VehicleStateObject vso : state.vehicles) {
      indexBuilder.add(vso.destination == null ? 0 : 1);
    }
    if (depthFirstSearch) {
      return Swaps.dfsOpt2(schedule, indexBuilder.build(), state, evaluator,
          rng);
    }
    return Swaps.bfsOpt2(schedule, indexBuilder.build(), state, evaluator);
  }

  /**
   * Decorates the specified {@link Solver} supplier with <i>breadth-first</i>
   * {@link Opt2}. This algorithm is deterministic, repeated invocations (even
   * with different random seeds) on the same data will yield the same result.
   * @param delegate The solver to decorate.
   * @param objFunc The objective function to use.
   * @return A supplier that creates instances of a solver decorated with
   *         {@link Opt2}.
   */
  public static StochasticSupplier<Solver> breadthFirstSupplier(
      final StochasticSupplier<Solver> delegate, final ObjectiveFunction objFunc) {
    return new Opt2Supplier(delegate, objFunc, false);
  }

  /**
   * Decorates the specified {@link Solver} supplier with <i>depth-first</i>
   * {@link Opt2}. This algorithm is non-deterministic, repeated invocations on
   * the same data may yield different results if different random seeds are
   * used.
   * @param delegate The solver to decorate.
   * @param objFunc The objective function to use.
   * @return A supplier that creates instances of a solver decorated with
   *         {@link Opt2}.
   */
  public static StochasticSupplier<Solver> depthFirstSupplier(
      final StochasticSupplier<Solver> delegate, final ObjectiveFunction objFunc) {
    return new Opt2Supplier(delegate, objFunc, true);
  }

  private static class Opt2Supplier extends AbstractStochasticSupplier<Solver> {
    private static final long serialVersionUID = -1213455191941076859L;
    private final StochasticSupplier<Solver> delegate;
    private final ObjectiveFunction objectiveFunction;
    private final boolean depthFirstSearch;

    Opt2Supplier(StochasticSupplier<Solver> del, ObjectiveFunction objFunc,
        boolean dfs) {
      delegate = del;
      objectiveFunction = objFunc;
      depthFirstSearch = dfs;
    }

    @Override
    public Solver get(long seed) {
      final RandomGenerator rng = new MersenneTwister(seed);
      return new Opt2(rng.nextLong(), delegate.get(rng.nextLong()),
          objectiveFunction, depthFirstSearch);
    }
  }
}
