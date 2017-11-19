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

import java.io.IOException;
import java.util.Set;

/**
 * Presents a common API, implementable on a variety of platforms, for storing a particular user's
 * {@link OAuthData} object persistently, retrieving it, and clearing it.
 */
public interface OAuthDataStore {

  /**
   * Stores a specified {@link OAuthData} object persistently.
   */
  void saveOAuthData(OAuthData oAuthData) throws IOException;

  /**
   * Retrieves the persistently stored {@link OAuthData} objects, if any.
   * 
   * @return never {@code null}
   */
  Set<OAuthData> loadOAuthData() throws IOException;

  /**
   * Removes a stored {@link OAuthData} matching the given {@code email}, in any.
   */
  void removeOAuthData(String email) throws IOException;

  /**
   * Clears the persistently stored {@link OAuthData} object, if any.
   */
  void clearStoredOAuthData() throws IOException;
}