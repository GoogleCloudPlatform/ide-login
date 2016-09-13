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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Internal class instantiated as a singleton member of {@link GoogleLoginState}. Manages a list
 * of currently logged-in accounts. The first account in the list, if exists, is always the
 * active account.
 */
class AccountRoster {

  private LinkedList<Account> accounts = new LinkedList<>();

  void clear() {
    accounts.clear();
  }

  boolean isEmpty() {
    return accounts.isEmpty();
  }

  void addActiveAccount(Account account) {
    Preconditions.checkNotNull(account.getEmail());

    accounts.addFirst(account);
  }

  Account getActiveAccount() {
    Preconditions.checkState(!accounts.isEmpty());

    return accounts.getFirst();
  }

  /**
   * @see GoogleLoginState#setActiveAccount
   */
  void setActiveAccount(String email) {
    Preconditions.checkNotNull(email);

    // Find the account and place it at the head.
    for (Iterator<Account> iterator = accounts.iterator(); iterator.hasNext(); ) {
      Account account = iterator.next();
      if (account.getEmail().equals(email)) {
        iterator.remove();
        accounts.addFirst(account);
        break;
      }
    }
  }

  /**
   * @see GoogleLoginState#listAccounts
   */
  List<AccountInfo> listAccounts() {
    ArrayList accountInfoList = new ArrayList();
    for (Account account : accounts) {
      // TODO(chanseok): fetch and provide real names and avatar images
      accountInfoList.add(new AccountInfo(account.getEmail(), "", null));
    }
    return accountInfoList;
  }

  @VisibleForTesting
  List<Account> getAccounts() {
    return accounts;
  }
}
