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

import java.io.File;
import java.io.Serializable;

import org.junit.Test;

import com.github.rinde.rinsim.central.Central;
import com.github.rinde.rinsim.core.SimulatorAPI;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.experiment.Experiment;
import com.github.rinde.rinsim.experiment.MASConfiguration;
import com.github.rinde.rinsim.pdptw.common.AddParcelEvent;
import com.github.rinde.rinsim.pdptw.common.RouteRenderer;
import com.github.rinde.rinsim.pdptw.common.TimeLinePanel;
import com.github.rinde.rinsim.scenario.TimedEventHandler;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.scenario.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;
import com.google.common.primitives.Chars;

/**
 *
 * @author Rinde van Lon
 */
public class OptaplannerIntegrationTest {

  static class NamedParcelCreator
      implements TimedEventHandler<AddParcelEvent>, Serializable {
    long counter = 0;
    private static final long serialVersionUID = 3888253170041895475L;

    @Override
    public void handleTimedEvent(AddParcelEvent event, SimulatorAPI simulator) {
      final String name;
      if (counter >= 26) {
        if (counter >= 676) {
          throw new IllegalStateException("Too many parcels.");
        }

        final char first =
          (char) ('A' + (int) Math.floor(counter / 26) - 1);
        final char second = (char) ('A' + counter % 26);
        name = Chars.join("", first, second);
      } else {
        name = Character.toString((char) (counter + 'A'));
      }
      counter++;
      simulator.register(
        Parcel.builder(event.getParcelDTO())
            .toString(name)
            .build());
    }
  }

  @Test
  public void test() {

    Experiment.build(Gendreau06ObjectiveFunction.instance())
        .withThreads(1)
        .addConfiguration(
          MASConfiguration.builder(Central
              .solverConfiguration(OptaplannerSolver.validatedSupplier(30d)))
              .addEventHandler(AddParcelEvent.class, new NamedParcelCreator())
              .build())
        .addScenarios(Gendreau06Parser.parser().addFile(new File(
            "files/scenarios/gendreau06/req_rapide_1_240_24"))
            .offline()
            .parse())
        .showGui(View.builder()
            .with(PlaneRoadModelRenderer.builder())
            .with(RoadUserRenderer.builder())
            .with(RouteRenderer.builder())
            .with(TimeLinePanel.builder())
            .withAutoPlay())
        .showGui(true)
        .perform();

  }

}
