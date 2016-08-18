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
  private static final String KEY_EMAIL = "email";
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
    Preferences prefs = Preferences.userRoot().node(preferencePath);

    prefs.remove(KEY_ACCESS_TOKEN);
    prefs.remove(KEY_REFRESH_TOKEN);
    prefs.remove(KEY_EMAIL);
    prefs.remove(KEY_OAUTH_SCOPES);
    prefs.remove(KEY_ACCESS_TOKEN_EXPIRY_TIME);
    flushPrefs(prefs);
  }

  @Override
  public void saveOAuthData(OAuthData credential) {
    // We rely on the fact that OAuthData.getStoredScopes() never returns null.
    Preconditions.checkNotNull(credential.getStoredScopes());
    for (String scopes : credential.getStoredScopes()) {
      Preconditions.checkArgument(
          !scopes.contains(SCOPE_DELIMITER), "Scopes must not have a delimiter character.");
    }

    Preferences prefs = Preferences.userRoot().node(preferencePath);

    prefs.put(KEY_ACCESS_TOKEN, Strings.nullToEmpty(credential.getAccessToken()));
    prefs.put(KEY_REFRESH_TOKEN, Strings.nullToEmpty(credential.getRefreshToken()));
    prefs.put(KEY_EMAIL, Strings.nullToEmpty(credential.getStoredEmail()));
    prefs.putLong(KEY_ACCESS_TOKEN_EXPIRY_TIME, credential.getAccessTokenExpiryTime());
    prefs.put(KEY_OAUTH_SCOPES, Joiner.on(SCOPE_DELIMITER).join(credential.getStoredScopes()));

    flushPrefs(prefs);
  }

  @Override
  public OAuthData loadOAuthData() {
    Preferences prefs = Preferences.userRoot().node(preferencePath);

    String accessToken = Strings.emptyToNull(prefs.get(KEY_ACCESS_TOKEN, null));
    String refreshToken = Strings.emptyToNull(prefs.get(KEY_REFRESH_TOKEN, null));
    String email = Strings.emptyToNull(prefs.get(KEY_EMAIL, null));
    long accessTokenExpiryTime = prefs.getLong(KEY_ACCESS_TOKEN_EXPIRY_TIME, 0);
    String scopesString = prefs.get(KEY_OAUTH_SCOPES, "");

    Set<String> oauthScopes = new HashSet<>(
        Splitter.on(SCOPE_DELIMITER).omitEmptyStrings().splitToList(scopesString));

    return new OAuthData(accessToken, refreshToken, email, oauthScopes, accessTokenExpiryTime);
  }

  private void flushPrefs(Preferences prefs) {
    try {
      prefs.flush();
    } catch (BackingStoreException bse) {
      logger.logWarning("Could not flush preferences: " + bse.getMessage());
    }
  }
}
