package com.github.rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.github.rinde.logistics.pdptw.mas.TruckConfiguration;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.Communicator;
import com.github.rinde.logistics.pdptw.mas.comm.SolverBidder;
import com.github.rinde.logistics.pdptw.mas.route.GotoClosestRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.RoutePlanner;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.rinsim.central.RandomSolver;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.experiment.Experiment;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06Parser;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06Scenario;
import com.github.rinde.rinsim.pdptw.gendreau06.GendreauProblemClass;
import com.github.rinde.rinsim.scenario.AddParcelEvent;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

@RunWith(Parameterized.class)
public class DiversionTruckIntegrationTest {

  static final ObjectiveFunction GENDREAU_OBJ_FUNC = Gendreau06ObjectiveFunction
      .instance();

  final TruckConfiguration truckConfig;
  final ObjectiveFunction objectiveFunction;
  final int numTrucks;

  public DiversionTruckIntegrationTest(TruckConfiguration tc,
      ObjectiveFunction objFunc, int numT) {
    truckConfig = tc;
    objectiveFunction = objFunc;
    numTrucks = numT;
  }

  @Parameters
  public static Collection<Object[]> configs() {

    final ImmutableList<StochasticSupplier<? extends RoutePlanner>> routePlanners = ImmutableList
        .<StochasticSupplier<? extends RoutePlanner>> of(
            GotoClosestRoutePlanner.supplier(),
            RandomRoutePlanner.supplier(),
            SolverRoutePlanner.supplier(RandomSolver.supplier())
        );

    final ImmutableList<StochasticSupplier<? extends Communicator>> communicators = ImmutableList
        .<StochasticSupplier<? extends Communicator>> of(SolverBidder
            .supplier(GENDREAU_OBJ_FUNC, RandomSolver.supplier()));

    final ImmutableList<Integer> numTrucks = ImmutableList
        .of(1, 2, 3, 4, 5, 10, 15);

    final List<Object[]> configs = newArrayList();
    for (final StochasticSupplier<? extends RoutePlanner> rp : routePlanners) {
      for (final StochasticSupplier<? extends Communicator> cm : communicators) {
        for (final int i : numTrucks) {
          configs.add(new Object[] {
              new TruckConfiguration(rp, cm, ImmutableList.of(AuctionCommModel
                  .supplier())), GENDREAU_OBJ_FUNC, i });
        }
      }
    }
    return configs;
  }

  @Test
  public void integration() {
    final Gendreau06Scenario scen = Gendreau06Parser.parse(new File(
        "files/scenarios/gendreau06/req_rapide_1_240_24"));

    final ImmutableList<AddParcelEvent> events = FluentIterable
        .from(scen.asList())
        .filter(AddParcelEvent.class)
        .limit(100)
        .toList();

    final List<Gendreau06Scenario> onlineScenarios = Gendreau06Parser
        .parser()
        .allowDiversion()
        .setNumVehicles(numTrucks)
        .addFile(events, "req_rapide_1_240_24")
        .filter(GendreauProblemClass.SHORT_LOW_FREQ)
        .parse();

    final Experiment.Builder builder = Experiment.build(objectiveFunction)
        .withRandomSeed(123)
        .repeat(1)
        .withThreads(1)
        .addScenarios(onlineScenarios);

    builder.addConfiguration(truckConfig);
    builder.perform();
  }
}
