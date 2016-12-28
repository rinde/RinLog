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
package com.github.rinde.logistics.pdptw.solver.optaplanner;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.Scheduler;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;

/**
 *
 * @author Rinde van Lon
 */
public class RealtimeSolverTest {

  Scheduler scheduler;
  RealtimeSolver solver;

  @Before
  public void setUp() {
    scheduler = mock(Scheduler.class);
    solver =
      OptaplannerSolvers.builder()
        .withName("testSolver")
        .withUnimprovedMsLimit(1000)
        .buildRealtimeSolverSupplier().get(123L);
    solver.init(scheduler);
  }

  @Test
  public void testNormalExecution() throws InterruptedException {
    assertThat(solver.isComputing()).isFalse();
    when(scheduler.getSharedExecutor()).thenReturn(
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));
    final GlobalStateObject snapshot = simpleProblem(1);

    solver.problemChanged(snapshot);

    assertThat(solver.isComputing()).isTrue();
    while (solver.isComputing()) {
      Thread.sleep(10L);
    }

    final ArgumentCaptor<GlobalStateObject> snapshotCaptor =
      ArgumentCaptor.forClass(GlobalStateObject.class);
    final ArgumentCaptor<ImmutableList> scheduleCaptor =
      ArgumentCaptor.forClass(ImmutableList.class);

    verify(scheduler, times(1)).getSharedExecutor();
    verify(scheduler, times(0)).reportException(Matchers.<Throwable>any());
    verify(scheduler, atLeastOnce())
      .updateSchedule(snapshotCaptor.capture(), scheduleCaptor.capture());

    assertThat(snapshotCaptor.getAllValues()).containsExactlyElementsIn(
      Collections.nCopies(snapshotCaptor.getAllValues().size(), snapshot));

    assertThat(scheduleCaptor.getAllValues()).doesNotContain(null);

    verify(scheduler, times(1)).doneForNow();
  }

  @Test
  public void testCancellingExecution() throws InterruptedException {
    assertThat(solver.isComputing()).isFalse();
    when(scheduler.getSharedExecutor()).thenReturn(
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));
    final GlobalStateObject snapshot = simpleProblem(1);

    solver.problemChanged(snapshot);

    assertThat(solver.isComputing()).isTrue();
    Thread.sleep(10);
    solver.cancel();

    while (solver.isComputing()) {
      Thread.sleep(10L);
    }

    verify(scheduler, times(1)).getSharedExecutor();
    verify(scheduler, times(0)).reportException(Matchers.<Throwable>any());
    verify(scheduler, times(1)).doneForNow();
  }

  @Test
  public void testRestartedExecution() throws InterruptedException {
    assertThat(solver.isComputing()).isFalse();
    when(scheduler.getSharedExecutor()).thenReturn(
      MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()));
    final GlobalStateObject snapshot1 = simpleProblem(1);
    solver.problemChanged(snapshot1);

    assertThat(solver.isComputing()).isTrue();

    Thread.sleep(10);
    final GlobalStateObject snapshot2 = simpleProblem(2);
    solver.problemChanged(snapshot2);

    while (solver.isComputing()) {
      Thread.sleep(10L);
    }

    final ArgumentCaptor<GlobalStateObject> snapshotCaptor =
      ArgumentCaptor.forClass(GlobalStateObject.class);
    final ArgumentCaptor<ImmutableList> scheduleCaptor =
      ArgumentCaptor.forClass(ImmutableList.class);

    verify(scheduler, times(2)).getSharedExecutor();
    verify(scheduler, times(0)).reportException(Matchers.<Throwable>any());
    verify(scheduler, atLeastOnce())
      .updateSchedule(snapshotCaptor.capture(), scheduleCaptor.capture());

    assertThat(scheduleCaptor.getAllValues()).doesNotContain(null);

    verify(scheduler, times(2)).doneForNow();
  }

  static GlobalStateObject simpleProblem(int n) {
    final GlobalStateObjectBuilder builder =
      GlobalStateObjectBuilder.globalBuilder();
    for (int i = 0; i < n; i++) {
      builder.addAvailableParcel(
        Parcel.builder(new Point(0, i), new Point(1, i)).build());
    }
    return builder
      .setPlaneTravelTimes(new Point(0, 0), new Point(10, 10))
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setRoute(ImmutableList.<Parcel>of())
        .build())
      .build();
  }

}
