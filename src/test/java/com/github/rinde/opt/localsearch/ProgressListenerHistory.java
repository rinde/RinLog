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

import static java.util.Collections.unmodifiableList;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleLists;

/**
 *
 * @author Rinde van Lon
 * @param <T> Type of schedulable item.
 */
public final class ProgressListenerHistory<T> implements ProgressListener<T> {

  private final List<ImmutableList<ImmutableList<T>>> scheduleHistory;
  private final DoubleList objectiveValueHistory;

  public ProgressListenerHistory() {
    scheduleHistory = new ArrayList<>();
    objectiveValueHistory = new DoubleArrayList();
  }

  @Override
  public void notify(ImmutableList<ImmutableList<T>> schedule,
      double objectiveValue) {
    scheduleHistory.add(schedule);
    objectiveValueHistory.add(objectiveValue);
  }

  public List<ImmutableList<ImmutableList<T>>> getSchedules() {
    return unmodifiableList(scheduleHistory);
  }

  public DoubleList getObjectiveValues() {
    return DoubleLists.unmodifiable(objectiveValueHistory);
  }

}
