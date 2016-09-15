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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal class instantiated as a singleton member of {@link GoogleLoginState}. Manages a list
 * of currently logged-in accounts.
 *
 * Not thread safe; {@link GoogleLoginState} must use it in a thread-safe way.
 */
class AccountRoster {

  private Account activeAccount;
  private List<Account> accounts = new ArrayList<>();

  void clear() {
    activeAccount = null;
    accounts.clear();
  }

  boolean isEmpty() {
    return accounts.isEmpty();
  }

  void addAndSetActiveAccount(Account account) {
    Preconditions.checkNotNull(account.getEmail());

    activeAccount = account;
    accounts.add(account);
  }

  Account getActiveAccount() {
    Preconditions.checkNotNull(activeAccount);
    Preconditions.checkState(!accounts.isEmpty());

    return activeAccount;
  }

  /**
   * @return true if an account existed with the {@code email} and became active; false otherwise.
   * @see GoogleLoginState#setActiveAccount
   */
  boolean setActiveAccount(String email) {
    Preconditions.checkNotNull(email);

    for (Account account : accounts) {
      if (account.getEmail().equals(email)) {
        activeAccount = account;
        return true;
      }
    }
    return false;
  }

  /**
   * @see GoogleLoginState#listAccounts
   */
  AccountsInfo listAccounts() {
    return new AccountsInfo(this);
  }

  @VisibleForTesting
  List<Account> getAccounts() {
    return accounts;
  }
}
