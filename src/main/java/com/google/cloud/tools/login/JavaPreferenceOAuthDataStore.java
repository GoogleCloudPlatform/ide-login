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

package com.google.cloud.tools.login;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Uses the standard Java Preferences for storing a particular user's {@link OAuthData} object
 * persistently, retrieving it, and clearing it.
 *
 * Not thread-safe.
 */
public class JavaPreferenceOAuthDataStore implements OAuthDataStore {

  private final String preferencePath;
  private final LoggerFacade logger;

  private static final String KEY_ACCESS_TOKEN = "access_token"; //$NON-NLS-1$
  private static final String KEY_REFRESH_TOKEN = "refresh_token"; //$NON-NLS-1$
  private static final String KEY_ACCESS_TOKEN_EXPIRY_TIME = "access_token_expiry_time"; //$NON-NLS-1$
  private static final String KEY_ACCOUNT_NAME = "account_name"; //$NON-NLS-1$
  private static final String KEY_AVATAR_URL = "avatar_url"; //$NON-NLS-1$
  private static final String KEY_OAUTH_SCOPES = "oauth_scopes"; //$NON-NLS-1$

  @VisibleForTesting
  static final String SCOPE_DELIMITER = " "; //$NON-NLS-1$

  private Preferences root;

  /**
   * @param preferencePath the path name of the root preference node. This node must be exclusive
   *     to this data store (i.e., must not be shared to save other preferences).
   */
  public JavaPreferenceOAuthDataStore(String preferencePath, LoggerFacade logger) {
    this(preferencePath, logger, Preferences.userRoot());
  }

  @VisibleForTesting
  JavaPreferenceOAuthDataStore(String preferencePath, LoggerFacade logger, Preferences root) {
    this.preferencePath = preferencePath;
    this.logger = logger;
    this.root = root;
  }


  @Override
  public void removeOAuthData(String email) throws IOException {
    removeNode(root.node(preferencePath).node(email));
  }

  @Override
  public void clearStoredOAuthData() throws IOException {
    removeNode(root.node(preferencePath));
  }

  @Override
  public void saveOAuthData(OAuthData oAuthData) throws IOException {
    Preconditions.checkNotNull(oAuthData.getEmail());
    Preconditions.checkNotNull(oAuthData.getStoredScopes());
    for (String scopes : oAuthData.getStoredScopes()) {
      Preconditions.checkArgument(
          !scopes.contains(SCOPE_DELIMITER), "Scopes must not have a delimiter character."); //$NON-NLS-1$
    }

    Preferences accountNode =
        root.node(preferencePath).node(oAuthData.getEmail());

    accountNode.put(KEY_ACCESS_TOKEN, Strings.nullToEmpty(oAuthData.getAccessToken()));
    accountNode.put(KEY_REFRESH_TOKEN, Strings.nullToEmpty(oAuthData.getRefreshToken()));
    accountNode.putLong(KEY_ACCESS_TOKEN_EXPIRY_TIME, oAuthData.getAccessTokenExpiryTime());
    accountNode.put(KEY_ACCOUNT_NAME, Strings.nullToEmpty(oAuthData.getName()));
    accountNode.put(KEY_AVATAR_URL, Strings.nullToEmpty(oAuthData.getAvatarUrl()));
    accountNode.put(KEY_OAUTH_SCOPES, Joiner.on(SCOPE_DELIMITER).join(oAuthData.getStoredScopes()));

    try {
      accountNode.flush();
    } catch (BackingStoreException | SecurityException ex) {
      logger.logWarning("Could not flush preferences: " + ex.getMessage()); //$NON-NLS-1$
      throw new IOException(ex);
    }
  }

  @Override
  public Set<OAuthData> loadOAuthData() throws IOException {
    Set<OAuthData> oAuthDataSet = new HashSet<>();
    try {
      Preferences prefs = root.node(preferencePath);

      for (String email : prefs.childrenNames()) {
        Preferences accountNode = prefs.node(email);

        String accessToken = Strings.emptyToNull(accountNode.get(KEY_ACCESS_TOKEN, null));
        String refreshToken = Strings.emptyToNull(accountNode.get(KEY_REFRESH_TOKEN, null));
        long accessTokenExpiryTime = accountNode.getLong(KEY_ACCESS_TOKEN_EXPIRY_TIME, 0);
        String name = Strings.emptyToNull(accountNode.get(KEY_ACCOUNT_NAME, null));
        String avatarUrl = Strings.emptyToNull(accountNode.get(KEY_AVATAR_URL, null));
        String scopesString = accountNode.get(KEY_OAUTH_SCOPES, ""); //$NON-NLS-1$

        Set<String> oAuthScopes = new HashSet<>(
            Splitter.on(SCOPE_DELIMITER).omitEmptyStrings().splitToList(scopesString));

        oAuthDataSet.add(new OAuthData(
            accessToken, refreshToken, email, name, avatarUrl, oAuthScopes, accessTokenExpiryTime));
      }
    } catch (BackingStoreException | SecurityException ex) {
      logger.logWarning("Could not load preferences: " + ex.getMessage()); //$NON-NLS-1$
      throw new IOException(ex);
    }
    return oAuthDataSet;
  }

  private void removeNode(Preferences node) throws IOException {
    try {
      node.removeNode();
      node.flush();
    } catch (IllegalStateException ise) {
      // Thrown if this node (or an ancestor) has already been removed; ignore.
    } catch (BackingStoreException | SecurityException ex) {
      logger.logWarning("Could not remove preferences node: " + ex.getMessage()); //$NON-NLS-1$
      throw new IOException(ex);
    }
  }
}
