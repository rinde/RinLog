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

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.domain.solution.descriptor.SolutionDescriptor;
import org.optaplanner.core.impl.score.director.ScoreDirector;
import org.optaplanner.core.impl.score.director.incremental.IncrementalScoreDirector;
import org.optaplanner.core.impl.score.director.incremental.IncrementalScoreDirectorFactory;

import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class MoveTest {

  Parcel A = Parcel.builder(new Point(0, 0), new Point(0, 1))
    .toString("A")
    .build();
  Parcel B = Parcel.builder(new Point(1, 0), new Point(1, 1))
    .toString("B")
    .build();
  Parcel C = Parcel.builder(new Point(2, 0), new Point(2, 1))
    .toString("C")
    .build();
  Parcel D = Parcel.builder(new Point(3, 0), new Point(3, 1))
    .toString("D")
    .build();

  @Test
  public void test() {
    final PDPSolution sol = create(vehicle(A, A), vehicle());

    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final Vehicle vehicle0 = sol.vehicleList.get(0);
    final Vehicle vehicle1 = sol.vehicleList.get(1);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    final MovePair move =
      MovePair.create(pickupA, delivrA,
        vehicle1, pickupA);

    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);

    final MovePair undo = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);

    undo.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
  }

  @Test
  public void test2() {
    final PDPSolution sol = create(vehicle(A, B, A, B), vehicle());

    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final ParcelVisit pickupB = sol.parcelList.get(2);
    final ParcelVisit delivrB = sol.parcelList.get(3);

    final Vehicle vehicle0 = sol.vehicleList.get(0);
    final Vehicle vehicle1 = sol.vehicleList.get(1);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);
    final HardSoftLongScore originalScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    final MovePair move =
      MovePair.create(pickupA, delivrA,
        vehicle1, pickupA);

    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);

    final MovePair undo = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);

    final HardSoftLongScore incrChangedScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    scoreDirector.setWorkingSolution(sol);
    final HardSoftLongScore directChangedScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    undo.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);

    final HardSoftLongScore scoreAfterUndo =
      (HardSoftLongScore) scoreDirector.calculateScore();

    assertThat(incrChangedScore).isNotEqualTo(originalScore);
    assertThat(directChangedScore).isEqualTo(incrChangedScore);
    assertThat(scoreAfterUndo).isEqualTo(originalScore);
  }

  @Test
  public void test3() {
    final PDPSolution sol = create(vehicle(A, B, A, B), vehicle(C, C));

    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final ParcelVisit pickupB = sol.parcelList.get(2);
    final ParcelVisit delivrB = sol.parcelList.get(3);
    final ParcelVisit pickupC = sol.parcelList.get(4);
    final ParcelVisit delivrC = sol.parcelList.get(5);

    final Vehicle vehicle0 = sol.vehicleList.get(0);
    final Vehicle vehicle1 = sol.vehicleList.get(1);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    final HardSoftLongScore originalScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    final MovePair move =
      MovePair.create(pickupB, delivrB,
        vehicle1, delivrC);

    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);

    final MovePair undo = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrC);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);
    final HardSoftLongScore incrChangedScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    scoreDirector.setWorkingSolution(sol);
    final HardSoftLongScore directChangedScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    undo.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);

    final HardSoftLongScore afterUndoScore =
      (HardSoftLongScore) scoreDirector.calculateScore();

    assertThat(incrChangedScore).isNotEqualTo(originalScore);
    assertThat(directChangedScore).isEqualTo(incrChangedScore);
    assertThat(afterUndoScore).isEqualTo(originalScore);
  }

  @Test
  public void reversedTest() {
    final PDPSolution sol = create(vehicle(A, A), vehicle());

    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);

    final Vehicle v0 = sol.vehicleList.get(0);
    final Vehicle v1 = sol.vehicleList.get(1);

    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    // reverse route
    v0.setNextVisit(delivrA);
    delivrA.setPreviousVisit(v0);
    delivrA.setNextVisit(pickupA);
    pickupA.setPreviousVisit(delivrA);
    pickupA.setNextVisit(null);

    // route is now:
    // v0 -> DELIVER-A -> PICKUP-A
    // v1
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(v0);

    final MovePair move =
      MovePair.create(pickupA, delivrA, v1, v1);
    final MovePair undoMove = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);

    // route is now:
    // v0 ->
    // v1 -> PICKUP-A -> DELIVER-A
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(v1);
    assertThat(v0.getNextVisit()).isNull();

    undoMove.doMove(scoreDirector);
    // route is now:
    // v0 -> DELIVER-A -> PICKUP-A
    // v1
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(v0);
  }

  @Test
  public void reversedTest2() {
    final PDPSolution sol = create(vehicle(A, B, B, A, C, C), vehicle(D, D));

    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final ParcelVisit pickupB = sol.parcelList.get(2);
    final ParcelVisit delivrB = sol.parcelList.get(3);
    final ParcelVisit pickupC = sol.parcelList.get(4);
    final ParcelVisit delivrC = sol.parcelList.get(5);
    final ParcelVisit pickupD = sol.parcelList.get(6);
    final ParcelVisit delivrD = sol.parcelList.get(7);

    final Vehicle v0 = sol.vehicleList.get(0);
    final Vehicle v1 = sol.vehicleList.get(1);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    // reverse route
    v0.setNextVisit(delivrA);
    delivrA.setPreviousVisit(v0);
    delivrA.setNextVisit(pickupB);
    pickupB.setPreviousVisit(delivrA);
    pickupA.setPreviousVisit(delivrB);
    delivrB.setNextVisit(pickupA);
    pickupC.setPreviousVisit(pickupA);
    pickupA.setNextVisit(pickupC);

    // route is now:
    // v0 -> DELIVER-A -> PICKUP-B -> DELIVER-B -> PICKUP-A->PICKUP-C->DELIVR-C
    // v1 -> PICKUP-D -> DELIVER-D
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(v0);
    assertThat(delivrD.getPreviousVisit()).isEqualTo(pickupD);
    assertThat(pickupD.getPreviousVisit()).isEqualTo(v1);

    final MovePair move =
      MovePair.create(pickupA, delivrA, v1, delivrD);
    final MovePair undoMove = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);

    // route is now:
    // v0 -> PICKUP-B -> DELIVER-B -> PICKUP-C -> DELIVR-C
    // v1 -> PICKUP-A -> PICKUP-D -> DELIVER-D -> DELIVER-A
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(delivrB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(v0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(delivrD);
    assertThat(delivrD.getPreviousVisit()).isEqualTo(pickupD);
    assertThat(pickupD.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(v1);

    undoMove.doMove(scoreDirector);

    // route is now:
    // v0 -> DELIVER-A -> PICKUP-B -> DELIVER-B -> PICKUP-A->PICKUP-C->DELIVR-C
    // v1 -> PICKUP-D -> DELIVER-D
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(v0);
    assertThat(delivrD.getPreviousVisit()).isEqualTo(pickupD);
    assertThat(pickupD.getPreviousVisit()).isEqualTo(v1);
  }

  /**
   * Special case where the pickup and delivery of a parcel occur reversed in
   * the route and are connected (i.e. adjacent to each other in the route),
   * additionally there is a tail of parcels in the original route.
   */
  @Test
  public void testConnectedReversedWithTail() {
    final PDPSolution sol = create(vehicle(A, A, B, B), vehicle());

    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final ParcelVisit pickupB = sol.parcelList.get(2);
    final ParcelVisit delivrB = sol.parcelList.get(3);

    final Vehicle v0 = sol.vehicleList.get(0);
    final Vehicle v1 = sol.vehicleList.get(1);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    v0.setNextVisit(delivrA);
    delivrA.setPreviousVisit(v0);
    delivrA.setNextVisit(pickupA);
    pickupA.setPreviousVisit(delivrA);
    pickupA.setNextVisit(pickupB);
    pickupB.setPreviousVisit(pickupA);

    // reversed and connected A with tail B, route:
    // v0 -> DELIVER-A -> PICKUP-A -> PICKUP-B -> DELIVER-B
    // v1
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(v0);

    final MovePair move =
      MovePair.create(pickupA, delivrA, v1, v1);
    final MovePair undoMove = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);

    // route:
    // v0 -> PICKUP-B -> DELIVER-B
    // v1 -> PICKUP-A -> DELIVER-A
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(v0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(v1);

    undoMove.doMove(scoreDirector);
    // route:
    // v0 -> DELIVER-A -> PICKUP-A -> PICKUP-B -> DELIVER-B
    // v1
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(v0);
  }

  @Test
  public void testWithinVehicle() {
    final PDPSolution sol = create(vehicle(A, A, B, B));
    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final ParcelVisit pickupB = sol.parcelList.get(2);
    final ParcelVisit delivrB = sol.parcelList.get(3);
    final Vehicle vehicle0 = sol.vehicleList.get(0);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    final MovePair move =
      MovePair.create(pickupA, delivrA, delivrB, delivrB);

    // A, A, B, B
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);

    final MovePair undo = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);
    // B, B, A, A
    assertThat(pickupB.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(delivrB);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);

    undo.doMove(scoreDirector);
    // A, A, B, B
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);
  }

  @Test
  public void testInsertion() {
    final PDPSolution sol =
      createWithUnassigned(ImmutableList.of(A, A, B, B), vehicle());
    final ParcelVisit pickupA = sol.parcelList.get(0);
    final ParcelVisit delivrA = sol.parcelList.get(1);
    final ParcelVisit pickupB = sol.parcelList.get(2);
    final ParcelVisit delivrB = sol.parcelList.get(3);
    final Vehicle vehicle0 = sol.vehicleList.get(0);
    final ScoreDirector scoreDirector = createScoreDirector();
    scoreDirector.setWorkingSolution(sol);

    final MovePair move =
      MovePair.create(pickupA, delivrA, vehicle0, vehicle0);
    final MovePair undo = move.createUndoMove(scoreDirector);

    assertThat(pickupA.getPreviousVisit()).isNull();
    assertThat(delivrA.getPreviousVisit()).isNull();

    // A, A
    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);

    undo.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isNull();
    assertThat(delivrA.getPreviousVisit()).isNull();

    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);

    final MovePair move2 =
      MovePair.create(pickupB, delivrB, vehicle0, delivrA);
    final MovePair undo2 = move2.createUndoMove(scoreDirector);

    // B, A, A, B
    move2.doMove(scoreDirector);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);

    System.out.println();
    // A, A
    undo2.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isNull();
    assertThat(delivrB.getPreviousVisit()).isNull();

  }

  @SafeVarargs
  static PDPSolution create(ImmutableList<Parcel>... schedule) {
    return createWithUnassigned(ImmutableList.<Parcel>of(), schedule);
  }

  @SafeVarargs
  static PDPSolution createWithUnassigned(ImmutableList<Parcel> unassigned,
      ImmutableList<Parcel>... schedule) {
    final VehicleDTO vehicleDto = VehicleDTO.builder()
      .speed(1d)
      .build();
    final GlobalStateObjectBuilder b = GlobalStateObjectBuilder.globalBuilder();

    final Set<Parcel> available = new LinkedHashSet<>();
    available.addAll(unassigned);
    for (final ImmutableList<Parcel> v : schedule) {
      available.addAll(v);
    }

    b.addAvailableParcels(available);

    for (final ImmutableList<Parcel> v : schedule) {
      b.addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setVehicleDTO(vehicleDto)
        .setRoute(v)
        .build());
    }
    return OptaplannerSolvers.convert(b.build());
  }

  static ImmutableList<Parcel> vehicle(Parcel... route) {
    return ImmutableList.copyOf(route);
  }

  static ScoreDirector createScoreDirector() {
    final IncrementalScoreDirectorFactory scoreDirectorFactory =
      new IncrementalScoreDirectorFactory(ScoreCalculator.class);
    scoreDirectorFactory.setSolutionDescriptor(
      SolutionDescriptor.buildSolutionDescriptor(PDPSolution.class,
        ParcelVisit.class, Visit.class));
    return new IncrementalScoreDirector(
      scoreDirectorFactory, false, new ScoreCalculator());
  }
}
