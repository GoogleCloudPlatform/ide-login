package com.google.cloud.tools.ide.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.api.client.auth.oauth2.Credential;
import org.junit.Test;

public class AccountRosterTest {

  private AccountRoster accountRoster = new AccountRoster();

  private static final Account[] fakeAccounts = new Account[] {
    new Account("email-1@example.com", mock(Credential.class), 0),
    new Account("email-2@example.com", mock(Credential.class), 0),
    new Account("email-3@example.com", mock(Credential.class), 0)
  };

  @Test(expected = NullPointerException.class)
  public void testAddAndSetActiveAccount_checkEmailIsNotNull() {
    accountRoster.addAndSetActiveAccount(new Account(null, mock(Credential.class), 0));
  }

  @Test(expected = NullPointerException.class)
  public void testAddAndSetActiveAccount_checkCredentialIsNotNull() {
    accountRoster.addAndSetActiveAccount(new Account("email@example.com", null, 0));
  }

  @Test
  public void testIsEmpty() {
    accountRoster.addAndSetActiveAccount(fakeAccounts[0]);
    assertFalse(accountRoster.isEmpty());
  }

  @Test(expected = NullPointerException.class)
  public void testGetActiveAccount_emptyRoster() {
    accountRoster.getActiveAccount();
  }

  @Test
  public void testGetActiveAccount() {
    accountRoster.addAndSetActiveAccount(fakeAccounts[0]);
    assertTrue(fakeAccounts[0] == accountRoster.getActiveAccount());
    accountRoster.addAndSetActiveAccount(fakeAccounts[1]);
    assertTrue(fakeAccounts[1] == accountRoster.getActiveAccount());
    accountRoster.addAndSetActiveAccount(fakeAccounts[2]);
    assertTrue(fakeAccounts[2] == accountRoster.getActiveAccount());
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
  public void testListAccounts() {
    addAllFakeAccounts();

    AccountsInfo accountsInfo = accountRoster.listAccounts();
    assertEquals(3, accountsInfo.size());
    assertEquals(accountsInfo.getActiveAccount().getEmail(), fakeAccounts[2].getEmail());
    assertTrue(fakeAccounts[2] == accountRoster.getActiveAccount());
  }

  @Test
  public void testSetActiveAccount_account1() {
    addAllFakeAccounts();
    accountRoster.switchActiveAccount(fakeAccounts[0].getEmail());

    AccountsInfo accountsInfo = accountRoster.listAccounts();
    assertEquals(3, accountsInfo.size());
    assertEquals(accountsInfo.getActiveAccount().getEmail(), fakeAccounts[0].getEmail());
    assertTrue(fakeAccounts[0] == accountRoster.getActiveAccount());
  }

  @Test
  public void testSetActiveAccount_account2() {
    addAllFakeAccounts();
    accountRoster.switchActiveAccount(fakeAccounts[1].getEmail());

    AccountsInfo accountsInfo = accountRoster.listAccounts();
    assertEquals(3, accountsInfo.size());
    assertEquals(accountsInfo.getActiveAccount().getEmail(), fakeAccounts[1].getEmail());
    assertTrue(fakeAccounts[1] == accountRoster.getActiveAccount());
  }

  @Test
  public void testSetActiveAccount_account3() {
    addAllFakeAccounts();
    accountRoster.switchActiveAccount(fakeAccounts[2].getEmail());

    AccountsInfo accountsInfo = accountRoster.listAccounts();
    assertEquals(3, accountsInfo.size());
    assertEquals(accountsInfo.getActiveAccount().getEmail(), fakeAccounts[2].getEmail());
    assertTrue(fakeAccounts[2] == accountRoster.getActiveAccount());
  }

  @Test
  public void testAddAndSetActiveAccount_replaceOldAccount() {
    addAllFakeAccounts();

    assertEquals(3, accountRoster.listAccounts().size());
    assertEquals(0, accountRoster.getActiveAccount().getAccessTokenExpiryTime());

    Account sameEmailAccount = new Account("email-3@example.com", mock(Credential.class), 123);
    accountRoster.addAndSetActiveAccount(sameEmailAccount);
    assertEquals(3, accountRoster.listAccounts().size());
    assertEquals(123, accountRoster.getActiveAccount().getAccessTokenExpiryTime());
  }

  private void addAllFakeAccounts() {
    accountRoster.addAndSetActiveAccount(fakeAccounts[0]);
    accountRoster.addAndSetActiveAccount(fakeAccounts[1]);
    accountRoster.addAndSetActiveAccount(fakeAccounts[2]);
  }
}
