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

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.logistics.pdptw.mas.TruckFactory.DefaultTruckFactory;
import com.github.rinde.logistics.pdptw.mas.route.GotoClosestRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import com.github.rinde.rinsim.central.RandomSolver;
import com.github.rinde.rinsim.central.SolverModel;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.DependencyProvider;
import com.github.rinde.rinsim.core.model.Model.AbstractModel;
import com.github.rinde.rinsim.core.model.ModelBuilder.AbstractModelBuilder;
import com.github.rinde.rinsim.core.model.pdp.PDPModel;
import com.github.rinde.rinsim.core.model.pdp.PDPModel.ParcelState;
import com.github.rinde.rinsim.core.model.time.TickListener;
import com.github.rinde.rinsim.core.model.time.TimeLapse;
import com.github.rinde.rinsim.experiment.ExperimentTestUtil;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.pdptw.common.AddVehicleEvent;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/**
 * @author Rinde van Lon
 *
 */
@RunWith(Parameterized.class)
public class CommunicationIntegrationTest implements TickListener {
  final MASConfiguration configuration;
  Simulator problem;

  /**
   * @param c The configuration under test.
   */
  @SuppressWarnings("null")
  public CommunicationIntegrationTest(MASConfiguration c) {
    configuration = c;
  }

  /**
   * Conducts the actual integration test.
   */
  @Test
  public void test() {
    problem = ExperimentTestUtil.init(
      Gendreau06Parser.parser()
        .addFile("files/scenarios/gendreau06/req_rapide_1_240_24")
        .parse().get(0),
      configuration, 123,
      false);

    problem.tick();
    problem.register(this);
    problem.start();
  }

  /**
   * @return The configurations to test.
   */
  @Parameters
  public static Collection<Object[]> configs() {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    return asList(new Object[][] {
      {MASConfiguration.pdptwBuilder()
        .addEventHandler(AddVehicleEvent.class,
          DefaultTruckFactory.builder()
            .setRoutePlanner(RandomRoutePlanner.supplier())
            .setCommunicator(RandomBidder.supplier())
            .build())
        .addModel(AuctionCommModel.builder(DoubleBid.class))
        .addModel(CommTestModel.builder())
        .build()},
      {MASConfiguration.pdptwBuilder()
        .addEventHandler(AddVehicleEvent.class,
          DefaultTruckFactory.builder()
            .setRoutePlanner(RandomRoutePlanner.supplier())
            .setCommunicator(
              SolverBidder.supplier(objFunc, RandomSolver.supplier()))
            .build())
        .addModel(AuctionCommModel.builder(DoubleBid.class))
        .addModel(CommTestModel.builder())
        .addModel(SolverModel.builder())
        .build()},
      {MASConfiguration
        .pdptwBuilder()
        .addEventHandler(AddVehicleEvent.class,
          DefaultTruckFactory.builder()
            .setRoutePlanner(GotoClosestRoutePlanner.supplier())
            .setCommunicator(BlackboardUser.supplier())
            .build())
        .addModel(BlackboardCommModel.builder())
        .addModel(CommTestModel.builder())
        .build()},
    });
  }

  @Override
  public void tick(TimeLapse timeLapse) {
    final Optional<PDPModel> pdpModel = Optional.fromNullable(problem
      .getModelProvider().getModel(PDPModel.class));

    final Optional<CommTestModel> commTestModel = Optional.fromNullable(problem
      .getModelProvider().getModel(CommTestModel.class));

    for (final Communicator c : commTestModel.get().communicators) {
      assertTrue(
        "The communicator may only return parcels which are not yet picked up",
        pdpModel
          .get()
          .getParcels(ParcelState.ANNOUNCED, ParcelState.AVAILABLE,
            ParcelState.PICKING_UP)
          .containsAll(c.getParcels()));
    }
  }

  @Override
  public void afterTick(TimeLapse timeLapse) {}

  public static class CommTestModel extends AbstractModel<Communicator> {
    final List<Communicator> communicators;

    CommTestModel() {
      communicators = newArrayList();
    }

    @Override
    public boolean register(Communicator element) {
      communicators.add(element);
      return false;
    }

    @Override
    public boolean unregister(Communicator element) {
      throw new UnsupportedOperationException();
    }

    static Builder builder() {
      return new AutoValue_CommunicationIntegrationTest_CommTestModel_Builder();
    }

    @AutoValue
    abstract static class Builder extends
        AbstractModelBuilder<CommTestModel, Communicator>
        implements Serializable {

      private static final long serialVersionUID = 3971268827484599768L;

      @Override
      public CommTestModel build(DependencyProvider dependencyProvider) {
        return new CommTestModel();
      }
    }
  }
}
