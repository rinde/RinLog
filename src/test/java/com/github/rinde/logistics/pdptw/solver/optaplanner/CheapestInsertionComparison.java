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

import org.junit.Test;

import com.github.rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class CheapestInsertionComparison {

  static final ObjectiveFunction objFunc =
    Gendreau06ObjectiveFunction.instance(50d);

  @Test
  public void test() throws InterruptedException {
    final Solver optaplannerCih =
      OptaplannerSolvers.builder()
        .withSolverXmlResource(
          "com/github/rinde/logistics/pdptw/solver/optaplanner/cheapestInsertion.xml")
        .withValidated(true)
        // .withUnimprovedMsLimit(1000L)
        .withObjectiveFunction(objFunc)
        .withName("test")
        .buildSolverSupplier()
        .get(123L);

    final Solver rindeCih =
      CheapestInsertionHeuristic.supplier(objFunc).get(123L);

    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
      .addAvailableParcel(
        Parcel.builder(new Point(0, 0), new Point(2, 0)).toString("A").build())
      .addAvailableParcel(
        Parcel.builder(new Point(3, 0), new Point(2, 1)).toString("B").build())
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of())
        .build())
      .build();

    final ImmutableList<ImmutableList<Parcel>> opResult =
      optaplannerCih.solve(gso);

    final ImmutableList<ImmutableList<Parcel>> rindeResult =
      rindeCih.solve(gso);

    System.out.println(opResult);
    System.out.println(rindeResult);

    System.out
      .println(objFunc.computeCost(Solvers.computeStats(gso, opResult)));
    System.out
      .println(objFunc.computeCost(Solvers.computeStats(gso, rindeResult)));

    assertThat(opResult).isEqualTo(rindeResult);
  }

}
