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
package com.github.rinde.logistics.pdptw.mas.route;

import java.util.ArrayList;
import java.util.List;

import com.github.rinde.rinsim.central.Measurable;
import com.github.rinde.rinsim.central.SolverTimeMeasurement;
import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.Model.AbstractModel;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * Class that allows to read time measurements of {@link RoutePlanner}s that
 * support it.
 * @author Rinde van Lon
 */
public class RoutePlannerStatsLogger extends AbstractModel<RoutePlanner> {
  private final List<RoutePlanner> routePlanners;

  RoutePlannerStatsLogger() {
    routePlanners = new ArrayList<>();
  }

  @Override
  public boolean register(RoutePlanner element) {
    if (element instanceof Measurable) {
      routePlanners.add(element);
    }
    return true;
  }

  @Override
  public boolean unregister(RoutePlanner element) {
    return false;
  }

  /**
   * @return A multimap of {@link RoutePlanner}s to
   *         {@link SolverTimeMeasurement}s.
   */
  public ImmutableListMultimap<RoutePlanner, SolverTimeMeasurement> getTimeMeasurements() {
    final ListMultimap<RoutePlanner, SolverTimeMeasurement> map =
      ArrayListMultimap.create();
    for (final RoutePlanner rp : routePlanners) {
      map.putAll(rp, ((Measurable) rp).getTimeMeasurements());
    }
    return ImmutableListMultimap.copyOf(map);
  }

  /**
   * @return {@link Builder} for creating {@link RoutePlannerStatsLogger}.
   */
  public static Builder builder() {
    return new AutoValue_RoutePlannerStatsLogger_Builder();
  }

  /**
   * Builder for {@link RoutePlannerStatsLogger}.
   * @author Rinde van Lon
   */
  @AutoValue
  public abstract static class Builder
      extends AbstractModelBuilder<RoutePlannerStatsLogger, RoutePlanner> {

    private static final long serialVersionUID = 6030423288351722795L;

    Builder() {}

    @Override
    public RoutePlannerStatsLogger build(
        DependencyProvider dependencyProvider) {
      return new RoutePlannerStatsLogger();
    }
  }
}
