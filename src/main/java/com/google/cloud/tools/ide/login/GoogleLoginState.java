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
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides methods for logging into and out of Google services via OAuth 2.0, and for returning
 * credentials while a user is logged in.
 *
 * <p>This class is platform independent, but an instance is constructed with platform-specific
 * implementations of the {@link OAuthDataStore} interface to store credentials persistently on
 * the platform, the {@link UiFacade} interface to perform certain user interactions using the
 * platform UI, and the {@link LoggerFacade} interface to write to the platform's logging system.
 *
 * Note: the support for support multi-account login was added later, and as such, not every
 * aspect of multi-account login is fully addressed.
 */
public class GoogleLoginState {

  private static final String OAUTH2_NATIVE_CALLBACK_URL = GoogleOAuthConstants.OOB_REDIRECT_URI;

  private static final String DEFAULT_API_APPLICATION_NAME = "google-ide-login";

  private final int USER_INFO_QUERY_HTTP_CONNECTION_TIMEOUT = 5000 /* ms */;
  private final int USER_INFO_QUERY_HTTP_READ_TIMEOUT = 3000 /* ms */;

  private static final JsonFactory jsonFactory = new JacksonFactory();
  private static final HttpTransport transport = new NetHttpTransport();

  private final String clientId;
  private final String clientSecret;
  private final ImmutableSet<String> oAuthScopes;
  private final OAuthDataStore authDataStore;
  private final UiFacade uiFacade;
  private final LoggerFacade loggerFacade;

  // List of currently logged-in users.
  private final AccountRoster accountRoster = new AccountRoster();

  private final Collection<LoginListener> listeners;

  // Wrapper of the GoogleAuthorizationCodeTokenRequest constructor. Only for unit-testing.
  private final GoogleAuthorizationCodeTokenRequestCreator authorizationCodeTokenRequestCreator;
  private final UserInfoService userInfoService;

  private String apiApplicationName = DEFAULT_API_APPLICATION_NAME;

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
  public GoogleLoginState(String clientId, String clientSecret, Set<String> oAuthScopes,
      OAuthDataStore authDataStore, UiFacade uiFacade, LoggerFacade loggerFacade) {
    this(clientId, clientSecret, oAuthScopes, authDataStore, uiFacade, loggerFacade,
        new GoogleAuthorizationCodeTokenRequestCreator(), new UserInfoService());
  }

  @VisibleForTesting
  GoogleLoginState(String clientId, String clientSecret, Set<String> oAuthScopes,
      OAuthDataStore authDataStore, UiFacade uiFacade, LoggerFacade loggerFacade,
      GoogleAuthorizationCodeTokenRequestCreator authorizationCodeTokenRequestCreator,
      UserInfoService userInfoService) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.oAuthScopes = ImmutableSet.copyOf(oAuthScopes);
    this.authDataStore = authDataStore;
    this.uiFacade = uiFacade;
    this.loggerFacade = loggerFacade;
    this.authorizationCodeTokenRequestCreator = authorizationCodeTokenRequestCreator;
    this.userInfoService = userInfoService;

    listeners = Lists.newLinkedList();
    retrieveSavedCredentials();
  }

  public void setApiApplicationName(String apiApplicationName) {
    this.apiApplicationName = apiApplicationName;
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
   * @return true if there is at least one account logged in; false otherwise
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
   * If a user signs in with an already existing account, the old account will be replaced with
   * the new login result.
   *
   * @param title
   *     the title to be displayed at the top of the interaction if the platform supports it, or
   *     {@code null} if no title is to be displayed
   * @return signed-in {@link Account} for successful login; {@code null} otherwise
   */
  public Account logIn(@Nullable String title) {
    GoogleAuthorizationCodeRequestUrl requestUrl =
        new GoogleAuthorizationCodeRequestUrl(clientId, OAUTH2_NATIVE_CALLBACK_URL, oAuthScopes);

    String verificationCode =
        uiFacade.obtainVerificationCodeFromUserInteraction(title, requestUrl);
    if (verificationCode == null) {
      return null;
    }

    GoogleAuthorizationCodeTokenRequest authRequest =
        authorizationCodeTokenRequestCreator.create(transport, jsonFactory,
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
   * If a user signs in with an already existing account, the old account will be replaced with
   * the new login result.
   *
   * @param title the title to be displayed at the top of the interaction if the platform supports it, or
   *     {@code null} if no title is to be displayed
   * @return signed-in {@link Account} for successful login; {@code null} otherwise
   */
  public Account logInWithLocalServer(@Nullable String title) {
    VerificationCodeHolder codeHolder =
        uiFacade.obtainVerificationCodeFromExternalUserInteraction(title);
    if (codeHolder == null) {
      return null;
    }

    GoogleAuthorizationCodeTokenRequest authRequest =
        authorizationCodeTokenRequestCreator.create(transport, jsonFactory,
            clientId, clientSecret,
            codeHolder.getVerificationCode(),
            codeHolder.getRedirectUrl());
    return logInHelper(authRequest);
  }

  private Account logInHelper(GoogleAuthorizationCodeTokenRequest authRequest) {
    try {
      GoogleTokenResponse authResponse = authRequest.execute();
      Account account = updateLoginState(authResponse);
      persistCredentials();
      uiFacade.notifyStatusIndicator();
      notifyLoginStatusChange();
      return account;
    } catch (IOException | EmailAddressNotReturnedException ex) {
      uiFacade.showErrorDialog(
          "Error while signing in",
          "An error occured while trying to sign in: " + ex.getMessage());
      loggerFacade.logError("Could not sign in", ex);
      return null;
    }
  }

  /**
   * Logs out all accounts. Pops up a question dialog asking if the user really wants to log out.
   *
   * @return false if the user canceled login; true otherwise
   */
  public boolean logOutAll() {
    return logOutAll(true);
  }

  /**
   * Logs out all accounts.
   *
   * @param showPrompt if true, opens a prompt asking if the user really wants
   *          to log out. If false, the user is logged out
   * @return false if the user canceled login; true otherwise
   */
  public boolean logOutAll(boolean showPrompt) {
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

  private Credential buildCredential() {
    return new GoogleCredential.Builder()
        .setJsonFactory(jsonFactory)
        .setTransport(transport)
        .setClientSecrets(clientId, clientSecret)
        .build();
  }

  private Credential makeCredential(
      String accessToken, String refreshToken, long expiryTimeInMilliSeconds) {
    return buildCredential().setAccessToken(accessToken)
        .setRefreshToken(refreshToken)
        .setExpirationTimeMilliseconds(expiryTimeInMilliSeconds);
  }

  private Account updateLoginState(GoogleTokenResponse tokenResponse)
      throws IOException, EmailAddressNotReturnedException {
    Credential credential = buildCredential().setFromTokenResponse(tokenResponse);
    UserInfo userInfo = queryUserInfo(credential);
    accountRoster.addAccount(
        new Account(userInfo.getEmail(), credential, userInfo.getName(), userInfo.getPicture()));

    // Return a copy of the credential.
    return new Account(userInfo.getEmail(), buildCredential().setFromTokenResponse(tokenResponse),
        userInfo.getName(), userInfo.getPicture());
  }

  private void retrieveSavedCredentials() {
    Preconditions.checkState(!isLoggedIn(), "Should be called only once in the constructor.");

    for (OAuthData savedAuthState : authDataStore.loadOAuthData()) {
      if (savedAuthState.getRefreshToken() == null || savedAuthState.getStoredScopes().isEmpty()) {
        authDataStore.removeOAuthData(savedAuthState.getEmail());
        continue;
      }

      if (!oAuthScopes.equals(savedAuthState.getStoredScopes())) {
        loggerFacade.logWarning(
            "OAuth scope set for stored credentials no longer valid, logging out.");
        loggerFacade.logWarning(oAuthScopes + " vs. " + savedAuthState.getStoredScopes());
        authDataStore.removeOAuthData(savedAuthState.getEmail());
        continue;
      }

      Credential credential = makeCredential(savedAuthState.getAccessToken(),
          savedAuthState.getRefreshToken(), savedAuthState.getAccessTokenExpiryTime());
      accountRoster.addAccount(new Account(savedAuthState.getEmail(), credential,
          savedAuthState.getName(), savedAuthState.getAvatarUrl()));
    }
  }

  private void notifyLoginStatusChange() {
    Set<Account> accounts = listAccounts();  // Take a snapshot of accounts.
    synchronized(listeners) {
      for (LoginListener listener : listeners) {
        listener.statusChanged(accounts);
      }
    }
  }

  @VisibleForTesting
  UserInfo queryUserInfo(Credential credential)
      throws IOException, EmailAddressNotReturnedException {
    HttpRequestInitializer timeoutSetter = new HttpRequestInitializer() {
      @Override
      public void initialize(HttpRequest httpRequest) throws IOException {
        httpRequest.setConnectTimeout(USER_INFO_QUERY_HTTP_CONNECTION_TIMEOUT);
        httpRequest.setReadTimeout(USER_INFO_QUERY_HTTP_READ_TIMEOUT);
      }
    };

    UserInfo userInfo = userInfoService.buildAndExecuteRequest(
        transport, jsonFactory, credential, timeoutSetter);
    if (userInfo == null || Strings.isNullOrEmpty(userInfo.getEmail())) {
      throw new EmailAddressNotReturnedException("Server failed to return email address");
    }
    return userInfo;
  }

  /**
   * Returns a (snapshot) list of currently logged-in accounts. UI may call this to update login
   * widgets, e.g., inside {@link UiFacade#notifyStatusIndicator}.
   *
   */
  @Nonnull
  public Set<Account> listAccounts() {
    Set<Account> snapshot = new HashSet<>();
    for (Account account : accountRoster.getAccounts()) {
      Credential credential = makeCredential(
          account.getAccessToken(), account.getRefreshToken(), account.getAccessTokenExpiryTime());

      snapshot.add(
          new Account(account.getEmail(), credential, account.getName(), account.getAvatarUrl()));
    }
    return snapshot;
  }

  private void persistCredentials() {
    Preconditions.checkState(isLoggedIn());

    for (Account account : accountRoster.getAccounts()) {
      OAuthData oAuthData = new OAuthData(account.getAccessToken(), account.getRefreshToken(),
          account.getEmail(), account.getName(), account.getAvatarUrl(),
          oAuthScopes, account.getAccessTokenExpiryTime());
      authDataStore.saveOAuthData(oAuthData);
    }
  }
}
