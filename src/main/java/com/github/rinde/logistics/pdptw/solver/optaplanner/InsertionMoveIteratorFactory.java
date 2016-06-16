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
import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nullable;

import org.optaplanner.core.impl.heuristic.move.Move;
import org.optaplanner.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import org.optaplanner.core.impl.score.director.ScoreDirector;

import com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.VisitType;
import com.github.rinde.opt.localsearch.Insertions;
import com.google.common.base.Joiner;
import com.google.common.collect.AbstractIterator;

import it.unimi.dsi.fastutil.ints.IntList;

/**
 *
 * @author Rinde van Lon
 */
public class InsertionMoveIteratorFactory implements MoveIteratorFactory {

  public InsertionMoveIteratorFactory() {}

  @Override
  public long getSize(ScoreDirector scoreDirector) {

    final PDPSolution sol = (PDPSolution) scoreDirector.getWorkingSolution();
    if (sol.vehicleList.size() <= 1) {
      return 0;
    }
    return sol.parcelList.size() + sol.vehicleList.size();
  }

  @Override
  public Iterator<Move> createOriginalMoveIterator(
      ScoreDirector scoreDirector) {

    final PDPSolution sol = (PDPSolution) scoreDirector.getWorkingSolution();

    return new InsertionIterator(sol);
  }

  // first select Parcel pair (assigned/unassigned) -> walk over parcel list?
  // then select destination pair

  @Override
  public Iterator<Move> createRandomMoveIterator(ScoreDirector scoreDirector,
      Random workingRandom) {
    throw new UnsupportedOperationException();
    // return new RandomIterator((PDPSolution)
    // scoreDirector.getWorkingSolution(),
    // workingRandom);
  }

  static class InsertionIterator extends AbstractIterator<Move> {
    final PDPSolution solution;
    @Nullable
    ParcelVisit current;

    @Nullable
    Vehicle currentVehicle;
    List<ParcelVisit> currentRoute;

    Iterator<ParcelVisit> parcelIterator;
    Iterator<Vehicle> vehicleIterator;
    Iterator<IntList> insertionIterator;

    InsertionIterator(PDPSolution sol) {
      solution = sol;
      parcelIterator = solution.unassignedPickups.iterator();
    }

    @Override
    protected Move computeNext() {
      if (current != null && current.getPreviousVisit() != null) {
        solution.unassignedPickups.remove(current);
        parcelIterator = solution.unassignedPickups.iterator();
        System.out.println("SKIP");
        // current has been inserterd in the meantime so we have to skip it
        current = null;
      }

      if (!parcelIterator.hasNext() && current == null) {
        System.out.println("endOfData");
        return endOfData();
      }

      if (current == null) {
        // switch to new parcel
        current = parcelIterator.next();
        vehicleIterator = solution.vehicleList.iterator();
        currentVehicle = null;
      }

      if (currentVehicle == null && vehicleIterator.hasNext()) {
        currentVehicle = vehicleIterator.next();

        currentRoute = new ArrayList<>();
        ParcelVisit pv = currentVehicle.getNextVisit();
        while (pv != null) {
          currentRoute.add(pv);
          pv = pv.getNextVisit();
        }

        final int startIndex =
          currentVehicle.getDestination().isPresent() ? 1 : 0;
        insertionIterator = Insertions.insertionsIndexIterator(2,
          currentRoute.size(), startIndex);
      }

      final IntList insertionPoints = insertionIterator.next();
      final MovePair move = MovePair.create(current, current.getAssociation(),
        getPrev(insertionPoints.getInt(0)), getPrev(insertionPoints.getInt(1)));

      System.out.println(Joiner.on("-").join(current, current.getAssociation(),
        getPrev(insertionPoints.getInt(0)),
        getPrev(insertionPoints.getInt(1))));

      if (!insertionIterator.hasNext()) {
        currentVehicle = null;
        if (!vehicleIterator.hasNext()) {
          current = null;
        }
      }

      System.out.println(move);
      return move;
    }

    Visit getPrev(int index) {
      final int i = index - 1;
      if (i == -1) {
        return currentVehicle;
      } else {
        return currentRoute.get(i);
      }
    }
  }

  static class RandomIterator extends AbstractIterator<Move> {
    final PDPSolution solution;
    final Random rng;
    final List<ParcelVisit> movablePickups;
    final List<Visit> allTargets;

    RandomIterator(PDPSolution sol, Random r) {
      solution = sol;
      rng = r;

      allTargets = new ArrayList<>();
      allTargets.addAll(sol.parcelList);
      allTargets.addAll(sol.vehicleList);

      movablePickups = new ArrayList<>();
      for (final ParcelVisit pv : sol.parcelList) {
        if (pv.getVisitType() == VisitType.PICKUP
          && pv.getAssociation() != null
          && pv.getVehicle() != null
          && Objects.equals(pv.getVehicle(),
            pv.getAssociation().getVehicle())) {
          movablePickups.add(pv);
        }
      }
    }

    @Override
    protected Move computeNext() {
      if (movablePickups.isEmpty() || solution.vehicleList.size() <= 1) {
        return endOfData();
      }
      final ParcelVisit pickup =
        movablePickups.get(rng.nextInt(movablePickups.size()));
      final ParcelVisit delivery = verifyNotNull(pickup.getAssociation());
      Visit pickupTarget;
      do {
        pickupTarget = allTargets.get(rng.nextInt(allTargets.size()));
      } while (Objects.equals(pickupTarget.getVehicle(), pickup.getVehicle()));

      // the delivery target can only be placed somewhere after the pickup
      // target (including the pickup itself)
      final List<ParcelVisit> options = new ArrayList<>();
      options.add(pickup);
      ParcelVisit next = pickupTarget.getNextVisit();
      while (next != null) {
        options.add(next);
        next = next.getNextVisit();
      }

      final ParcelVisit deliverTarget =
        options.get(rng.nextInt(options.size()));

      return MovePair.create(pickup, delivery, pickupTarget,
        deliverTarget);
    }
  }
}
