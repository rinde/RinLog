/*
 * Copyright (C) 2013-2015 Rinde van Lon, iMinds-DistriNet, KU Leuven
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verifyNotNull;

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

  // final MoveOne pickup;
  // final MoveOne delvry;

  final ImmutableList<Changeset> changesets;
  final boolean isUndo;

  @Nullable
  ImmutableSet<Visit> planningEntities;
  @Nullable
  ImmutableSet<Visit> planningValues;

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

    // if (pick.isBefore(delv)) {
    // 'normal' situation
    // final MoveOne pickupMove = createPickup(pick, pickToPrev);
    //
    // return new MoveBetweenVehicles(pickupMove,
    // createDelivery(pickupMove, delv, delvToPrev));
    // }
    // reversed situation
    // final MoveOne deliveryMove = MoveOne.create(delv, delvToPrev);
    // return new MoveBetweenVehicles(deliveryMove,
    // createDelivery(deliveryMove, pick, pickToPrev));
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

  static MoveOne createPickup(ParcelVisit pick, Visit pickToPrev) {
    final ParcelVisit parcelVisit = pick;
    final Visit toPrev = pickToPrev;
    @Nullable
    final ParcelVisit toNext = toPrev.getNextVisit();
    // if (toPrev.equals(pickup.getSubject())) {// toNext != null &&
    // // toNext.equals(parcelVisit)) {
    // // System.out.println("yooo");
    // toNext = pickup.getToNext();
    // }

    final Visit originalPrev = verifyNotNull(pick.getPreviousVisit());
    // if (originalPrev.equals(pick.getAssociation())) {
    // originalPrev = pickup.getOriginalPrev();
    // }

    final ParcelVisit originalNext = pick.getNextVisit();
    return MoveOne.create(parcelVisit, toPrev, toNext, originalPrev,
      originalNext);
  }

  static MoveOne createDelivery(MoveOne pickup, ParcelVisit delv,
      Visit delvToParent) {

    // System.out.println("from");
    // System.out.println(pickup.getOriginalPrev().getVehicle().printRoute());
    // System.out.println("to");
    // System.out.println(pickup.getToPrev().getVehicle().printRoute());

    final ParcelVisit parcelVisit = delv;
    Visit toPrev = delvToParent;
    if (toPrev.equals(pickup.getToPrev())) {
      toPrev = pickup.getSubject();
    }
    @Nullable
    ParcelVisit toNext = toPrev.getNextVisit();
    if (toPrev.equals(pickup.getSubject())) {// toNext != null &&
                                             // toNext.equals(parcelVisit)) {
      // System.out.println("yooo");
      toNext = pickup.getToNext();
    }
    Visit originalPrev = verifyNotNull(delv.getPreviousVisit());
    if (originalPrev.equals(pickup.getSubject())) {
      originalPrev = pickup.getOriginalPrev();
    }

    ParcelVisit originalNext = delv.getNextVisit();
    if (originalNext != null && originalNext.equals(delv.getAssociation())) {
      originalNext = delv.getAssociation().getNextVisit();
    }

    return MoveOne.create(parcelVisit, toPrev, toNext, originalPrev,
      originalNext);
  }

  MoveBetweenVehicles(ImmutableList<Changeset> cs, boolean undo) {
    changesets = cs;
    isUndo = undo;

  }

  // MoveBetweenVehicles(MoveOne pick, MoveOne delv) {
  // pickup = pick;
  // delvry = delv;
  //
  // // if(pickup.getOriginalNext().equals(delv.getSubject()){
  // //
  // // }
  //
  // planningEntities = MoveBetweenVehicles.<Visit>nonNulls(
  // pickup.getOriginalNext(), pickup.getSubject(), pickup.getToNext(),
  // delvry.getOriginalNext(), delvry.getSubject(), delvry.getToNext());
  //
  // planningValues = MoveBetweenVehicles.<Visit>nonNulls(
  // pickup.getOriginalPrev(), pickup.getToPrev(), pickup.getSubject(),
  // delvry.getOriginalPrev(), delvry.getToPrev(), delvry.getSubject());
  // // System.out.println(
  // // "MoveBetweenVehicles CONSTRUCTOR " + Integer.toHexString(hashCode()));
  // }

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

    // ParcelVisit pickupToNext = pickup.getOriginalNext();
    // if (delvry.getSubject().equals(pickupToNext)) {
    // pickupToNext = null;
    // }
    //
    // ParcelVisit originalNext = pickup.getToNext();
    // if (delvry.getToPrev().equals(pickup.getSubject())) {
    // originalNext = delvry.getSubject();
    // }
    //
    // Visit to = pickup.getOriginalPrev();
    // if (to.equals(delvry.getSubject())) {
    // to = delvry.getOriginalPrev();
    // }
    // final MoveOne undoPickup =
    // MoveOne.create(pickup.getSubject(), to,
    // pickupToNext, pickup.getToPrev(), originalNext);
    //
    // Visit toPrev = delvry.getOriginalPrev();
    // if (toPrev.equals(undoPickup.getToPrev())
    // && !delvry.getSubject().equals(pickup.getOriginalPrev())) {
    //
    // toPrev = undoPickup.getSubject();
    // }
    //
    // Visit originalPrev = delvry.getToPrev();
    // if (originalPrev.equals(pickup.getSubject())) {
    // originalPrev = pickup.getToPrev();
    // }
    //
    // ParcelVisit toNext = delvry.getOriginalNext();
    // if (pickup.getSubject().equals(delvry.getSubject().getNextVisit())) {
    // toNext = pickup.getSubject();
    // }
    //
    // final MoveOne undoDelivery =
    // MoveOne.create(delvry.getSubject(), toPrev, toNext,
    // originalPrev, delvry.getToNext());
    //
    // return new MoveBetweenVehicles(undoPickup, undoDelivery);
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector scoreDirector) {
    // System.out.println(" > DO MOVE " + Integer.toHexString(hashCode()));

    // final PDPSolution sol = (PDPSolution) scoreDirector.getWorkingSolution();

    // scoreDirector.

    // final SolutionDescriptor solDesc =
    // SolutionDescriptor.buildSolutionDescriptor(PDPSolution.class,
    // ParcelVisit.class, Visit.class);
    // final SolutionCloner<PDPSolution> cloner =
    // new FieldAccessingSolutionCloner<>(solDesc);
    // final PDPSolution original = cloner.cloneSolution(sol);
    // System.out.println("created clone");

    // final MoveBetweenVehicles undo = createUndoMove(scoreDirector);

    // checkState(PDPSolution.equal(original, sol));
    // System.out.println("compare");

    if (isUndo) {
      for (final Changeset cs : changesets) {
        cs.undo(scoreDirector);
      }
    } else {
      for (final Changeset cs : changesets) {
        cs.execute(scoreDirector);
      }
    }

    // System.out.println("apply move: pickup");
    // pickup.execute(scoreDirector);
    // System.out.println("apply move: delivery");
    // delvry.execute(scoreDirector);
    // // checkState(!PDPSolution.equal(original, sol));
    // System.out.println("apply move: done");

    // undo.doMove(scoreDirector);
    // checkState(PDPSolution.equal(original, sol));
    //
    // pickup.execute(scoreDirector);
    // delvry.execute(scoreDirector);
    // System.out.println(" >> MOVE DONE");
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
  abstract static class MoveOne {
    static final String ARROW = "->";

    abstract ParcelVisit getSubject();

    abstract Visit getToPrev();

    @Nullable
    abstract ParcelVisit getToNext();

    abstract Visit getOriginalPrev();

    @Nullable
    abstract ParcelVisit getOriginalNext();

    void execute(ScoreDirector scoreDirector) {
      // System.out.println("execute " + this);

      System.out.println("before: " +
          getSubject().getVehicle().printRoute());
      System.out.println(this);
      System.out.println(" > " + getSubject());
      checkState(
        Objects.equals(getSubject().getPreviousVisit(), getOriginalPrev()));
      checkState(
        Objects.equals(getSubject().getNextVisit(), getOriginalNext()),
        "%s of %s is %s but should equal the original next: %s",
        ScoreCalculator.NEXT_VISIT, getSubject(), getSubject().getNextVisit(),
        getOriginalNext());

      if (getOriginalNext() != null) {
        scoreDirector.beforeVariableChanged(getOriginalNext(), PREV_VISIT);
        getOriginalNext().setPreviousVisit(getOriginalPrev());
        scoreDirector.afterVariableChanged(getOriginalNext(), PREV_VISIT);
      }
      scoreDirector.beforeVariableChanged(getSubject(), PREV_VISIT);
      getSubject().setPreviousVisit(getToPrev());
      scoreDirector.afterVariableChanged(getSubject(), PREV_VISIT);
      if (getToNext() != null) {
        scoreDirector.beforeVariableChanged(getToNext(), PREV_VISIT);
        getToNext().setPreviousVisit(getSubject());
        scoreDirector.afterVariableChanged(getToNext(), PREV_VISIT);
      }
      scoreDirector.triggerVariableListeners();
      System.out.println("after: " + getSubject().getVehicle().printRoute());
    }

    @Override
    public String toString() {
      return new StringBuilder()
          .append("MoveOne{before:")
          .append(getOriginalPrev())
          .append(ARROW)
          .append(getSubject())
          .append(ARROW)
          .append(getOriginalNext())
          .append(",after:")
          .append(getToPrev())
          .append(ARROW)
          .append(getSubject())
          .append(ARROW)
          .append(getToNext())
          .append("}")
          .toString();
    }

    static MoveOne create(ParcelVisit pv, Visit to, @Nullable ParcelVisit toNxt,
        Visit origPrev, @Nullable ParcelVisit origNext) {
      final ParcelVisit parcelVisit = pv;
      final Visit toPrev = to;
      final ParcelVisit toNext = toNxt;
      final Visit originalPrev = origPrev;
      final ParcelVisit originalNext = origNext;
      return new AutoValue_MoveBetweenVehicles_MoveOne(parcelVisit, toPrev,
          toNext, originalPrev, originalNext);
    }
  }
}
