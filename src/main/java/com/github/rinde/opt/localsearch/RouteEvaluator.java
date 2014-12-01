/*
 * Copyright (C) 2013-2014 Rinde van Lon, iMinds DistriNet, KU Leuven
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

import com.google.common.collect.ImmutableList;

/**
 * Implementations of this interface should assign a cost value to a route.
 * 
 * @param <C> The context type.
 * @param <T> The generic type of a route.
 * @author Rinde van Lon 
 */
public interface RouteEvaluator<C, T> {

  /**
   * Should compute the cost of the new route.
   * @param context The context (schedule).
   * @param routeIndex The index of the new route in the context.
   * @param newRoute The new route.
   * @return The cost of the new route.
   */
  double computeCost(C context, int routeIndex, ImmutableList<T> newRoute);
}
