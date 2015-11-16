/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.logistics.pdptw.solver;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.opt.localsearch.Swaps;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/**
 * Implementation of 2-opt local search algorithm. It is a decorator for another
 * {@link Solver}, it cannot be used directly since it relies on a complete
 * schedule as input (i.e. all parcels must already be assigned to a vehicle).
 * For more information about the algorithm see
 * {@link Swaps#bfsOpt2(ImmutableList, IntList, Object, com.github.rinde.opt.localsearch.RouteEvaluator)}
 * and
 * {@link Swaps#dfsOpt2(ImmutableList, IntList, Object, com.github.rinde.opt.localsearch.RouteEvaluator, RandomGenerator)}
 * .
 * @author Rinde van Lon
 */
public final class Opt2 implements Solver {

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
  Opt2(long seed, Solver deleg, ObjectiveFunction objFunc, boolean dfs) {
    rng = new MersenneTwister(seed);
    delegate = deleg;
    evaluator = new ParcelRouteEvaluator(objFunc);
    depthFirstSearch = dfs;
  }

  @Override
  public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
      throws InterruptedException {
    final ImmutableList<ImmutableList<Parcel>> schedule = delegate
        .solve(state);
    final IntList indices = new IntArrayList();
    for (final VehicleStateObject vso : state.getVehicles()) {
      indices.add(vso.getDestination().isPresent() ? 1 : 0);
    }
    if (depthFirstSearch) {
      return Swaps.dfsOpt2(schedule, IntLists.unmodifiable(indices), state,
        evaluator, rng);
    }
    return Swaps.bfsOpt2(schedule, IntLists.unmodifiable(indices), state,
      evaluator);
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
      final StochasticSupplier<Solver> delegate,
      final ObjectiveFunction objFunc) {
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
      final StochasticSupplier<Solver> delegate,
      final ObjectiveFunction objFunc) {
    return new Opt2Supplier(delegate, objFunc, true);
  }

  private static class Opt2Supplier extends AbstractStochasticSupplier<Solver> {
    private static final long serialVersionUID = -1213455191941076859L;
    private final StochasticSupplier<Solver> delegate;
    private final ObjectiveFunction objectiveFunction;
    private final boolean dfs;

    Opt2Supplier(StochasticSupplier<Solver> del, ObjectiveFunction objFunc,
        boolean depthFirst) {
      delegate = del;
      objectiveFunction = objFunc;
      dfs = depthFirst;
    }

    @Override
    public Solver get(long seed) {
      final RandomGenerator rand = new MersenneTwister(seed);
      return new Opt2(rand.nextLong(), delegate.get(rand.nextLong()),
          objectiveFunction, dfs);
    }

    @Override
    public String toString() {
      return new StringBuilder(Opt2.class.getSimpleName())
          .append((dfs ? ".deptFirstSupplier(" : ".breadthFirstSupplier("))
          .append(delegate)
          .append(",")
          .append(objectiveFunction)
          .append(")")
          .toString();
    }
  }
}
