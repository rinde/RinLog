/**
 * 
 */
package rinde.logistics.pdptw.mas;

import rinde.logistics.pdptw.mas.GSimulation.Configurator;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.gendreau06.Gendreau06Scenario;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class GSimulationTestUtil {

  public static DynamicPDPTWProblem init(Gendreau06Scenario scenario,
      Configurator config, boolean showGui) {
    return GSimulation.init(scenario, config, showGui);
  }

}
