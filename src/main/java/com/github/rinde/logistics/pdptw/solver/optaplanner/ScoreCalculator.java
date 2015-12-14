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

import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.score.director.incremental.AbstractIncrementalScoreCalculator;

import com.github.rinde.rinsim.geom.Point;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 *
 * @author Rinde van Lon
 */
public class ScoreCalculator
    extends AbstractIncrementalScoreCalculator<PDPSolution> {

  long hardScore;
  long softScore;

  Object2LongMap<ParcelVisit> parcelDoneTimes;
  Object2LongMap<ParcelVisit> parcelTravelTimes;
  Object2LongMap<ParcelVisit> parcelTardiness;
  Object2LongMap<Vehicle> vehicleOverTime;

  @Override
  public void resetWorkingSolution(PDPSolution workingSolution) {
    System.out.println("resetWorkingSolution: " + workingSolution);

    final int numVisits = workingSolution.parcelList.size();
    final int numVehicles = workingSolution.vehicleList.size();
    parcelDoneTimes = new Object2LongOpenHashMap<>(numVisits);
    parcelTravelTimes = new Object2LongOpenHashMap<>(numVisits);
    parcelTardiness = new Object2LongOpenHashMap<>(numVisits);
    vehicleOverTime = new Object2LongOpenHashMap<>(numVehicles);

    for (final Vehicle v : workingSolution.vehicleList) {
      long currentTime = workingSolution.startTime;

      ParcelVisit pv = v.getNextVisit();
      Point currentPos = v.getPosition();

      while (pv != null) {
        final Point newPos = pv.getPosition();

        // compute travel time from current pos to parcel pos
        final long tt = v.computeTravelTime(currentPos, newPos);
        currentTime += tt;
        parcelTravelTimes.put(pv, tt);

        // compute tardiness
        currentTime = pv.computeServiceStartTime(currentTime);
        parcelTardiness.put(pv, pv.computeTardiness(currentTime));

        // compute time when servicing of this parcel is done
        currentTime += pv.getServiceDuration();
        parcelDoneTimes.put(pv, currentTime);

        // update variables for next step
        currentPos = newPos;
        pv = pv.getNextVisit();
      }

      final long depotTT =
        v.computeTravelTime(currentPos, v.getDepotLocation());
      currentTime += depotTT;

      final long depotTardiness = v.computeDepotTardiness(currentTime);

      // TODO where to store depot values?

    }

    // TODO what about constraint violations?

    // parcelArrivalTimes.g

  }

  @Override
  public void beforeEntityAdded(Object entity) {
    System.out.println("beforeEntityAdded: " + entity);

  }

  @Override
  public void afterEntityAdded(Object entity) {
    System.out.println("afterEntityAdded: " + entity);

  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    System.out.println("beforeVariableChanged: " + entity);
    System.out.println(" > next:" + ((Visit) entity).getNextVisit());
  }

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    System.out.println("afterVariableChanged: " + entity);
    System.out.println(" > next:" + ((Visit) entity).getNextVisit());

  }

  @Override
  public void beforeEntityRemoved(Object entity) {
    System.out.println("beforeEntityRemoved: " + entity);

  }

  @Override
  public void afterEntityRemoved(Object entity) {
    System.out.println("afterEntityRemoved: " + entity);

  }

  @Override
  public Score calculateScore() {
    System.out.println("***calculate score***");
    // TODO Auto-generated method stub
    return HardSoftLongScore.valueOf(0, 0);
  }

}
