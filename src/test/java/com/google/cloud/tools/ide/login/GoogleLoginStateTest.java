package com.google.cloud.tools.ide.login;

import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.api.client.util.Sets;
import org.mockito.Mockito;

public class GoogleLoginStateTest {
  
  @Test
  public void testConstructor() {
    Set<String> oAuthScopes = Sets.newHashSet();
    String clientId = "myId";
    String clientSecret = "mySecret";
    LoggerFacade loggerFacade = null;
    UiFacade uiFacade = null;
    OAuthDataStore authDataStore = Mockito.mock(OAuthDataStore.class);
    OAuthData data = new OAuthData(null, null, null, oAuthScopes, 0);
    Mockito.when(authDataStore.loadOAuthData()).thenReturn(data);

    GoogleLoginState state = new GoogleLoginState(clientId, clientSecret, oAuthScopes,
        authDataStore, uiFacade, loggerFacade);
    
    Assert.assertFalse(state.isLoggedIn());
  }
  
}
