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
package com.github.rinde.logistics.pdptw.mas.comm;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

import com.github.rinde.logistics.pdptw.mas.Truck;
import com.github.rinde.logistics.pdptw.mas.comm.RtSolverBidder.BidFunctions;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.RtSimSolverBuilder;
import com.github.rinde.rinsim.central.rt.RtSolverModel;
import com.github.rinde.rinsim.central.rt.RtStAdapters;
import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.FakeDependencyProvider;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.VehicleParcelActionInfo;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.VehicleState;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.VehicleDTO;
import com.github.rinde.rinsim.core.model.road.RoadModelSnapshotTestUtil;
import com.github.rinde.rinsim.core.model.time.RealtimeClockController;
import com.github.rinde.rinsim.core.model.time.TimeModel;
import com.github.rinde.rinsim.event.EventAPI;
import com.github.rinde.rinsim.geom.GeomHeuristics;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.PDPRoadModel;
import com.github.rinde.rinsim.pdptw.common.RouteFollowingVehicle.RouteAdjuster;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 *
 * @author Rinde van Lon
 */
public class RtSolverBidderTest {

  @SuppressWarnings("null")
  RtSolverModel model;

  @SuppressWarnings("null")
  DependencyProvider dependencyProvider;

  @SuppressWarnings("null")
  RealtimeClockController clock;

  PDPRoadModel rm;

  PDPModel pm;

  @Before
  public void setUp() {
    clock = spy((RealtimeClockController) TimeModel.builder()
      .withRealTime()
      .build(FakeDependencyProvider.empty()));
    doNothing().when(clock).switchToRealTime();
    doNothing().when(clock).switchToSimulatedTime();

    rm = mock(PDPRoadModel.class);
    when(rm.getSpeedUnit()).thenReturn(NonSI.KILOMETERS_PER_HOUR);
    when(rm.getDistanceUnit()).thenReturn(SI.KILOMETER);
    when(rm.getSnapshot()).thenReturn(
      RoadModelSnapshotTestUtil.createPlaneRoadModelSnapshot(new Point(0, 0),
        new Point(10, 10), SI.KILOMETER));

    pm = mock(PDPModel.class);
    when(pm.getEventAPI()).thenReturn(mock(EventAPI.class));

    dependencyProvider = FakeDependencyProvider.builder()
      .add(clock, RealtimeClockController.class)
      .add(rm, PDPRoadModel.class)
      .add(pm, PDPModel.class)
      .build();

    model = RtSolverModel.builder().build(dependencyProvider);
    ((TimeModel) clock).register(model);
  }

  // test for cancelling a bid computation.

  // 1. callForBids
  // 2. endOfAuction (while computation is still going on)

  @Test
  public void test() throws InterruptedException {
    final RealtimeSolver solver = RtStAdapters.toRealtime(new Solver() {
      @Override
      public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
          throws InterruptedException {
        while (true) {
          Thread.sleep(100);
        }
      }
    });
    final RtSolverBidder bidder =
      new RtSolverBidder(mock(ObjectiveFunction.class), solver,
        BidFunctions.PLAIN, -1, true, GeomHeuristics.euclidean());

    final RoutePlanner rp = mock(RoutePlanner.class);
    when(rp.getEventAPI()).thenReturn(mock(EventAPI.class));

    final Truck truck = new Truck(VehicleDTO.builder().build(),
      rp, bidder, mock(RouteAdjuster.class), true);

    when(rm.getPosition(truck)).thenReturn(new Point(0, 0));
    final Parcel a = mock(Parcel.class);
    when(pm.getContents(truck)).thenReturn(ImmutableSet.<Parcel>of());

    final VehicleParcelActionInfo info = mock(VehicleParcelActionInfo.class);
    when(info.getParcel()).thenReturn(a);
    when(rm.getSnapshot()).thenReturn(
      RoadModelSnapshotTestUtil.createPlaneRoadModelSnapshot(new Point(0, 0),
        new Point(10, 10), SI.METER));

    when(pm.getVehicleState(truck)).thenReturn(VehicleState.IDLE);
    when(pm.getVehicleActionInfo(truck)).thenReturn(info);
    when(pm.getParcelState(a)).thenReturn(ParcelState.AVAILABLE);

    bidder.init(rm, pm, truck);
    bidder.setSolverProvider(model.get(RtSimSolverBuilder.class));
    final Auctioneer auctioneer = mock(Auctioneer.class);

    bidder.callForBids(auctioneer, a, 0);
    assertThat(bidder.cfbQueue).hasSize(1);
    assertThat(bidder.computing.get()).isTrue();
    // give a little time to the solver to start computing
    Thread.sleep(100);
    assertThat(bidder.solverHandle.get().isComputing()).isTrue();
    assertThat(bidder.computing.get()).isTrue();
    // wait a bit more, then announce end of auction
    Thread.sleep(100);
    bidder.endOfAuction(auctioneer, a, 0);

    assertThat(bidder.cfbQueue).isEmpty();
    assertThat(bidder.computing.get()).isFalse();

    verify(auctioneer, never()).submit(Matchers.any(DoubleBid.class));
  }

}
