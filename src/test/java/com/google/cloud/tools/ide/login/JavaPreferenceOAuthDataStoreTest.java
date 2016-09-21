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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

@RunWith(MockitoJUnitRunner.class)
public class JavaPreferenceOAuthDataStoreTest {

  private static final Set<String> FAKE_OAUTH_SCOPES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("my-scope1", "my-scope2")));

  private static final OAuthData[] fakeOAuthData = new OAuthData[] {
      new OAuthData("accessToken1", "refreshToken1", "email1@example.com", FAKE_OAUTH_SCOPES, 123),
      new OAuthData("accessToken2", "refreshToken2", "email2@example.com", FAKE_OAUTH_SCOPES, 234),
      new OAuthData("accessToken3", "refreshToken3", "email3@example.com", FAKE_OAUTH_SCOPES, 345)
  };
  public static final String TEST_PREFERENCE_PATH = "some_test_preference_path";

  @Mock private LoggerFacade logger;

  private JavaPreferenceOAuthDataStore dataStore =
      new JavaPreferenceOAuthDataStore(TEST_PREFERENCE_PATH, logger);

  @Test
  public void testLoadOAuthData_returnEmptyOAuthData() {
    assertTrue(dataStore.loadOAuthData().isEmpty());
  }

  @Test
  public void testSaveLoadOAuthData() {
    dataStore.saveOAuthData(fakeOAuthData[0]);
    verifyContains(dataStore.loadOAuthData(), fakeOAuthData[0]);
  }

  @Test
  public void testSaveLoadOAuthData_nullValues() {
    dataStore.saveOAuthData(new OAuthData(null, null, "email@example.com", null, 0));
    OAuthData loaded = dataStore.loadOAuthData().iterator().next();

    assertNull(loaded.getAccessToken());
    assertNull(loaded.getRefreshToken());
    assertEquals("email@example.com", loaded.getEmail());
    assertTrue(loaded.getStoredScopes().isEmpty());
    assertEquals(0, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData_emptyValues() {
    dataStore.saveOAuthData(new OAuthData("", "", "email@example.com", null, 0));
    OAuthData loaded = dataStore.loadOAuthData().iterator().next();

    assertNull(loaded.getAccessToken());
    assertNull(loaded.getRefreshToken());
    assertEquals("email@example.com", loaded.getEmail());
    assertTrue(loaded.getStoredScopes().isEmpty());
    assertEquals(0, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveClearLoadOAuthData() {
    saveThreeFakeOAuthData();
    dataStore.clearStoredOAuthData();
    assertTrue(dataStore.loadOAuthData().isEmpty());
  }

  @Test
  public void testSaveLoadOAuthData_nullScopeSet() {
    dataStore.saveOAuthData(new OAuthData("accessToken", "refreshToken", "email", null, 123));
    OAuthData loaded = dataStore.loadOAuthData().iterator().next();

    assertEquals("accessToken", loaded.getAccessToken());
    assertEquals("refreshToken", loaded.getRefreshToken());
    assertEquals("email", loaded.getEmail());
    assertTrue(loaded.getStoredScopes().isEmpty());
    assertEquals(123, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData_emptyScopeSet() {
    OAuthData oAuthData =
        new OAuthData("accessToken", "refreshToken", "email", new HashSet<String>(), 123);

    dataStore.saveOAuthData(oAuthData);
    Set<OAuthData> loaded = dataStore.loadOAuthData();
    assertEquals(1, loaded.size());
    verifyContains(loaded, oAuthData);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveOAuthData_scopeWithDelimiter() {
    Set<String> scopes = new HashSet<>(Arrays.asList(JavaPreferenceOAuthDataStore.SCOPE_DELIMITER,
        "head" + JavaPreferenceOAuthDataStore.SCOPE_DELIMITER + "tail"));
    OAuthData oAuthData = new OAuthData(null, null, "email@example.com", scopes, 0);

    dataStore.saveOAuthData(oAuthData);
  }

  @Test
  public void testSaveLoadOAuthData_emptyEmail() {
    dataStore.saveOAuthData(new OAuthData(null, null, "", null, 0));
    assertTrue(dataStore.loadOAuthData().isEmpty());
  }

  @Test
  public void testSaveLoadOAuthData_multipleCredentials() {
    saveThreeFakeOAuthData();

    Set<OAuthData> loaded = dataStore.loadOAuthData();
    assertEquals(3, loaded.size());
    verifyContains(loaded, fakeOAuthData[0]);
    verifyContains(loaded, fakeOAuthData[1]);
    verifyContains(loaded, fakeOAuthData[2]);
  }

  @Test
  public void testRemoveOAuthData() {
    saveThreeFakeOAuthData();
    dataStore.removeOAuthData("email1@example.com");
    dataStore.removeOAuthData("email3@example.com");

    assertEquals(1, dataStore.loadOAuthData().size());
    verifyContains(dataStore.loadOAuthData(), fakeOAuthData[1]);
  }

  @Test
  public void testRemoveOAuthData_nonExistingEmail() {
    saveThreeFakeOAuthData();
    dataStore.removeOAuthData("email999@example.com");

    Set<OAuthData> loaded = dataStore.loadOAuthData();
    assertEquals(3, loaded.size());
    verifyContains(loaded, fakeOAuthData[0]);
    verifyContains(loaded, fakeOAuthData[1]);
    verifyContains(loaded, fakeOAuthData[2]);
  }

  @After
  public void tearDown() throws BackingStoreException {
    dataStore.clearStoredOAuthData();
    Preferences.userRoot().node(TEST_PREFERENCE_PATH).removeNode();
  }

  private void saveThreeFakeOAuthData() {
    dataStore.saveOAuthData(fakeOAuthData[0]);
    dataStore.saveOAuthData(fakeOAuthData[1]);
    dataStore.saveOAuthData(fakeOAuthData[2]);
  }

  private void verifyContains(Set<OAuthData> oAuthDataSet, OAuthData oAuthDataToMatch) {
    for (OAuthData oAuthData : oAuthDataSet) {
      if (Objects.equals(oAuthData.getEmail(), oAuthDataToMatch.getEmail())
        && Objects.equals(oAuthData.getAccessToken(), oAuthDataToMatch.getAccessToken())
          && Objects.equals(oAuthData.getRefreshToken(), oAuthDataToMatch.getRefreshToken())
          && Objects.equals(oAuthData.getStoredScopes(), oAuthDataToMatch.getStoredScopes())
          && oAuthData.getAccessTokenExpiryTime() == oAuthDataToMatch.getAccessTokenExpiryTime()) {
        return;
      }
    }
    fail();
  }
}
