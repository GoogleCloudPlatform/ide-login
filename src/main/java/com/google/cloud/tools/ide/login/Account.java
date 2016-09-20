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

import com.google.api.client.auth.oauth2.Credential;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.net.URL;

/**
 * Represents a single logged-in account.
 */
public class Account {

  private String email;
  private Credential oAuth2Credential;
  @Nullable private String name;
  @Nullable private URL avatarUrl;

  Account(String email, Credential oAuth2Credential) {
    Preconditions.checkNotNull(email);
    Preconditions.checkNotNull(oAuth2Credential);

    this.email = email;
    this.oAuth2Credential = oAuth2Credential;
  }

  public String getEmail() {
    return email;
  }

  @Nullable
  public String getName() {
    return name;
  }

  @Nullable
  public URL getAvatarUrl() {
    return avatarUrl;
  }

  public Credential getOAuth2Credential() {
    return oAuth2Credential;
  }

  /**
   * Identical to {@code getOAuth2Credential().getAccessToken()}.
   */
  @Nullable
  public String getAccessToken() {
    // TODO(chanseok): investigate if we need to refresh access token.
    // (https://github.com/GoogleCloudPlatform/ide-login/issues/28)
    return oAuth2Credential.getAccessToken();
  }

  /**
   * Identical to {@code getOAuth2Credential().getRefreshToken()}.
   */
  @Nullable
  public String getRefreshToken() {
    return oAuth2Credential.getRefreshToken();
  }

  long getAccessTokenExpiryTime() {
    return oAuth2Credential.getExpirationTimeMilliseconds();
  }

  @Override
  public boolean equals(Object account) {
    return email.equals(((Account) account).getEmail());
  }

  @Override
  public int hashCode() {
    return email.hashCode();
  }
}
