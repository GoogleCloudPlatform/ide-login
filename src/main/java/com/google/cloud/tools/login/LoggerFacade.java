
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
 * Presents a common API, implementable on a variety of platforms, for making log entries.
 */
public interface LoggerFacade {

  /**
   * Create an error log entry with a specified message and a specified {@link Throwable}.
   * 
   * @param message the specified message
   * @param t the specified {@code Throwable}
   */
  void logError(String message, Throwable t);

  /**
   * Create a warning log entry with a specified message.
   * 
   * @param message the specified message
   */
  void logWarning(String message);

}
