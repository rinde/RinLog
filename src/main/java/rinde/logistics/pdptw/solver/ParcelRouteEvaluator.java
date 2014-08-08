package rinde.logistics.pdptw.solver;

import rinde.opt.localsearch.RouteEvaluator;
import rinde.sim.core.pdptw.ParcelDTO;
import rinde.sim.pdptw.central.GlobalStateObject;
import rinde.sim.pdptw.central.Solvers;
import rinde.sim.pdptw.common.ObjectiveFunction;

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
