/*******************************************************************************
 * Copyright 2014 Google Inc. All Rights Reserved.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.google.cloud.tools.ide.login;

import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;
import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Uses the standard Java Preferences for storing a particular user's {@link OAuthData} object persistently,
 * retrieving it, and clearing it.
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

  JavaPreferenceOAuthDataStore(String preferencePath, LoggerFacade logger) {
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
    Preconditions.checkNotNull(credential.getStoredScopes());  // Why? See OAuthData Javadoc.
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
