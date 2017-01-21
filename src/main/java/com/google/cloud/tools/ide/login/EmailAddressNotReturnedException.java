package com.google.cloud.tools.ide.login;

import com.google.common.annotations.VisibleForTesting;

@VisibleForTesting class EmailAddressNotReturnedException extends Exception {
    public EmailAddressNotReturnedException(String message) {
      super(message);
    }
  }