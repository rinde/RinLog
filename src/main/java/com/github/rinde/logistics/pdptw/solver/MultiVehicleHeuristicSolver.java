/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds DistriNet, KU Leuven
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
package com.github.rinde.logistics.pdptw.solver;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.SolverValidator;
import com.github.rinde.rinsim.central.arrays.ArraysSolverValidator;
import com.github.rinde.rinsim.central.arrays.ArraysSolvers;
import com.github.rinde.rinsim.central.arrays.MultiVehicleArraysSolver;
import com.github.rinde.rinsim.central.arrays.MultiVehicleSolverAdapter;
import com.github.rinde.rinsim.central.arrays.SolutionObject;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.primitives.Ints;

/**
 * A heuristic implementation of the {@link MultiVehicleArraysSolver} interface.
 * 
 * @author Tony
 * 
 */
public class MultiVehicleHeuristicSolver implements MultiVehicleArraysSolver {

  private static final int TRAVEL_TIME_WEIGHT = 1;
  private static final int TARDINESS_WEIGHT = 1;
  private final boolean debug;
  private final boolean strictMode;

  private final RandomGenerator rand;
  private final int listLength;
  private final int maxNrOfNonImprovements;
  private SolutionObject[] sols;

  public MultiVehicleHeuristicSolver(RandomGenerator rand, int listLength,
      int maxNrOfNonImprovements) {
    this(rand, listLength, maxNrOfNonImprovements, false, false);
  }

  public MultiVehicleHeuristicSolver(RandomGenerator rand, int listLength,
      int maxNrOfNonImprovements, boolean debug, boolean strictMode) {
    this.rand = rand;
    this.listLength = listLength;
    this.maxNrOfNonImprovements = maxNrOfNonImprovements;
    this.debug = debug;
    this.strictMode = strictMode;
  }

  @Override
  public SolutionObject[] solve(int[][] travelTime, int[] releaseDates,
      int[] dueDates, int[][] servicePairs, int[] serviceTimes,
      int[][] vehicleTravelTimes, int[][] inventories,
      int[] remainingServiceTimes, int[] currentDestinations,
      @Nullable SolutionObject[] currentSolutions) {
    final int n = releaseDates.length;
    final int v = vehicleTravelTimes.length;

    /* Calculate useful data structures */
    final int[] pickupToDeliveryMap = new int[n];
    for (int i = 0; i < pickupToDeliveryMap.length; i++) {
      pickupToDeliveryMap[i] = -1;
    }
    final int[] deliveryToPickupMap = new int[n];
    for (int i = 0; i < deliveryToPickupMap.length; i++) {
      deliveryToPickupMap[i] = -1;
    }
    for (final int[] pair : servicePairs) {
      final int pickup = pair[0];
      final int delivery = pair[1];
      pickupToDeliveryMap[pickup] = delivery;
      deliveryToPickupMap[delivery] = pickup;
    }

    final int[] fixedVehicleAssignment = new int[n];
    for (int i = 0; i < n; i++) {
      // not fixed = -1
      fixedVehicleAssignment[i] = -1;
    }
    for (final int[] inventoryPair : inventories) {
      fixedVehicleAssignment[inventoryPair[1]] = inventoryPair[0];
    }
    for (int veh = 0; veh < v; veh++) {
      if (currentDestinations[veh] != 0) {
        fixedVehicleAssignment[currentDestinations[veh]] = veh;
      }
    }
    // --

    //
    //
    // /* Determine a random feasible vehicle assignment */
    // int[] initialVehicleAssignment =
    // randomFeasibleAssignment(n,v,fixedVehicleAssignment,pickupToDeliveryMap,deliveryToPickupMap,currentDestinations);
    //
    // /* Determine a random feasible permutation of orders */
    // final List<Integer> perm0 = generateFeasibleRandomPermutation(n,
    // servicePairs,currentDestinations);
    //
    // /* Construct a solution with this permutation and vehicle assignment */
    // final SolutionObject[] sol0 =
    // construct(n,v,intListToArray(perm0),initialVehicleAssignment,travelTime,
    // releaseDates, dueDates,
    // servicePairs, serviceTimes, vehicleTravelTimes, inventories,
    // remainingServiceTimes);
    //

    sols = solveWithLateAcceptance(n, v, travelTime, releaseDates, dueDates,
        servicePairs, serviceTimes, vehicleTravelTimes, inventories,
        remainingServiceTimes, currentDestinations, pickupToDeliveryMap,
        deliveryToPickupMap, fixedVehicleAssignment);
    return sols;
  }

  private SolutionObject[] solveWithLateAcceptance(int n, int v,
      int[][] travelTime, int[] releaseDates, int[] dueDates,
      int[][] servicePairs, int[] serviceTimes, int[][] vehicleTravelTimes,
      int[][] inventories, int[] remainingServiceTimes,
      int[] currentDestinations, int[] pickupToDeliveryMap,
      int[] deliveryToPickupMap, int[] fixedVehicleAssignment) {

    /* Determine a random feasible vehicle assignment */
    final int[] initialVehicleAssignment = randomFeasibleAssignment(n, v,
        fixedVehicleAssignment, pickupToDeliveryMap, deliveryToPickupMap,
        currentDestinations);

    /* Determine a random feasible permutation of orders */
    final List<Integer> perm0 = generateFeasibleRandomPermutation(n,
        servicePairs, currentDestinations, dueDates);

    /* Construct a solution with this permutation and vehicle assignment */
    final SolutionObject[] sol0 = construct(n, v, Ints.toArray(perm0),
        initialVehicleAssignment, travelTime, releaseDates, dueDates,
        servicePairs, serviceTimes, vehicleTravelTimes, inventories,
        remainingServiceTimes);

    // ADDED BY RINDE
    // only for checking feasibility of initial permutation
    if (strictMode) {
      ArraysSolverValidator.validateOutputs(sol0, travelTime, releaseDates,
          dueDates, servicePairs, serviceTimes, vehicleTravelTimes,
          inventories, remainingServiceTimes, currentDestinations);
    }
    // END ADDED BY RINDE

    final double obj0 = getTotalObjective(sol0);

    /* initialize LA list with initial objective value */
    final double[] laList = new double[listLength];
    for (int l = 0; l < listLength; l++) {
      laList[l] = obj0;
    }

    List<Integer> currentPerm = new ArrayList<Integer>(perm0);
    int[] currentVehicleAssignment = Arrays.copyOf(initialVehicleAssignment,
        initialVehicleAssignment.length);
    double currentObj = obj0;
    SolutionObject[] currentSol = sol0;

    // List<Integer> bestPerm = new ArrayList<Integer>(perm0);
    int[] bestVehicleAssignment = Arrays.copyOf(initialVehicleAssignment,
        initialVehicleAssignment.length);
    double bestObj = obj0;
    SolutionObject[] bestSol = sol0;

    int nrOfNonImprovements = 0;
    boolean stagnation = false;
    int it = 0;
    while (!stagnation) {
      if (debug) {
        if (it % 100 == 0) {
          System.out.println("[" + it + "] Current:\t " + (currentObj / 60d)
              + "\tbest:\t" + (bestObj / 60d));
        }
      }

      final List<Integer> newPerm = new ArrayList<Integer>(currentPerm);
      final int[] newVehicleAssignment = Arrays.copyOf(
          currentVehicleAssignment, currentVehicleAssignment.length);

      SolutionObject[] newSol = null;

      // move 1:change vehicle assignment
      if (newPerm.size() <= 4 || rand.nextBoolean()) {
        // System.out.println("VM");
        int ro = 1 + rand.nextInt(n - 2); // random order
        while (fixedVehicleAssignment[ro] != -1
        // if is fixed or its pickup is fixed
            || (deliveryToPickupMap[ro] != -1 && fixedVehicleAssignment[deliveryToPickupMap[ro]] != -1)) {
          ro = rand.nextInt(n); // new random order
        }
        final int rv = rand.nextInt(v); // random vehicle
        newVehicleAssignment[ro] = rv; // assign
        if (deliveryToPickupMap[ro] != -1) {
          newVehicleAssignment[deliveryToPickupMap[ro]] = rv; // pickup must
                                                              // have same
                                                              // vehicle as
                                                              // delivery
        }
        if (pickupToDeliveryMap[ro] != -1) {
          newVehicleAssignment[pickupToDeliveryMap[ro]] = rv; // delivery must
                                                              // have same
                                                              // vehicle as
                                                              // pickup
        }

        final int originalVehicle = currentVehicleAssignment[ro];

        // newSol =
        // construct(n,v,intListToArray(newPerm),newVehicleAssignment,travelTime,
        // releaseDates, dueDates,
        // servicePairs, serviceTimes, vehicleTravelTimes, inventories,
        // remainingServiceTimes);
        // delta eval
        newSol = copySolution(currentSol);
        final int[] newPermAr = Ints.toArray(newPerm);
        final SolutionObject newSolForOriginalVehicle = constructSingleVehicle(
            n, newPermAr, newVehicleAssignment, travelTime, releaseDates,
            dueDates, serviceTimes, vehicleTravelTimes, remainingServiceTimes,
            originalVehicle);
        newSol[originalVehicle] = newSolForOriginalVehicle;
        final SolutionObject newSolForNewVehicle = constructSingleVehicle(n,
            newPermAr, newVehicleAssignment, travelTime, releaseDates,
            dueDates, serviceTimes, vehicleTravelTimes, remainingServiceTimes,
            rv);
        newSol[rv] = newSolForNewVehicle;

        // for (int k = 0; k < dsol.length; k++) {
        // if (getTotalObjective(newSol)!=getTotalObjective(dsol)){
        // System.out.println("Difference in obj!");
        // for (int q = 0; q < dsol.length; q++) {
        // System.out.println(dsol[q]);
        // }
        // System.out.println("---");
        // for (int q = 0; q < newSol.length; q++) {
        // System.out.println(newSol[q]);
        // }
        // System.out.println();
        // }
        // }

      } else {
        // System.out.println("FM");
        final int[] elementLocations = new int[n];
        for (int i = 0; i < n; i++) {
          elementLocations[currentPerm.get(i)] = i;
        }
        if (rand.nextBoolean()) {
          // try all forward shifts
          boolean ok = false;
          int i = 0;
          int j = 0;
          do {
            ok = true;
            i = 1 + rand.nextInt(n - 3);
            final int delivery = pickupToDeliveryMap[currentPerm.get(i)];
            int deliveryLocation = n;
            if (delivery != -1) {
              deliveryLocation = elementLocations[delivery];
            }
            if (Math.min(deliveryLocation, n - 1) - (i + 1) <= 0) {
              ok = false;
              continue;
            }
            j = i + 1
                + rand.nextInt(Math.min(deliveryLocation, n - 1) - (i + 1));

          } while (!ok);

          final int el = newPerm.remove(i);
          newPerm.add(j, el);

          putFixedFirstLocationsAtTheBeginning(n, currentDestinations, newPerm);

          // delta eval
          newSol = copySolution(currentSol);
          final int veh = newVehicleAssignment[el];
          final SolutionObject newSolForVehicle = constructSingleVehicle(n,
              Ints.toArray(newPerm), newVehicleAssignment, travelTime,
              releaseDates, dueDates, serviceTimes, vehicleTravelTimes,
              remainingServiceTimes, veh);
          newSol[veh] = newSolForVehicle;

        } else {

          // try all backward shifts
          boolean ok = false;
          int i = 0;
          int j = 0;
          do {
            ok = true;
            i = 2 + rand.nextInt(n - 3);
            final int pickup = deliveryToPickupMap[currentPerm.get(i)];
            int pickupLocation = 0;
            if (pickup != -1) {
              pickupLocation = elementLocations[pickup];
            }
            if (i - Math.max(1, pickupLocation + 1) <= 0) {
              ok = false;
              continue;
            }
            j = Math.max(1, pickupLocation + 1)
                + rand.nextInt(i - Math.max(1, pickupLocation + 1));
          } while (!ok);

          final int el = newPerm.remove(i);
          newPerm.add(j, el);
          // System.out.println("BM "+el+" : "+i+"->"+j);

          putFixedFirstLocationsAtTheBeginning(n, currentDestinations, newPerm);

          // delta eval
          newSol = copySolution(currentSol);
          final int veh = newVehicleAssignment[el];
          final SolutionObject newSolForVehicle = constructSingleVehicle(n,
              Ints.toArray(newPerm), newVehicleAssignment, travelTime,
              releaseDates, dueDates, serviceTimes, vehicleTravelTimes,
              remainingServiceTimes, veh);
          newSol[veh] = newSolForVehicle;
        }
      }

      final double newObj = getTotalObjective(newSol);
      if (newObj < 0) {
        throw new RuntimeException("Found a negative objective value: "
            + newObj);
      }

      // ADDED BY RINDE
      // only for checking feasibility
      if (strictMode) {
        ArraysSolverValidator.validateOutputs(newSol, travelTime, releaseDates,
            dueDates, servicePairs, serviceTimes, vehicleTravelTimes,
            inventories, remainingServiceTimes, currentDestinations);
      }
      // END ADDED BY RINDE

      if (newObj <= laList[it % listLength]) {
        // accept
        currentPerm = newPerm;
        currentVehicleAssignment = newVehicleAssignment;
        currentObj = newObj;
        currentSol = newSol;

        // if (newObj < laList[it % L]) nrOfNonAcceptances=0;

        if (newObj < bestObj) {
          // better than best
          bestSol = copySolution(newSol);
          // bestPerm = new ArrayList<Integer>(newPerm);
          bestObj = newObj;
          bestVehicleAssignment = Arrays.copyOf(newVehicleAssignment,
              newVehicleAssignment.length);
          nrOfNonImprovements = 0;
          if (debug) {
            System.out.println("Found new best solution with objective: "
                + (newObj / 60d));
          }
        }

      }
      nrOfNonImprovements++;

      laList[it % listLength] = currentObj;
      it++;
      if (nrOfNonImprovements > maxNrOfNonImprovements) {
        stagnation = true;
      }
    }

    return bestSol;
  }

  public SolutionObject[] copySolution(SolutionObject[] sol) {
    final SolutionObject[] copy = new SolutionObject[sol.length];
    for (int i = 0; i < sol.length; i++) {
      copy[i] = copySolutionObject(sol[i]);
    }
    return copy;
  }

  private SolutionObject copySolutionObject(SolutionObject solutionObject) {
    final int[] route = Arrays.copyOf(solutionObject.route,
        solutionObject.route.length);
    final int[] arrivalTimes = Arrays.copyOf(solutionObject.arrivalTimes,
        solutionObject.arrivalTimes.length);
    final int objectiveValue = solutionObject.objectiveValue;
    checkArgument(objectiveValue >= 0);
    final SolutionObject copy = new SolutionObject(route, arrivalTimes,
        objectiveValue);
    return copy;
  }

  private void putFixedFirstLocationsAtTheBeginning(int n,
      int[] currentDestinations, final List<Integer> newPerm) {
    /* Fixed first locations at the beginning */
    for (int d = 0; d < currentDestinations.length; d++) {
      if (currentDestinations[d] != 0) {
        final int ffl = currentDestinations[d];
        int fflLoc = -1;
        for (int p = 1; p < n - 1; p++) {
          if (newPerm.get(p) == ffl) {
            fflLoc = p;
          }
        }
        newPerm.remove(fflLoc);
        newPerm.add(1, ffl);
      }
    }
  }

  /**
   * This method calculates the total objective cost from an array of
   * solutionObjects
   * @param sol
   * @return total objective cost
   */
  private double getTotalObjective(SolutionObject[] sol) {
    double obj = 0;
    for (int i = 0; i < sol.length; i++) {
      checkArgument(sol[i].objectiveValue >= 0,
          "Found negative objective value: " + sol[i].objectiveValue);
      obj += sol[i].objectiveValue;
    }
    return obj;
  }

  /**
   * Given a permutation of orders and an assignment of orders to vehicles, this
   * method constructs a Multivehicle solution.
   * @param n
   * @param v
   * @param permutation
   * @param vehicleAssignment
   * @param travelTime
   * @param releaseDates
   * @param dueDates
   * @param servicePairs
   * @param serviceTimes
   * @param vehicleTravelTimes
   * @param inventories
   * @param remainingServiceTimes
   * @return A multivehicle solution (SolutionObject[])
   */
  private SolutionObject[] construct(int n, int v, int[] permutation,
      int[] vehicleAssignment, int[][] travelTime, int[] releaseDates,
      int[] dueDates, int[][] servicePairs, int[] serviceTimes,
      int[][] vehicleTravelTimes, int[][] inventories,
      int[] remainingServiceTimes) {

    final SolutionObject[] solution = new SolutionObject[v];
    for (int j = 0; j < v; j++) { // for each vehicle j

      final SolutionObject solutionObject = constructSingleVehicle(n,
          permutation, vehicleAssignment, travelTime, releaseDates, dueDates,
          serviceTimes, vehicleTravelTimes, remainingServiceTimes, j);
      solution[j] = solutionObject;

    }
    return solution;
  }

  private SolutionObject constructSingleVehicle(int n, int[] permutation,
      int[] vehicleAssignment, int[][] travelTime, int[] releaseDates,
      int[] dueDates, int[] serviceTimes, int[][] vehicleTravelTimes,
      int[] remainingServiceTimes, int j) {
    /* calculate route */
    final List<Integer> route = getRouteForVehicle(n, permutation,
        vehicleAssignment, j);

    /* calculate arrival times + track total travel time */
    int totalTravelTime = 0;
    final int[] arrivalTimes = new int[route.size()];
    int previousT = Math.max(releaseDates[0], remainingServiceTimes[j]);
    arrivalTimes[0] = 0;// previousT;
    for (int i = 1; i < route.size(); i++) {
      final int next = route.get(i);
      int tt = 0;
      if (i == 1 /* && route.size()>2 */) {
        tt = vehicleTravelTimes[j][next];
      } else {
        tt = travelTime[route.get(i - 1)][next];
      }
      checkArgument(tt >= 0 && tt < Integer.MAX_VALUE,
          "Found invalid travel time: %s", tt);

      arrivalTimes[i] = Math.max(previousT + tt, releaseDates[next]);
      totalTravelTime += tt;

      previousT = arrivalTimes[i] + serviceTimes[next];
    }

    /* compute total tardiness */
    final int totalTardiness = ArraysSolvers.computeRouteTardiness(
        Ints.toArray(route), arrivalTimes, serviceTimes, dueDates,
        remainingServiceTimes[j]);

    // calculate objective value for vehicle j
    final int objectiveValue = totalTardiness * TARDINESS_WEIGHT
        + totalTravelTime * TRAVEL_TIME_WEIGHT;

    final SolutionObject solutionObject = new SolutionObject(
        Ints.toArray(route), arrivalTimes, objectiveValue);
    return solutionObject;
  }

  /**
   * This method calculates the route for a given vehicle.
   * @param n
   * @param permutation
   * @param vehicleAssignment
   * @param vehicle
   * @return Route
   */
  private List<Integer> getRouteForVehicle(int n, int[] permutation,
      int[] vehicleAssignment, int vehicle) {
    final List<Integer> route = new ArrayList<Integer>();
    route.add(0);
    for (final int i : permutation) {
      if (vehicleAssignment[i] == vehicle && i != 0 && i != n - 1) {
        route.add(i);
      }
    }
    route.add(n - 1);
    return route;
  }

  /**
   * Generates a feasible permutation for this problem. Feasible = respecting
   * the pickup and delivery pairs
   * @param n
   * @param servicePairs
   * @param fixedFirstLocation
   * @return
   */
  private List<Integer> generateFeasibleRandomPermutation(int n,
      int[][] servicePairs, int[] fixedFirstLocation, final int[] dueDates) {

    final List<Integer> elements = new ArrayList<Integer>();
    for (int i = 1; i < n - 1; i++) {
      elements.add(i);
    }
    // Collections.shuffle(elements, new RandomAdaptor(rand));
    Collections.sort(elements, new Comparator<Integer>() {
      @Override
      public int compare(Integer o1, Integer o2) {
        if (dueDates[o1] < dueDates[o2]) {
          return -1;
        } else if (dueDates[o1] > dueDates[o2]) {
          return 1;
        }
        return 0;
      }
    });
    elements.add(0, 0);
    elements.add(n - 1);

    /* Check and correct pickup-delivery order */
    for (final int[] pair : servicePairs) {
      final int pickup = pair[0];
      final int delivery = pair[1];

      int pickupLoc = -1;
      int deliveryLoc = -1;
      for (int i = 1; i < n - 1; i++) {
        if (elements.get(i) == pickup) {
          pickupLoc = i;
        }
        if (elements.get(i) == delivery) {
          deliveryLoc = i;
        }
      }
      if (pickupLoc > deliveryLoc) {
        // move pickup before delivery
        final int randomPosition = 1 + rand.nextInt(deliveryLoc);
        elements.remove(pickupLoc);
        elements.add(randomPosition, pickup);

      }

    }

    putFixedFirstLocationsAtTheBeginning(n, fixedFirstLocation, elements);

    return elements;
  }

  /**
   * Generates a random but feasible assignment of locations to vehicles.
   * Feasible = respecting the inventories.
   * @param n
   * @param v
   * @param vehicleTravelTimes
   * @param inventories
   * @param fixedVehicleAssignment
   * @param fixedFirstLocation
   * @return
   */
  private int[] randomFeasibleAssignment(int n, int v,
      int[] fixedVehicleAssignment, int[] pickupToDeliveryMap,
      int[] deliveryToPickupMap, int[] fixedFirstLocation) {

    final int[] assignment = new int[n];
    for (int i = 1; i < n - 1; i++) {
      // no assignment made = -1
      assignment[i] = -1;
    }

    // fixed first locations should be assigned to their vehicle
    for (int j = 0; j < v; j++) {
      if (fixedFirstLocation[j] != 0) {
        assignment[fixedFirstLocation[j]] = j;
        if (pickupToDeliveryMap[fixedFirstLocation[j]] != -1) {
          // delivery must have same assignment
          assignment[pickupToDeliveryMap[fixedFirstLocation[j]]] = j;
        }
      }
    }

    /* generate assignments */
    for (int i = 1; i < n - 1; i++) {
      if (assignment[i] != -1) {
        continue;
      }

      if (fixedVehicleAssignment[i] != -1) {
        // fixed assignment because of inventory
        assignment[i] = fixedVehicleAssignment[i];
      } else {
        if (pickupToDeliveryMap[i] != -1) {
          // random assignment
          assignment[i] = rand.nextInt(v);
          // delivery must have same assignment
          assignment[pickupToDeliveryMap[i]] = assignment[i];
        } else if (assignment[i] == -1) {
          // random assignment
          assignment[i] = rand.nextInt(v);
        }
      }
    }
    return assignment;
  }

  public static StochasticSupplier<Solver> supplier(int pListLength,
      int pMaxNrOfNonImprovements) {
    return new Supplier(pListLength, pMaxNrOfNonImprovements);
  }

  public static StochasticSupplier<Solver> supplier(int pListLength,
      int pMaxNrOfNonImprovements, boolean pDebug, boolean pStrictMode) {
    return new Supplier(pListLength, pMaxNrOfNonImprovements, pDebug,
        pStrictMode);
  }

  private static class Supplier implements StochasticSupplier<Solver> {

    private final int listLength;
    private final int maxNrOfNonImprovements;
    private final boolean debug;
    private final boolean strictMode;

    /**
     * Create a new instance with the specified list length and maximum number
     * of non-improvements.
     * @param pListLength see {@link MultiVehicleHeuristicSolver}.
     * @param pMaxNrOfNonImprovements see {@link MultiVehicleHeuristicSolver}.
     */
    Supplier(int pListLength, int pMaxNrOfNonImprovements) {
      this(pListLength, pMaxNrOfNonImprovements, false, false);
    }

    Supplier(int pListLength, int pMaxNrOfNonImprovements, boolean pDebug,
        boolean pStrictMode) {
      listLength = pListLength;
      maxNrOfNonImprovements = pMaxNrOfNonImprovements;
      debug = pDebug;
      strictMode = pStrictMode;
    }

    @Override
    public Solver get(long seed) {
      return SolverValidator.wrap(new MultiVehicleSolverAdapter(
          ArraysSolverValidator.wrap(new MultiVehicleHeuristicSolver(
              new MersenneTwister(seed), listLength, maxNrOfNonImprovements,
              debug, strictMode)), SI.SECOND));
    }

    @Override
    public String toString() {
      return new StringBuilder("Heuristic-").append(listLength).append("-")
          .append(maxNrOfNonImprovements).toString();
    }
  }
}
