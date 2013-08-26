package rinde.logistics.pdptw.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.random.RandomAdaptor;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.pdptw.central.arrays.SingleVehicleArraysSolver;
import rinde.sim.pdptw.central.arrays.SolutionObject;

/**
 * This class contains a heuristic implementation of the solver interface.
 * @author Tony Wauters
 * 
 */
public class HeuristicSolver implements SingleVehicleArraysSolver {

  public static final int TRAVEL_TIME_WEIGHT = 1;
  public static final int TARDINESS_WEIGHT = 1;
  private static final boolean DEBUG = false;

  private final RandomGenerator rand;

  public HeuristicSolver(RandomGenerator rand) {
    this.rand = rand;
  }

  /**
   * Heuristic implementation of the solver interface method
   */
  public SolutionObject solve(int[][] travelTime, int[] releaseDates,
      int[] dueDates, int[][] servicePairs, int[] serviceTime) {
    final int n = releaseDates.length;

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

    // two possible heuristics (steepest descent and late acceptance)
    // SolutionObject bestSol = performSteepestDescent(travelTime,
    // releaseDates,
    // dueDates, servicePairs,
    // serviceTime,pickupToDeliveryMap,deliveryToPickupMap);

    final int laListSize = 2000;
    final int maxIterations = 100000;
    final SolutionObject bestSol = performLateAcceptance(travelTime, releaseDates, dueDates, servicePairs, serviceTime, pickupToDeliveryMap, deliveryToPickupMap, laListSize, maxIterations);

    // ADDED BY RINDE TO CONFORM TO CHANGED SOLUTION OBJECT SPEC
    // SEE SolutionObject.arrivalTimes
    final int[] newArrivalTimes = new int[bestSol.route.length];
    for (int i = 0; i < bestSol.arrivalTimes.length; i++) {
      newArrivalTimes[i] = bestSol.arrivalTimes[bestSol.route[i]];
    }
    for (int i = 0; i < bestSol.arrivalTimes.length; i++) {
      bestSol.arrivalTimes[i] = newArrivalTimes[i];
    }

    // END ADDED BY RINDE

    return bestSol;
  }

  private SolutionObject performSteepestDescent(int[][] travelTime,
      int[] releaseDates, int[] dueDates, int[][] servicePairs,
      int[] serviceTime, int[] pickupToDeliveryMap, int[] deliveryToPickupMap) {
    final int n = releaseDates.length;

    final List<Integer> perm0 = generateFeasibleRandomPermutation(n, servicePairs);
    final SolutionObject sol0 = construct(intListToArray(perm0), travelTime, releaseDates, dueDates, servicePairs, serviceTime);

    boolean improved = true;

    List<Integer> current = new ArrayList<Integer>(perm0);

    SolutionObject bestSol = sol0;
    List<Integer> bestPerm = new ArrayList<Integer>(perm0);

    while (improved) {
      improved = false;

      final int[] elementLocations = new int[n];
      for (int i = 0; i < n; i++) {
        elementLocations[current.get(i)] = i;
      }

      // try all forward shifts
      for (int i = 1; i < n - 2; i++) {
        final int delivery = pickupToDeliveryMap[current.get(i)];
        int deliveryLocation = n;
        if (delivery != -1) {
          deliveryLocation = elementLocations[delivery];
        }

        for (int j = i + 1; j < Math.min(deliveryLocation, n - 1); j++) {
          final List<Integer> newPerm = new ArrayList<Integer>(current);
          final int el = newPerm.remove(i);

          newPerm.add(j, el);
          final SolutionObject newSol = construct(intListToArray(newPerm), travelTime, releaseDates, dueDates, servicePairs, serviceTime);
          if (newSol.objectiveValue < bestSol.objectiveValue) {
            bestSol = newSol;
            bestPerm = newPerm;
            improved = true;
            if (DEBUG) {
              System.out.println("Found new best solution with objective: "
                  + newSol.objectiveValue);
            }
          }
        }
      }

      // try all backward shifts
      for (int i = 2; i < n - 1; i++) {
        final int pickup = deliveryToPickupMap[current.get(i)];
        int pickupLocation = 0;
        if (pickup != -1) {
          pickupLocation = elementLocations[pickup];
        }
        for (int j = Math.max(1, pickupLocation + 1); j < i; j++) {
          final List<Integer> newPerm = new ArrayList<Integer>(current);
          final int el = newPerm.remove(i);
          newPerm.add(j, el);
          final SolutionObject newSol = construct(intListToArray(newPerm), travelTime, releaseDates, dueDates, servicePairs, serviceTime);
          if (newSol.objectiveValue < bestSol.objectiveValue) {
            bestSol = newSol;
            bestPerm = newPerm;
            improved = true;
            if (DEBUG) {
              System.out.println("Found new best solution with objective: "
                  + newSol.objectiveValue);
            }
          }
        }
      }

      current = bestPerm;
    }

    return bestSol;
  }

  private SolutionObject performLateAcceptance(int[][] travelTime,
      int[] releaseDates, int[] dueDates, int[][] servicePairs,
      int[] serviceTime, int[] pickupToDeliveryMap, int[] deliveryToPickupMap,
      int L, int maxIt) {
    final int n = releaseDates.length;

    final List<Integer> perm0 = generateFeasibleRandomPermutation(n, servicePairs);
    final SolutionObject sol0 = construct(intListToArray(perm0), travelTime, releaseDates, dueDates, servicePairs, serviceTime);

    List<Integer> current = new ArrayList<Integer>(perm0);
    int currentObj = sol0.objectiveValue;

    SolutionObject bestSol = sol0;
    List<Integer> bestPerm = new ArrayList<Integer>(perm0);

    final int[] laList = new int[L];
    for (int l = 0; l < L; l++) {
      laList[l] = sol0.objectiveValue;
    }

    for (int it = 0; it < maxIt; it++) {
      if (DEBUG) {
        if (it % 100 == 0) {
          System.out.println("Current:\t " + currentObj + "\tbest:\t"
              + bestSol.objectiveValue);
        }
      }

      final int[] elementLocations = new int[n];
      for (int i = 0; i < n; i++) {
        elementLocations[current.get(i)] = i;
      }

      final List<Integer> newPerm = new ArrayList<Integer>(current);

      if (rand.nextBoolean()) {
        // try all forward shifts
        boolean ok = false;
        int i = 0;
        int j = 0;
        do {
          ok = true;
          i = 1 + rand.nextInt(n - 3);
          final int delivery = pickupToDeliveryMap[current.get(i)];
          int deliveryLocation = n;
          if (delivery != -1) {
            deliveryLocation = elementLocations[delivery];
          }
          if (Math.min(deliveryLocation, n - 1) - (i + 1) <= 0) {
            ok = false;
            continue;
          }
          j = i + 1 + rand.nextInt(Math.min(deliveryLocation, n - 1) - (i + 1));
        } while (!ok);

        final int el = newPerm.remove(i);
        newPerm.add(j, el);

      } else {
        // try all backward shifts
        boolean ok = false;
        int i = 0;
        int j = 0;
        do {
          ok = true;
          i = 2 + rand.nextInt(n - 3);
          final int pickup = deliveryToPickupMap[current.get(i)];
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
      }

      final SolutionObject newSol = construct(intListToArray(newPerm), travelTime, releaseDates, dueDates, servicePairs, serviceTime);
      if (newSol.objectiveValue <= laList[it % L]) {
        // accept
        current = newPerm;
        currentObj = newSol.objectiveValue;

        if (newSol.objectiveValue < bestSol.objectiveValue) {
          // better than best
          bestSol = newSol;
          bestPerm = newPerm;
          // if (DEBUG)
          // System.out.println("Found new best solution with objective: "+newSol.objectiveValue);
        }

      }
      laList[it % L] = currentObj;

    }

    return bestSol;
  }

  /**
   * Generates a feasible permutation (route) for this problem. Feasible =
   * respecting the pickup and delivery pairs
   * @param n
   * @param servicePairs
   * @return
   */
  private List<Integer> generateFeasibleRandomPermutation(int n,
      int[][] servicePairs) {

    final List<Integer> elements = new ArrayList<Integer>();
    for (int i = 1; i < n - 1; i++) {
      elements.add(i);
    }
    Collections.shuffle(elements, new RandomAdaptor(rand));
    elements.add(0, 0);
    elements.add(n - 1);

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

    return elements;
  }

  /**
   * Constructive heuristic, builds a solution from a given permutation
   * 
   * @param permutation
   * @param travelTime
   * @param releaseDates
   * @param dueDates
   * @param servicePairs
   * @param serviceTime
   * @return a solution
   */
  private SolutionObject construct(int[] permutation, int[][] travelTime,
      int[] releaseDates, int[] dueDates, int[][] servicePairs,
      int[] serviceTime) {

    final int n = permutation.length;

    // calculate arrival times
    int totalTravelTime = 0;
    final int[] arrivalTimes = new int[n];
    int previous = 0;
    int previousT = releaseDates[0];
    arrivalTimes[0] = releaseDates[0];
    for (int i = 1; i < n; i++) {
      final int next = permutation[i];
      arrivalTimes[next] = Math
          .max(previousT + travelTime[previous][next], releaseDates[next]);
      totalTravelTime += travelTime[previous][next];

      previousT = arrivalTimes[next] + serviceTime[next];
      previous = next;
    }

    // calculate totalTardiness
    int totalTardiness = 0;
    for (int i = 0; i < n; i++) {
      final int tardiness = Math.max(0, arrivalTimes[i] + serviceTime[i]
          - dueDates[i]);
      totalTardiness += tardiness;
    }
    // calculate objective value
    final int objectiveValue = totalTardiness * TARDINESS_WEIGHT
        + totalTravelTime * TRAVEL_TIME_WEIGHT;

    final SolutionObject solutionObject = new SolutionObject(permutation,
        arrivalTimes, objectiveValue);
    return solutionObject;
  }

  private int[] intListToArray(List<Integer> list) {
    final int[] perm = new int[list.size()];
    for (int i = 0; i < list.size(); i++) {
      perm[i] = list.get(i);
    }
    return perm;
  }

}
