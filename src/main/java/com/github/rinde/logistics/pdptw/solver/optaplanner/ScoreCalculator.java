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

import static com.google.common.base.Verify.verifyNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.score.director.incremental.AbstractIncrementalScoreCalculator;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitType;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Strings;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

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

  Object2LongMap<Visit> doneTimes;
  Object2LongMap<Visit> travelTimes;
  Object2LongMap<Visit> tardiness;

  Map<Parcel, Vehicle> pickupOwner;
  Map<Parcel, Vehicle> deliveryOwner;

  Object2LongMap<Vehicle> routeHardScores;
  Object2LongMap<Vehicle> routeSoftScores;

  SetMultimap<Vehicle, Visit> changes;

  long startTime;

  PDPSolution solution;

  Set<ParcelVisit> unplannedParcelVisits;

  @Override
  public void resetWorkingSolution(PDPSolution workingSolution) {
    // System.out.println("resetWorkingSolution: \n" + workingSolution);
    solution = workingSolution;

    unplannedParcelVisits = new LinkedHashSet<>(workingSolution.parcelList);
    changes = LinkedHashMultimap.create();
    routeHardScores = new Object2LongOpenHashMap<>();
    routeSoftScores = new Object2LongOpenHashMap<>();
    startTime = workingSolution.getStartTime();

    final int numVisits = workingSolution.parcelList.size();
    final int numVehicles = workingSolution.vehicleList.size();
    final int size = numVisits + numVehicles;
    doneTimes = new Object2LongOpenHashMap<>(size);
    travelTimes = new Object2LongOpenHashMap<>(size);
    tardiness = new Object2LongOpenHashMap<>(size);

    pickupOwner = new LinkedHashMap<>(numVisits);
    deliveryOwner = new LinkedHashMap<>(numVisits);

    hardScore = 0;
    softScore = 0;
    for (final Vehicle v : workingSolution.vehicleList) {
      // final long currentTime = workingSolution.startTime;

      updateRoute(v, Collections.<Visit>emptyList());

      // ParcelVisit pv = v.getNextVisit();
      // while (pv != null) {
      //
      //
      // insert(pv);
      // pv = pv.getNextVisit();
      // }
      //
      // updateDepotScore(v);

    }
  }

  @Override
  public void beforeEntityAdded(Object entity) {
    System.out.println("beforeEntityAdded: " + entity);

  }

  @Override
  public void afterEntityAdded(Object entity) {
    System.out.println("afterEntityAdded: " + entity);

  }

  static String asString(Object entity, String variableName) {
    final StringBuilder sb = new StringBuilder()
        .append(entity)
        .append(" \tvariable: ")
        .append(variableName)
        .append(Strings.repeat(" ",
          ParcelVisit.PREV_VISIT.length() - variableName.length()))
        .append(" \tvalue: ");
    if (variableName.equals(NEXT_VISIT)) {
      sb.append(((Visit) entity).getNextVisit());
    } else if (variableName.equals(ParcelVisit.PREV_VISIT)) {
      sb.append(((ParcelVisit) entity).getPreviousVisit());
    } else if (variableName.equals(VEHICLE)) {
      sb.append(((ParcelVisit) entity).getVehicle());
    }
    return sb.toString();
  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    // System.out
    // .println("beforeVariableChanged: " + asString(entity, variableName));

    // System.out.println(" > nextVisit:" + ((Visit) entity).getNextVisit());
    // System.out.println(solution);

    final Visit visit = (Visit) entity;
    if (visit.getVehicle() == null) {
      return;
    }
    if (variableName.equals(NEXT_VISIT)) {
      if (visit.getNextVisit() == null) {
        // we can safely ignore this

      } else {
        // we have to remove this entity from the schedule
        // remove(visit.getNextVisit());
        changes.put(visit.getVehicle(), visit.getNextVisit());
      }
    } else if (variableName.equals(ParcelVisit.PREV_VISIT)) {

      final ParcelVisit pv = (ParcelVisit) visit;
      if (pv.getPreviousVisit() == null) {
        // we can safely ignore this

      } else {
        changes.put(pv.getVehicle(), pv.getPreviousVisit());
      }
    } else if (variableName.equals(VEHICLE)) {

      changes.put(visit.getVehicle(), visit);
    }
    // System.out.println("softScore: " + softScore);
  }

  static final String NEXT_VISIT = "nextVisit";
  static final String VEHICLE = "vehicle";

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    // System.out
    // .println("afterVariableChanged : " + asString(entity, variableName));
    // System.out.println(" > nextVisit:" + ((Visit) entity).getNextVisit());
    // System.out.println(solution);

    final Visit visit = (Visit) entity;
    if (visit.getVehicle() == null) {
      return;
    }
    if (variableName.equals(NEXT_VISIT)) {
      if (visit.getNextVisit() == null) {
        // we can ignore this
      } else {
        // we have to add this entity to the schedule

        // insert(visit.getNextVisit());
        changes.put(visit.getVehicle(), visit.getNextVisit());
      }
    } else if (variableName.equals(ParcelVisit.PREV_VISIT)) {
      final ParcelVisit pv = (ParcelVisit) visit;

      if (pv.getPreviousVisit() == null) {
        // we can ignore this
      } else {
        // we have to add this entity to the schedule

        // insert(visit.getNextVisit());
        changes.put(pv.getVehicle(), pv.getPreviousVisit());
      }
    } else if (variableName.equals(VEHICLE)) {

      changes.put(visit.getVehicle(), visit);
    }

    // System.out.println(solution);

    // System.out.println("softScore: " + softScore);
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
  public HardSoftLongScore calculateScore() {
    if (!changes.isEmpty()) {
      for (final Entry<Vehicle, Collection<Visit>> entry : changes.asMap()
          .entrySet()) {
        updateRoute(entry.getKey(), entry.getValue());
      }
    }
    changes.clear();
    // System.out.println("***calculate score ***" + hardScore + "/" +
    // softScore);
    return HardSoftLongScore.valueOf(hardScore, softScore);
  }

  public long getTardiness() {
    long sumTardiness = 0;
    for (final ParcelVisit pv : solution.parcelList) {
      sumTardiness += tardiness.getLong(pv);
    }
    return sumTardiness;
  }

  public long getTravelTime() {
    long travelTime = 0;
    for (final ParcelVisit pv : solution.parcelList) {
      travelTime += travelTimes.getLong(pv);
    }
    for (final Vehicle v : solution.vehicleList) {
      travelTime += travelTimes.getLong(v);
    }
    return travelTime;
  }

  public long getOvertime() {
    long sumOvertime = 0;
    for (final Vehicle v : solution.vehicleList) {
      sumOvertime += tardiness.getLong(v);
    }
    return sumOvertime;
  }

  void updateRoute(Vehicle v, Collection<Visit> visits) {
    hardScore -= routeHardScores.getLong(v);
    // softScore -= routeSoftScores.getLong(v);

    long routeHardScore = 0L;

    ParcelVisit cur = v.getNextVisit();
    if (v.getDestination().isPresent()) {
      if (cur == null
          || !cur.getParcel().equals(v.getDestination().get())) {
        routeHardScore -= 1L;
      }
    }

    final Set<Parcel> deliveryRequired = new LinkedHashSet<>();
    deliveryRequired.addAll(v.getContents());

    final long beforeSoftScore = softScore;

    while (cur != null) {
      // we know that visits is always a set
      // if (!visits.contains(cur)) {
      remove(cur);
      // }
      insert(cur);

      // check hard constraints
      if (deliveryRequired.contains(cur.getParcel())) {
        // it needs to be delivered
        if (cur.getVisitType() == VisitType.DELIVER) {
          deliveryRequired.remove(cur.getParcel());
        } else {
          routeHardScore -= 1L;
        }
      } else {
        // it needs to be picked up
        if (cur.getVisitType() == VisitType.PICKUP) {
          deliveryRequired.add(cur.getParcel());
        } else {
          routeHardScore -= 1L;
        }
      }

      cur = cur.getNextVisit();
    }

    final long routeSoftScore = beforeSoftScore - softScore;
    routeSoftScores.put(v, routeSoftScore);

    // the number of parcels that are not delivered even though they should
    // have been is used as a hard constraint violation
    routeHardScore -= deliveryRequired.size();

    routeHardScores.put(v, routeHardScore);
    hardScore += routeHardScore;

    updateDepotScore(v);
  }

  void updateDepotScore(Vehicle v) {
    softScore += tardiness.getLong(v);
    softScore += travelTimes.getLong(v);

    final ParcelVisit lastStop = v.getLastVisit();

    // if (v.getRemainingServiceTime() > 0) {
    // checkState(lastStop != null);
    // }

    final Point fromPos =
      lastStop == null ? v.getPosition() : lastStop.getPosition();
    long currentTime =
      lastStop == null ? startTime : doneTimes.getLong(lastStop);

    // travel to depot soft constraints
    final long depotTT = v.computeTravelTime(fromPos, v.getDepotLocation());
    currentTime += depotTT;
    softScore -= depotTT;
    travelTimes.put(v, depotTT);

    final long depotTardiness = v.computeDepotTardiness(currentTime);
    softScore -= depotTardiness;
    tardiness.put(v, depotTardiness);
  }

  void remove(ParcelVisit pv) {
    hardScore -= 1L;
    unplannedParcelVisits.add(pv);
    softScore += travelTimes.getLong(pv);
    softScore += tardiness.getLong(pv);
  }

  void insert(ParcelVisit pv) {
    hardScore += 1L;
    unplannedParcelVisits.remove(pv);

    final Vehicle vehicle = verifyNotNull(pv.getVehicle());
    final Visit prev = verifyNotNull(pv.getPreviousVisit());
    final Point prevPos = prev.getPosition();

    boolean firstAndServicing = false;
    long currentTime;
    if (prev.equals(vehicle)) {
      if (vehicle.getRemainingServiceTime() > 0) {
        firstAndServicing = true;
      }
      currentTime = startTime + vehicle.getRemainingServiceTime();
    } else {
      currentTime = doneTimes.getLong(prev);
    }

    if (firstAndServicing) {
      travelTimes.put(pv, 0L);

      final long tard =
        pv.computeTardiness(currentTime - pv.getServiceDuration());
      softScore -= tard;
      tardiness.put(pv, tard);

      doneTimes.put(pv, currentTime);
    } else {
      // compute travel time from current pos to parcel pos
      final Point newPos = pv.getPosition();
      final long tt = vehicle.computeTravelTime(prevPos, newPos);
      currentTime += tt;
      softScore -= tt;
      travelTimes.put(pv, tt);

      // compute tardiness
      currentTime = pv.computeServiceStartTime(currentTime);
      final long tard = pv.computeTardiness(currentTime);
      softScore -= tard;
      tardiness.put(pv, tard);

      // compute time when servicing of this parcel is done
      currentTime += pv.getServiceDuration();
      doneTimes.put(pv, currentTime);
    }
  }

}
