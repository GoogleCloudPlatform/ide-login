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
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.Nullable;

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

  private static final JsonFactory jsonFactory = new JacksonFactory();
  private static final HttpTransport transport = new NetHttpTransport();

  private String clientId;
  private String clientSecret;
  private Set<String> oAuthScopes;
  private OAuthDataStore authDataStore;
  private UiFacade uiFacade;
  private LoggerFacade loggerFacade;

  private Credential oAuth2Credential;
  private String accessToken;
  private long accessTokenExpiryTime;
  private String refreshToken;
  private boolean isLoggedIn;
  private String email;

  private final Collection<LoginListener> listeners;

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
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.oAuthScopes = oAuthScopes;
    this.authDataStore = authDataStore;
    this.uiFacade = uiFacade;
    this.loggerFacade = loggerFacade;

    this.isLoggedIn = false;
    this.email = "";

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
    Preconditions.checkState(isLoggedIn);

    return transport.createRequestFactory(oAuth2Credential);
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
    Preconditions.checkState(isLoggedIn);

    if (accessTokenExpiryTime == 0) {
      return fetchOAuth2Token();
    }

    long currentTime = System.currentTimeMillis() / 1000;
    if (currentTime >= accessTokenExpiryTime) {
      return fetchOAuth2Token();
    }
    return accessToken;
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
    Preconditions.checkState(isLoggedIn);

    return refreshToken;
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
    Preconditions.checkState(isLoggedIn);

    try {
      GoogleRefreshTokenRequest request = new GoogleRefreshTokenRequest(
          transport, jsonFactory, refreshToken, clientId, clientSecret);
      GoogleTokenResponse authResponse = request.execute();
      accessToken = authResponse.getAccessToken();
      oAuth2Credential.setAccessToken(accessToken);
      accessTokenExpiryTime = System.currentTimeMillis() / 1000
          + authResponse.getExpiresInSeconds();
    } catch (IOException ex) {
      loggerFacade.logError("Could not obtain an OAuth2 access token.", ex);
      throw ex;
    }
    persistCredentials();
    return accessToken;
  }

  public Credential getCredential() {
    if (oAuth2Credential == null) {
      oAuth2Credential = makeCredential();
    }
    return oAuth2Credential;
  }

  /**
   * @return the user's email address, or the empty string if the user is logged
   *         out, or null if the user's email couldn't be retrieved
   */
  public String getEmail() {
    return email;
  }

  /**
   * @return true if the user is logged in, false otherwise
   */
  public boolean isLoggedIn() {
    return isLoggedIn;
  }

  /**
   * Conducts a user interaction, which may involve both browsers and platform-specific UI widgets,
   * if the user is not already signed in, to allow the user to attempt to sign in, and returns a
   * result indicating whether the user is successfully signed in. (If the user is already signed in
   * when this method is called, then the method immediately returns true, without conducting any
   * user interaction.)
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
    if (isLoggedIn) {
      return true;
    }

    GoogleAuthorizationCodeRequestUrl requestUrl =
        new GoogleAuthorizationCodeRequestUrl(clientId, OAUTH2_NATIVE_CALLBACK_URL, oAuthScopes);

    String verificationCode =
        uiFacade.obtainVerificationCodeFromUserInteraction(title, requestUrl);
    if (verificationCode == null) {
      return false;
    }

    GoogleAuthorizationCodeTokenRequest authRequest =
        new GoogleAuthorizationCodeTokenRequest(
            transport,
            jsonFactory,
            clientId,
            clientSecret,
            verificationCode,
            OAUTH2_NATIVE_CALLBACK_URL);
    GoogleTokenResponse authResponse;
    try {
      authResponse = authRequest.execute();
    } catch (IOException ex) {
      uiFacade.showErrorDialog(
          "Error while signing in",
          "An error occured while trying to sign in: " + ex.getMessage()
              + ". See the error log for more details.");
      loggerFacade.logError(
          "Could not sign in. Make sure that you entered the correct verification code.", ex);
      return false;
    }
    isLoggedIn = true;
    updateLoginState(authResponse);
    return true;
  }

  /**
   * Conducts a user interaction, which may involve a browser or platform-specific UI widgets,
   * if the user is not already signed in, to allow the user to attempt to sign in, and returns a
   * result indicating whether the user is successfully signed in. (If the user is already signed in
   * when this method is called, then the method immediately returns true, without conducting any
   * user interaction.)
   *
   * The caller would generate their own Google authorization URL which allows the user to set
   * their local http server. This allows the user to get the verification code from a local
   * server that OAuth can redirect to.
   *
   * @param title
   *     the title to be displayed at the top of the interaction if the platform supports it, or
   *     {@code null} if no title is to be displayed
   * @return true if the user signed in or is already signed in, false otherwise
   */
  public boolean logInWithLocalServer(@Nullable String title) {
    if (isLoggedIn) {
      return true;
    }

    VerificationCodeHolder codeHolder =
        uiFacade.obtainVerificationCodeFromExternalUserInteraction(title);
    if (codeHolder == null) {
      return false;
    }

    GoogleAuthorizationCodeTokenRequest authRequest =
        new GoogleAuthorizationCodeTokenRequest(
            transport,
            jsonFactory,
            clientId,
            clientSecret,
            codeHolder.getVerificationCode(),
            codeHolder.getRedirectUrl());
    GoogleTokenResponse authResponse;
    try {
      authResponse = authRequest.execute();
    } catch (IOException ex) {
      uiFacade.showErrorDialog(
          "Error while signing in",
          "An error occured while trying to sign in: " + ex.getMessage());
      loggerFacade.logError("Could not sign in", ex);
      return false;
    }
    isLoggedIn = true;
    updateLoginState(authResponse);
    return true;
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
    if (!isLoggedIn) {
      return true;
    }

    if (showPrompt) {
      if (!uiFacade.askYesOrNo("Sign out?", "Are you sure you want to sign out?")) {
        return false;
      }
    }

    email = "";
    isLoggedIn = false;

    authDataStore.clearStoredOAuthData();

    notifyLoginStatusChange(false);
    uiFacade.notifyStatusIndicator();
    return true;
  }

  public Credential makeCredential() {
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

  /**
   * Performs the firing of listeners and the updates to the UI that are normally performed as part
   * of a log in or log out, and retrieves any persistently stored credentials upon log in, but does
   * not actually interact with an OAuth server. This method is provided for use in tests.
   *
   * @param login
   *     {@code true} if a log in is to be simulated, {@code false} if a log out is to be simulated
   */
  public void simulateLoginStatusChange(boolean login) {
    if (login) {
      retrieveSavedCredentials();
    }
    notifyLoginStatusChange(login);
    uiFacade.notifyStatusIndicator();
  }

  private void updateLoginState(GoogleTokenResponse tokenResponse) {
    refreshToken = tokenResponse.getRefreshToken();
    accessToken = tokenResponse.getAccessToken();
    oAuth2Credential = makeCredential();
    accessTokenExpiryTime = System.currentTimeMillis() / 1000 + tokenResponse.getExpiresInSeconds();
    email = queryEmail();
    persistCredentials();
    uiFacade.notifyStatusIndicator();
    notifyLoginStatusChange(true);
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

    accessToken = savedAuthState.getAccessToken();
    refreshToken = savedAuthState.getRefreshToken();
    accessTokenExpiryTime = savedAuthState.getAccessTokenExpiryTime();
    email = savedAuthState.getStoredEmail();
    oAuth2Credential = makeCredential();

    isLoggedIn = true;
  }

  private void notifyLoginStatusChange(boolean login) {
    synchronized(listeners) {
      for (LoginListener listener : listeners) {
        listener.statusChanged(login);
      }
    }
  }

  private String queryEmail() {
    try {
      HttpRequest get = createRequestFactory().buildGetRequest(new GenericUrl(GET_EMAIL_URL));
      HttpResponse resp = get.execute();

      String responseString = "";
      try (Scanner scan = new Scanner(resp.getContent())) {
        while (scan.hasNext()) {
          responseString += scan.nextLine();
        }
      }

      String userEmail = parseUrlParameters(responseString).get("email");
      if (userEmail == null) {
        loggerFacade.logWarning("Could not parse email after Google service sign-in");
      }
      return userEmail;

    } catch (IOException ioe) {
      // catch exception in case something goes wrong in parsing the response
      loggerFacade.logError("Could not parse email after Google service sign-in", ioe);
      return null;
    }
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

  private void persistCredentials() {
    Preconditions.checkState(isLoggedIn);

    OAuthData creds = new OAuthData(
        accessToken, refreshToken, email, oAuthScopes, accessTokenExpiryTime);
    authDataStore.saveOAuthData(creds);
  }
}
