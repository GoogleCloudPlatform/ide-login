package com.google.cloud.tools.login;

import org.junit.Assert;
import org.junit.Test;

public class VerificationCodeHolderTest {

  private VerificationCodeHolder holder = new VerificationCodeHolder("a code", "a URL");
  
  @Test
  public void testGetRedirectUrl() {
    Assert.assertEquals("a URL", holder.getRedirectUrl());
  }

  @Test
  public void testGetVerificationCode() {
    Assert.assertEquals("a code", holder.getVerificationCode());
  }
}

