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

import javax.annotation.Nullable;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents information about all logged-in accounts. The information is intended for use by UI.
 * Instantiated and returned only by {@link AccountRoster#listAccounts()}.
 *
 * @see GoogleLoginState#listAccounts()
 */
public class AccountsInfo {

  @Nullable private AccountInfo activeAccount;
  private Set<AccountInfo> inactiveAccounts = new HashSet<>();

  AccountsInfo(AccountRoster accountRoster) {
    for (Account account : accountRoster.getAccounts()) {
      if (account == accountRoster.getActiveAccount()) {
        activeAccount = new AccountInfo(account.getEmail(), "", null);
      } else {
        inactiveAccounts.add(new AccountInfo(account.getEmail(), "", null));
      }
    }
  }

  /**
   * Returns information about an active account. It is the account of the credential returned
   * from {@link GoogleLoginState#getActiveCredential}.
   */
  @Nullable
  public AccountInfo getActiveAccount() {
    return activeAccount;
  }

  public Set<AccountInfo> getInactiveAccounts() {
    return Collections.unmodifiableSet(inactiveAccounts);
  }

  @VisibleForTesting
  int size() {
    if (activeAccount == null) {
      return inactiveAccounts.size();
    } else {
      return inactiveAccounts.size() + 1;
    }
  }
}
