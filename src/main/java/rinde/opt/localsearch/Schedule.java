package rinde.opt.localsearch;

import static com.google.common.base.Preconditions.checkArgument;
import rinde.opt.localsearch.Swaps.Evaluator;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

class Schedule<C, T> {
  public final C context;
  public final ImmutableList<ImmutableList<T>> routes;
  public final ImmutableList<Double> objectiveValues;
  public final double objectiveValue;
  public final Evaluator<C, T> evaluator;

  static <C, T> Schedule<C, T> create(C context,
      ImmutableList<ImmutableList<T>> routes,
      ImmutableList<Double> objectiveValues, double globalObjectiveValue,
      Evaluator<C, T> routeEvaluator) {
    return new Schedule<C, T>(context, routes, objectiveValues,
        globalObjectiveValue, routeEvaluator);
  }

  static <C, T> Schedule<C, T> create(C context,
      ImmutableList<ImmutableList<T>> routes, Evaluator<C, T> routeEvaluator) {
    final ImmutableList.Builder<Double> costsBuilder = ImmutableList.builder();
    double sumCost = 0;
    for (int i = 0; i < routes.size(); i++) {
      final double cost = routeEvaluator.eval(context, i, routes.get(i));
      costsBuilder.add(cost);
      sumCost += cost;
    }

    return new Schedule<C, T>(context, routes, costsBuilder.build(), sumCost,
        routeEvaluator);
  }

  private Schedule(C s, ImmutableList<ImmutableList<T>> r,
      ImmutableList<Double> ovs, double ov, Evaluator<C, T> eval) {
    checkArgument(r.size() == ovs.size());
    context = s;
    routes = r;
    objectiveValues = ovs;
    objectiveValue = ov;
    evaluator = eval;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("context", context)
        .add("routes", routes).add("objectiveValues", objectiveValues)
        .add("objectiveValue", objectiveValue).add("evaluator", evaluator)
        .toString();
  }
}
