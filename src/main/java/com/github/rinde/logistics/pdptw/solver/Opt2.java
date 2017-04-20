/*
 * Copyright (C) 2013-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

import java.util.concurrent.CancellationException;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rinde.opt.localsearch.ProgressListener;
import com.github.rinde.opt.localsearch.Swaps;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObject.VehicleStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.Scheduler;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.GeomHeuristic;
import com.github.rinde.rinsim.geom.GeomHeuristics;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/**
 * Implementation of 2-opt local search algorithm. The algorithm is a decorator
 * for another {@link Solver}, it cannot be used directly since it relies on a
 * complete schedule as input (i.e. all parcels must already be assigned to a
 * vehicle). For more information about the algorithm see
 * {@link Swaps#bfsOpt2(ImmutableList, IntList, Object, com.github.rinde.opt.localsearch.RouteEvaluator,Optional)}
 * and
 * {@link Swaps#dfsOpt2(ImmutableList, IntList, Object, com.github.rinde.opt.localsearch.RouteEvaluator, RandomGenerator,Optional)}
 * .
 * @author Rinde van Lon
 */
public final class Opt2 {
  static final Logger LOGGER =
    LoggerFactory.getLogger(Opt2.class);

  private Opt2() {}

  public static Builder builder() {
    return Builder.create(null, null, false, GeomHeuristics.euclidean());
  }

  @AutoValue
  public abstract static class Builder {

    Builder() {}

    @Nullable
    abstract StochasticSupplier<Solver> solverSup();

    @Nullable
    abstract ObjectiveFunction objFunc();

    abstract boolean deptFirstSearch();

    abstract GeomHeuristic geomHeuristic();

    @CheckReturnValue
    public Builder withDepthFirstSearch() {
      return create(solverSup(), objFunc(), true, geomHeuristic());
    }

    @CheckReturnValue
    public Builder withDelegate(
        StochasticSupplier<? extends Solver> solverSupplier) {
      return create(solverSupplier, objFunc(), deptFirstSearch(),
        geomHeuristic());
    }

    @CheckReturnValue
    public Builder withObjectiveFunction(ObjectiveFunction of) {
      return create(solverSup(), of, deptFirstSearch(), geomHeuristic());
    }

    @CheckReturnValue
    public Builder withGeomHeuristic(GeomHeuristic h) {
      return create(solverSup(), objFunc(), deptFirstSearch(), h);
    }

    @CheckReturnValue
    public StochasticSupplier<Solver> buildSolverSupplier() {
      return buildSolverSupplier(null, geomHeuristic());
    }

    @CheckReturnValue
    public StochasticSupplier<RealtimeSolver> buildRealtimeSolverSupplier() {
      final Builder b = this;
      return new StochasticSupplier<RealtimeSolver>() {
        @Override
        public RealtimeSolver get(long seed) {
          return new RealtimeOpt2(b, seed);
        }

        @Override
        public String toString() {
          return supplierToString(b.deptFirstSearch(), true, b.objFunc());
        }
      };
    }

    @CheckReturnValue
    StochasticSupplier<Solver> buildSolverSupplier(
        @Nullable final ProgressListener<Parcel> progressListener,
        final GeomHeuristic heuristic) {
      final ObjectiveFunction objFunc = objFunc();
      checkArgument(objFunc != null,
        "An objective function must be defined.");

      final StochasticSupplier<Solver> deleg = solverSup();
      final StochasticSupplier<Solver> delegate = deleg != null
        ? deleg : CheapestInsertionHeuristic.supplier(objFunc);

      final boolean dfs = deptFirstSearch();
      return new StochasticSupplier<Solver>() {
        @Override
        public Solver get(long seed) {
          if (dfs) {
            final RandomGenerator rng = new MersenneTwister(seed);
            return new DfsOpt2(rng.nextLong(), delegate.get(rng.nextLong()),
              objFunc, progressListener, heuristic);
          }
          return new BfsOpt2(delegate.get(seed), objFunc, progressListener,
            heuristic);
        }

        @Override
        public String toString() {
          return supplierToString(dfs, false, objFunc);
        }
      };
    }

    static String supplierToString(boolean dfs, boolean rt,
        @Nullable ObjectiveFunction objFunc) {
      return Joiner.on("").join(
        Opt2.class.getSimpleName(),
        dfs ? "Dfs" : "Bfs",
        rt ? "RT(" : "(",
        objFunc,
        ")");
    }

    @SuppressWarnings("unchecked")
    static Builder create(
        @Nullable StochasticSupplier<? extends Solver> solverSup,
        @Nullable ObjectiveFunction objFunc,
        boolean dfs, GeomHeuristic heuristic) {
      return new AutoValue_Opt2_Builder(
        (StochasticSupplier<Solver>) solverSup,
        objFunc, dfs, heuristic);
    }
  }

  abstract static class AbstractOpt2Solver implements Solver {
    final Solver delegate;
    final ParcelRouteEvaluator evaluator;
    final Optional<ProgressListener<Parcel>> progressListener;

    AbstractOpt2Solver(Solver deleg, ObjectiveFunction objFunc,
        @Nullable ProgressListener<Parcel> pl, GeomHeuristic heuristic) {
      delegate = deleg;
      evaluator = new ParcelRouteEvaluator(objFunc, heuristic);
      progressListener = Optional.fromNullable(pl);
    }

    @Override
    public final ImmutableList<ImmutableList<Parcel>> solve(
        GlobalStateObject state) throws InterruptedException {
      final ImmutableList<ImmutableList<Parcel>> schedule =
        delegate.solve(state);

      return doSolve(schedule, state);
    }

    abstract ImmutableList<ImmutableList<Parcel>> doSolve(
        ImmutableList<ImmutableList<Parcel>> schedule, GlobalStateObject state)
        throws InterruptedException;

    static IntList indices(GlobalStateObject state) {
      final IntList indices = new IntArrayList();
      for (final VehicleStateObject vso : state.getVehicles()) {
        indices.add(vso.getDestination().isPresent() ? 1 : 0);
      }
      return IntLists.unmodifiable(indices);
    }
  }

  static class BfsOpt2 extends AbstractOpt2Solver {
    BfsOpt2(Solver deleg, ObjectiveFunction objFunc,
        @Nullable ProgressListener<Parcel> pl, GeomHeuristic h) {
      super(deleg, objFunc, pl, h);
    }

    @Override
    ImmutableList<ImmutableList<Parcel>> doSolve(
        ImmutableList<ImmutableList<Parcel>> schedule,
        GlobalStateObject state) throws InterruptedException {
      return Swaps.bfsOpt2(schedule, indices(state), state, evaluator,
        progressListener);
    }
  }

  static class DfsOpt2 extends AbstractOpt2Solver {
    RandomGenerator rng;

    DfsOpt2(long seed, Solver deleg, ObjectiveFunction objFunc,
        @Nullable ProgressListener<Parcel> pl, GeomHeuristic h) {
      super(deleg, objFunc, pl, h);
      rng = new MersenneTwister(seed);
    }

    @Override
    ImmutableList<ImmutableList<Parcel>> doSolve(
        ImmutableList<ImmutableList<Parcel>> schedule, GlobalStateObject state)
        throws InterruptedException {
      return Swaps.dfsOpt2(schedule, indices(state), state, evaluator, rng,
        progressListener);
    }
  }

  // @Override
  // public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject
  // state)
  // throws InterruptedException {
  // final ImmutableList<ImmutableList<Parcel>> schedule =
  // delegate.solve(state);
  //
  // if (depthFirstSearch) {
  // return Swaps.dfsOpt2(schedule, indices(state), state, evaluator, rng,
  // progressListener);
  // }
  // return Swaps.bfsOpt2(schedule, indices(state), state, evaluator,
  // progressListener);
  // }

  /**
   * Decorates the specified {@link Solver} supplier with <i>breadth-first</i>
   * {@link Opt2}. This algorithm is deterministic, repeated invocations (even
   * with different random seeds) on the same data will yield the same result.
   * @param delegate The solver to decorate.
   * @param objFunc The objective function to use.
   * @return A supplier that creates instances of a solver decorated with
   *         {@link Opt2}.
   */
  // public static StochasticSupplier<Solver> breadthFirstSupplier(
  // final StochasticSupplier<Solver> delegate,
  // final ObjectiveFunction objFunc) {
  // return new Opt2Supplier(delegate, objFunc, false);
  // }

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
  // public static StochasticSupplier<Solver> depthFirstSupplier(
  // final StochasticSupplier<Solver> delegate,
  // final ObjectiveFunction objFunc) {
  // return new Opt2Supplier(delegate, objFunc, true);
  // }

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

  static class RealtimeOpt2
      implements RealtimeSolver, ProgressListener<Parcel> {

    Optional<ListenableFuture<ImmutableList<ImmutableList<Parcel>>>> currentFuture;
    Optional<Scheduler> scheduler;

    Solver solver;
    @Nullable
    GlobalStateObject lastSnapshot;

    RealtimeOpt2(Builder b, long seed) {
      solver = b.buildSolverSupplier(this, b.geomHeuristic()).get(seed);
      currentFuture = Optional.absent();
      scheduler = Optional.absent();
      lastSnapshot = null;
    }

    @Override
    public void init(Scheduler s) {
      scheduler = Optional.of(s);
    }

    @Override
    public void problemChanged(final GlobalStateObject snapshot) {
      checkState(scheduler.isPresent(), "Not yet initialized.");
      cancel();
      lastSnapshot = snapshot;
      currentFuture = Optional.of(
        scheduler.get().getSharedExecutor().submit(
          Solvers.createSolverCallable(solver, snapshot)));

      Futures.addCallback(currentFuture.get(),
        new FutureCallback<ImmutableList<ImmutableList<Parcel>>>() {
          @Override
          public void onSuccess(
              @Nullable ImmutableList<ImmutableList<Parcel>> result) {
            LOGGER.trace("onSuccess: " + result);
            if (result == null) {
              scheduler.get().reportException(
                new IllegalArgumentException("Solver.solve(..) must return a "
                  + "non-null result. Solver: " + solver));
            } else {
              scheduler.get().updateSchedule(snapshot, result);
              scheduler.get().doneForNow();
            }
          }

          @Override
          public void onFailure(Throwable t) {
            if (t instanceof CancellationException) {
              LOGGER.trace("Solver execution got cancelled");
              return;
            }
            scheduler.get().reportException(t);
          }
        });
    }

    @Override
    public void receiveSnapshot(GlobalStateObject snapshot) {}

    @Override
    public void cancel() {
      if (isComputing()) {
        currentFuture.get().cancel(true);
      }
    }

    @Override
    public boolean isComputing() {
      return currentFuture.isPresent() && !currentFuture.get().isDone();
    }

    @Override
    public void notify(ImmutableList<ImmutableList<Parcel>> schedule,
        double objectiveValue) {
      scheduler.get().updateSchedule(verifyNotNull(lastSnapshot), schedule);
    }
  }
}
