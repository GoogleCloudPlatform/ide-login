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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AccountTest {

  @Test
  public void testEquals_compareNull() {
    Account account = new Account("email@google.com", mock(Credential.class), null, null);
    assertFalse(account.equals(null));
  }

  @Test
  public void testEquals_wrongType() {
    Account account = new Account("email@google.com", mock(Credential.class), null, null);
    assertFalse(account.equals("email@google.com"));
  }

  @Test
  public void testEquals_differentEmails() {
    Credential credential = newCredential("access-token-1", "refresh-token-1", 123);
    Account account1 = new Account("email-1@google.com", credential, null, null);
    Account account2 = new Account("email-2@google.com", credential, null, null);
    assertFalse(account1.equals(account2));
  }

  @Test
  public void testEquals_sameEmail() {
    Credential credential1 = newCredential("access-token-1", "refresh-token-1", 123);
    Credential credential2 = newCredential("access-token-2", "refresh-token-2", 456);

    Account account1 = new Account("email@google.com", credential1, "name1", "avatar-url1");
    Account account2 = new Account("email@google.com", credential2, "name2", "avatar-url2");
    assertTrue(account1.equals(account2));
  }

  @Test
  public void testHashCode() {
    Account account1 = new Account("email@google.com", mock(Credential.class), "name", "url");
    assertEquals("email@google.com".hashCode(), account1.hashCode());

    Account account2 = new Account("some string", mock(Credential.class), "some name", "some url");
    assertEquals("some string".hashCode(), account2.hashCode());
  }

  private Credential newCredential(String accessToken, String refreshToken, long expiresInSeconds) {
    Credential credential = new GoogleCredential.Builder()
        .setJsonFactory(new JacksonFactory())
        .setTransport(new NetHttpTransport())
        .setClientSecrets("client-id", "client-secret")
        .build();
    return credential.setAccessToken(accessToken)
        .setRefreshToken(refreshToken)
        .setExpiresInSeconds(expiresInSeconds);
  }
}
