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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.oauth2.Oauth2;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GoogleLoginStateTest {

  private static final Set<String> FAKE_OAUTH_SCOPES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("oauth-scope-1", "oauth-scope-2")));

  private static final OAuthData[] fakeOAuthDataList = new OAuthData[] {
      new OAuthData("accessToken5", "refreshToken5", "email5@example.com",
                    "accountName5", "http://example.com/image5", FAKE_OAUTH_SCOPES, 543),
      new OAuthData("accessToken6", "refreshToken6", "email6@example.com",
                    "accountName6", "http://example.com/image6", FAKE_OAUTH_SCOPES, 654),
      new OAuthData("accessToken7", "refreshToken7", "email7@example.com",
                    "accountName7", "http://example.com/image7", FAKE_OAUTH_SCOPES, 765)
  };

  @Mock private GoogleAuthorizationCodeTokenRequestCreator authorizationCodeTokenRequestCreator;
  @Mock private OAuth2Wrapper oAuth2Wrapper;

  private final OAuthDataStore authDataStore =
      new JavaPreferenceOAuthDataStore("test-node", mock(LoggerFacade.class));

  @Before
  public void setUp() throws IOException {
    mockAuthorizationCodeTokenRequestCreator();
    mockUserInfoService();
  }

  @After
  public void tearDown() throws IOException {
    authDataStore.clearStoredOAuthData();
  }

  @Test
  public void testIsLoggedIn() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    assertFalse(state.isLoggedIn());
  }

  @Test
  public void testListAccounts() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    assertTrue(state.listAccounts().isEmpty());
  }

  @Test
  public void testLoadPersistedAccount() throws IOException {
    authDataStore.saveOAuthData(fakeOAuthDataList[0]);

    GoogleLoginState state = newGoogleLoginState();

    assertTrue(state.isLoggedIn());
    Set<Account> accounts = state.listAccounts();
    assertEquals(1, accounts.size());
    verifyAccountsContain(accounts, "email5@example.com", "accessToken5", "refreshToken5", 543,
        "accountName5", "http://example.com/image5");
  }

  @Test
  public void testLoadPersistedAccounts() throws IOException {
    authDataStore.saveOAuthData(fakeOAuthDataList[0]);
    authDataStore.saveOAuthData(fakeOAuthDataList[1]);
    authDataStore.saveOAuthData(fakeOAuthDataList[2]);

    GoogleLoginState state = newGoogleLoginState();

    assertTrue(state.isLoggedIn());
    Set<Account> accounts = state.listAccounts();
    assertEquals(3, accounts.size());
    verifyAccountsContain(accounts, "email5@example.com", "accessToken5", "refreshToken5", 543,
        "accountName5", "http://example.com/image5");
    verifyAccountsContain(accounts, "email6@example.com", "accessToken6", "refreshToken6", 654,
        "accountName6", "http://example.com/image6");
    verifyAccountsContain(accounts, "email7@example.com", "accessToken7", "refreshToken7", 765,
        "accountName7", "http://example.com/image7");
  }

  @Test
  public void testLoadPersistedAccount_removeCredentialIfScopesMismatch() throws IOException {
    Set<String> deprecatedScopes = new HashSet<>(Arrays.asList("deprecated-scope"));
    OAuthData invalidatedOAuthData = new OAuthData("access-token-1", "refresh-token-1",
        "email-1@example.com", "name-1", "avatar-url-1", deprecatedScopes, 0);
    authDataStore.saveOAuthData(invalidatedOAuthData);

    GoogleLoginState state = newGoogleLoginState();

    assertFalse(state.isLoggedIn());
    assertTrue(state.listAccounts().isEmpty());
  }

  @Test
  public void testLoadPersistedAccounts_removeCredentialsIfScopesMismatch() throws IOException {
    Set<String> deprecatedScopes = new HashSet<>(Arrays.asList("deprecated-scope"));
    OAuthData invalidatedOAuthData = new OAuthData("access-token-1", "refresh-token-1",
        "email-1@example.com", "name-1", "avatar-url-1", deprecatedScopes, 0);
    authDataStore.saveOAuthData(fakeOAuthDataList[0]);
    authDataStore.saveOAuthData(invalidatedOAuthData);
    authDataStore.saveOAuthData(fakeOAuthDataList[1]);
    authDataStore.saveOAuthData(invalidatedOAuthData);
    authDataStore.saveOAuthData(fakeOAuthDataList[2]);
    authDataStore.saveOAuthData(invalidatedOAuthData);

    GoogleLoginState state = newGoogleLoginState();

    assertTrue(state.isLoggedIn());
    Set<Account> accounts = state.listAccounts();
    assertEquals(3, accounts.size());
    verifyAccountsContain(accounts, "email5@example.com", "accessToken5", "refreshToken5", 543,
        "accountName5", "http://example.com/image5");
    verifyAccountsContain(accounts, "email6@example.com", "accessToken6", "refreshToken6", 654,
        "accountName6", "http://example.com/image6");
    verifyAccountsContain(accounts, "email7@example.com", "accessToken7", "refreshToken7", 765,
        "accountName7", "http://example.com/image7");
  }

  @Test
  public void testLogInWithLocalServer() throws IOException {
    GoogleLoginState state = newGoogleLoginState();

    long currentTime = System.currentTimeMillis();
    Account account1 = state.logInWithLocalServer(null /* no title */);

    assertTrue(state.isLoggedIn());
    verifyAccountEquals(account1,
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1,
        "account-name-1", "http://example.com/image-1");
    assertTrue(currentTime + 100 * 1000 <= account1.getAccessTokenExpiryTime());
    assertTrue(currentTime + 105 * 1000 > account1.getAccessTokenExpiryTime());

    Account account2 = state.listAccounts().iterator().next();
    verifyAccountEquals(account2,
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1,
        "account-name-1", "http://example.com/image-1");
    assertTrue(currentTime + 100 * 1000 <= account2.getAccessTokenExpiryTime());
    assertTrue(currentTime + 105 * 1000 > account2.getAccessTokenExpiryTime());
  }

  @Test
  public void testLogInWithLocalServer_threeLogins() throws IOException {
    GoogleLoginState state = newGoogleLoginState();

    Account account1 = state.logInWithLocalServer(null /* no title */);
    Account account2 = state.logInWithLocalServer(null);
    Account account3 = state.logInWithLocalServer(null);

    verifyAccountEquals(account1,
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1,
        "account-name-1", "http://example.com/image-1");
    verifyAccountEquals(account2,
        "email-from-server-2@example.com", "access-token-login-2", "refresh-token-login-2", -1,
        "account-name-2", "http://example.com/image-2");
    verifyAccountEquals(account3,
        "email-from-server-3@example.com", "access-token-login-3", "refresh-token-login-3", -1,
        "account-name-3", "http://example.com/image-3");

    Set<Account> accounts = state.listAccounts();
    assertEquals(3, accounts.size());
    verifyAccountsContain(accounts,
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1,
        "account-name-1", "http://example.com/image-1");
    verifyAccountsContain(accounts,
        "email-from-server-2@example.com", "access-token-login-2", "refresh-token-login-2", -1,
        "account-name-2", "http://example.com/image-2");
    verifyAccountsContain(accounts,
        "email-from-server-3@example.com", "access-token-login-3", "refresh-token-login-3", -1,
        "account-name-3", "http://example.com/image-3");
  }

  @Test
  public void testLogOutAll() throws IOException {
    GoogleLoginState state = newGoogleLoginState();

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    assertTrue(state.isLoggedIn());
    assertEquals(3, state.listAccounts().size());

    state.logOutAll(false /* don't show prompt dialog */);

    assertFalse(state.isLoggedIn());
    assertTrue(state.listAccounts().isEmpty());
  }

  @Test
  public void testListAccounts_isSnapshot() throws IOException {
    GoogleLoginState state = newGoogleLoginState();

    state.logInWithLocalServer(null /* no title */);

    assertTrue(state.isLoggedIn());
    Set<Account> accounts = state.listAccounts();
    assertEquals(1, accounts.size());

    state.logOutAll(false /* don't show prompt dialog */);

    assertFalse(state.isLoggedIn());
    assertEquals(1, accounts.size());
    assertTrue(accounts != state.listAccounts());
  }

  @Test
  public void testPersistLoadAccount() throws IOException {
    GoogleLoginState state1 = newGoogleLoginState();
    state1.logInWithLocalServer(null);  // Credentials will be persisted in authDataStore.

    GoogleLoginState state2 = newGoogleLoginState();
    assertTrue(state2.isLoggedIn());
    assertEquals(1, state2.listAccounts().size());
    verifyAccountsContain(state2.listAccounts(),
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1,
        "account-name-1", "http://example.com/image-1");
  }

  @Test
  public void testPersistLoadAccounts() throws IOException {
    GoogleLoginState state1 = newGoogleLoginState();
    state1.logInWithLocalServer(null);  // Credentials will be persisted in authDataStore.
    state1.logInWithLocalServer(null);
    state1.logInWithLocalServer(null);

    GoogleLoginState state2 = newGoogleLoginState();
    assertTrue(state2.isLoggedIn());
    Set<Account> accounts = state2.listAccounts();
    assertEquals(3, accounts.size());
    verifyAccountsContain(accounts,
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1,
        "account-name-1", "http://example.com/image-1");
    verifyAccountsContain(accounts,
        "email-from-server-2@example.com", "access-token-login-2", "refresh-token-login-2", -1,
        "account-name-2", "http://example.com/image-2");
    verifyAccountsContain(accounts,
        "email-from-server-3@example.com", "access-token-login-3", "refresh-token-login-3", -1,
        "account-name-3", "http://example.com/image-3");
  }

  @Test
  public void testQueryUserInfo() throws IOException, EmailAddressNotReturnedException {
    GoogleLoginState state = newGoogleLoginState();
    UserInfo userInfo = state.queryUserInfo(mock(Credential.class));
    assertEquals("email-from-server-1@example.com", userInfo.getEmail());
    assertEquals("account-name-1", userInfo.getName());
    assertEquals("http://example.com/image-1", userInfo.getPicture());
  }

  @Test(expected = IOException.class)
  public void testQueryUserInfo_IOExceptionInUserInfoRequest()
      throws IOException, EmailAddressNotReturnedException {
    GoogleLoginState state = newGoogleLoginState();
    mockUserInfoServiceThrowingIOException();

    state.queryUserInfo(mock(Credential.class));
  }

  @Test(expected = EmailAddressNotReturnedException.class)
  public void testQueryUserInfo_nullUserInfoResponse()
      throws IOException, EmailAddressNotReturnedException {
    GoogleLoginState state = newGoogleLoginState();
    mockUserInfoServiceReturningNullUserInfo();

    state.queryUserInfo(mock(Credential.class));
  }

  @Test(expected = EmailAddressNotReturnedException.class)
  public void testQueryEmail_nullEmailUserInfoResponse()
      throws IOException, EmailAddressNotReturnedException {
    GoogleLoginState state = newGoogleLoginState();
    mockUserInfoServiceReturningNullEmail();

    state.queryUserInfo(mock(Credential.class));
  }

  @Test
  public void testLogIn_IOExceptionInUserInfoRequest() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    mockUserInfoServiceThrowingIOException();

    state.logInWithLocalServer(null);
    assertFalse(state.isLoggedIn());
    assertTrue(state.listAccounts().isEmpty());
  }

  @Test
  public void testLogIn_nullUserInfoResponse() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    mockUserInfoServiceReturningNullUserInfo();

    state.logInWithLocalServer(null);
    assertFalse(state.isLoggedIn());
    assertTrue(state.listAccounts().isEmpty());
  }

  @Test
  public void testLogIn_nullEmailUserInfoResponse() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    mockUserInfoServiceReturningNullEmail();

    state.logInWithLocalServer(null);
    assertFalse(state.isLoggedIn());
    assertTrue(state.listAccounts().isEmpty());
  }

  @Test
  public void testBuildOAuth2_credentialSetInRequest() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    Credential credential = mock(Credential.class);
    Oauth2 oAuth2 = state.buildOAuth2(credential);

    oAuth2.getRequestFactory().buildGetRequest(null);
    verify(credential).initialize(any(HttpRequest.class));
  }

  @Test
  public void testBuildOAuth2_timeoutSetInRequest() throws IOException {
    GoogleLoginState state = newGoogleLoginState();
    Oauth2 oAuth2 = state.buildOAuth2(mock(Credential.class));

    HttpRequest request = oAuth2.getRequestFactory().buildGetRequest(null);
    assertEquals(5000, request.getConnectTimeout());
    assertEquals(3000, request.getReadTimeout());
  }

  private GoogleLoginState newGoogleLoginState() throws IOException {
    UiFacade uiFacade = mock(UiFacade.class);
    when(uiFacade.obtainVerificationCodeFromExternalUserInteraction(anyString()))
        .thenReturn(new VerificationCodeHolder(null, null));

    GoogleLoginState state = new GoogleLoginState("client-id", "client-secret", FAKE_OAUTH_SCOPES,
        authDataStore, uiFacade, mock(LoggerFacade.class),
        authorizationCodeTokenRequestCreator, oAuth2Wrapper);

    return state;
  }

  private void mockAuthorizationCodeTokenRequestCreator() throws IOException {
    GoogleTokenResponse authResponse1 = new GoogleTokenResponse();
    authResponse1.setAccessToken("access-token-login-1");
    authResponse1.setRefreshToken("refresh-token-login-1");
    authResponse1.setExpiresInSeconds(100L);

    GoogleTokenResponse authResponse2 = new GoogleTokenResponse();
    authResponse2.setAccessToken("access-token-login-2");
    authResponse2.setRefreshToken("refresh-token-login-2");
    authResponse2.setExpiresInSeconds(100L);

    GoogleTokenResponse authResponse3 = new GoogleTokenResponse();
    authResponse3.setAccessToken("access-token-login-3");
    authResponse3.setRefreshToken("refresh-token-login-3");
    authResponse3.setExpiresInSeconds(100L);

    GoogleAuthorizationCodeTokenRequest request = mock(GoogleAuthorizationCodeTokenRequest.class);
    when(request.execute())
        .thenReturn(authResponse1).thenReturn(authResponse2).thenReturn(authResponse3);

    when(authorizationCodeTokenRequestCreator.create(any(HttpTransport.class),
        any(JsonFactory.class), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(request);
  }

  private void mockUserInfoService() throws IOException {
    UserInfo userInfo1 = mock(UserInfo.class);
    when(userInfo1.getEmail()).thenReturn("email-from-server-1@example.com");
    when(userInfo1.getName()).thenReturn("account-name-1");
    when(userInfo1.getPicture()).thenReturn("http://example.com/image-1");

    UserInfo userInfo2 = mock(UserInfo.class);
    when(userInfo2.getEmail()).thenReturn("email-from-server-2@example.com");
    when(userInfo2.getName()).thenReturn("account-name-2");
    when(userInfo2.getPicture()).thenReturn("http://example.com/image-2");

    UserInfo userInfo3 = mock(UserInfo.class);
    when(userInfo3.getEmail()).thenReturn("email-from-server-3@example.com");
    when(userInfo3.getName()).thenReturn("account-name-3");
    when(userInfo3.getPicture()).thenReturn("http://example.com/image-3");

    when(oAuth2Wrapper.executeOAuth2(any(Oauth2.class)))
            .thenReturn(userInfo1).thenReturn(userInfo2).thenReturn(userInfo3);
  }

  private void mockUserInfoServiceThrowingIOException() throws IOException {
    when(oAuth2Wrapper.executeOAuth2(any(Oauth2.class))).thenThrow(new IOException());
  }

  private void mockUserInfoServiceReturningNullUserInfo() throws IOException {
    when(oAuth2Wrapper.executeOAuth2(any(Oauth2.class))).thenReturn(null);
  }

  private void mockUserInfoServiceReturningNullEmail() throws IOException {
    UserInfo userInfo = mock(UserInfo.class);
    when(userInfo.getEmail()).thenReturn(null);
    when(userInfo.getName()).thenReturn("account-name-1");
    when(userInfo.getPicture()).thenReturn("http://example.com/image-1");

    when(oAuth2Wrapper.executeOAuth2(any(Oauth2.class))).thenReturn(userInfo);
  }

  private void verifyAccountsContain(Set<Account> accounts, String email, String accessToken,
      String refreshToken, long expiryTime, String name, String avatarUrl) {
    ArrayList<Account> accountList = new ArrayList<>(accounts);
    int index = accountList.indexOf(new Account(email, mock(Credential.class), null, null));
    assertNotEquals(-1, index);
    verifyAccountEquals(accountList.get(index),
        email, accessToken, refreshToken, expiryTime, name, avatarUrl);
  }

  private void verifyAccountEquals(Account account, String email, String accessToken,
      String refreshToken, long expiryTime, String name, String avatarUrl) {
    assertEquals(email, account.getEmail());
    assertEquals(accessToken, account.getAccessToken());
    assertEquals(refreshToken, account.getRefreshToken());
    if (expiryTime != -1) {
      assertEquals(expiryTime, account.getAccessTokenExpiryTime());
    }
    assertEquals(name, account.getName());
    assertEquals(avatarUrl, account.getAvatarUrl());
  }
}