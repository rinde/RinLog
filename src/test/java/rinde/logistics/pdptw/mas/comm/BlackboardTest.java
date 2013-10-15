/**
 * 
 */
package rinde.logistics.pdptw.mas.comm;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType.CHANGE;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import rinde.sim.core.graph.Point;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.event.ListenerEventHistory;
import rinde.sim.pdptw.common.DefaultParcel;
import rinde.sim.pdptw.common.ParcelDTO;
import rinde.sim.util.TimeWindow;

/**
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 * 
 */
public class BlackboardTest {

  BlackboardCommModel model;
  List<BlackboardUser> users;
  List<ListenerEventHistory> listeners;

  @Before
  public void setUp() {
    model = new BlackboardCommModel();
    users = newArrayList();
    listeners = newArrayList();

    for (int i = 0; i < 5; i++) {
      final BlackboardUser bu = new BlackboardUser();
      users.add(bu);
      model.register(bu);
      final ListenerEventHistory l = new ListenerEventHistory();
      bu.addUpdateListener(l);
      listeners.add(l);
    }

    assertEquals(users, model.communicators);
  }

  @Test
  public void test1() {
    final DefaultParcel p = create();
    assertEquals(new HashSet<Parcel>(), model.getUnclaimedParcels());
    model.receiveParcel(p, 0);
    assertEquals(newHashSet(p), model.getUnclaimedParcels());

    users.get(0).claim(p);
    assertEquals(new HashSet<Parcel>(), model.getUnclaimedParcels());
    assertEquals(users.get(0).getParcels(), model.getUnclaimedParcels());

    // the user doing the claim should have dispatched only 1 update event
    // (based on the new parcel).
    assertEquals(asList(CHANGE), listeners.get(0).getEventTypeHistory());
    // all other users should also dispatch an event for the claim
    for (int i = 1; i < 5; i++) {
      assertEquals(asList(CHANGE, CHANGE), listeners.get(i)
          .getEventTypeHistory());
    }
  }

  /**
   * Test the check for double claims.
   */
  @Test
  public void claimFail() {
    final DefaultParcel p = create();
    model.receiveParcel(p, 0);
    users.get(0).claim(p);
    boolean fail = false;
    try {
      users.get(0).claim(p);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  DefaultParcel create() {
    return new DefaultParcel(new ParcelDTO(new Point(0, 0), new Point(1, 1),
        TimeWindow.ALWAYS, TimeWindow.ALWAYS, 0, 0, 0, 0));
  }

}
