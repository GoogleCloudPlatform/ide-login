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
import com.google.common.collect.ImmutableSet;

import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Authentication data, consisting of an access token, a refresh token, an access-token expiration
 * time, and a user email address, each of which may be null, and a set of authorized scopes that is
 * never null but may be empty.
 */
@Immutable
public class OAuthData {
  private final String email;
  @Nullable private final String accessToken;
  @Nullable private final String refreshToken;
  @Nullable private final String name;
  @Nullable private final String avatarUrl;
  private final long accessTokenExpiryTime;
  private final Set<String> storedScopes;

  /**
   * @param scopes if null, an empty set will be created and set
   */
  public OAuthData(@Nullable String accessToken, @Nullable String refreshToken, String email,
      @Nullable String name, @Nullable String avatarUrl,
      @Nullable Set<String> scopes, long accessTokenExpiryTime) {
    Preconditions.checkNotNull(email);

    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.email = email;
    this.name = name;
    this.avatarUrl = avatarUrl;
    this.storedScopes = (scopes == null ? ImmutableSet.<String>of() : scopes);
    this.accessTokenExpiryTime = accessTokenExpiryTime;
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

  public Set<String> getStoredScopes() {
    return storedScopes;
  }

  @Nullable
  public String getAccessToken() {
    return accessToken;
  }

  @Nullable
  public String getRefreshToken() {
    return refreshToken;
  }

  public long getAccessTokenExpiryTime() {
    return accessTokenExpiryTime;
  }
}