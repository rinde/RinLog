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

import com.github.rinde.rinsim.geom.Point;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Move for a pair of pickup and delivery visits.
 * @author Rinde van Lon
 */
public class MovePair extends AbstractMove {
  final ImmutableList<Changeset> changesets;
  final boolean isUndo;

  @Nullable
  ImmutableSet<Visit> planningEntities;
  @Nullable
  ImmutableSet<Visit> planningValues;

  enum NullVisit implements Visit {
    INSTANCE;

    @Override
    public ParcelVisit getNextVisit() {
      return null;
    }

    @Override
    public void setNextVisit(ParcelVisit v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Vehicle getVehicle() {
      return null;
    }

    @Override
    public void setVehicle(Vehicle v) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Point getPosition() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ParcelVisit getLastVisit() {
      return null;
    }

    @Override
    public String toString() {
      return "NullVisit";
    }

  }

  MovePair(ImmutableList<Changeset> cs, boolean undo) {
    changesets = cs;
    isUndo = undo;
  }

  static MovePair create(ParcelVisit pick, ParcelVisit delv,
      Visit pickToPrev, Visit delvToPrev) {
    final ImmutableList.Builder<Changeset> changesets = ImmutableList.builder();

    if (delv.equals(pick.getNextVisit())
      || pick.equals(delv.getNextVisit())) {
      // pickup and delivery are neighbors in originating vehicle
      if (pick.isBefore(delv)) {
        changesets.add(
          Changeset.create(
            nonNulls(pick.getPreviousVisit(), pick, delv,
              delv.getNextVisit()),
            nonNulls(pick.getPreviousVisit(), delv.getNextVisit())));
      } else {
        changesets.add(
          Changeset.create(
            nonNulls(delv.getPreviousVisit(), delv, pick,
              pick.getNextVisit()),
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
    return new MovePair(changesets.build(), false);
  }

  @Override
  public String toString() {
    return "MovePair{" + changesets.toString() + "}";
  }

  @SafeVarargs
  static ImmutableList<Visit> nonNulls(final Visit... values) {
    final ImmutableList.Builder<Visit> builder = ImmutableList.builder();
    boolean first = true;
    for (final Visit t : values) {
      if (first && t == null) {
        builder.add(NullVisit.INSTANCE);
      } else if (t != null) {
        builder.add(t);
      }
      first = false;
    }
    return builder.build();
  }

  @Override
  public boolean isMoveDoable(ScoreDirector scoreDirector) {
    return true;
  }

  @Override
  public MovePair createUndoMove(ScoreDirector scoreDirector) {
    return new MovePair(changesets, true);
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
    } else if (other instanceof MovePair) {
      final MovePair o = (MovePair) other;
      return isUndo == o.isUndo && Objects.equals(changesets, o.changesets);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(changesets, isUndo);
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

        final Visit target = state.get(i - 1);
        if (target == NullVisit.INSTANCE) {
          subject.setPreviousVisit(null);
        } else {
          subject.setPreviousVisit(target);
        }
        scoreDirector.afterVariableChanged(subject, PREV_VISIT);
      }
    }

    static Changeset create(ImmutableList<Visit> original,
        ImmutableList<Visit> target) {
      return new AutoValue_MovePair_Changeset(original, target);
    }
  }
}
