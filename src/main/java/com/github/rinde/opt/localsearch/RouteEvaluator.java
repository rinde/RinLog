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
