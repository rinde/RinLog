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

import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.score.definition.ScoreDefinitionType;
import org.optaplanner.core.config.score.director.ScoreDirectorFactoryConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.random.RandomType;
import org.optaplanner.core.config.solver.termination.TerminationConfig;

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.Solver;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Rinde van Lon
 */
public class OptplannerSolver implements Solver {

  private final org.optaplanner.core.api.solver.Solver solver;

  OptplannerSolver() {
    final SolverFactory factory = SolverFactory.createEmpty();
    final SolverConfig config = factory.getSolverConfig();

    config.setEntityClassList(ImmutableList.<Class<?>>of(ParcelVisit.class));
    config.setSolutionClass(PDPSolution.class);
    final TerminationConfig terminationConfig = config.getTerminationConfig();

    // continue reading:
    // https://docs.jboss.org/optaplanner/release/6.3.0.Final/optaplanner-docs/html_single/index.html
    // at section 5.3

    final ScoreDirectorFactoryConfig scoreConfig =
      new ScoreDirectorFactoryConfig();
    scoreConfig.setScoreDefinitionType(ScoreDefinitionType.HARD_SOFT_LONG);
    config.setScoreDirectorFactoryConfig(scoreConfig);

    config.setRandomSeed(123L);
    config.setRandomType(RandomType.MERSENNE_TWISTER);
    config.setEnvironmentMode(EnvironmentMode.FULL_ASSERT);

    solver = factory.buildSolver();
  }

  @Override
  public ImmutableList<ImmutableList<Parcel>> solve(GlobalStateObject state)
      throws InterruptedException {
    // TODO Auto-generated method stub
    return null;
  }

}
