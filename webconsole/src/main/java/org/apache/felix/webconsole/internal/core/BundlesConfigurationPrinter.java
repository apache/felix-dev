/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.webconsole.internal.core;


import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.TreeMap;

import org.apache.felix.inventory.Format;
import org.apache.felix.webconsole.internal.AbstractConfigurationPrinter;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleRevision;


/**
 * The <code>BundlesConfigurationPrinter</code> prints out the bundle list.
 */
public class BundlesConfigurationPrinter extends AbstractConfigurationPrinter {

    private String getHeaderValue(final Bundle b, final String name) {
        String val = (String)b.getHeaders().get(name);
        if ( val == null ) {
            val = "";
        }
        return val;
    }

    private String getState(final int state) {
        switch (state) {
            case Bundle.ACTIVE : return "active";
            case Bundle.INSTALLED : return "installed";
            case Bundle.RESOLVED : return "resolved";
            case Bundle.STARTING : return "starting";
            case Bundle.STOPPING : return "stopping";
            case Bundle.UNINSTALLED : return "uninstalled";
        }
        return String.valueOf(state);
    }

    private final boolean isFragmentBundle( final Bundle bundle) {
        final BundleRevision rev = bundle.adapt(BundleRevision.class);
        return rev != null && (rev.getTypes() & BundleRevision.TYPE_FRAGMENT) == BundleRevision.TYPE_FRAGMENT;
    }

    @Override
    protected final String getTitle() {
        return "Bundlelist";
    }

    @Override
    public void print(final PrintWriter pw, final Format format, final boolean isZip) {
        final Bundle[] bundles = BundleContextUtil.getWorkingBundleContext(this.getBundleContext()).getBundles();
        // create a map for sorting first
        final TreeMap<String, String> bundlesMap = new TreeMap<>();
        int active = 0, installed = 0, resolved = 0, fragments = 0;
        for( int i =0; i<bundles.length; i++) {
            final Bundle bundle = bundles[i];
            final String symbolicName = bundle.getSymbolicName();
            final String version = (String)bundle.getHeaders().get(Constants.BUNDLE_VERSION);

            // count states and calculate prefix
            switch ( bundle.getState() ) {
                case Bundle.ACTIVE:
                    active++;
                    break;
                case Bundle.INSTALLED:
                    installed++;
                    break;
                case Bundle.RESOLVED:
                    if ( isFragmentBundle( bundle ) ) {
                        fragments++;
                    } else {
                        resolved++;
                    }
                    break;
            }

            final String key = symbolicName + ':' + version;
            final String value = MessageFormat.format( "{0} ({1}) \"{2}\" [{3}, {4}] {5}", new Object[]
                  { symbolicName,
                    version,
                    getHeaderValue(bundle, Constants.BUNDLE_NAME),
                    getState(bundle.getState()),
                    String.valueOf(bundle.getBundleId()),
                    isFragmentBundle(bundle) ? "(fragment)" : ""} );
            bundlesMap.put(key, value);

        }
        final StringBuilder buffer = new StringBuilder();
        buffer.append("Status: ");
        appendBundleInfoCount(buffer, "in total", bundles.length);
        if ( active == bundles.length || active + fragments == bundles.length ) {
            buffer.append(" - all ");
            appendBundleInfoCount(buffer, "active.", bundles.length);
        } else{
            if ( active != 0 ) {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "active", active);
            }
            if ( fragments != 0 ) {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "active fragments", fragments);
            }
            if ( resolved != 0 ) {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "resolved", resolved);
            }
            if ( installed != 0 ) {
                buffer.append(", ");
                appendBundleInfoCount(buffer, "installed", installed);
            }
        }
        pw.println(buffer.toString());
        pw.println();
        for(final String value : bundlesMap.values()) {
            pw.println(value);
        }
    }

    private void appendBundleInfoCount( final StringBuilder buf, String msg, int count ) {
        buf.append(count);
        buf.append(" bundle");
        if ( count != 1 ) {
            buf.append( 's' );
        }
        buf.append(' ');
        buf.append(msg);
    }
}
