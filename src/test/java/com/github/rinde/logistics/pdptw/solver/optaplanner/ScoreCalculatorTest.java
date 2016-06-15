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
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.GlobalStateObjectBuilder;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class ScoreCalculatorTest {

  static final Parcel A =
    Parcel.builder(new Point(0, 0), new Point(2, 0)).toString("A").build();
  static final Parcel B =
    Parcel.builder(new Point(3, 0), new Point(2, 0)).toString("B").build();

  @Test
  public void calcTest() {

    assertThat(getScoreV1().getHardScore()).isEqualTo(-40L);
    assertThat(getScoreV1().getSoftScore()).isEqualTo(0L);
    assertThat(getScoreV1(A).getHardScore()).isEqualTo(-31L);
    assertThat(getScoreV1(B).getHardScore()).isEqualTo(-31L);
    assertThat(getScoreV1(A, B).getHardScore()).isEqualTo(-22L);
    assertThat(getScoreV1(A, B, A).getHardScore()).isEqualTo(-11L);
    assertThat(getScoreV1(A, B, A, B).getHardScore()).isEqualTo(0L);
    assertThat(getScoreV1(B, A, A, B).getHardScore()).isEqualTo(0L);

  }

  static HardSoftLongScore getScoreV1(Parcel... parcels) {
    final ScoreCalculator sc = new ScoreCalculator();

    sc.resetWorkingSolution(asPDPSolutionV1(parcels));
    return sc.calculateScore();
  }

  static PDPSolution asPDPSolutionV1(Parcel... parcels) {
    final GlobalStateObject gso = GlobalStateObjectBuilder.globalBuilder()
      .addAvailableParcels(A, B)
      .addVehicle(GlobalStateObjectBuilder.vehicleBuilder()
        .setRoute(ImmutableList.copyOf(parcels))
        .build())
      .buildUnsafe();
    return OptaplannerSolvers.convert(gso);
  }

}
