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
 * Internal class holding all necessary data for logged-in accounts. Instances of the class
 * are placed inside {@link AccountRoster} as list elements. {@link Account#email} must not be
 * {@code null}.
 */
class Account {

  private String email;
  private Credential oAuth2Credential;
  private long accessTokenExpiryTime;
  @Nullable private String name;
  @Nullable private URL avatarUrl;

  Account(String email, Credential oAuth2Credential, long accessTokenExpiryTime) {
    Preconditions.checkNotNull(email);
    Preconditions.checkNotNull(oAuth2Credential);

    this.email = email;
    this.oAuth2Credential = oAuth2Credential;
    this.accessTokenExpiryTime = accessTokenExpiryTime;
  }

  String getEmail() {
    return email;
  }

  Credential getOAuth2Credential() {
    return oAuth2Credential;
  }

  long getAccessTokenExpiryTime() {
    return accessTokenExpiryTime;
  }

  void setAccessTokenExpiryTime(long accessTokenExpiryTime) {
    this.accessTokenExpiryTime = accessTokenExpiryTime;
  }

  @Nullable
  String getName() {
    return name;
  }

  @Nullable
  URL getAvatarUrl() {
    return avatarUrl;
  }

  @Nullable
  String getAccessToken() {
    return oAuth2Credential.getAccessToken();
  }

  @Nullable
  String getRefreshToken() {
    return oAuth2Credential.getRefreshToken();
  }
}
