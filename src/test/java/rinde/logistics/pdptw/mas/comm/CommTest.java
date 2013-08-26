/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import rinde.logistics.pdptw.mas.GSimulation.Configurator;
import rinde.logistics.pdptw.mas.GSimulationTestUtil;
import rinde.logistics.pdptw.mas.Truck;
import rinde.logistics.pdptw.mas.route.RandomRoutePlanner;
import rinde.sim.core.Simulator;
import rinde.sim.core.TickListener;
import rinde.sim.core.TimeLapse;
import rinde.sim.core.model.Model;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.road.RoadModel;
import rinde.sim.pdptw.common.AddVehicleEvent;
import rinde.sim.pdptw.common.DynamicPDPTWProblem;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
@RunWith(Parameterized.class)
public class CommTest implements TickListener {

  static CommunicatorCreator RANDOM_BIDDER = new CommunicatorCreator() {
    public Communicator create() {
      return new RandomBidder(123);
    }
  };

  protected static CommunicationModelCreator AUCTION_COMM_MODEL = new CommunicationModelCreator() {
    public AbstractCommModel<?> create() {
      return new AuctionCommModel();
    }
  };

  static CommunicatorCreator BLACKBOARD_USER = new CommunicatorCreator() {
    public Communicator create() {
      return new BlackboardUser();
    }
  };

  static CommunicationModelCreator BLACKBOARD_COMM_MODEL = new CommunicationModelCreator() {
    public AbstractCommModel<?> create() {
      return new BlackboardCommModel();
    }
  };

  protected final TestConfigurator configurator;
  protected DynamicPDPTWProblem problem;

  public CommTest(TestConfigurator c) {
    configurator = c;
  }

  @Test
  public void test() throws IOException {
    problem = GSimulationTestUtil
        .init(Gendreau06Parser
            .parse("files/scenarios/gendreau06/req_rapide_1_240_24", 10), configurator, false);

    problem.getSimulator().tick();
    problem.getSimulator().addTickListener(this);
    problem.simulate();
  }

  @Parameters
  public static Collection<Object[]> configs() {
    return asList(new Object[][] { /* */
    { new TestConfigurator(AUCTION_COMM_MODEL, RANDOM_BIDDER) }, /* */
    { new TestConfigurator(BLACKBOARD_COMM_MODEL, BLACKBOARD_USER) } /* */
    });
  }

  public static class TestConfigurator implements Configurator {
    protected final CommunicatorCreator commCr;
    protected final List<Communicator> communicators;
    protected final AbstractCommModel<?> commModel;

    public TestConfigurator(CommunicationModelCreator cmc,
        CommunicatorCreator cc) {
      commModel = cmc.create();
      commCr = cc;
      communicators = newArrayList();
    }

    public boolean create(Simulator sim, AddVehicleEvent event) {
      final Communicator comm = commCr.create();
      communicators.add(comm);
      assertTrue("communicator should be registered", sim.register(comm));
      return sim.register(new Truck(event.vehicleDTO, new RandomRoutePlanner(
          123), comm));
    }

    public Model<?>[] createModels() {
      return new Model<?>[] { commModel };
    }
  }

  protected interface CommunicatorCreator {
    Communicator create();
  }

  protected interface CommunicationModelCreator {
    AbstractCommModel<?> create();
  }

  public void tick(TimeLapse timeLapse) {
    final RoadModel roadModel = problem.getSimulator().getModelProvider()
        .getModel(RoadModel.class);
    for (final Communicator c : configurator.communicators) {
      assertTrue("The communicator may only return parcels which are not yet picked up", roadModel
          .getObjectsOfType(Parcel.class).containsAll(c.getParcels()));
    }
  }

  public void afterTick(TimeLapse timeLapse) {}

}
