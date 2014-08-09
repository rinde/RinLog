package com.github.rinde.logistics.pdptw.solver;

import com.github.rinde.opt.localsearch.RouteEvaluator;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.pdptw.central.GlobalStateObject;
import com.github.rinde.rinsim.pdptw.central.Solvers;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.google.common.collect.ImmutableList;

class ParcelRouteEvaluator implements
    RouteEvaluator<GlobalStateObject, ParcelDTO> {
  private final ObjectiveFunction objectiveFunction;

  ParcelRouteEvaluator(ObjectiveFunction objFunc) {
    objectiveFunction = objFunc;
  }

  @Override
  public double computeCost(GlobalStateObject context, int routeIndex,
      ImmutableList<ParcelDTO> newRoute) {
    return objectiveFunction.computeCost(Solvers.computeStats(
        context.withSingleVehicle(routeIndex), ImmutableList.of(newRoute)));
  }
}
