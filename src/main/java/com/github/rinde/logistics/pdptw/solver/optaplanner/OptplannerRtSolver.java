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

import com.github.rinde.rinsim.central.GlobalStateObject;
import com.github.rinde.rinsim.central.rt.RealtimeSolver;
import com.github.rinde.rinsim.central.rt.Scheduler;

/**
 *
 * @author Rinde van Lon
 */
public class OptplannerRtSolver implements RealtimeSolver {

  @Override
  public void init(Scheduler scheduler) {
    // TODO Auto-generated method stub

  }

  @Override
  public void problemChanged(GlobalStateObject snapshot) {
    // TODO Auto-generated method stub

  }

  @Override
  public void receiveSnapshot(GlobalStateObject snapshot) {
    // TODO Auto-generated method stub

  }

  @Override
  public void cancel() {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isComputing() {
    // TODO Auto-generated method stub
    return false;
  }

}
