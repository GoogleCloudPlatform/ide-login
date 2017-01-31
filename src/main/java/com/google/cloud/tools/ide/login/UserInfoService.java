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
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;

/**
 * A wrapper around {@link Oauth2.Builder}, {@link Oauth2}, and {@link Userinfoplus} to enable
 * unit testing, since Mockito cannot mock {@code final} classes. ({@link Oauth2.Builder} and
 * {@link Userinfoplus} are {@code final}.)
 */
@VisibleForTesting
class UserInfoService {

  UserInfo buildAndExecuteRequest(HttpTransport httpTransport, JsonFactory jsonFactory,
      final Credential credential, final HttpRequestInitializer additionalInitializer)
      throws IOException {

    // Chain the credential's initializer and the additional initializer.
    HttpRequestInitializer chainedInitializer = new HttpRequestInitializer() {
      @Override
      public void initialize(HttpRequest httpRequest) throws IOException {
        credential.getRequestInitializer().initialize(httpRequest);
        additionalInitializer.initialize(httpRequest);
      }
    };

    Oauth2 oAuth2 = new Oauth2.Builder(httpTransport, jsonFactory, credential)
        .setHttpRequestInitializer(chainedInitializer)
        .build();

    Userinfoplus userInfoPlus = oAuth2.userinfo().get().execute();
    if (userInfoPlus == null) {
      return null;
    }
    return new UserInfo(userInfoPlus);
  }

  /** A wrapper for {@link Userinfoplus} to enable unit testing. */
  @VisibleForTesting
  class UserInfo {

    private Userinfoplus userInfoPlus;

    UserInfo(Userinfoplus userInfoPlus) {
      this.userInfoPlus = userInfoPlus;
    }

    String getEmail() {
      return userInfoPlus.getEmail();
    }

    String getName() {
      return userInfoPlus.getName();
    }

    String getPicture() {
      return userInfoPlus.getPicture();
    }
  }
}
