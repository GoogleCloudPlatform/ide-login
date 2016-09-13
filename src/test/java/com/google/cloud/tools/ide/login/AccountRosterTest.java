package com.google.cloud.tools.ide.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class AccountRosterTest {

  private AccountRoster accountRoster = new AccountRoster();

  private Account[] fakeAccounts;

  @Before
  public void setUp() {
    fakeAccounts = new Account[] { new Account("email-1@example.com", null, 0),
                                   new Account("email-2@example.com", null, 0),
                                   new Account("email-3@example.com", null, 0) };
  }

  @Test(expected = NullPointerException.class)
  public void testAddActiveAccount_checkEmailIsNotNull() {
    accountRoster.addActiveAccount(new Account(null, null, 0));
  }

  @Test
  public void testIsEmpty() {
    accountRoster.addActiveAccount(fakeAccounts[0]);
    assertFalse(accountRoster.isEmpty());
  }

  @Test
  public void testGetActiveAccount() {
    accountRoster.addActiveAccount(fakeAccounts[0]);
    assertEquals(fakeAccounts[0], accountRoster.getActiveAccount());
    accountRoster.addActiveAccount(fakeAccounts[1]);
    assertEquals(fakeAccounts[1], accountRoster.getActiveAccount());
    accountRoster.addActiveAccount(fakeAccounts[2]);
    assertEquals(fakeAccounts[2], accountRoster.getActiveAccount());
  }

  @Test
  public void testListAccounts_emptyAccounts() {
    assertNotNull(accountRoster.listAccounts());
    assertEquals(0, accountRoster.listAccounts().size());
  }

  @Test
  public void testClear() {
    addAllFakeAccounts();
    accountRoster.clear();
    assertTrue(accountRoster.isEmpty());
  }

  @Test
  public void testListAccounts_reverseOrder() {
    addAllFakeAccounts();

    List<AccountInfo> accountInfoList = accountRoster.listAccounts();
    assertEquals(3, accountInfoList.size());
    assertEquals(accountInfoList.get(0).email, fakeAccounts[2].getEmail());
    assertEquals(accountInfoList.get(1).email, fakeAccounts[1].getEmail());
    assertEquals(accountInfoList.get(2).email, fakeAccounts[0].getEmail());
  }

  @Test
  public void testSetActiveAccount_setHeadActive() {
    addAllFakeAccounts();
    accountRoster.setActiveAccount(fakeAccounts[2].getEmail());

    List<AccountInfo> accountInfoList = accountRoster.listAccounts();
    assertEquals(accountInfoList.get(0).email, fakeAccounts[2].getEmail());
    assertEquals(accountInfoList.get(1).email, fakeAccounts[1].getEmail());
    assertEquals(accountInfoList.get(2).email, fakeAccounts[0].getEmail());
    assertEquals(fakeAccounts[2], accountRoster.getActiveAccount());
  }

  @Test
  public void testSetActiveAccount_setTailActive() {
    addAllFakeAccounts();
    accountRoster.setActiveAccount(fakeAccounts[0].getEmail());

    List<AccountInfo> accountInfoList = accountRoster.listAccounts();
    assertEquals(accountInfoList.get(0).email, fakeAccounts[0].getEmail());
    assertEquals(accountInfoList.get(1).email, fakeAccounts[2].getEmail());
    assertEquals(accountInfoList.get(2).email, fakeAccounts[1].getEmail());
    assertEquals(fakeAccounts[0], accountRoster.getActiveAccount());
  }

  @Test
  public void testSetActiveAccount_setMidBodyActive() {
    addAllFakeAccounts();
    accountRoster.setActiveAccount(fakeAccounts[1].getEmail());

    List<AccountInfo> accountInfoList = accountRoster.listAccounts();
    assertEquals(accountInfoList.get(0).email, fakeAccounts[1].getEmail());
    assertEquals(accountInfoList.get(1).email, fakeAccounts[2].getEmail());
    assertEquals(accountInfoList.get(2).email, fakeAccounts[0].getEmail());
    assertEquals(fakeAccounts[1], accountRoster.getActiveAccount());
  }

  private void addAllFakeAccounts() {
    accountRoster.addActiveAccount(fakeAccounts[0]);
    accountRoster.addActiveAccount(fakeAccounts[1]);
    accountRoster.addActiveAccount(fakeAccounts[2]);
  }
}
