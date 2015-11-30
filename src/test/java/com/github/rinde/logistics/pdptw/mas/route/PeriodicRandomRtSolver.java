/*
 * Copyright (C) 2011-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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
package com.github.rinde.logistics.pdptw.mas.route;

import static com.google.common.base.Verify.verifyNotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.RandomSolver;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.Scheduler;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers.AbstractStochasticSupplier;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 *
 * @author Rinde van Lon
 */
public class PeriodicRandomRtSolver implements RealtimeSolver {

  @Nullable
  Scheduler scheduler;
  @Nullable
  ListeningScheduledExecutorService executor;

  Solver randomSolver;

  final long schedulerPeriod;
  final AtomicInteger counter;
  final int maxInvocations;

  PeriodicRandomRtSolver(long period, int repetitions, long seed) {
    System.out.println("constructor");
    schedulerPeriod = period;
    maxInvocations = repetitions;
    randomSolver = RandomSolver.create(seed);
    counter = new AtomicInteger(0);
  }

  @Override
  public void init(Scheduler sched) {
    scheduler = sched;
  }

  @Override
  public void problemChanged(final GlobalStateObject snapshot) {
    cancel();
    counter.set(0);
    executor = MoreExecutors.listeningDecorator(
      Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@Nullable Runnable r) {
          return new Thread(r,
              Thread.currentThread().getName() + "-"
                  + PeriodicRandomRtSolver.class.getSimpleName());
        }
      }));

    executor.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        System.out.println("go " + counter.get());
        try {
          verifyNotNull(scheduler).updateSchedule(snapshot,
            randomSolver.solve(snapshot));
          if (counter.incrementAndGet() >= maxInvocations) {
            cancel();
          }
        } catch (final InterruptedException e) {
          cancel();
        }
      }
    }, schedulerPeriod,
      schedulerPeriod,
      TimeUnit.MILLISECONDS);
  }

  @Override
  public void receiveSnapshot(final GlobalStateObject snapshot) {}

  @Override
  public void cancel() {
    if (isComputing()) {
      executor.shutdown();
      scheduler.doneForNow();
    }

  }

  @Override
  public boolean isComputing() {
    return executor == null ? false : !executor.isTerminated();
  }

  static StochasticSupplier<RealtimeSolver> supplier(long period,
      int invocations) {
    return new Supp(period, invocations);
  }

  static class Supp extends AbstractStochasticSupplier<RealtimeSolver> {

    private static final long serialVersionUID = -8644195938978223699L;
    final long period;
    final int invocations;

    Supp(long per, int invoc) {
      period = per;
      invocations = invoc;
    }

    @Override
    public RealtimeSolver get(long seed) {
      return new PeriodicRandomRtSolver(period, invocations, seed);
    }
  }

}
