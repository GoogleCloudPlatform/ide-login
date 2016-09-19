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

import com.google.common.base.Preconditions;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Internal class instantiated as a singleton member of {@link GoogleLoginState}. Manages a list
 * of currently logged-in accounts.
 *
 * Not thread safe; {@link GoogleLoginState} must use it in a thread-safe way.
 */
class AccountRoster {

  private Set<Account> accounts = new HashSet<>();

  void clear() {
    accounts.clear();
  }

  boolean isEmpty() {
    return accounts.isEmpty();
  }

  void addAccount(Account account) {
    Preconditions.checkNotNull(account.getEmail());

    // Remove if there exists an account with the same email.
    for (Iterator<Account> iterator = accounts.iterator(); iterator.hasNext(); ) {
      Account existingAccount = iterator.next();
      if (existingAccount.getEmail().equals(account.getEmail())) {
        iterator.remove();
        break;
      }
    }

    accounts.add(account);
  }

  Set<Account> getAccounts() {
    return accounts;
  }
}
