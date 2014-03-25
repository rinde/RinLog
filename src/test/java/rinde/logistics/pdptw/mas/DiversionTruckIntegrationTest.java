package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.Communicator;
import rinde.logistics.pdptw.mas.comm.SolverBidder;
import rinde.logistics.pdptw.mas.route.GotoClosestRoutePlanner;
import rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import rinde.logistics.pdptw.mas.route.RoutePlanner;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.sim.pdptw.central.RandomSolver;
import rinde.sim.pdptw.common.AddParcelEvent;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;
import rinde.sim.pdptw.gendreau06.GendreauProblemClass;
import rinde.sim.util.SupplierRng;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

@RunWith(Parameterized.class)
public class DiversionTruckIntegrationTest {

  static final ObjectiveFunction GENDREAU_OBJ_FUNC = new Gendreau06ObjectiveFunction();

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

    final ImmutableList<SupplierRng<? extends RoutePlanner>> routePlanners = ImmutableList
        .<SupplierRng<? extends RoutePlanner>> of(
            GotoClosestRoutePlanner.supplier(),
            RandomRoutePlanner.supplier(),
            SolverRoutePlanner.supplier(RandomSolver.supplier())
        );

    final ImmutableList<SupplierRng<? extends Communicator>> communicators = ImmutableList
        .<SupplierRng<? extends Communicator>> of(SolverBidder
            .supplier(GENDREAU_OBJ_FUNC, RandomSolver.supplier()));

    final ImmutableList<Integer> numTrucks = ImmutableList
        .of(1, 2, 3, 4, 5, 10, 15);

    final List<Object[]> configs = newArrayList();
    for (final SupplierRng<? extends RoutePlanner> rp : routePlanners) {
      for (final SupplierRng<? extends Communicator> cm : communicators) {
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
