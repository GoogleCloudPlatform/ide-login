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

package com.google.cloud.tools.login;

import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.common.annotations.VisibleForTesting;

/** A wrapper for {@link Userinfoplus} to enable unit testing. */
@VisibleForTesting
class UserInfo {

  private final Userinfoplus userInfoPlus;

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
