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
import java.util.Objects;

import javax.annotation.Nullable;

import org.optaplanner.core.impl.heuristic.move.AbstractMove;
import org.optaplanner.core.impl.score.director.ScoreDirector;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class MoveBetweenVehicles extends AbstractMove {

  final MoveOne pickup;
  final MoveOne delvry;

  final ImmutableList<Visit> planningEntities;
  final ImmutableList<Visit> planningValues;

  static MoveBetweenVehicles create(ParcelVisit pick, ParcelVisit delv,
      Visit pickToPrev, Visit delvToPrev) {

    if (pick.isBefore(delv)) {
      // 'normal' situation
      final MoveOne pickupMove = MoveOne.create(pick, pickToPrev);
      return new MoveBetweenVehicles(pickupMove,
          createDelivery(pickupMove, delv, delvToPrev));
    }
    // reversed situation
    final MoveOne deliveryMove = MoveOne.create(delv, delvToPrev);
    return new MoveBetweenVehicles(deliveryMove,
        createDelivery(deliveryMove, pick, pickToPrev));
  }

  // MoveBetweenVehicles(ParcelVisit pick, ParcelVisit delv, Visit pickToPrev,
  // Visit delvToPrev) {
  // this(MoveOne.create(pick, pickToPrev),
  // createDelivery(MoveOne.create(pick, pickToPrev), delv, delvToPrev));
  // }

  static MoveOne createDelivery(MoveOne pickup, ParcelVisit delv,
      Visit delvToParent) {

    // System.out.println("from");
    // System.out.println(pickup.getOriginalPrev().getVehicle().printRoute());
    // System.out.println("to");
    // System.out.println(pickup.getToPrev().getVehicle().printRoute());

    final ParcelVisit parcelVisit = delv;
    final Visit toPrev = delvToParent;
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

    final ParcelVisit originalNext = delv.getNextVisit();
    return MoveOne.create(parcelVisit, toPrev, toNext, originalPrev,
      originalNext);
  }

  MoveBetweenVehicles(MoveOne pick, MoveOne delv) {
    pickup = pick;
    delvry = delv;

    // if(pickup.getOriginalNext().equals(delv.getSubject()){
    //
    // }

    planningEntities = MoveBetweenVehicles.<Visit>nonNulls(
      pickup.getOriginalNext(), pickup.getSubject(), pickup.getToNext(),
      delvry.getOriginalNext(), delvry.getSubject(), delvry.getToNext());

    planningValues = MoveBetweenVehicles.<Visit>nonNulls(
      pickup.getOriginalPrev(), pickup.getToPrev(), pickup.getSubject(),
      delvry.getOriginalPrev(), delvry.getToPrev(), delvry.getSubject());
    // System.out.println(
    // "MoveBetweenVehicles CONSTRUCTOR " + Integer.toHexString(hashCode()));
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

    ParcelVisit pickupToNext = pickup.getOriginalNext();
    if (delvry.getSubject().equals(pickupToNext)) {
      pickupToNext = null;
    }

    ParcelVisit originalNext = pickup.getToNext();
    if (delvry.getToPrev().equals(pickup.getSubject())) {
      originalNext = delvry.getSubject();
    }

    final MoveOne undoPickup =
      MoveOne.create(pickup.getSubject(), pickup.getOriginalPrev(),
        pickupToNext, pickup.getToPrev(), originalNext);

    Visit toPrev = delvry.getOriginalPrev();
    if (toPrev.equals(undoPickup.getToPrev())) {
      toPrev = undoPickup.getSubject();
    }

    Visit originalPrev = delvry.getToPrev();
    if (originalPrev.equals(pickup.getSubject())) {
      originalPrev = pickup.getToPrev();
    }

    final MoveOne undoDelivery =
      MoveOne.create(delvry.getSubject(), toPrev, delvry.getOriginalNext(),
        originalPrev, delvry.getToNext());

    return new MoveBetweenVehicles(undoPickup, undoDelivery);
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

    System.out.println("apply move: pickup");
    pickup.execute(scoreDirector);
    System.out.println("apply move: delivery");
    delvry.execute(scoreDirector);
    // checkState(!PDPSolution.equal(original, sol));
    System.out.println("apply move: done");

    // undo.doMove(scoreDirector);
    // checkState(PDPSolution.equal(original, sol));
    //
    // pickup.execute(scoreDirector);
    // delvry.execute(scoreDirector);
    // System.out.println(" >> MOVE DONE");
  }

  @Override
  public Collection<? extends Object> getPlanningEntities() {
    return planningEntities;
  }

  @Override
  public Collection<? extends Object> getPlanningValues() {
    return planningValues;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    } else if (other instanceof MoveBetweenVehicles) {
      final MoveBetweenVehicles o = (MoveBetweenVehicles) other;
      return Objects.equals(pickup, o.pickup)
          && Objects.equals(delvry, o.delvry);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pickup, delvry);
  }

  @AutoValue
  abstract static class MoveOne {

    abstract ParcelVisit getSubject();

    abstract Visit getToPrev();

    @Nullable
    abstract ParcelVisit getToNext();

    abstract Visit getOriginalPrev();

    @Nullable
    abstract ParcelVisit getOriginalNext();

    void execute(ScoreDirector scoreDirector) {
      // System.out.println("execute " + this);

      System.out.println("before: " + getSubject().getVehicle().printRoute());
      System.out.println(this);
      System.out.println(" > " + getSubject());
      checkState(
        Objects.equals(getSubject().getPreviousVisit(), getOriginalPrev()));
      checkState(
        Objects.equals(getSubject().getNextVisit(), getOriginalNext()),
        "%s should equal %s", getSubject().getNextVisit(),
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

      System.out.println("after: " + getSubject().getVehicle().printRoute());
    }

    static MoveOne create(ParcelVisit pv, Visit to) {
      final ParcelVisit parcelVisit = pv;
      final Visit toPrev = to;
      final ParcelVisit toNext = to.getNextVisit();
      final Visit originalPrev = verifyNotNull(pv.getPreviousVisit());
      final ParcelVisit originalNext = pv.getNextVisit();
      return new AutoValue_MoveBetweenVehicles_MoveOne(parcelVisit, toPrev,
          toNext, originalPrev, originalNext);
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
