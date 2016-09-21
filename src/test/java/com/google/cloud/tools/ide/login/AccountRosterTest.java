/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.ide.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.google.api.client.auth.oauth2.Credential;
import org.junit.Test;

import java.util.Set;

public class AccountRosterTest {

  private AccountRoster accountRoster = new AccountRoster();

  private static final Account[] fakeAccounts = new Account[] {
    new Account("email-1@example.com", mock(Credential.class)),
    new Account("email-2@example.com", mock(Credential.class)),
    new Account("email-3@example.com", mock(Credential.class))
  };

  @Test(expected = NullPointerException.class)
  public void testAddAccount_checkEmailIsNotNull() {
    accountRoster.addAccount(new Account(null, mock(Credential.class)));
  }

  @Test(expected = NullPointerException.class)
  public void testAddAccount_checkCredentialIsNotNull() {
    accountRoster.addAccount(new Account("email@example.com", null));
  }

  @Test
  public void testIsEmpty() {
    accountRoster.addAccount(fakeAccounts[0]);
    assertFalse(accountRoster.isEmpty());
  }

  @Test
  public void testListAccounts_emptyAccounts() {
    assertNotNull(accountRoster.getAccounts());
    assertEquals(0, accountRoster.getAccounts().size());
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

    Set<Account> accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    assertTrue(accounts.contains(fakeAccounts[0]));
    assertTrue(accounts.contains(fakeAccounts[1]));
    assertTrue(accounts.contains(fakeAccounts[2]));
  }

  @Test
  public void testAddAccount_replaceOldAccount() {
    addAllFakeAccounts();

    Set<Account> accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    assertTrue(accounts.contains(fakeAccounts[2]));

    Account sameEmailAccount = new Account("email-3@example.com", mock(Credential.class));
    accountRoster.addAccount(sameEmailAccount);

    accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    for (Account account : accounts) {
      assertFalse(account == fakeAccounts[2]);
    }

    boolean replaced = false;
    for (Account account : accounts) {
      if (account == sameEmailAccount) {
        replaced = true;
      }
    }
    assertTrue(replaced);
  }

  private void addAllFakeAccounts() {
    accountRoster.addAccount(fakeAccounts[0]);
    accountRoster.addAccount(fakeAccounts[1]);
    accountRoster.addAccount(fakeAccounts[2]);
  }
}
