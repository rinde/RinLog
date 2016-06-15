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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.optaplanner.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import org.optaplanner.core.impl.score.director.incremental.AbstractIncrementalScoreCalculator;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitType;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.geom.Point;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.PeekingIterator;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 *
 * @author Rinde van Lon
 */
public class ScoreCalculator
    extends AbstractIncrementalScoreCalculator<PDPSolution> {

  static final String NEXT_VISIT = "nextVisit";
  static final String VEHICLE = "vehicle";

  static final long MISSING_VISIT_PENALTY = 10L;
  static final long PARCEL_ORDER_PENALTY = 1L;

  long hardScore;
  long softScore;

  Object2LongMap<Visit> doneTimes;
  Object2LongMap<Visit> travelTimes;
  Object2LongMap<Visit> tardiness;

  Map<Parcel, Vehicle> pickupOwner;
  Map<Parcel, Vehicle> deliveryOwner;

  Object2LongMap<Vehicle> routeHardScores;
  Object2LongMap<Vehicle> routeSoftScores;

  Set<Vehicle> changedVehicles;
  ListMultimap<Vehicle, ParcelVisit> routes;

  long startTime;

  PDPSolution solution;

  Set<ParcelVisit> unplannedParcelVisits;

  public ScoreCalculator() {}

  @Override
  public void resetWorkingSolution(
      @SuppressWarnings("null") PDPSolution workingSolution) {
    System.out.println("resetWorkingSolution: \n" + workingSolution);
    solution = workingSolution;

    unplannedParcelVisits = new LinkedHashSet<>(workingSolution.parcelList);
    changedVehicles = new LinkedHashSet<>();
    routes = ArrayListMultimap.create();
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

    hardScore = -MISSING_VISIT_PENALTY * unplannedParcelVisits.size();
    softScore = 0;
    for (final Vehicle v : workingSolution.vehicleList) {
      updateCurRoute(v);
      updateRoute(v, v.getNextVisit());
    }

    System.out.println(" > " + softScore);
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
    final Visit visit = (Visit) entity;
    if (visit.getVehicle() == null) {
      return;
    }
    changedVehicles.add(visit.getVehicle());
  }

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    final Visit visit = (Visit) entity;
    if (visit.getVehicle() == null) {
      return;
    }
    changedVehicles.add(visit.getVehicle());
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
    if (!changedVehicles.isEmpty()) {

      final List<ParcelVisit> firstDiffs =
        new ArrayList<>(changedVehicles.size());
      Iterator<Vehicle> changesIt = changedVehicles.iterator();
      for (int i = 0; i < changedVehicles.size(); i++) {
        firstDiffs.add(updateRouteRemovals(changesIt.next()));
      }
      changesIt = changedVehicles.iterator();
      for (int i = 0; i < changedVehicles.size(); i++) {
        updateRoute(changesIt.next(), firstDiffs.get(i));
      }
      changedVehicles.clear();
    }
    System.out.println("*** calculate score ***");
    System.out.println(solution);
    System.out.println("Score " + hardScore + "/" + softScore);
    System.out.println("***********************");
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

  List<ParcelVisit> updateCurRoute(Vehicle v) {
    final List<ParcelVisit> newRoute = new ArrayList<>();
    ParcelVisit cur = v.getNextVisit();
    while (cur != null) {
      newRoute.add(cur);
      cur = cur.getNextVisit();
    }
    System.out.println("old route: " + routes.get(v));
    System.out.println("new route: " + newRoute);
    routes.replaceValues(v, newRoute);
    return newRoute;
  }

  // returns the first ParcelVisit that needs to be inserted
  @Nullable
  ParcelVisit updateRouteRemovals(Vehicle v) {
    final List<ParcelVisit> prevRoute = ImmutableList.copyOf(routes.get(v));
    final List<ParcelVisit> newRoute = updateCurRoute(v);

    final PeekingIterator<ParcelVisit> prevIt =
      Iterators.peekingIterator(prevRoute.iterator());
    final PeekingIterator<ParcelVisit> newIt =
      Iterators.peekingIterator(newRoute.iterator());

    while (prevIt.hasNext() && newIt.hasNext()
      && prevIt.peek().equals(newIt.peek())) {
      // advance both iterators until we are at the position of the first
      // difference
      prevIt.next();
      newIt.next();
    }

    while (prevIt.hasNext()) {
      remove(prevIt.next());
    }
    if (newIt.hasNext()) {
      return newIt.peek();
    } else {
      return null;
    }
  }

  void updateRoute(Vehicle v, @Nullable ParcelVisit firstDiff) {
    System.out.println("update, " + firstDiff);
    ParcelVisit c = firstDiff;
    while (c != null) {
      insert(c);
      c = c.getNextVisit();
    }

    // hard constraints
    hardScore -= routeHardScores.getLong(v);

    long routeHardScore = 0L;
    if (v.getDestination().isPresent()) {
      final ParcelVisit next = v.getNextVisit();
      if (next == null || !next.getParcel().equals(v.getDestination().get())) {
        routeHardScore -= 1L;
      }
    }
    final Set<Parcel> deliveryRequired = new LinkedHashSet<>();
    deliveryRequired.addAll(v.getContents());

    // final long beforeSoftScore = softScore;

    for (final ParcelVisit pv : routes.get(v)) {
      // check hard constraints
      if (deliveryRequired.contains(pv.getParcel())) {
        // it needs to be delivered
        if (pv.getVisitType() == VisitType.DELIVER) {
          deliveryRequired.remove(pv.getParcel());
        } else {
          routeHardScore -= PARCEL_ORDER_PENALTY;
        }
      } else {
        // it needs to be picked up
        if (pv.getVisitType() == VisitType.PICKUP) {
          deliveryRequired.add(pv.getParcel());
        } else {
          routeHardScore -= PARCEL_ORDER_PENALTY;
        }
      }
    }

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
    System.out.println("remove " + pv);
    hardScore -= MISSING_VISIT_PENALTY;
    unplannedParcelVisits.add(pv);
    softScore += travelTimes.getLong(pv);
    softScore += tardiness.getLong(pv);
  }

  void insert(ParcelVisit pv) {
    System.out.println("insert " + pv);
    hardScore += MISSING_VISIT_PENALTY;
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
