/*
 * Copyright Terracotta, Inc.
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
package org.terracotta.management.provider.action;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.terracotta.management.call.Parameter;
import org.terracotta.management.capabilities.context.CapabilityContext;
import org.terracotta.management.capabilities.descriptors.CallDescriptor;
import org.terracotta.management.capabilities.descriptors.Descriptor;
import org.terracotta.management.context.Context;
import org.terracotta.management.registry.ManagementProvider;
import org.terracotta.management.registry.action.AbstractActionManagementProvider;
import org.terracotta.management.registry.action.Exposed;
import org.terracotta.management.registry.action.ExposedObject;
import org.terracotta.management.registry.action.Named;
import org.terracotta.management.registry.action.RequiredContext;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Mathieu Carbou
 */
@RunWith(JUnit4.class)
public class ActionProviderTest {

  ManagementProvider<MyObject> managementProvider = new MyManagementProvider();

  @Test
  public void testName() throws Exception {
    assertThat(managementProvider.getCapabilityName(), equalTo("TheActionProvider"));
  }

  @Test
  public void testDescriptors() throws Exception {
    managementProvider.register(new MyObject("myCacheManagerName", "myCacheName1"));
    managementProvider.register(new MyObject("myCacheManagerName", "myCacheName2"));

    Collection<Descriptor> descriptors = managementProvider.getDescriptors();
    assertThat(descriptors.size(), is(1));
    assertThat(descriptors.iterator().next(), is(instanceOf(CallDescriptor.class)));
    assertThat((CallDescriptor) descriptors.iterator().next(), equalTo(
        new CallDescriptor("incr", "int", Collections.singletonList(new CallDescriptor.Parameter("n", "int")))
    ));
  }

  @Test
  public void testCapabilityContext() throws Exception {
    managementProvider.register(new MyObject("myCacheManagerName", "myCacheName1"));
    managementProvider.register(new MyObject("myCacheManagerName", "myCacheName2"));

    CapabilityContext capabilityContext = managementProvider.getCapabilityContext();

    assertThat(capabilityContext.getAttributes().size(), is(2));

    Iterator<CapabilityContext.Attribute> iterator = capabilityContext.getAttributes().iterator();
    CapabilityContext.Attribute next = iterator.next();
    assertThat(next.getName(), equalTo("cacheManagerName"));
    assertThat(next.isRequired(), is(true));
    next = iterator.next();
    assertThat(next.getName(), equalTo("cacheName"));
    assertThat(next.isRequired(), is(true));
  }

  @Test
  public void testCollectStatistics() throws Exception {
    try {
      managementProvider.collectStatistics(null, null, System.currentTimeMillis());
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException uoe) {
      // expected
    }
  }

  @Test
  public void testCallAction() throws Exception {
    managementProvider.register(new MyObject("cache-manager-0", "cache-0"));

    Context context = Context.empty()
        .with("cacheManagerName", "cache-manager-0")
        .with("cacheName", "cache-0");

    int n = managementProvider.callAction(context, "incr", int.class, new Parameter(1, "int"));

    assertThat(n, equalTo(2));
  }

  @Test
  public void testCallAction_bad_context() throws Exception {
    managementProvider.register(new MyObject("cache-manager-0", "cache-0"));

    Context context = Context.empty()
        .with("cacheManagerName", "cache-manager-0")
        .with("cacheName", "cache-1");

    try {
      managementProvider.callAction(context, "int", int.class, new Parameter(1, "int"));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  @Test
  public void testCallAction_bad_method() throws Exception {
    managementProvider.register(new MyObject("cache-manager-0", "cache-0"));

    Context context = Context.empty()
        .with("cacheManagerName", "cache-manager-0")
        .with("cacheName", "cache-0");

    try {
      managementProvider.callAction(context, "clearer", Void.class);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  @Test
  public void testCallAction_noSuchMethod() throws Exception {
    managementProvider.register(new MyObject("cache-manager-0", "cache-0"));

    Context context = Context.empty()
        .with("cacheManagerName", "cache-manager-1")
        .with("cacheName", "cache-0");

    try {
      managementProvider.callAction(context, "int", int.class, new Parameter(1, "long"));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // expected
    }
  }

  @Named("TheActionProvider")
  @RequiredContext({@Named("cacheManagerName"), @Named("cacheName")})
  public static class MyManagementProvider extends AbstractActionManagementProvider<MyObject> {
    public MyManagementProvider() {
      super(MyObject.class);
    }

    @Override
    protected ExposedObject<MyObject> wrap(MyObject managedObject) {
      return managedObject;
    }
  }

  public static class MyObject implements ExposedObject<MyObject> {

    private final String cmName;
    private final String cName;

    public MyObject(String cmName, String cName) {
      this.cmName = cmName;
      this.cName = cName;
    }

    @Exposed
    public int incr(@Named("n") int n) { return n + 1; }

    @Override
    public MyObject getTarget() {
      return this;
    }

    @Override
    public ClassLoader getClassLoader() {
      return MyObject.class.getClassLoader();
    }

    @Override
    public boolean matches(Context context) {
      return cmName.equals(context.get("cacheManagerName")) && cName.equals(context.get("cacheName"));
    }
  }

}