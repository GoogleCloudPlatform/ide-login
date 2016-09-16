package com.google.cloud.tools.ide.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(MockitoJUnitRunner.class)
public class GoogleLoginStateTest {

  private static final Set<String> FAKE_OAUTH_SCOPES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("oauth-scope-1", "oauth-scope-2")));

  @Mock private GoogleAuthorizationCodeTokenRequestCreator tokenRequestCreator;
  @Mock private UiFacade uiFacade;
  @Mock private LoggerFacade loggerFacade;

  private AccountRoster accountRoster;
  private OAuthDataStore authDataStore =
      new JavaPreferenceOAuthDataStore("test-node", loggerFacade);

  private ServerSocket emailServerSocket;

  @Test
  public void testIsLoggedIn() throws IOException {
    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);
    assertFalse(state.isLoggedIn());
    assertTrue(accountRoster.isEmpty());
  }

  @Test
  public void testGetActiveCredential() throws IOException {
    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);
    assertNull(state.getActiveCredential());
    assertTrue(accountRoster.isEmpty());
  }

  @Test
  public void testListAccounts() throws IOException {
    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);
    assertEquals(0, state.listAccounts().size());
    assertTrue(accountRoster.isEmpty());
  }

  @Test
  public void testLoadPersistedAccount() throws IOException {
    OAuthData fakeOAuthData = new OAuthData(
        "access-token-5", "refresh-token-5", "email-5@example.com", FAKE_OAUTH_SCOPES, 543);
    authDataStore.saveOAuthData(fakeOAuthData);

    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);

    assertTrue(state.isLoggedIn());
    assertEquals("access-token-5", state.getActiveCredential().getAccessToken());
    assertEquals("refresh-token-5", state.getActiveCredential().getRefreshToken());

    assertFalse(accountRoster.isEmpty());
    Account account = accountRoster.getActiveAccount();
    assertEquals("access-token-5", account.getAccessToken());
    assertEquals("refresh-token-5", account.getRefreshToken());
    assertEquals("email-5@example.com", account.getEmail());
    assertEquals(543, account.getAccessTokenExpiryTime());
  }

  @Test
  public void testLoadPersistedAccount_clearLoginIfScopesMismatch() throws IOException {
    Set<String> deprecatedScopes = new HashSet<>(Arrays.asList("deprecated-scope"));
    OAuthData invalidatedOAuthData = new OAuthData(
        "access-token-1", "refresh-token-1", "email-1@example.com", deprecatedScopes, 0);
    authDataStore.saveOAuthData(invalidatedOAuthData);

    GoogleLoginState state = newGoogleLoginState(null /* emailQueryUrl */);

    assertFalse(state.isLoggedIn());
    assertTrue(accountRoster.isEmpty());
    assertNull(authDataStore.loadOAuthData().getAccessToken());
    assertNull(authDataStore.loadOAuthData().getRefreshToken());
  }

  @Test
  public void testLogInWithLocalServer() throws IOException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    long currentTimeInSecs = System.currentTimeMillis() / 1000;
    state.logInWithLocalServer(null /* no title */);

    assertTrue(state.isLoggedIn());
    assertEquals("access-token-login-1", state.getActiveCredential().getAccessToken());
    assertEquals("refresh-token-login-1", state.getActiveCredential().getRefreshToken());

    assertFalse(accountRoster.isEmpty());
    Account account = accountRoster.getActiveAccount();
    assertEquals("access-token-login-1", account.getAccessToken());
    assertEquals("refresh-token-login-1", account.getRefreshToken());
    assertEquals("email-from-server-1@example.com", account.getEmail());
    assertTrue(currentTimeInSecs + 10 <= account.getAccessTokenExpiryTime());
    assertTrue(currentTimeInSecs + 15 > account.getAccessTokenExpiryTime());
  }

  @Test
  public void testLogInWithLocalServer_threeLogins() throws IOException {
    String emailQueryUrl = runEmailQueryServer(3, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    AccountsInfo accountsInfo = state.listAccounts();
    assertEquals(3, accountsInfo.size());
    assertEquals("email-from-server-3@example.com", accountsInfo.getActiveAccount().getEmail());
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-1@example.com");
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-2@example.com");

    Set<Account> accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-1");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-2");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-3");
    accountContains(accounts, CompareType.REFRESH_TOKEN, "refresh-token-login-1");
    accountContains(accounts, CompareType.REFRESH_TOKEN, "refresh-token-login-2");
    accountContains(accounts, CompareType.REFRESH_TOKEN, "refresh-token-login-3");
    accountContains(accounts, CompareType.EMAIL, "email-from-server-1@example.com");
    accountContains(accounts, CompareType.EMAIL, "email-from-server-2@example.com");
    accountContains(accounts, CompareType.EMAIL, "email-from-server-3@example.com");
  }

  @Test
  public void testSwitchActiveAccount() throws IOException {
    String emailQueryUrl = runEmailQueryServer(3, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    state.switchActiveAccount("email-from-server-2@example.com");
    AccountsInfo accountsInfo = state.listAccounts();
    assertEquals("email-from-server-2@example.com", accountsInfo.getActiveAccount().getEmail());
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-1@example.com");
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-3@example.com");
    Set<Account> accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-1");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-2");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-3");

    state.switchActiveAccount("email-from-server-1@example.com");
    accountsInfo = state.listAccounts();
    assertEquals("email-from-server-1@example.com", accountsInfo.getActiveAccount().getEmail());
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-2@example.com");
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-3@example.com");
    accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-1");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-2");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-3");

    state.switchActiveAccount("email-from-server-3@example.com");
    accountsInfo = state.listAccounts();
    assertEquals("email-from-server-3@example.com", accountsInfo.getActiveAccount().getEmail());
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-1@example.com");
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-2@example.com");
    accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-1");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-2");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-3");
  }

  @Test
  public void testSwitchActiveAccount_nonExistingEmail() throws IOException {
    String emailQueryUrl = runEmailQueryServer(3, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    state.switchActiveAccount("non-existing-email@example.com");
    AccountsInfo accountsInfo = state.listAccounts();
    assertEquals(accountsInfo.getActiveAccount().getEmail(), "email-from-server-3@example.com");
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-1@example.com");
    accountInfoContainsEmail(accountsInfo.getInactiveAccounts(), "email-from-server-2@example.com");
    Set<Account> accounts = accountRoster.getAccounts();
    assertEquals(3, accounts.size());
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-1");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-2");
    accountContains(accounts, CompareType.ACCESS_TOKEN, "access-token-login-3");
  }

  @Test
  public void testLogOut() throws IOException {
    String emailQueryUrl = runEmailQueryServer(3, EmailServerResponse.OK);
    GoogleLoginState state = newGoogleLoginState(emailQueryUrl);

    state.logInWithLocalServer(null /* no title */);
    state.logInWithLocalServer(null);
    state.logInWithLocalServer(null);

    assertTrue(state.isLoggedIn());
    assertEquals(3, state.listAccounts().size());
    assertEquals(3, accountRoster.getAccounts().size());

    state.logOut(false /* don't show prompt dialog */);

    assertFalse(state.isLoggedIn());
    assertEquals(0, state.listAccounts().size());
    assertTrue(accountRoster.isEmpty());
    assertTrue(accountRoster.getAccounts().isEmpty());
  }

  @Test
  public void testPersistLoadAccounts() throws IOException {
    String emailQueryUrl = runEmailQueryServer(1, EmailServerResponse.OK);
    GoogleLoginState state1 = newGoogleLoginState(emailQueryUrl);
    state1.logInWithLocalServer(null);  // Credentials will be persisted in authDataStore.

    GoogleLoginState state2 = newGoogleLoginState(null /* emailQueryUrl */);
    assertEquals("access-token-login-1", state2.getActiveCredential().getAccessToken());
    assertEquals("refresh-token-login-1", state2.getActiveCredential().getRefreshToken());
  }

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
    authResponse1.setExpiresInSeconds(Long.valueOf(10));

    GoogleTokenResponse authResponse2 = new GoogleTokenResponse();
    authResponse2.setAccessToken("access-token-login-2");
    authResponse2.setRefreshToken("refresh-token-login-2");
    authResponse2.setExpiresInSeconds(Long.valueOf(10));

    GoogleTokenResponse authResponse3 = new GoogleTokenResponse();
    authResponse3.setAccessToken("access-token-login-3");
    authResponse3.setRefreshToken("refresh-token-login-3");
    authResponse3.setExpiresInSeconds(Long.valueOf(10));

    GoogleAuthorizationCodeTokenRequest tokenRequest =
        mock(GoogleAuthorizationCodeTokenRequest.class);
    when(tokenRequest.execute())
        .thenReturn(authResponse1).thenReturn(authResponse2).thenReturn(authResponse3);

    when(tokenRequestCreator.create(any(HttpTransport.class), any(JsonFactory.class),
                                    anyString(), anyString(), anyString(), anyString()))
        .thenReturn(tokenRequest);

    when(uiFacade.obtainVerificationCodeFromExternalUserInteraction(anyString()))
        .thenReturn(new VerificationCodeHolder(null, null));

    accountRoster = new AccountRoster();
    return new GoogleLoginState("client-id", "client-secret", FAKE_OAUTH_SCOPES, authDataStore,
        uiFacade, loggerFacade, accountRoster, tokenRequestCreator, emailQueryUrl);
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

  private enum CompareType { EMAIL, ACCESS_TOKEN, REFRESH_TOKEN };
  private static void accountContains(Set<Account> set, CompareType type, String value) {
    for (Account account : set) {
      switch (type) {
        case EMAIL:
          if (value.equals(account.getEmail())) {
            return;
          }
          break;
        case ACCESS_TOKEN:
          if (value.equals(account.getAccessToken())) {
            return;
          }
          break;
        case REFRESH_TOKEN:
          if (value.equals(account.getRefreshToken())) {
            return;
          }
          break;
        default:
          throw new RuntimeException();
      }
    }
    fail();
  }

  private static void accountInfoContainsEmail(Set<AccountInfo> set, String email) {
    for (AccountInfo accountInfo : set) {
      if (email.equals(accountInfo.getEmail())) {
        return;
      }
    }
    fail();
  }
}
