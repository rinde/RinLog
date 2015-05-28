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
package com.github.rinde.opt.localsearch;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

final class Schedule<C, T> {
  final C context;
  final ImmutableList<ImmutableList<T>> routes;
  /**
   * Start indices of the routes which may be changed. If 0, the entire route
   * may be changed, if n all indices starting from n may be changed in the
   * route.
   */
  final ImmutableList<Integer> startIndices;
  final ImmutableList<Double> objectiveValues;
  final double objectiveValue;
  final RouteEvaluator<C, T> evaluator;

  private Schedule(C s, ImmutableList<ImmutableList<T>> r,
      ImmutableList<Integer> si, ImmutableList<Double> ovs, double ov,
      RouteEvaluator<C, T> eval) {
    checkArgument(r.size() == si.size());
    checkArgument(r.size() == ovs.size());
    context = s;
    routes = r;
    startIndices = si;
    objectiveValues = ovs;
    objectiveValue = ov;
    evaluator = eval;
  }

  static <C, T> Schedule<C, T> create(C context,
      ImmutableList<ImmutableList<T>> routes,
      ImmutableList<Integer> startIndices,
      ImmutableList<Double> objectiveValues, double globalObjectiveValue,
      RouteEvaluator<C, T> routeEvaluator) {
    return new Schedule<C, T>(context, routes, startIndices, objectiveValues,
        globalObjectiveValue, routeEvaluator);
  }

  static <C, T> Schedule<C, T> create(C context,
      ImmutableList<ImmutableList<T>> routes,
      ImmutableList<Integer> startIndices, RouteEvaluator<C, T> routeEvaluator) {
    final ImmutableList.Builder<Double> costsBuilder = ImmutableList.builder();
    double sumCost = 0;
    for (int i = 0; i < routes.size(); i++) {
      final double cost = routeEvaluator.computeCost(context, i, routes.get(i));
      costsBuilder.add(cost);
      sumCost += cost;
    }

    return new Schedule<C, T>(context, routes, startIndices,
        costsBuilder.build(), sumCost, routeEvaluator);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("context", context)
        .add("routes", routes).add("startIndices", startIndices)
        .add("objectiveValues", objectiveValues)
        .add("objectiveValue", objectiveValue).add("evaluator", evaluator)
        .toString();
  }
}
