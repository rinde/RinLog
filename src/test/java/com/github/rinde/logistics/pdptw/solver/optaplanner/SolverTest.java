/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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

import org.junit.Test;

import com.github.rinde.logistics.pdptw.solver.optaplanner.OptaplannerSolvers.OptaplannerSolver;
import com.github.rinde.logistics.pdptw.solver.optaplanner.OptaplannerSolvers.Validator;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class SolverTest {

  @Test
  public void test() {
    final OptaplannerSolver optaplannerSolver =
      ((Validator) OptaplannerSolvers.builder()
          .withValidated(true)
          .withUnimprovedMsLimit(10L)
          .withObjectiveFunction(Gendreau06ObjectiveFunction.instance())
          .withName("test")
          .buildSolverSupplier()
          .get(123L)).solver;

    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
        .addAvailableParcel(
          Parcel.builder(new Point(0, 0), new Point(2, 0)).build())
        .addAvailableParcel(
          Parcel.builder(new Point(3, 0), new Point(2, 0)).build())
        .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
            .setLocation(new Point(5, 5))
            .setRoute(ImmutableList.<Parcel>of())
            .build())
        .build();

    compare(optaplannerSolver, gso);
  }

  @Test
  public void testPartiallyLoadedParcel() {
    final Parcel A = Parcel.builder(new Point(5, 5), new Point(2, 0))
        .serviceDuration(180000L)
        .build();

    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
        .addAvailableParcel(A)
        .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
            .setLocation(new Point(5, 5))
            .setRoute(ImmutableList.<Parcel>of(A, A))
            .setRemainingServiceTime(120000L)
            .setDestination(A)
            .build())
        .build();

    final OptaplannerSolver optaplannerSolver =
      ((Validator) OptaplannerSolvers.builder()
          .withValidated(true)
          .withUnimprovedMsLimit(10L)
          .withName("test")
          .withObjectiveFunction(Gendreau06ObjectiveFunction.instance())
          .buildSolverSupplier()
          .get(123L)).solver;

    compare(optaplannerSolver, gso);
  }

  static void compare(OptaplannerSolver solv, GlobalStateObject gso) {
    ImmutableList<ImmutableList<Parcel>> schedule;
    try {
      schedule = solv.solve(gso);
    } catch (final InterruptedException e) {
      throw new IllegalStateException(e);
    }
    final double optaPlannerCost = solv.getSoftScore() / -1000000d;

    final double rinSimCost = Gendreau06ObjectiveFunction.instance(50d)
        .computeCost(Solvers.computeStats(gso, schedule)) * 60000d;

    assertThat(optaPlannerCost).isWithin(.0001).of(rinSimCost);
  }

}
