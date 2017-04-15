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

import javax.measure.unit.SI;

import org.junit.Test;

import com.github.rinde.logistics.pdptw.solver.optaplanner.OptaplannerSolvers.OptaplannerSolver;
import com.github.rinde.logistics.pdptw.solver.optaplanner.OptaplannerSolvers.Validator;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.road.RoadModelSnapshotTestUtil;
import com.github.rinde.rinsim.geom.GeomHeuristic;
import com.github.rinde.rinsim.geom.GeomHeuristics;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.MultiAttributeData;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.TableGraph;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class SolverTest {

  static final MultiAttributeData DATA_L1 =
    MultiAttributeData.builder().setMaxSpeed(50d).setLength(1).build();
  static final MultiAttributeData DATA_L2 =
    MultiAttributeData.builder().setMaxSpeed(50d).setLength(2).build();
  static final MultiAttributeData DATA_L3 =
    MultiAttributeData.builder().setMaxSpeed(50d).setLength(3).build();
  static final MultiAttributeData DATA_L5 =
    MultiAttributeData.builder().setMaxSpeed(50d).setLength(5).build();

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
      .setPlaneTravelTimes(new Point(0, 0), new Point(10, 10))
      .build();

    compare(optaplannerSolver, gso, GeomHeuristics.euclidean());
  }

  @Test
  public void testWithGraph() {
    // A square graph over point (0,0), (5,0), (0,5) and (5,5).
    // Extra node (2,0) and (3,0) are included for the parcels.
    final Graph<MultiAttributeData> graph = new TableGraph<>();
    graph.addConnection(new Point(0, 0), new Point(0, 5), DATA_L5);
    graph.addConnection(new Point(0, 5), new Point(0, 0), DATA_L5);
    graph.addConnection(new Point(0, 5), new Point(5, 5), DATA_L5);
    graph.addConnection(new Point(5, 5), new Point(0, 5), DATA_L5);
    graph.addConnection(new Point(5, 5), new Point(5, 0), DATA_L5);
    graph.addConnection(new Point(5, 0), new Point(5, 5), DATA_L5);
    graph.addConnection(new Point(0, 0), new Point(2, 0), DATA_L2);
    graph.addConnection(new Point(2, 0), new Point(0, 0), DATA_L2);
    graph.addConnection(new Point(2, 0), new Point(3, 0), DATA_L1);
    graph.addConnection(new Point(3, 0), new Point(2, 0), DATA_L1);
    graph.addConnection(new Point(3, 0), new Point(5, 0), DATA_L2);
    graph.addConnection(new Point(5, 0), new Point(3, 0), DATA_L2);

    final OptaplannerSolver optaplannerSolver =
      ((Validator) OptaplannerSolvers.builder()
        .withSolverHeuristic(GeomHeuristics.time(50d))
        .withValidated(true)
        .withUnimprovedMsLimit(10L)
        .withObjectiveFunction(Gendreau06ObjectiveFunction.instance(50d))
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
      .setSnapshot(RoadModelSnapshotTestUtil.createGraphRoadModelSnapshot(graph,
        SI.KILOMETER))
      .build();

    compare(optaplannerSolver, gso, GeomHeuristics.time(50d));
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
      .setPlaneTravelTimes(new Point(0, 0), new Point(10, 10))
      .build();

    final OptaplannerSolver optaplannerSolver =
      ((Validator) OptaplannerSolvers.builder()
        .withValidated(true)
        .withUnimprovedMsLimit(10L)
        .withName("test")
        .withObjectiveFunction(Gendreau06ObjectiveFunction.instance())
        .buildSolverSupplier()
        .get(123L)).solver;

    compare(optaplannerSolver, gso, GeomHeuristics.euclidean());
  }

  static void compare(OptaplannerSolver solv, GlobalStateObject gso,
      GeomHeuristic heuristic) {
    ImmutableList<ImmutableList<Parcel>> schedule;
    try {
      schedule = solv.solve(gso);
    } catch (final InterruptedException e) {
      throw new IllegalStateException(e);
    }
    final double optaPlannerCost = solv.getSoftScore() / -1000000d;

    final double rinSimCost = Gendreau06ObjectiveFunction.instance(50d)
      .computeCost(Solvers.computeStats(gso, schedule, heuristic)) * 60000d;

    System.out.println("--------------------------------");
    System.out.println("OptaPlannerCost = " + optaPlannerCost);
    System.out.println("RinSimCost      = " + rinSimCost);
    System.out.println("--------------------------------");

    assertThat(optaPlannerCost)
      .named("OptaPlanner cost")
      .isWithin(.0001)
      .of(rinSimCost);
  }

}
