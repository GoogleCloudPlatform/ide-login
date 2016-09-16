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
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Provides methods for logging into and out of Google services via OAuth 2.0, and for fetching
 * credentials while a user is logged in.
 *
 * <p>This class is platform independent, but an instance is constructed with platform-specific
 * implementations of the {@link OAuthDataStore} interface to store credentials persistently on
 * the platform, the {@link UiFacade} interface to perform certain user interactions using the
 * platform UI, and the {@link LoggerFacade} interface to write to the platform's logging system.
 */
public class GoogleLoginState {

  private static final String GET_EMAIL_URL = "https://www.googleapis.com/userinfo/email";
  private static final String OAUTH2_NATIVE_CALLBACK_URL = GoogleOAuthConstants.OOB_REDIRECT_URI;

  private final int EMAIL_QUERY_HTTP_CONNECTION_TIMEOUT = 5000 /* ms */;
  private final int EMAIL_QUERY_HTTP_READ_TIMEOUT = 3000 /* ms */;

  private static final JsonFactory jsonFactory = new JacksonFactory();
  private static final HttpTransport transport = new NetHttpTransport();

  private String clientId;
  private String clientSecret;
  private Set<String> oAuthScopes;
  private OAuthDataStore authDataStore;
  private UiFacade uiFacade;
  private LoggerFacade loggerFacade;

  // List of currently logged-in users.
  private AccountRoster accountRoster;

  private final Collection<LoginListener> listeners;

  // Wrapper of the GoogleAuthorizationCodeTokenRequest constructor. Only for unit-testing.
  private GoogleAuthorizationCodeTokenRequestCreator googleAuthorizationCodeTokenRequestCreator;
  private String emailQueryUrl;

  /**
   * Construct a new platform-specific {@code GoogleLoginState} for a specified client application
   * and specified authorization scopes.
   *
   * @param clientId the client ID for the specified client application
   * @param clientSecret the client secret for the specified client application
   * @param oAuthScopes the authorization scopes
   * @param authDataStore
   *     a platform-specific implementation of the {@link OAuthDataStore} interface
   * @param uiFacade a platform-specific implementation of the {@link UiFacade} interface
   * @param loggerFacade a platform-specific implementation of the {@link LoggerFacade} interface
   */
  public GoogleLoginState(
      String clientId, String clientSecret, Set<String> oAuthScopes,
      OAuthDataStore authDataStore, UiFacade uiFacade, LoggerFacade loggerFacade) {
    this(clientId, clientSecret, oAuthScopes, authDataStore, uiFacade, loggerFacade,
        new AccountRoster(), new GoogleAuthorizationCodeTokenRequestCreator(), GET_EMAIL_URL);
  }

  @VisibleForTesting
  GoogleLoginState(
      String clientId, String clientSecret, Set<String> oAuthScopes,
      OAuthDataStore authDataStore, UiFacade uiFacade, LoggerFacade loggerFacade,
      AccountRoster accountRoster,
      GoogleAuthorizationCodeTokenRequestCreator googleAuthorizationCodeTokenRequestCreator,
      String emailQueryUrl) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.oAuthScopes = oAuthScopes;
    this.authDataStore = authDataStore;
    this.uiFacade = uiFacade;
    this.loggerFacade = loggerFacade;
    this.accountRoster = accountRoster;
    this.googleAuthorizationCodeTokenRequestCreator = googleAuthorizationCodeTokenRequestCreator;
    this.emailQueryUrl = emailQueryUrl;

    listeners = Lists.newLinkedList();
    retrieveSavedCredentials();
  }

  /**
   * Register a specified {@link LoginListener} to be notified of changes to the logged-in state.
   *
   * @param listener the specified {@code LoginListener}
   */
  public void addLoginListener(LoginListener listener) {
    synchronized(listeners) {
      listeners.add(listener);
    }
  }

  /**
   * Returns an HttpRequestFactory object that has been signed with the users's
   * authentication headers to use to make http requests.
   *
   * <p>If the access token that was used to sign this transport was revoked or
   * has expired, then execute() invoked on Request objects constructed from
   * this transport will throw an exception, for example,
   * "com.google.api.client.http.HttpResponseException: 401 Unauthorized"
   *
   * @throws IllegalStateException if no user is currently signed in
   */
  public HttpRequestFactory createRequestFactory() {
    Preconditions.checkState(isLoggedIn());

    return createRequestFactoryInternal(getActiveAccount().getOAuth2Credential());
  }

  private HttpRequestFactory createRequestFactoryInternal(Credential credential) {
    return transport.createRequestFactory(credential);
  }

  /**
   * Makes a request to get an OAuth2 access token from the OAuth2 refresh token
   * if it is expired.
   *
   * @return an OAuth2 token
   * @throws IllegalStateException if no user is currently signed in
   * @throws IOException if something goes wrong while fetching the token
   */
  public String fetchAccessToken() throws IOException {
    Preconditions.checkState(isLoggedIn());

    Account account = getActiveAccount();
    if (account.getAccessTokenExpiryTime() == 0) {
      return fetchOAuth2Token();
    }

    long currentTime = System.currentTimeMillis() / 1000;
    if (currentTime >= account.getAccessTokenExpiryTime()) {
      return fetchOAuth2Token();
    }
    return account.getAccessToken();
  }

  public String fetchOAuth2ClientId() {
    return clientId;
  }

  public String fetchOAuth2ClientSecret() {
    return clientSecret;
  }

  /**
   * Returns the OAuth2 refresh token.
   *
   * @throws IllegalStateException if no user is currently signed in
   */
  public String fetchOAuth2RefreshToken() {
    Preconditions.checkState(isLoggedIn());

    return getActiveAccount().getRefreshToken();
  }

  /**
   * Makes a request to get an OAuth2 access token from the OAuth2 refresh
   * token. This token is short lived.
   *
   * @return an OAuth2 token
   * @throws IllegalStateException if no user is currently signed in
   * @throws IOException if something goes wrong while fetching the token
   *
   */
  public String fetchOAuth2Token() throws IOException {
    Preconditions.checkState(isLoggedIn());

    Account account = getActiveAccount();
    try {
      GoogleRefreshTokenRequest request = new GoogleRefreshTokenRequest(
          transport, jsonFactory, account.getRefreshToken(), clientId, clientSecret);
      GoogleTokenResponse authResponse = request.execute();

      account.getOAuth2Credential().setAccessToken(authResponse.getAccessToken());
      account.setAccessTokenExpiryTime(System.currentTimeMillis() / 1000
          + authResponse.getExpiresInSeconds());
    } catch (IOException ex) {
      loggerFacade.logError("Could not obtain an OAuth2 access token.", ex);
      throw ex;
    }
    persistCredentials();
    return account.getAccessToken();
  }

  /**
   * Returns a credential of an active account. {@link GoogleLoginState} designates only one
   * account as active, which can be switched using {@link #switchActiveAccount}. The very first
   * account added will automatically be designated as active.
   */
  public Credential getActiveCredential() {
    if (!isLoggedIn()) {
      return null;
    }

    return makeCredential(getActiveAccount().getAccessToken(),
                          getActiveAccount().getRefreshToken());
  }

  /**
   * @return true if the user is logged in, false otherwise
   */
  public boolean isLoggedIn() {
    return !accountRoster.isEmpty();
  }

  /**
   * Conducts a user interaction, which may involve both browsers and platform-specific UI widgets,
   * to allow the user to sign in, and returns a result indicating whether the user successfully
   * signed in. (This method always prompts to sign in, allowing signing in with multiple accounts.)
   *
   * <p>The caller may optionally specify a title to be displayed at the top of the interaction, if
   * the platform supports it. This is for when the user is presented the login dialog from doing
   * something other than logging in, such as accessing Google API services. It should say something
   * like "Importing a project from Drive requires signing in."
   *
   * @param title
   *     the title to be displayed at the top of the interaction if the platform supports it, or
   *     {@code null} if no title is to be displayed
   *
   * @return true if the user signed in or is already signed in, false otherwise
   */
  public boolean logIn(@Nullable String title) {
    GoogleAuthorizationCodeRequestUrl requestUrl =
        new GoogleAuthorizationCodeRequestUrl(clientId, OAUTH2_NATIVE_CALLBACK_URL, oAuthScopes);

    String verificationCode =
        uiFacade.obtainVerificationCodeFromUserInteraction(title, requestUrl);
    if (verificationCode == null) {
      return false;
    }

    GoogleAuthorizationCodeTokenRequest authRequest =
        googleAuthorizationCodeTokenRequestCreator.create(transport, jsonFactory,
            clientId, clientSecret,
            verificationCode,
            OAUTH2_NATIVE_CALLBACK_URL);
    return logInHelper(authRequest);
  }

  /**
   * Conducts a user interaction, which may involve a browser or platform-specific UI widgets,
   * to allow the user to sign in, and returns a result indicating whether the user successfully
   * signed in. (This method always prompts to sign in, allowing signing in with multiple accounts.)
   *
   * The caller generates their own Google authorization URL which allows the user to set
   * their local http server. This allows the user to get the verification code from a local
   * server that OAuth can redirect to.
   *
   * @param title
   *     the title to be displayed at the top of the interaction if the platform supports it, or
   *     {@code null} if no title is to be displayed
   * @return true if the user signed in or is already signed in, false otherwise
   */
  public boolean logInWithLocalServer(@Nullable String title) {
    VerificationCodeHolder codeHolder =
        uiFacade.obtainVerificationCodeFromExternalUserInteraction(title);
    if (codeHolder == null) {
      return false;
    }

    GoogleAuthorizationCodeTokenRequest authRequest =
        googleAuthorizationCodeTokenRequestCreator.create(transport, jsonFactory,
            clientId, clientSecret,
            codeHolder.getVerificationCode(),
            codeHolder.getRedirectUrl());
    return logInHelper(authRequest);
  }

  private boolean logInHelper(GoogleAuthorizationCodeTokenRequest authRequest) {
    try {
      GoogleTokenResponse authResponse = authRequest.execute();
      updateLoginState(authResponse);
      persistCredentials();
      uiFacade.notifyStatusIndicator();
      notifyLoginStatusChange();
      return true;
    } catch (IOException | EmailAddressNotReturnedException ex) {
      uiFacade.showErrorDialog(
          "Error while signing in",
          "An error occured while trying to sign in: " + ex.getMessage());
      loggerFacade.logError("Could not sign in", ex);
      return false;
    }
  }

  /**
   * Logs the user out. Pops up a question dialog asking if the user really
   * wants to quit.
   *
   * @return true if the user logged out, false otherwise
   */
  public boolean logOut() {
    return logOut(true);
  }

  /**
   * Logs the user out.
   *
   * @param showPrompt if true, opens a prompt asking if the user really wants
   *          to log out. If false, the user is logged out
   * @return true if the user was logged out or is already logged out, and false
   *         if the user chose not to log out
   */
  public boolean logOut(boolean showPrompt) {
    if (!isLoggedIn()) {
      return true;
    }

    if (showPrompt) {
      if (!uiFacade.askYesOrNo("Sign out?", "Are you sure you want to sign out?")) {
        return false;
      }
    }

    accountRoster.clear();
    authDataStore.clearStoredOAuthData();

    notifyLoginStatusChange();
    uiFacade.notifyStatusIndicator();
    return true;
  }

  private Credential makeCredential(String accessToken, String refreshToken) {
    Credential cred =
        new GoogleCredential.Builder()
            .setJsonFactory(jsonFactory)
            .setTransport(transport)
            .setClientSecrets(clientId, clientSecret)
            .build();
    cred.setAccessToken(accessToken);
    cred.setRefreshToken(refreshToken);
    return cred;
  }

  private void updateLoginState(GoogleTokenResponse tokenResponse)
      throws IOException, EmailAddressNotReturnedException {
    Credential credential = makeCredential(tokenResponse.getAccessToken(),
                                           tokenResponse.getRefreshToken());
    String email = queryEmail(credential);
    Long expiryTime = System.currentTimeMillis() / 1000 + tokenResponse.getExpiresInSeconds();
    accountRoster.addAndSetActiveAccount(new Account(email, credential, expiryTime));
  }

  private void retrieveSavedCredentials() {
    Preconditions.checkState(!isLoggedIn(), "Should be called only once in the constructor.");

    OAuthData savedAuthState = authDataStore.loadOAuthData();

    if (savedAuthState.getRefreshToken() == null || savedAuthState.getStoredScopes().isEmpty()) {
      authDataStore.clearStoredOAuthData();
      return;
    }

    if (!oAuthScopes.equals(savedAuthState.getStoredScopes())) {
      loggerFacade.logWarning(
          "OAuth scope set for stored credentials no longer valid, logging out.");
      loggerFacade.logWarning(oAuthScopes + " vs. " + savedAuthState.getStoredScopes());
      authDataStore.clearStoredOAuthData();
      return;
    }

    Credential credential = makeCredential(savedAuthState.getAccessToken(),
                                           savedAuthState.getRefreshToken());
    String email = savedAuthState.getStoredEmail();
    Account account = new Account(email, credential, savedAuthState.getAccessTokenExpiryTime());
    accountRoster.addAndSetActiveAccount(account);
  }

  private void notifyLoginStatusChange() {
    AccountsInfo accountsInto = listAccounts();
    synchronized(listeners) {
      for (LoginListener listener : listeners) {
        listener.statusChanged(accountsInto);
      }
    }
  }

  @VisibleForTesting
  String queryEmail(Credential credential) throws IOException, EmailAddressNotReturnedException {
    HttpRequest get = createRequestFactoryInternal(credential)
        .buildGetRequest(new GenericUrl(emailQueryUrl))
        .setConnectTimeout(EMAIL_QUERY_HTTP_CONNECTION_TIMEOUT)
        .setReadTimeout(EMAIL_QUERY_HTTP_READ_TIMEOUT);
    HttpResponse response = get.execute();

    String responseString = "";
    try (Scanner scan = new Scanner(response.getContent())) {
      while (scan.hasNext()) {
        responseString += scan.nextLine();
      }
    }

    String userEmail = parseUrlParameters(responseString).get("email");
    if (userEmail == null) {
      throw new EmailAddressNotReturnedException("Server failed to return email address");
    }
    return userEmail;
  }

  /**
   * Takes a string that looks like "param1=val1&param2=val2&param3=val3" and
   * puts the key-value pairs into a map. The string is assumed to be UTF-8
   * encoded. If the string has a '?' character, then only the characters after
   * the question mark are considered.
   *
   * @param params The parameter string.
   * @return A map with the key value pairs
   * @throws UnsupportedEncodingException if UTF-8 encoding is not supported
   */
  private static Map<String, String> parseUrlParameters(String params)
      throws UnsupportedEncodingException {
    Map<String, String> paramMap = new HashMap<>();

    int questionMark = params.indexOf('?');
    if (questionMark > -1) {
      params = params.substring(questionMark + 1);
    }

    String[] paramArray = params.split("&");
    for (String param : paramArray) {
      String[] keyVal = param.split("=");
      if (keyVal.length == 2) {
        paramMap.put(URLDecoder.decode(keyVal[0], "UTF-8"), URLDecoder.decode(keyVal[1], "UTF-8"));
      }
    }
    return paramMap;
  }

  private Account getActiveAccount() {
    Preconditions.checkState(isLoggedIn());
    Preconditions.checkState(!accountRoster.isEmpty());

    return accountRoster.getActiveAccount();
  }

  /**
   * Sets an account with the given {@code email} as an active account. Does nothing if a matching
   * account does not exist. Calls {@link UiFacade#notifyStatusIndicator} at the end.
   *
   * @param email cannot be {@code null}.
   */
  public void switchActiveAccount(String email) {
    Preconditions.checkNotNull(email);

    if (accountRoster.switchActiveAccount(email)) {
      persistCredentials();
      notifyLoginStatusChange();
      uiFacade.notifyStatusIndicator();
    }
  }

  /**
   * Returns a list of currently logged-in accounts. Intended for UI to call for the purpose of
   * updating login widgets, e.g., inside it inside {@link UiFacade#notifyStatusIndicator}.
   *
   * @return never {@code null}. {@link AccountsInfo#activeAccount} is {@code null} is there is
   *     no logged-in account. {@link AccountsInfo#inactiveAccounts} is never {@code null}.
   */
  public AccountsInfo listAccounts() {
    return accountRoster.listAccounts();
  }

  private void persistCredentials() {
    Preconditions.checkState(isLoggedIn());

    Account account = getActiveAccount();
    OAuthData oAuthData = new OAuthData(account.getAccessToken(), account.getRefreshToken(),
        account.getEmail(), oAuthScopes, account.getAccessTokenExpiryTime());
    authDataStore.saveOAuthData(oAuthData);
  }

  @VisibleForTesting
  class EmailAddressNotReturnedException extends Exception {
    public EmailAddressNotReturnedException(String message) {
      super(message);
    }
  };
}
