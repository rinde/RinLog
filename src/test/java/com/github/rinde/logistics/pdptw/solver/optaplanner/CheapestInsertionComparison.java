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

  static final ObjectiveFunction OBJ_FUNC =
    Gendreau06ObjectiveFunction.instance(50d);

  static final Solver OP_CIH = OptaplannerSolvers.builder()
    .withSolverXmlResource(
      "com/github/rinde/logistics/pdptw/solver/optaplanner/cheapestInsertion.xml")
    .withValidated(true)
    .withObjectiveFunction(OBJ_FUNC)
    .withName("test")
    .buildSolverSupplier()
    .get(123L);

  static final Solver RIN_CIH =
    CheapestInsertionHeuristic.supplier(OBJ_FUNC).get(123L);

  static final Parcel A =
    Parcel.builder(new Point(0, 0), new Point(2, 0)).toString("A").build();
  static final Parcel B =
    Parcel.builder(new Point(0, 1), new Point(2, 1)).toString("B").build();
  static final Parcel C =
    Parcel.builder(new Point(0, 2), new Point(2, 2)).toString("C").build();
  static final Parcel D =
    Parcel.builder(new Point(0, 3), new Point(2, 3)).toString("D").build();
  static final Parcel E =
    Parcel.builder(new Point(0, 4), new Point(2, 4)).toString("E").build();
  static final Parcel F =
    Parcel.builder(new Point(0, 5), new Point(2, 5)).toString("F").build();

  @Test
  public void testSimple() throws InterruptedException {
    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
      .addAvailableParcels(A)
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of())
        .build())
      .build();

    final ImmutableList<ImmutableList<Parcel>> opResult =
      OP_CIH.solve(gso);

    final ImmutableList<ImmutableList<Parcel>> rindeResult =
      RIN_CIH.solve(gso);
    assertThat(opResult).isEqualTo(rindeResult);
  }

  @Test
  public void test5() throws InterruptedException {
    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
      .addAvailableParcels(A, B, C, D, E)
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of())
        .build())
      .build();

    final ImmutableList<ImmutableList<Parcel>> opResult =
      OP_CIH.solve(gso);

    final ImmutableList<ImmutableList<Parcel>> rindeResult =
      RIN_CIH.solve(gso);
    assertThat(opResult).isEqualTo(rindeResult);
  }

  @Test
  public void test5_2() throws InterruptedException {
    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
      .addAvailableParcels(A, B, C, D, E, F)
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of(A, B, A, B))
        .build())
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of(C, C))
        .build())
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of(D, D, E, E))
        .build())
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setLocation(new Point(5, 5))
        .setRoute(ImmutableList.<Parcel>of())
        .build())
      .build();

    final ImmutableList<ImmutableList<Parcel>> opResult =
      OP_CIH.solve(gso);
    final ImmutableList<ImmutableList<Parcel>> rindeResult =
      RIN_CIH.solve(gso);

    assertThat(opResult).isEqualTo(rindeResult);
  }
}
