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

import com.google.common.collect.ImmutableSet;

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

public class OAuthDataTest {

  private Set<String> scopes = ImmutableSet.of("scope 1", "scope 2");
  
  private OAuthData data = new OAuthData("access token", "refresh token", "storedEmail@example.com",
      scopes, 10);

  @Test
  public void testNullable() {
    data = new OAuthData(null, null, "email@example.com", null, 10);
    Assert.assertNull(data.getRefreshToken());
    Assert.assertNull(data.getAccessToken());
    Assert.assertTrue(data.getStoredScopes().isEmpty());
  }

  @Test(expected = NullPointerException.class)
  public void testNullEmail() {
    data = new OAuthData(null, null, null, null, 10);
  }

  @Test
  public void testGetEmail() {
    Assert.assertEquals("storedEmail@example.com", data.getEmail());
  }

  @Test
  public void testGetStoredScopes() {
    Assert.assertEquals(scopes, data.getStoredScopes());
  }

  @Test
  public void testGetAccessToken() {
    Assert.assertEquals("access token", data.getAccessToken());
  }

  @Test
  public void testGetRefreshToken() {
    Assert.assertEquals("refresh token", data.getRefreshToken());
  }

  @Test
  public void testGetAccessTokenExpiryTime() {
    Assert.assertEquals(10L, data.getAccessTokenExpiryTime());
  }
}

