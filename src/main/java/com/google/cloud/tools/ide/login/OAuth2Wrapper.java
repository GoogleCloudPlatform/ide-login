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
 * A wrapper around {@link Oauth2}, and {@link Userinfoplus} to enable unit testing, since Mockito
 * cannot mock {@code final} classes.
 */
@VisibleForTesting
class OAuth2Wrapper {

  UserInfo executeOAuth2(Oauth2 oAuth2) throws IOException {
    Userinfoplus userInfoPlus = oAuth2.userinfo().get().execute();
    if (userInfoPlus == null) {
      return null;
    }
    return new UserInfo(userInfoPlus);
  }
}
