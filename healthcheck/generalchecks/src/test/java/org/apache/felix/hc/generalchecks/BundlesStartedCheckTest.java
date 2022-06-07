/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.felix.hc.generalchecks;

import static java.util.Collections.emptyMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.osgi.framework.Constants.ACTIVATION_LAZY;
import static org.osgi.framework.Constants.BUNDLE_ACTIVATIONPOLICY;
import static org.osgi.framework.Constants.FRAGMENT_HOST;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.felix.hc.api.Result;
import org.apache.felix.hc.api.Result.Status;
import org.apache.felix.hc.generalchecks.BundlesStartedCheck.Config;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.util.converter.Converters;

public class BundlesStartedCheckTest {

    private BundleContext context;
    
    @Before
    public void before() {
        context = mock(BundleContext.class);
    }

    @Test
    public void testOKResultWithNoBundles() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Result result = executeCheck(check);
        assertThat(result.getStatus(), equalTo(Status.OK));
    }
    
    @Test
    public void testOKResultWithResolvedNotIncludedBundle() {
        BundlesStartedCheck check = createCheck(configWith("includesRegex", "a.*"));
        Bundle bundle = mockBundle("mybundle", Bundle.RESOLVED);
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.OK));
    }
    
    @Test
    public void testOKResultWithResolvedExcludedBundle() {
        BundlesStartedCheck check = createCheck(configWith("excludesRegex", "mybundle"));
        Bundle bundle = mockBundle("mybundle", Bundle.RESOLVED);
        Bundle other = mockBundle("other", Bundle.ACTIVE);
        Result result = executeCheck(check, bundle, other);
        assertThat(result.getStatus(), equalTo(Status.OK));
    }
    
    @Test
    public void testOKResultWithActiveBundle() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Bundle bundle = mockBundle("mybundle", Bundle.ACTIVE);
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.OK));
    }
    
    @Test
    public void testWARNResultWithResolvedBundle() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Bundle bundle = mockBundle("mybundle", Bundle.RESOLVED);
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.TEMPORARILY_UNAVAILABLE));
    }
    
    @Test
    public void testCRITICALResultWithResolvedBundleAndConfig() {
        BundlesStartedCheck check = createCheck(configWith("useCriticalForInactive", "true"));
        Bundle bundle = mockBundle("mybundle", Bundle.RESOLVED);
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.CRITICAL));
    }

    @Test
    public void testOKResultWithResolvedFragmentBundle() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Bundle bundle = mockBundle("mybundle", Bundle.RESOLVED, withHeader(FRAGMENT_HOST, "fragmentbundle"));
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.OK));
    }

    @Test
    public void testOKResultWithStartingLazyBundle() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Bundle bundle = mockBundle("mybundle", Bundle.STARTING, withHeader(BUNDLE_ACTIVATIONPOLICY, ACTIVATION_LAZY));
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.OK));
        System.out.println(result);
    }
    
    @Test
    public void testWarnResultWithStartingBundle() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Bundle bundle = mockBundle("mybundle", Bundle.STARTING);
        Result result = executeCheck(check, bundle);
        assertThat(result.getStatus(), equalTo(Status.TEMPORARILY_UNAVAILABLE));
        System.out.println(result);
    }
    
    @Test
    public void testWARNResultWithOtherStatuses() {
        BundlesStartedCheck check = createCheck(emptyMap());
        Bundle bundle = mockBundle("mybundle", Bundle.INSTALLED);
        Bundle bundle2 = mockBundle("uninstalledbundle", Bundle.UNINSTALLED);
        Bundle bundle3 = mockBundle("stoppingbundle", Bundle.STOPPING);
        Bundle bundle4 = mockBundle("startunkownstatebundle", 50);
        Result result = executeCheck(check, bundle, bundle2, bundle3, bundle4);
        assertThat(result.getStatus(), equalTo(Status.TEMPORARILY_UNAVAILABLE));
    }

    private Hashtable<String, String> withHeader(String key, String value) {
        Hashtable<String, String> headers = new Hashtable<String, String>();
        headers.put(key, value);
        return headers;
    }
    
    private Map<String, String> configWith(String ... contents) {
        Map<String, String> props = new HashMap<>();
        if (contents.length >= 2) {
            props.put(contents[0], contents[1]);
        }
        return props;
    }

    private BundlesStartedCheck createCheck(Map<String, String> props) {
        BundlesStartedCheck check = new BundlesStartedCheck();
        Config config = Converters.standardConverter().convert(props).to(BundlesStartedCheck.Config.class);
        check.activate(context, config);
        return check;
    }

    private Result executeCheck(BundlesStartedCheck check, Bundle... bundles) {
        when(context.getBundles()).thenReturn(bundles);
        Result result = check.execute();
        System.out.println(result);
        return result;
    }

    private Bundle mockBundle(String symbolicName, Integer state) {
        return mockBundle(symbolicName, state, new Hashtable<>());
    }

    private Bundle mockBundle(String symbolicName, Integer state, Hashtable<String, String> headers) {
        Bundle bundle = mock(Bundle.class);
        when(bundle.getSymbolicName()).thenReturn(symbolicName);
        when(bundle.getState()).thenReturn(state);
        when(bundle.getHeaders()).thenReturn(headers);
        return bundle;
    }
}
