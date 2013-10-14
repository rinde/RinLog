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
import rinde.sim.pdptw.common.DynamicPDPTWProblem.Creator;
import rinde.sim.pdptw.experiment.DefaultMASConfiguration;
import rinde.sim.pdptw.experiment.ExperimentTest;
import rinde.sim.pdptw.gendreau06.Gendreau06Parser;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
@RunWith(Parameterized.class)
public class CommTest implements TickListener {

  static CommunicatorCreator RANDOM_BIDDER = new CommunicatorCreator() {
    @Override
    public Communicator create() {
      return new RandomBidder(123);
    }
  };

  protected static CommunicationModelCreator AUCTION_COMM_MODEL = new CommunicationModelCreator() {
    @Override
    public AbstractCommModel<?> create() {
      return new AuctionCommModel();
    }
  };

  static CommunicatorCreator BLACKBOARD_USER = new CommunicatorCreator() {
    @Override
    public Communicator create() {
      return new BlackboardUser();
    }
  };

  static CommunicationModelCreator BLACKBOARD_COMM_MODEL = new CommunicationModelCreator() {
    @Override
    public AbstractCommModel<?> create() {
      return new BlackboardCommModel();
    }
  };

  protected final TestConfiguration configuration;
  protected DynamicPDPTWProblem problem;

  public CommTest(TestConfiguration c) {
    configuration = c;
  }

  @Test
  public void test() throws IOException {
    problem = ExperimentTest.init(Gendreau06Parser.parse(
        "files/scenarios/gendreau06/req_rapide_1_240_24", 10), configuration,
        false);

    problem.getSimulator().tick();
    problem.getSimulator().addTickListener(this);
    problem.simulate();
  }

  @Parameters
  public static Collection<Object[]> configs() {
    return asList(new Object[][] { /* */
    { new TestConfiguration(AUCTION_COMM_MODEL, RANDOM_BIDDER) }, /* */
    { new TestConfiguration(BLACKBOARD_COMM_MODEL, BLACKBOARD_USER) } /* */
    });
  }

  public static class TestConfiguration extends DefaultMASConfiguration {
    protected final CommunicatorCreator commCr;
    protected final List<Communicator> communicators;
    protected final AbstractCommModel<?> commModel;

    public TestConfiguration(CommunicationModelCreator cmc,
        CommunicatorCreator cc) {
      super(0L);
      commModel = cmc.create();
      commCr = cc;
      communicators = newArrayList();
    }

    @Override
    public ImmutableList<? extends Model<?>> getModels() {
      return ImmutableList.of(commModel);
    }

    @Override
    public Creator<AddVehicleEvent> getVehicleCreator() {
      return new Creator<AddVehicleEvent>() {
        @Override
        public boolean create(Simulator sim, AddVehicleEvent event) {
          final Communicator comm = commCr.create();
          communicators.add(comm);
          assertTrue("communicator should be registered", sim.register(comm));
          return sim.register(new Truck(event.vehicleDTO,
              new RandomRoutePlanner(randomSeed), comm));
        }
      };
    }
  }

  protected interface CommunicatorCreator {
    Communicator create();
  }

  protected interface CommunicationModelCreator {
    AbstractCommModel<?> create();
  }

  @Override
  public void tick(TimeLapse timeLapse) {

    final Optional<RoadModel> roadModel = Optional.fromNullable(problem
        .getSimulator().getModelProvider().getModel(RoadModel.class));
    for (final Communicator c : configuration.communicators) {
      assertTrue(
          "The communicator may only return parcels which are not yet picked up",
          roadModel.get().getObjectsOfType(Parcel.class)
              .containsAll(c.getParcels()));
    }
  }

  @Override
  public void afterTick(TimeLapse timeLapse) {}

}
