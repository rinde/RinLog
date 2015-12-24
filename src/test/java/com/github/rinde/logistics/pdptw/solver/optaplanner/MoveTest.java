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

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;
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

    final MoveBetweenVehicles move = new MoveBetweenVehicles(pickupA, delivrA,
        vehicle1, pickupA);

    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);

    final MoveBetweenVehicles undo = move.createUndoMove(scoreDirector);

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

    final MoveBetweenVehicles move = new MoveBetweenVehicles(pickupA, delivrA,
        vehicle1, pickupA);

    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);

    final MoveBetweenVehicles undo = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(pickupB);

    undo.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);

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

    final MoveBetweenVehicles move = new MoveBetweenVehicles(pickupB, delivrB,
        vehicle1, delivrC);

    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);

    final MoveBetweenVehicles undo = move.createUndoMove(scoreDirector);

    move.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrC);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);

    undo.doMove(scoreDirector);
    assertThat(pickupA.getPreviousVisit()).isEqualTo(vehicle0);
    assertThat(pickupB.getPreviousVisit()).isEqualTo(pickupA);
    assertThat(delivrA.getPreviousVisit()).isEqualTo(pickupB);
    assertThat(delivrB.getPreviousVisit()).isEqualTo(delivrA);
    assertThat(pickupC.getPreviousVisit()).isEqualTo(vehicle1);
    assertThat(delivrC.getPreviousVisit()).isEqualTo(pickupC);
  }

  @SafeVarargs
  static PDPSolution create(ImmutableList<Parcel>... schedule) {
    final VehicleDTO vehicleDto = VehicleDTO.builder()
        .speed(1d)
        .build();
    final GlobalStateObjectBuilder b = GlobalStateObjectBuilder.globalBuilder();

    final Set<Parcel> available = new LinkedHashSet<>();
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
    return OptaplannerSolver.convert(b.build());
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
