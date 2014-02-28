package rinde.logistics.pdptw.mas;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayList;
import static rinde.sim.pdptw.generator.Metrics.travelTime;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.analysis.integration.RombergIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.PDPScenarioEvent;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.ScenarioIO;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.ExperimentUtil;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.generator.Analysis;
import rinde.sim.pdptw.generator.ArrivalTimesGenerator;
import rinde.sim.pdptw.generator.Metrics;
import rinde.sim.pdptw.generator.NHPoissonProcess;
import rinde.sim.pdptw.generator.NHPoissonProcess.IntensityFunctionWrapper;
import rinde.sim.pdptw.generator.NHPoissonProcess.SineIntensity;
import rinde.sim.pdptw.generator.ScenarioGenerator;
import rinde.sim.pdptw.generator.UrgencyProportionateUniformTWGenerator;
import rinde.sim.pdptw.vanlon14.VanLon14.ExperimentClass;
import rinde.sim.pdptw.vanlon14.VanLon14.VanLon14ScenarioFactory;
import rinde.sim.pdptw.vanlon14.VanLon14Scenario;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;

public class DynExpTest {

  public static void main(String[] args) {
    // generate();
    run();
  }

  static void run() {

    final String dir = "files/dataset/dynexp/urgency/";
    final List<String> files = ExperimentUtil.getFilesFromDir(dir, ".scenario");

    final List<VanLon14Scenario> scenarios = newArrayList();
    for (final String file : files) {

      final StringBuilder sb = new StringBuilder();
      try {
        Files.asCharSource(new File(file), Charsets.UTF_8).copyTo(sb);
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }

      scenarios.add(ScenarioIO.read(sb.toString(), VanLon14Scenario.class));
    }
    final ObjectiveFunction objFunc = new Gendreau06ObjectiveFunction();
    final ExperimentResults results = Experiment.build(objFunc)
        .addScenarios(scenarios).withThreads(22).withRandomSeed(123).repeat(1)
        // .showGui()

        // CLOSEST
        // .addConfiguration(
        // new TruckConfiguration(GotoClosestRoutePlanner.supplier(),
        // BlackboardUser.supplier(), ImmutableList.of(BlackboardCommModel
        // .supplier())))

        // RANDOM MAS
        // .addConfiguration(
        // new TruckConfiguration(RandomRoutePlanner.supplier(),
        // BlackboardUser.supplier(), ImmutableList.of(BlackboardCommModel
        // .supplier())))

        // RANDOM CENTRAL
        // .addConfiguration(
        // Central.solverConfiguration(RandomSolver.supplier(), "-Random"))

        // NEGOTIATION
        // .addConfiguration(
        // new TruckConfiguration(SolverRoutePlanner
        // .supplier(MultiVehicleHeuristicSolver.supplier(200, 50000)),
        // NegotiatingBidder.supplier(objFunc,
        // MultiVehicleHeuristicSolver.supplier(20, 10000),
        // MultiVehicleHeuristicSolver.supplier(200, 50000), 2,
        // SelectNegotiatorsHeuristic.FIRST_DESTINATION_POSITION),
        // ImmutableList.of(AuctionCommModel.supplier())))

        // CHEAPEST INSERTION CENTRAL
        .addConfiguration(
            Central.solverConfiguration(
                CheapestInsertionHeuristic.supplier(objFunc),
                "-CheapestInsertion"))

        // CENTRAL
        // .addConfiguration(
        // Central.solverConfiguration(
        // MultiVehicleHeuristicSolver.supplier(50, 1000), "-Online"))

        .perform();

    final HashMultimap<MASConfiguration, DataPoint> map = HashMultimap.create();
    for (final SimulationResult res : results.results) {
      final double dyn = Metrics.measureUrgency(res.scenario) / 60000d; // Metrics
                                                                        // .measureDynamism(res.scenario);
      final double cost = objFunc.computeCost(res.stats);
      map.put(res.masConfiguration, new DataPoint(dyn, cost));
    }

    for (final MASConfiguration config : map.keySet()) {

      final List<DataPoint> datapoints = newArrayList(map.get(config));
      Collections.sort(datapoints, new Comparator<DataPoint>() {
        @Override
        public int compare(DataPoint o1, DataPoint o2) {
          return Double.compare(o1.dyn, o1.dyn);
        }
      });

      final String name = config instanceof TruckConfiguration ? ((TruckConfiguration) config).rpSupplier
          .toString() : config.toString();
      try {
        Files.write(Joiner.on("\n").join(datapoints), new File(dir + name
            + ".txt"), Charsets.UTF_8);
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }

    }

  }

  static class DataPoint {
    public final double dyn;
    public final double cost;

    public DataPoint(double d, double c) {
      dyn = d;
      cost = c;
    }

    @Override
    public String toString() {
      return dyn + ", " + cost;
    }
  }

  static void generate() {
    final DateTimeFormatter formatter = ISODateTimeFormat
        .dateHourMinuteSecondMillis();
    final long length = 28800000;
    final int orders = 200;

    final RandomGenerator rng = new MersenneTwister(123);
    // int k = 1;
    final int j = 5;
    for (int k = 0; k < 10; k++) {
      final double vehicleSpeed = ScenarioGenerator.Builder.DEFAULT_VEHICLE_SPEED;
      final Point min = new Point(0, 0);
      final Point max = new Point(10, 10);
      final Point depotLocation = new Point(5, 5);
      final long time1 = travelTime(min, max, vehicleSpeed);
      final long time2 = travelTime(max, depotLocation, vehicleSpeed);
      final long travelTime = time1 + time2;

      final long serviceTime = 5;
      final long maxRequiredTime = (ScenarioGenerator.Builder.DEFAULT_MIN_RESPONSE_TIME
          + travelTime + (2 * serviceTime)) * 60000;
      final long latestOrderAnnounceTime = length - maxRequiredTime;

      final long minResponseTime = (0 * 60000) + (k * 5 * 60000);
      final long maxResponseTime = (5 * 60000) + (k * 5 * 60000);

      final ScenarioGenerator<VanLon14Scenario> scenGen = ScenarioGenerator
          .builder(new VanLon14ScenarioFactory(ExperimentClass.MEDIUM_MEDIUM))
          .setArrivalTimesGenerator(

              new TestArrivalTimes(orders, -.8 + (j * .2), j * .10d,
                  latestOrderAnnounceTime))
          .setScale(.1d, 10d)
          .setTimeWindowGenerator(
              new UrgencyProportionateUniformTWGenerator(depotLocation, length,
                  serviceTime * 60000, minResponseTime, maxResponseTime,
                  vehicleSpeed)).setScenarioLength(length).build();

      try {
        final File dir = new File("files/dataset/dynexp/urgency/");
        Files.createParentDirs(dir);
        checkState(dir.exists() || dir.mkdir(), "Could not create dir %s.", dir);
        for (int i = 0; i < 10; i++) {
          final VanLon14Scenario scen = scenGen.generate(rng);
          final double dyn = Metrics.measureDynamism(scen);

          final String scenarioName = "scen" + j + "-" + i + "-" + dyn;

          // Analysis.writeLoads(Metrics.measureRelativeLoad(scen), new
          // File(dir,
          // scenarioName + ".load"));
          Analysis.writeLocationList(Metrics.getServicePoints(scen), new File(
              dir, scenarioName + ".points"));
          Analysis.writeTimes(scen.getTimeWindow().end,
              Metrics.getArrivalTimes(scen), new File(dir, scenarioName
                  + ".times"));
          Metrics.checkTimeWindowStrictness(scen);
          final ImmutableMap.Builder<String, Object> properties = ImmutableMap
              .<String, Object> builder()
              .put("generation_date",
                  formatter.print(System.currentTimeMillis()))
              // FIXME
              // .put("dynamism", Metrics.measureDynamismOld(s))
              .put("vehicle_speed_kmh", Metrics.getVehicleSpeed(scen))
              .put("dynamism", dyn);

          final ImmutableMultiset<Enum<?>> eventTypes = Metrics
              .getEventTypeCounts(scen);
          for (final Multiset.Entry<Enum<?>> en : eventTypes.entrySet()) {
            properties.put(en.getElement().name(), en.getCount());
          }
          final double parcelToVehicleRatio = (double) eventTypes
              .count(PDPScenarioEvent.ADD_PARCEL)
              / eventTypes.count(PDPScenarioEvent.ADD_VEHICLE);
          properties.put("parcel_to_vehicle_ratio", parcelToVehicleRatio);

          // System.out.println(parcelToVehicleRatio + " " + eventTypes
          // .count(PDPScenarioEvent.ADD_PARCEL) + " "
          // + eventTypes.count(PDPScenarioEvent.ADD_VEHICLE));

          Files.write(
              Joiner.on("\n").withKeyValueSeparator(" = ")
                  .join(properties.build()), new File(dir, scenarioName
                  + ".properties"), Charsets.UTF_8);

          Files.write(ScenarioIO.write(scen), new File(dir, scenarioName
              + ".scenario"), Charsets.UTF_8);

        }
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  static class TestArrivalTimes implements ArrivalTimesGenerator {
    final ArrivalTimesGenerator generator;

    public TestArrivalTimes(int orders, double relHeight, double min,
        long length) {
      final double freq = 1d / 3600000d;
      final SineIntensity intensity = new SineIntensity(1d, freq, relHeight,
          min);
      final UnivariateIntegrator ri = new RombergIntegrator(16, 32);// new

      final double val = ri.integrate(10000000, new IntensityFunctionWrapper(
          intensity), 0, length);
      final double newAmpl = orders / val;

      final SineIntensity finalIntensity = new SineIntensity(newAmpl, freq,
          relHeight, newAmpl * min);
      generator = new NHPoissonProcess(length, finalIntensity);
    }

    @Override
    public ImmutableList<Double> generate(RandomGenerator rng) {
      return generator.generate(rng);
    }
  }
}
