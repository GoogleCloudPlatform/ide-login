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
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Uses the standard Java Preferences for storing a particular user's {@link OAuthData} object
 * persistently, retrieving it, and clearing it.
 */
public class JavaPreferenceOAuthDataStore implements OAuthDataStore {

  private String preferencePath;
  private LoggerFacade logger;

  private static final String KEY_ACCESS_TOKEN = "access_token";
  private static final String KEY_REFRESH_TOKEN = "refresh_token";
  private static final String KEY_ACCESS_TOKEN_EXPIRY_TIME = "access_token_expiry_time";
  private static final String KEY_OAUTH_SCOPES = "oauth_scopes";

  @VisibleForTesting
  static final String SCOPE_DELIMITER = " ";

  public JavaPreferenceOAuthDataStore(String preferencePath, LoggerFacade logger) {
    this.preferencePath = preferencePath;
    this.logger = logger;
  }

  @Override
  public void clearStoredOAuthData() {
    removeNode(Preferences.userRoot().node(preferencePath));
  }

  @Override
  public void removeOAuthData(String email) {
    removeNode(Preferences.userRoot().node(preferencePath).node(email));
  }

  @Override
  public void saveOAuthData(OAuthData oAuthData) {
    Preconditions.checkNotNull(oAuthData.getEmail());
    Preconditions.checkNotNull(oAuthData.getStoredScopes());
    for (String scopes : oAuthData.getStoredScopes()) {
      Preconditions.checkArgument(
          !scopes.contains(SCOPE_DELIMITER), "Scopes must not have a delimiter character.");
    }

    Preferences accountNode =
        Preferences.userRoot().node(preferencePath).node(oAuthData.getEmail());

    accountNode.put(KEY_ACCESS_TOKEN, Strings.nullToEmpty(oAuthData.getAccessToken()));
    accountNode.put(KEY_REFRESH_TOKEN, Strings.nullToEmpty(oAuthData.getRefreshToken()));
    accountNode.putLong(KEY_ACCESS_TOKEN_EXPIRY_TIME, oAuthData.getAccessTokenExpiryTime());
    accountNode.put(KEY_OAUTH_SCOPES, Joiner.on(SCOPE_DELIMITER).join(oAuthData.getStoredScopes()));

    try {
      accountNode.flush();
    } catch (BackingStoreException bse) {
      logger.logWarning("Could not flush preferences: " + bse.getMessage());
    }
  }

  @Override
  public Set<OAuthData> loadOAuthData() {
    Set<OAuthData> oAuthDataSet = new HashSet<>();
    try {
      Preferences prefs = Preferences.userRoot().node(preferencePath);

      for (String email : prefs.childrenNames()) {
        Preferences accountNode = prefs.node(email);

        String accessToken = Strings.emptyToNull(accountNode.get(KEY_ACCESS_TOKEN, null));
        String refreshToken = Strings.emptyToNull(accountNode.get(KEY_REFRESH_TOKEN, null));
        long accessTokenExpiryTime = accountNode.getLong(KEY_ACCESS_TOKEN_EXPIRY_TIME, 0);
        String scopesString = accountNode.get(KEY_OAUTH_SCOPES, "");

        Set<String> oAuthScopes = new HashSet<>(
            Splitter.on(SCOPE_DELIMITER).omitEmptyStrings().splitToList(scopesString));

        oAuthDataSet.add(
            new OAuthData(accessToken, refreshToken, email, oAuthScopes, accessTokenExpiryTime));
      }
    } catch (BackingStoreException bse) {
      logger.logWarning("Could not flush preferences: " + bse.getMessage());
    }
    return oAuthDataSet;
  }

  private void removeNode(Preferences node) {
    try {
      node.removeNode();
      node.flush();
    } catch (BackingStoreException bse) {
      logger.logWarning("Could not remove preferences node: " + bse.getMessage());
    }
  }
}
