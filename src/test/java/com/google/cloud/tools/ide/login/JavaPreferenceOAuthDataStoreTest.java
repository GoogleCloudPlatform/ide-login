package com.google.cloud.tools.ide.login;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

@RunWith(MockitoJUnitRunner.class)
public class JavaPreferenceOAuthDataStoreTest {

  @Mock private LoggerFacade logger;

  private Preferences prefs = Preferences.userRoot().node("some preference node");
  private JavaPreferenceOAuthDataStore dataStore =
    new JavaPreferenceOAuthDataStore(prefs.name(), logger);

  @Test
  public void testLoadOAuthData_returnEmptyOAuthData() {
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertNull(loaded.getAccessToken());
    Assert.assertNull(loaded.getRefreshToken());
    Assert.assertNull(loaded.getStoredEmail());
    Assert.assertEquals(0, loaded.getStoredScopes().size());
    Assert.assertEquals(0, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData() {
    Set<String> scopes = new HashSet<String>(Arrays.asList("my-scope1", "my-scope2"));
    OAuthData oauthData = new OAuthData(
        "my-access-token", "my-refresh-token", "my-email", scopes, 12345);

    dataStore.saveOAuthData(oauthData);
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertEquals("my-access-token", loaded.getAccessToken());
    Assert.assertEquals("my-refresh-token", loaded.getRefreshToken());
    Assert.assertEquals("my-email", loaded.getStoredEmail());
    Assert.assertEquals(scopes, loaded.getStoredScopes());
    Assert.assertEquals(12345, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData_nullValues() {
    OAuthData oauthData = new OAuthData(null, null, null, null, 0);

    dataStore.saveOAuthData(oauthData);
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertEquals(null, loaded.getAccessToken());
    Assert.assertEquals(null, loaded.getRefreshToken());
    Assert.assertEquals(null, loaded.getStoredEmail());
    Assert.assertEquals(0, loaded.getStoredScopes().size());
    Assert.assertEquals(0, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData_emptyValues() {
    OAuthData oauthData = new OAuthData("", "", "", null, 0);

    dataStore.saveOAuthData(oauthData);
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertEquals(null, loaded.getAccessToken());
    Assert.assertEquals(null, loaded.getRefreshToken());
    Assert.assertEquals(null, loaded.getStoredEmail());
    Assert.assertEquals(0, loaded.getStoredScopes().size());
    Assert.assertEquals(0, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveClearLoadOAuthData_returnEmptyOAuthData() {
    Set<String> scopes = new HashSet<String>(Arrays.asList("my-scope1", "my-scope2"));
    OAuthData oauthData = new OAuthData(
        "my-access-token", "my-refresh-token", "my-email", scopes, 12345);

    dataStore.saveOAuthData(oauthData);
    dataStore.clearStoredOAuthData();
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertNull(loaded.getAccessToken());
    Assert.assertNull(loaded.getRefreshToken());
    Assert.assertNull(loaded.getStoredEmail());
    Assert.assertEquals(0, loaded.getStoredScopes().size());
    Assert.assertEquals(0, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData_nullScopeSet() {
    OAuthData oauthData = new OAuthData("my-access-token", "my-refresh-token", "my-email",
        null, 12345);

    dataStore.saveOAuthData(oauthData);
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertEquals("my-access-token", loaded.getAccessToken());
    Assert.assertEquals("my-refresh-token", loaded.getRefreshToken());
    Assert.assertEquals("my-email", loaded.getStoredEmail());
    Assert.assertEquals(0, loaded.getStoredScopes().size());
    Assert.assertEquals(12345, loaded.getAccessTokenExpiryTime());
  }

  @Test
  public void testSaveLoadOAuthData_emptyScopeSet() {
    OAuthData oauthData = new OAuthData("my-access-token", "my-refresh-token", "my-email",
        new HashSet<String>(), 12345);

    dataStore.saveOAuthData(oauthData);
    OAuthData loaded = dataStore.loadOAuthData();

    Assert.assertEquals("my-access-token", loaded.getAccessToken());
    Assert.assertEquals("my-refresh-token", loaded.getRefreshToken());
    Assert.assertEquals("my-email", loaded.getStoredEmail());
    Assert.assertEquals(0, loaded.getStoredScopes().size());
    Assert.assertEquals(12345, loaded.getAccessTokenExpiryTime());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSaveOAuthData_scopeWithDelimiter() {
    Set<String> scopes = new HashSet<>(Arrays.asList(JavaPreferenceOAuthDataStore.SCOPE_DELIMITER,
        "head" + JavaPreferenceOAuthDataStore.SCOPE_DELIMITER + "tail"));
    OAuthData oauthData = new OAuthData(null, null, null, scopes, 0);

    dataStore.saveOAuthData(oauthData);
  }

  @After
  public void tearDown() {
    try {
      prefs.clear();
    } catch (BackingStoreException e) {
      e.printStackTrace();
    }
  }
}
