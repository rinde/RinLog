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
package com.github.rinde.logistics.pdptw.solver.optaplanner;

import static com.github.rinde.logistics.pdptw.solver.optaplanner.ParcelVisit.PREV_VISIT;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.optaplanner.core.impl.heuristic.move.AbstractMove;
import org.optaplanner.core.impl.score.director.ScoreDirector;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 *
 * @author Rinde van Lon
 */
public class MoveBetweenVehicles extends AbstractMove {
  final ImmutableList<Changeset> changesets;
  final boolean isUndo;

  @Nullable
  ImmutableSet<Visit> planningEntities;
  @Nullable
  ImmutableSet<Visit> planningValues;

  MoveBetweenVehicles(ImmutableList<Changeset> cs, boolean undo) {
    changesets = cs;
    isUndo = undo;
  }

  static MoveBetweenVehicles create(ParcelVisit pick, ParcelVisit delv,
      Visit pickToPrev, Visit delvToPrev) {

    final ImmutableList.Builder<Changeset> changesets = ImmutableList.builder();

    if (delv.equals(pick.getNextVisit()) || pick.equals(delv.getNextVisit())) {
      // pickup and delivery are neighbors in originating vehicle
      if (pick.isBefore(delv)) {
        changesets.add(
          Changeset.create(
            nonNulls(pick.getPreviousVisit(), pick, delv, delv.getNextVisit()),
            nonNulls(pick.getPreviousVisit(), delv.getNextVisit())));
      } else {
        changesets.add(
          Changeset.create(
            nonNulls(delv.getPreviousVisit(), delv, pick, pick.getNextVisit()),
            nonNulls(delv.getPreviousVisit(), pick.getNextVisit())));
      }
    } else {
      // not neighbors in originating vehicle
      changesets.add(
        Changeset.create(
          nonNulls(pick.getPreviousVisit(), pick, pick.getNextVisit()),
          nonNulls(pick.getPreviousVisit(), pick.getNextVisit())));
      changesets.add(
        Changeset.create(
          nonNulls(delv.getPreviousVisit(), delv, delv.getNextVisit()),
          nonNulls(delv.getPreviousVisit(), delv.getNextVisit())));
    }

    if (pickToPrev.equals(delvToPrev) || delvToPrev.equals(pick)) {
      // targets are the same, meaning that they are neighbors
      changesets.add(
        Changeset.create(
          nonNulls(pickToPrev, pickToPrev.getNextVisit()),
          nonNulls(pickToPrev, pick, delv, pickToPrev.getNextVisit())));
    } else {
      changesets.add(
        Changeset.create(
          nonNulls(pickToPrev, pickToPrev.getNextVisit()),
          nonNulls(pickToPrev, pick, pickToPrev.getNextVisit())));
      changesets.add(
        Changeset.create(
          nonNulls(delvToPrev, delvToPrev.getNextVisit()),
          nonNulls(delvToPrev, delv, delvToPrev.getNextVisit())));
    }
    return new MoveBetweenVehicles(changesets.build(), false);
  }

  @SafeVarargs
  static <T> ImmutableList<T> nonNulls(final T... values) {
    final ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (final T t : values) {
      if (t != null) {
        builder.add(t);
      }
    }
    return builder.build();
  }

  @Override
  public boolean isMoveDoable(ScoreDirector scoreDirector) {
    return true;
  }

  @Override
  public MoveBetweenVehicles createUndoMove(ScoreDirector scoreDirector) {
    return new MoveBetweenVehicles(changesets, true);
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector scoreDirector) {
    if (isUndo) {
      for (final Changeset cs : changesets) {
        cs.undo(scoreDirector);
      }
    } else {
      for (final Changeset cs : changesets) {
        cs.execute(scoreDirector);
      }
    }
  }

  @Override
  public Collection<? extends Object> getPlanningEntities() {
    if (planningEntities == null) {
      final ImmutableSet.Builder<Visit> entityBuilder = ImmutableSet.builder();
      for (final Changeset c : changesets) {
        entityBuilder
            .addAll(isUndo ? c.getOriginalEntities() : c.getTargetEntities());
      }
      planningEntities = entityBuilder.build();
    }
    return planningEntities;
  }

  @Override
  public Collection<? extends Object> getPlanningValues() {
    if (planningValues == null) {
      final ImmutableSet.Builder<Visit> valuesBuilder = ImmutableSet.builder();
      for (final Changeset c : changesets) {
        valuesBuilder
            .addAll(isUndo ? c.getOriginalValues() : c.getTargetValues());
      }
      planningValues = valuesBuilder.build();
    }
    return planningValues;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    } else if (other instanceof MoveBetweenVehicles) {
      final MoveBetweenVehicles o = (MoveBetweenVehicles) other;
      return Objects.equals(changesets, o.changesets);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(changesets);
  }

  @AutoValue
  abstract static class Changeset {

    abstract ImmutableList<Visit> getOriginalState();

    abstract ImmutableList<Visit> getTargetState();

    void execute(ScoreDirector scoreDirector) {
      apply(getTargetState(), scoreDirector);
    }

    void undo(ScoreDirector scoreDirector) {
      apply(getOriginalState(), scoreDirector);
    }

    ImmutableList<Visit> getTargetEntities() {
      return getTargetState().subList(1, getTargetState().size());
    }

    ImmutableList<Visit> getOriginalEntities() {
      return getOriginalState().subList(1, getOriginalState().size());
    }

    ImmutableList<Visit> getTargetValues() {
      return getTargetState().subList(0, getTargetState().size() - 1);
    }

    ImmutableList<Visit> getOriginalValues() {
      return getOriginalState().subList(0, getOriginalState().size() - 1);
    }

    static void apply(List<Visit> state, ScoreDirector scoreDirector) {
      for (int i = state.size() - 1; i > 0; i--) {
        final ParcelVisit subject = (ParcelVisit) state.get(i);
        scoreDirector.beforeVariableChanged(subject, PREV_VISIT);
        subject.setPreviousVisit(state.get(i - 1));
        scoreDirector.afterVariableChanged(subject, PREV_VISIT);
      }
    }

    static Changeset create(ImmutableList<Visit> original,
        ImmutableList<Visit> target) {
      return new AutoValue_MoveBetweenVehicles_Changeset(original, target);
    }
  }
}
