package com.github.rinde.logistics.pdptw.mas.comm;

import static com.github.rinde.logistics.pdptw.mas.comm.Communicator.CommunicatorEventType.CHANGE;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.github.rinde.logistics.pdptw.mas.comm.BlackboardCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.BlackboardUser;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.pdptw.DefaultParcel;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO;
import com.github.rinde.rinsim.event.ListenerEventHistory;
import com.github.rinde.rinsim.geom.Point;

/**
 * @author Rinde van Lon 
 * 
 */
public class BlackboardTest {

  @SuppressWarnings("null")
  BlackboardCommModel model;
  @SuppressWarnings("null")
  List<BlackboardUser> users;
  @SuppressWarnings("null")
  List<ListenerEventHistory> listeners;

  /**
   * Sets up several blackboard users.
   */
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

  /**
   * Test claiming of parcels.
   */
  @Test
  public void testClaim() {
    final DefaultParcel p = parcel();
    assertEquals(new HashSet<Parcel>(), model.getUnclaimedParcels());
    model.receiveParcel(p, 0);
    assertEquals(newHashSet(p), model.getUnclaimedParcels());

    users.get(0).claim(p);
    assertTrue(model.getUnclaimedParcels().isEmpty());
    assertEquals(newHashSet(p), users.get(0).getParcels());

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
   * Tests a scenario where a parcel is claimed, unclaimed, claimed again and
   * then done.
   */
  @Test
  public void claimUnclaimScenario() {
    final DefaultParcel p = parcel();
    model.receiveParcel(p, 0L);
    for (int i = 0; i < users.size(); i++) {
      final BlackboardUser bu = users.get(i);
      final ListenerEventHistory li = listeners.get(i);
      assertEquals(1, bu.getParcels().size());
      assertTrue(bu.getClaimedParcels().isEmpty());
      assertEquals(1, li.getHistory().size());
    }

    users.get(0).claim(p);
    for (int i = 0; i < users.size(); i++) {
      final BlackboardUser bu = users.get(i);
      final ListenerEventHistory li = listeners.get(i);
      if (i == 0) {
        assertEquals(1, bu.getClaimedParcels().size());
        assertEquals(1, bu.getParcels().size());
        assertEquals(1, li.getHistory().size());
      } else {
        assertTrue(bu.getClaimedParcels().isEmpty());
        assertTrue(bu.getParcels().isEmpty());
        assertEquals(2, li.getHistory().size());
      }
    }

    users.get(0).unclaim(p);
    for (int i = 0; i < users.size(); i++) {
      final BlackboardUser bu = users.get(i);
      final ListenerEventHistory li = listeners.get(i);
      assertEquals(1, bu.getParcels().size());
      assertTrue(bu.getClaimedParcels().isEmpty());
      if (i == 0) {
        assertEquals(1, li.getHistory().size());
      } else {
        assertEquals(3, li.getHistory().size());
      }
    }

    users.get(0).claim(p);
    for (int i = 0; i < users.size(); i++) {
      final BlackboardUser bu = users.get(i);
      final ListenerEventHistory li = listeners.get(i);
      if (i == 0) {
        assertEquals(1, bu.getClaimedParcels().size());
        assertEquals(1, bu.getParcels().size());
        assertEquals(1, li.getHistory().size());
      } else {
        assertTrue(bu.getClaimedParcels().isEmpty());
        assertTrue(bu.getParcels().isEmpty());
        assertEquals(4, li.getHistory().size());
      }
    }

    users.get(0).done();
    for (int i = 0; i < users.size(); i++) {
      final BlackboardUser bu = users.get(i);
      final ListenerEventHistory li = listeners.get(i);
      assertTrue(bu.getClaimedParcels().isEmpty());
      assertTrue(bu.getParcels().isEmpty());
      if (i == 0) {
        assertEquals(1, li.getHistory().size());
      } else {
        assertEquals(4, li.getHistory().size());
      }
    }
  }

  /**
   * Test the check for double claims.
   */
  @Test
  public void claimFail() {
    final DefaultParcel p = parcel();
    model.receiveParcel(p, 0);
    users.get(0).claim(p);
    // try claim via user
    boolean fail = false;
    try {
      users.get(0).claim(p);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
    // try claim directly on model
    fail = false;
    try {
      model.claim(users.get(0), p);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  /**
   * Tests defensive checks of unclaim.
   */
  @Test
  public void unclaimFail() {
    boolean fail = false;
    try {
      users.get(0).unclaim(parcel());
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
    fail = false;
    final DefaultParcel p = parcel();
    model.receiveParcel(p, 0L);
    try {
      model.unclaim(users.get(0), p);
    } catch (final IllegalArgumentException e) {
      fail = true;
    }
    assertTrue(fail);
  }

  /**
   * Test the suppliers.
   */
  @Test
  public void supplierTest() {
    assertNotNull(BlackboardCommModel.supplier().get(0));
    assertNotNull(BlackboardUser.supplier().get(0));
  }

  static DefaultParcel parcel() {
    return new DefaultParcel(ParcelDTO
        .builder(new Point(0, 0), new Point(1, 1)).build());
  }
}
