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
package com.github.rinde.logistics.pdptw.solver;

import com.github.rinde.opt.localsearch.RouteEvaluator;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solvers;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.google.common.collect.ImmutableList;

class ParcelRouteEvaluator implements
RouteEvaluator<GlobalStateObject, Parcel> {
  private final ObjectiveFunction objectiveFunction;

  ParcelRouteEvaluator(ObjectiveFunction objFunc) {
    objectiveFunction = objFunc;
  }

  @Override
  public double computeCost(GlobalStateObject context, int routeIndex,
    ImmutableList<Parcel> newRoute) {
    return objectiveFunction.computeCost(Solvers.computeStats(
      context.withSingleVehicle(routeIndex), ImmutableList.of(newRoute)));
  }
}
