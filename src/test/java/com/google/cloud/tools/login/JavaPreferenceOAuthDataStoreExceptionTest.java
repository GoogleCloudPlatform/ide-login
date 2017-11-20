/*
 * Copyright 2017 Google Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test that exceptions raised on load or save are turned into IOExceptions
 */
@RunWith(Parameterized.class)
public class JavaPreferenceOAuthDataStoreExceptionTest {

  // The different exceptions that may be raised by Java Preferences.
  @Parameters
  public static Iterable<Exception> parameters() {
    return Arrays.asList(new SecurityException(), new BackingStoreException("")); //$NON-NLS-1$
  }

  private static final Set<String> FAKE_OAUTH_SCOPES = Collections.singleton("scope1"); //$NON-NLS-1$

  private static final OAuthData fakeOAuthData = new OAuthData("accessToken1", "refreshToken1", //$NON-NLS-1$ //$NON-NLS-2$
      "email1@example.com", "name1", "http://example.com/image1", FAKE_OAUTH_SCOPES, 123); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

  private ExceptionRaisingPreferenceWrapper root;
  private JavaPreferenceOAuthDataStore dataStore;
  private LoggerFacade loggerFacade;
  private Throwable thrownException;

  public JavaPreferenceOAuthDataStoreExceptionTest(Exception exception) {
    loggerFacade = mock(LoggerFacade.class);
    thrownException = exception;
    root = new ExceptionRaisingPreferenceWrapper(exception);
    dataStore = new JavaPreferenceOAuthDataStore("path", loggerFacade, root); //$NON-NLS-1$
  }

  @Test
  public void testSaveOAuthData() throws IOException {
    try {
      dataStore.saveOAuthData(fakeOAuthData);
      fail("should have thrown an IOException"); //$NON-NLS-1$
    } catch (IOException ex) {
      assertEquals("should have nested exception", thrownException, ex.getCause()); //$NON-NLS-1$
    }
  }

  @Test
  public void testRemoveOAuthData() throws IOException {
    try {
      dataStore.removeOAuthData("email1@example.com"); //$NON-NLS-1$
      fail("should have thrown an IOException"); //$NON-NLS-1$
    } catch (IOException ex) {
      assertEquals("should have nested exception", thrownException, ex.getCause()); //$NON-NLS-1$
    }
  }

  @Test
  public void testLoadOAuthData() throws IOException {
    try {
      dataStore.loadOAuthData();
      fail("should have thrown an IOException"); //$NON-NLS-1$
    } catch (IOException ex) {
      assertEquals("should have nested exception", thrownException, ex.getCause()); //$NON-NLS-1$
    }
  }

  /**
   * A wrapper that throws the given exception on load or save.
   */
  final class ExceptionRaisingPreferenceWrapper extends Preferences {
    private String name;
    private ExceptionRaisingPreferenceWrapper parent;
    private Map<String, Object> values = new HashMap<>();
    private Map<String, ExceptionRaisingPreferenceWrapper> children = new HashMap<>();
    private Exception exception;

    ExceptionRaisingPreferenceWrapper(Exception exception) {
      this.exception = exception;
      this.name = ""; //$NON-NLS-1$
    }

    ExceptionRaisingPreferenceWrapper(ExceptionRaisingPreferenceWrapper parent, String name,
        Exception exception) {
      this(exception);
      this.parent = parent;
      this.name = name;
    }

    private void throwException() throws BackingStoreException {
      if (exception instanceof BackingStoreException) {
        throw (BackingStoreException) exception;
      }
      throw (RuntimeException) exception;
    }

    public void removeNode() throws BackingStoreException {
      throwException();
    }

    public void flush() throws BackingStoreException {
      throwException();
    }

    public void sync() throws BackingStoreException {
      throwException();
    }

    public void clear() throws BackingStoreException {
      throwException();
    }

    public String[] childrenNames() throws BackingStoreException {
      throwException();
      return children.keySet().toArray(new String[children.size()]);
    }

    public Preferences parent() {
      return parent;
    }

    public Preferences node(String pathName) {
      if (Strings.isNullOrEmpty(pathName)) {
        return this;
      }
      if (pathName.startsWith("/")) { //$NON-NLS-1$
        if (parent != null) {
          return parent.node(pathName);
        }
        pathName = pathName.substring(1);
      }
      int slash = pathName.indexOf('/');
      String childName = slash > 0 ? pathName.substring(0, slash) : pathName;
      String remainder = slash > 0 ? pathName.substring(slash + 1) : ""; //$NON-NLS-1$
      ExceptionRaisingPreferenceWrapper child = children.get(childName);
      if (child == null) {
        children.put(childName,
            child = new ExceptionRaisingPreferenceWrapper(this, childName, exception));
      }
      return child.node(remainder);
    }

    public boolean nodeExists(String pathName) throws BackingStoreException {
      throwException();
      if (Strings.isNullOrEmpty(pathName)) {
        return true;
      }
      if (pathName.startsWith("/")) { //$NON-NLS-1$
        if (parent != null) {
          return parent.nodeExists(pathName);
        }
        pathName = pathName.substring(1);
      }
      int slash = pathName.indexOf('/');
      String childName = slash > 0 ? pathName.substring(0, slash) : pathName;
      String remainder = slash > 0 ? pathName.substring(slash + 1) : ""; //$NON-NLS-1$
      Preferences child = children.get(childName);
      if (child == null) {
        return false;
      }
      return child.nodeExists(remainder);
    }


    public void put(String key, String value) {
      values.put(key, value);
    }

    public String get(String key, String def) {
      return values.containsKey(key) ? (String) values.get(key) : def;
    }

    public void remove(String key) {
      values.remove(key);
    }

    public void putInt(String key, int value) {
      values.put(key, value);
    }

    public int getInt(String key, int def) {
      return values.containsKey(key) ? (Integer) values.get(key) : def;
    }

    public void putLong(String key, long value) {
      values.put(key, value);
    }

    public long getLong(String key, long def) {
      return values.containsKey(key) ? (Long) values.get(key) : def;
    }

    public void putBoolean(String key, boolean value) {
      values.put(key, value);
    }

    public boolean getBoolean(String key, boolean def) {
      return values.containsKey(key) ? (Boolean) values.get(key) : def;
    }

    public void putFloat(String key, float value) {
      values.put(key, value);
    }

    public float getFloat(String key, float def) {
      return values.containsKey(key) ? (Float) values.get(key) : def;
    }

    public void putDouble(String key, double value) {
      values.put(key, value);
    }

    public double getDouble(String key, double def) {
      return values.containsKey(key) ? (Double) values.get(key) : def;
    }

    public void putByteArray(String key, byte[] value) {
      values.put(key, value);
    }

    public byte[] getByteArray(String key, byte[] def) {
      return values.containsKey(key) ? (byte[]) values.get(key) : def;
    }

    public String[] keys() throws BackingStoreException {
      return values.keySet().toArray(new String[values.size()]);
    }

    public String name() {
      return name;
    }

    public String absolutePath() {
      if (parent == null) {
        return name;
      }
      return parent.absolutePath() + "/" + name; //$NON-NLS-1$
    }

    public boolean isUserNode() {
      return true;
    }

    public String toString() {
      return name;
    }

    public void addPreferenceChangeListener(PreferenceChangeListener pcl) {
    }

    public void removePreferenceChangeListener(PreferenceChangeListener pcl) {
    }

    public void addNodeChangeListener(NodeChangeListener ncl) {
    }

    public void removeNodeChangeListener(NodeChangeListener ncl) {
    }

    public void exportNode(OutputStream os) throws IOException, BackingStoreException {
    }

    public void exportSubtree(OutputStream os) throws IOException, BackingStoreException {
    }
  }
}
