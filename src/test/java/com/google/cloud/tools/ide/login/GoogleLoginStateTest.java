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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(MockitoJUnitRunner.class)
public class GoogleLoginStateTest {

  private static final Set<String> FAKE_OAUTH_SCOPES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("oauth-scope-1", "oauth-scope-2")));

  @Mock private GoogleAuthorizationCodeTokenRequestCreator tokenRequestCreator;
  @Mock private UiFacade uiFacade;
  @Mock private LoggerFacade loggerFacade;

  private OAuthDataStore authDataStore =
      new JavaPreferenceOAuthDataStore("test-node", loggerFacade);

  private ServerSocket emailServerSocket;

  @Test
  public void testIsLoggedIn() throws IOException {
    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);
    assertFalse(state.isLoggedIn());
  }

  @Test
  public void testListAccounts() throws IOException {
    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);
    assertEquals(0, state.listAccounts().size());
  }

  @Test
  public void testLoadPersistedAccount() throws IOException {
    OAuthData fakeOAuthData = new OAuthData(
        "access-token-5", "refresh-token-5", "email-5@example.com", FAKE_OAUTH_SCOPES, 543);
    authDataStore.saveOAuthData(fakeOAuthData);

    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);

    assertTrue(state.isLoggedIn());
    assertEquals(1, state.listAccounts().size());
    verifyAccountsContain(state.listAccounts(),
        "email-5@example.com", "access-token-5", "refresh-token-5", 543);
  }

  @Test
  public void testLoadPersistedAccount_clearLoginIfScopesMismatch() throws IOException {
    Set<String> deprecatedScopes = new HashSet<>(Arrays.asList("deprecated-scope"));
    OAuthData invalidatedOAuthData = new OAuthData(
        "access-token-1", "refresh-token-1", "email-1@example.com", deprecatedScopes, 0);
    authDataStore.saveOAuthData(invalidatedOAuthData);

    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);

    assertFalse(state.isLoggedIn());
    assertNull(authDataStore.loadOAuthData().getAccessToken());
    assertNull(authDataStore.loadOAuthData().getRefreshToken());
  }

  @Test
  public void testLogInWithLocalServer() throws IOException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    long currentTime = System.currentTimeMillis();
    state.logInWithLocalServer(null /* no title */);

    assertTrue(state.isLoggedIn());
    Account account = state.listAccounts().iterator().next();
    assertEquals("access-token-login-1", account.getAccessToken());
    assertEquals("refresh-token-login-1", account.getRefreshToken());
    assertEquals("email-from-server-1@example.com", account.getEmail());
    assertTrue(currentTime + 100 * 1000 <= account.getAccessTokenExpiryTime());
    assertTrue(currentTime + 105 * 1000 > account.getAccessTokenExpiryTime());
  }

  @Test
  public void testLogInWithLocalServer_threeLogins() throws IOException {
    String emailQueryUrl = runEmailQueryServer(3, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    Set<Account> accounts = state.listAccounts();
    assertEquals(3, accounts.size());
    verifyAccountsContain(accounts,
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1);
    verifyAccountsContain(accounts,
        "email-from-server-2@example.com", "access-token-login-2", "refresh-token-login-2", -1);
    verifyAccountsContain(accounts,
        "email-from-server-3@example.com", "access-token-login-3", "refresh-token-login-3", -1);
  }

  @Test
  public void testLogOutAll() throws IOException {
    String emailQueryUrl = runEmailQueryServer(3, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    assertTrue(state.isLoggedIn());
    assertEquals(3, state.listAccounts().size());

    state.logOutAll(false /* don't show prompt dialog */);

    assertFalse(state.isLoggedIn());
    assertEquals(0, state.listAccounts().size());
  }

  @Test
  public void testListAccounts_isSnapshot() throws IOException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

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
  public void testPersistLoadSingleAccount() throws IOException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.OK);
    GoogleLoginState state1 = newGoogleLoginState(emailQueryUrl);
    state1.logInWithLocalServer(null);  // Credentials will be persisted in authDataStore.

    GoogleLoginState state2 = newGoogleLoginState(null /* emailQueryUrl */);
    assertTrue(state2.isLoggedIn());
    assertEquals(1, state2.listAccounts().size());
    verifyAccountsContain(state2.listAccounts(),
        "email-from-server-1@example.com", "access-token-login-1", "refresh-token-login-1", -1);
  }
  // TODO(chanseok): test persisting multiple accounts too once we have #23 in.
  // (Issue #23: https://github.com/GoogleCloudPlatform/ide-login/issues/23)

  @Test
  public void testQueryEmail()
      throws IOException, GoogleLoginState.EmailAddressNotReturnedException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);
    assertEquals("email-from-server-1@example.com", state.queryEmail(null));
  }

  @Test(expected = IOException.class)
  public void testQueryEmail_internalServerError()
      throws IOException, GoogleLoginState.EmailAddressNotReturnedException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.INTERNAL_SERVER_ERROR);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);
    state.queryEmail(null);
  }

  @Test(expected = GoogleLoginState.EmailAddressNotReturnedException.class)
  public void testQueryEmail_malformedContent()
      throws IOException, GoogleLoginState.EmailAddressNotReturnedException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.MALFORMED_CONTENT);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);
    state.queryEmail(null);
  }

  @Test(expected = IOException.class)
  public void testQueryEmail_connectionClose()
      throws IOException, GoogleLoginState.EmailAddressNotReturnedException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.CONNECTION_CLOSE);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);
    state.queryEmail(null);
  }

  @After
  public void tearDown() throws IOException {
    authDataStore.clearStoredOAuthData();
    if (emailServerSocket != null) {
      emailServerSocket.close();
    }
  }

  private GoogleLoginState newGoogleLoginState(String emailQueryUrl) throws IOException {
    GoogleTokenResponse authResponse1 = new GoogleTokenResponse();
    authResponse1.setAccessToken("access-token-login-1");
    authResponse1.setRefreshToken("refresh-token-login-1");
    authResponse1.setExpiresInSeconds(Long.valueOf(100));

    GoogleTokenResponse authResponse2 = new GoogleTokenResponse();
    authResponse2.setAccessToken("access-token-login-2");
    authResponse2.setRefreshToken("refresh-token-login-2");
    authResponse2.setExpiresInSeconds(Long.valueOf(100));

    GoogleTokenResponse authResponse3 = new GoogleTokenResponse();
    authResponse3.setAccessToken("access-token-login-3");
    authResponse3.setRefreshToken("refresh-token-login-3");
    authResponse3.setExpiresInSeconds(Long.valueOf(100));

    GoogleAuthorizationCodeTokenRequest tokenRequest =
        mock(GoogleAuthorizationCodeTokenRequest.class);
    when(tokenRequest.execute())
        .thenReturn(authResponse1).thenReturn(authResponse2).thenReturn(authResponse3);

    when(tokenRequestCreator.create(any(HttpTransport.class), any(JsonFactory.class),
                                    anyString(), anyString(), anyString(), anyString()))
        .thenReturn(tokenRequest);

    when(uiFacade.obtainVerificationCodeFromExternalUserInteraction(anyString()))
        .thenReturn(new VerificationCodeHolder(null, null));

    return new GoogleLoginState("client-id", "client-secret", FAKE_OAUTH_SCOPES, authDataStore,
        uiFacade, loggerFacade, tokenRequestCreator, emailQueryUrl);
  }

  private enum EmailServerResponse {
    OK, INTERNAL_SERVER_ERROR, MALFORMED_CONTENT, CONNECTION_CLOSE
  };

  private String runEmailQueryServer(int timesServing, EmailServerResponse responseType)
      throws IOException {
    emailServerSocket = new ServerSocket();
    emailServerSocket.bind(null);
    emailServerSocket.setSoTimeout(5000 /* ms */);
    for (int i = 0; i < timesServing; i++) {
      startQueryHandlerThread(responseType);
    }
    return "http://127.0.0.1:" + emailServerSocket.getLocalPort();
  }

  private AtomicInteger emailTagNumber = new AtomicInteger(1);

  private void startQueryHandlerThread(final EmailServerResponse responseType) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        try (
            Socket socket = emailServerSocket.accept();
            OutputStreamWriter writer =
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8.name());
        ) {
          switch (responseType) {
            case INTERNAL_SERVER_ERROR:
              writer.write("HTTP/1.1 500 Server Error\n");
              writer.write("Content-Length: 0\n\n");
              break;

            case MALFORMED_CONTENT:
              writer.write("HTTP/1.1 200 OK\n\n");
              writer.write("malformed-content\n\n");
              break;

            case CONNECTION_CLOSE:
              emailServerSocket.close();
              break;

            default:
              int tag = emailTagNumber.getAndIncrement();
              writer.write("HTTP/1.1 200 OK\n\n");
              writer.write("email=email-from-server-" + tag + "@example.com\n\n");
          }
        } catch (IOException ioe) {
          // Not expected under normal circumstances. Ignored.
        }
      }
    }).start();
  }

  private void verifyAccountsContain(Set<Account> accounts,
        String email, String accessToken, String refreshToken, long expiryTime) {
    ArrayList<Account> accountList = new ArrayList<>(accounts);
    int index = accountList.indexOf(new Account(email, mock(Credential.class)));
    assertNotEquals(-1, index);

    Account account = accountList.get(index);
    assertEquals(accessToken, account.getAccessToken());
    assertEquals(refreshToken, account.getRefreshToken());
    if (expiryTime != -1) {
      assertEquals(expiryTime, account.getAccessTokenExpiryTime());
    }
  }
}