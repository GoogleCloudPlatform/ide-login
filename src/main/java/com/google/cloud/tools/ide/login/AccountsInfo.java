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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents information about all logged-in accounts. The information is intended for use by UI.
 * Instantiated and returned only by {@link AccountRoster#listAccounts()}.
 *
 * @see GoogleLoginState#listAccounts()
 */
public class AccountsInfo {

  @Nullable private AccountInfo activeAccount;
  private List<AccountInfo> inactiveAccounts = new ArrayList<>();

  AccountsInfo(AccountRoster accountRoster) {
    for (Account account : accountRoster.getAccounts()) {
      if (account == accountRoster.getActiveAccount()) {
        activeAccount = new AccountInfo(account.getEmail(), "", "");
      } else {
        inactiveAccounts.add(new AccountInfo(account.getEmail(), "", ""));
      }
    }
  }

  @Nullable
  public AccountInfo getActiveAccount() {
    return activeAccount;
  }

  public List<AccountInfo> getInactiveAccounts() {
    return Collections.unmodifiableList(inactiveAccounts);
  }

  public static class AccountInfo {
    private String email;
    @Nullable private String name;
    @Nullable private String avatarUrl;

    private AccountInfo(String email, @Nullable String name, @Nullable String avatarUrl) {
      this.email = email;
      this.name = name;
      this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
      return email;
    }

    @Nullable
    public String getName() {
      return name;
    }

    @Nullable
    public String getAvatarUrl() {
      return avatarUrl;
    }
  }

  @VisibleForTesting
  int size() {
    return inactiveAccounts.size() + ((activeAccount == null) ? 0 : 1);
  }
}
