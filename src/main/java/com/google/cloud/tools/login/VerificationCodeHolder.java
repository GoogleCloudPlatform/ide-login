
/*
 * Copyright 2014 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.login;

/**
 * Holder for a Google OAuth verification code and the redirect URL used to create it.
 */
public class VerificationCodeHolder {
  private final String verificationCode;
  private final String redirectUrl;

  public VerificationCodeHolder(String verificationCode, String redirectUrl) {
    this.verificationCode = verificationCode;
    this.redirectUrl = redirectUrl;
  }

  public String getRedirectUrl() {
    return redirectUrl;
  }

  public String getVerificationCode() {
    return verificationCode;
  }
}
