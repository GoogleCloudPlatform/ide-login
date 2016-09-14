package com.google.cloud.tools.ide.login;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;

/**
 * Wrapper for the GoogleAuthorizationCodeTokenRequest constructor. Only for unit-testing.
 */
@VisibleForTesting
class GoogleAuthorizationCodeTokenRequestCreator {

  GoogleAuthorizationCodeTokenRequest create(
      HttpTransport transport, JsonFactory jsonFactory,
      String clientId, String clientSecret, String code, String redirectUri) {
    return new GoogleAuthorizationCodeTokenRequest(
        transport, jsonFactory, clientId, clientSecret, code, redirectUri);
  }
}
