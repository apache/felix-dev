/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.webconsole.plugins.packageadmin.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.felix.utils.json.JSONWriter;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Parser;
import org.apache.felix.webconsole.servlet.AbstractServlet;
import org.apache.felix.webconsole.servlet.ServletConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Provides a Web bases interface to the Packages Admin service, allowing
 * the user to find package / maven information and identify duplicate exports.
 */
@SuppressWarnings("deprecation")
class WebConsolePlugin extends AbstractServlet {

    private static final long serialVersionUID = 1L;
    private static final String LABEL = "depfinder"; 
    private static final String TITLE = "%pluginTitle";
    private static final String CATEGORY = "OSGi";
    private static final String CSS[] = { "/" + LABEL + "/res/plugin.css" };

    private static final Comparator<ExportedPackage> EXPORT_PACKAGE_COMPARATOR = new ExportedPackageComparator();

    private final PackageAdmin pa;
    private final BundleContext bc;

    // templates
    private final String TEMPLATE;

    WebConsolePlugin(BundleContext bc, PackageAdmin pa) {
        this.pa = (PackageAdmin) pa;
        this.bc = bc;

        // load templates
        try {
            TEMPLATE = readTemplateFile("/res/plugin.html");
        } catch (IOException e) {
            throw new RuntimeException("Cannot load template files", e);
        }
    }

    public ServiceRegistration<Servlet> register() {
        final Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(ServletConstants.PLUGIN_LABEL, LABEL);
        props.put(ServletConstants.PLUGIN_TITLE, TITLE);
        props.put(ServletConstants.PLUGIN_CATEGORY, CATEGORY);
        props.put(ServletConstants.PLUGIN_CSS_REFERENCES, CSS);
        return this.bc.registerService(Servlet.class, this, props);
    }

    @Override
    public final void renderContent(HttpServletRequest req,
        HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().print(TEMPLATE);
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
     */
    @Override
    protected final void doPost(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException
    {
        final Object json;

        String action = req.getParameter("action");
        if ("deps".equals(action)) {
            json = doFindDependencies(req, pa);
        }
        else if ("dups".equals(action)) {
            Map<String, Set<ExportedPackage>> packages = collectExportedPackages(
                pa, bc);
            json = doFindDuplicates(packages);
        }
        else
        {
            throw new ServletException("Invalid action: " + action);
        }

        AbstractServlet.setNoCache(resp);
        resp.setContentType("application/json; utf-8");
        JSONWriter writer = new JSONWriter(resp.getWriter());
        writer.value(json);
        writer.flush();
    }

    static final Map<String, Set<ExportedPackage>> collectExportedPackages(
        final PackageAdmin pa, final BundleContext bundleContext)
    {
        Map<String, Set<ExportedPackage>> exports = new TreeMap<String, Set<ExportedPackage>>();

        Bundle[] bundles = bundleContext.getBundles();
        for (int i = 0; bundles != null && i < bundles.length; i++)
        {
            final Bundle bundle = bundles[i];
            final ExportedPackage[] bundleExports = pa.getExportedPackages(bundle);
            for (int j = 0; bundleExports != null && j < bundleExports.length; j++)
            {
                final ExportedPackage exportedPackage = bundleExports[j];
                Set<ExportedPackage> exportSet = exports.get(exportedPackage.getName());
                if (exportSet == null)
                {
                    exportSet = new TreeSet<ExportedPackage>(
                        EXPORT_PACKAGE_COMPARATOR);
                    exports.put(exportedPackage.getName(), exportSet);
                }
                exportSet.add(exportedPackage);
            }
        }

        return exports;
    }

    private static final Map<String, Object> doFindDependencies(HttpServletRequest req,
        PackageAdmin pa)
    {
        final Map<String, Object> json = new HashMap<String, Object>();

        final String findField = req.getParameter("plugin.find");
        if (findField != null)
        {
            Set<String> packageNames = getPackageNames(findField);
            Set<Bundle> exportingBundles = new LinkedHashSet<Bundle>();

            for (Iterator<String> i = packageNames.iterator(); i.hasNext();)
            {
                String name = i.next();
                @SuppressWarnings("unchecked")
                List<Object> pl = (List<Object>)json.get("packages");
                if ( pl == null )
                {
                    pl = new ArrayList<Object>();
                    json.put("packages", pl);
                }
                pl.add( getPackageInfo(name, pa, exportingBundles));
            }

            final Map<String, Object> mavenJson = new HashMap<String, Object>();
            json.put("maven", mavenJson);
            for (Iterator<Bundle> i = exportingBundles.iterator(); i.hasNext();)
            {
                Bundle bundle = i.next();
                final Object value = getMavenInfo(bundle);
                if ( value != null )
                {
                    mavenJson.put(String.valueOf(bundle.getBundleId()), value);
                }
            }
        }

        return json;

    }

    private static final Collection<Object> doFindDuplicates(
        final Map<String, Set<ExportedPackage>> exports)
    {
        final List<Object> ret = new ArrayList<Object>();
        for (Iterator<Entry<String, Set<ExportedPackage>>> entryIter = exports.entrySet().iterator(); entryIter.hasNext();)
        {
            Entry<String, Set<ExportedPackage>> exportEntry = entryIter.next();
            Set<ExportedPackage> exportSet = exportEntry.getValue();
            if (exportSet.size() > 1)
            {
                final Map<String, Object> container = new HashMap<String, Object>();
                ret.add(container);
                for (Iterator<ExportedPackage> packageIter = exportSet.iterator(); packageIter.hasNext();)
                {
                    ExportedPackage exportedPackage = packageIter.next();
                    final Map<String, Object> json = toJSON(exportedPackage);
                    container.put("name", exportedPackage.getName());
                    @SuppressWarnings("unchecked")
                    List<Object> imps = (List<Object>) container.get("entries");
                    if ( imps == null )
                    {
                        imps = new ArrayList<Object>();
                        container.put("entries", imps);
                    }
                    imps.add(json);
                }
            }
        }
        return ret;
    }

    private static final void toJSON(Bundle bundle, Map<String, Object> json)
    {
        json.put("bid", bundle.getBundleId());
        if ( bundle.getSymbolicName() != null )
        {
            json.put("bsn", bundle.getSymbolicName());
        }
    }

    private static final void toJSON(final String pkgName, final Bundle[] importers, final Map<String, Object> json)
    {
        for (int i = 0; i < importers.length; i++)
        {
            Bundle bundle = importers[i];
            final Map<String, Object> usingJson = new HashMap<String, Object>();
            toJSON(bundle, usingJson);
            final String ip = bundle.getHeaders().get(Constants.IMPORT_PACKAGE);
            final Clause[] clauses = Parser.parseHeader(ip);
            for (int j = 0; j < clauses.length; j++)
            {
                Clause clause = clauses[j];
                if (pkgName.equals(clause.getName()))
                {
                    usingJson.put("ver", clause.getAttribute(Constants.VERSION_ATTRIBUTE));
                    break;
                }
            }
            @SuppressWarnings("unchecked")
            List<Object> imps = (List<Object>) json.get("importers");
            if ( imps == null )
            {
                imps = new ArrayList<Object>();
                json.put("importers", imps);
            }
            imps.add(usingJson);
        }
    }

    private static final Map<String, Object> toJSON(final ExportedPackage pkg)
    {
        final Map<String, Object> ret = new HashMap<String, Object>();
        ret.put("version", pkg.getVersion());
        toJSON(pkg.getExportingBundle(), ret);
        toJSON(pkg.getName(), pkg.getImportingBundles(), ret);
        return ret;
    }

    private static final Map<String, Object> getPackageInfo(String packageName, PackageAdmin pa,
        Set<Bundle> exportingBundles)
    {
        final Map<String, Object> ret = new HashMap<String, Object>();
        final ExportedPackage[] exports = pa.getExportedPackages(packageName);
        for (int i = 0; exports != null && i < exports.length; i++)
        {
            final ExportedPackage x = exports[i];
            @SuppressWarnings("unchecked")
            List<Object> el = (List<Object>)ret.get("exporters");
            if ( el == null )
            {
                el = new ArrayList<Object>();
                ret.put("exporters", el);
            }
            el.add(toJSON(x));
            exportingBundles.add(x.getExportingBundle());
        }
        ret.put("name", packageName);
        return ret;
    }

    private static final Map<Object, Object> getMavenInfo(Bundle bundle)
    {
        Map<Object, Object> ret = null;

        Enumeration<URL> entries = bundle.findEntries("META-INF/maven", "pom.properties", true); //$NON-NLS-2$
        if (entries != null)
        {
            URL u = entries.nextElement();
            java.util.Properties props = new java.util.Properties();
            InputStream is = null;
            try
            {
                is = u.openStream();
                props.load(u.openStream());

                ret = new HashMap<Object, Object>(props);
            }
            catch (IOException e)
            {
                // ignore
            }
            finally
            {
                IOUtils.closeQuietly(is);
            }
        }
        return ret;
    }

    static final Set<String> getPackageNames(String findField)
    {
        StringTokenizer tok = new StringTokenizer(findField, " \t\n\f\r");
        SortedSet<String> result = new TreeSet<String>();
        while (tok.hasMoreTokens())
        {
            String part = tok.nextToken().trim();
            if (part.length() > 0)
            {
                int idx = part.lastIndexOf('.');
                if (idx == part.length() - 1)
                {
                    part = part.substring(0, part.length() - 1);
                    idx = part.lastIndexOf('.');
                }
                if (idx != -1)
                {
                    char firstCharAfterLastDot = part.charAt(idx + 1);
                    if (Character.isUpperCase(firstCharAfterLastDot))
                    {
                        result.add(part.substring(0, idx));
                    }
                    else
                    {
                        result.add(part);
                    }
                }
                else
                {
                    result.add(part);
                }
            }
        }
        return result;
    }

}
