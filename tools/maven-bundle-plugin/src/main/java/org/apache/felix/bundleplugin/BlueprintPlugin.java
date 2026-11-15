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
package org.apache.felix.bundleplugin;


import static org.apache.felix.utils.manifest.Parser.parseHeader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.felix.utils.manifest.Attribute;
import org.apache.felix.utils.manifest.Clause;
import org.apache.felix.utils.manifest.Directive;
import org.osgi.framework.Constants;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Descriptors.PackageRef;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.bnd.service.AnalyzerPlugin;
import aQute.libg.generics.Create;


public class BlueprintPlugin implements AnalyzerPlugin
{

    static Pattern QN = Pattern.compile( "[_A-Za-z$][_A-Za-z0-9$]*(\\.[_A-Za-z$][_A-Za-z0-9$]*)*" );
    static Pattern PATHS = Pattern.compile( ".*\\.xml" );

    Transformer transformer;


    public BlueprintPlugin() throws Exception
    {
        transformer = getTransformer( getClass().getResource( "blueprint.xsl" ) );
    }


    public boolean analyzeJar( Analyzer analyzer ) throws Exception
    {
        String mode = analyzer.getProperty("service_mode");
        if (mode == null) {
            mode = "service";
        }

        transformer.setParameter( "nsh_interface",
            analyzer.getProperty( "nsh_interface" ) != null ? analyzer.getProperty( "nsh_interface" ) : "" );
        transformer.setParameter( "nsh_namespace",
            analyzer.getProperty( "nsh_namespace" ) != null ? analyzer.getProperty( "nsh_namespace" ) : "" );

        Set<String> headers = Create.set();

        String bpHeader = analyzer.getProperty( "Bundle-Blueprint", "OSGI-INF/blueprint" );
        Map<String, ? extends Map<String, String>> map = Processor.parseHeader( bpHeader, null );
		bpHeader = "";
        for ( String root : map.keySet() )
        {
            Jar jar = analyzer.getJar();
            Map<String, Resource> dir = jar.getDirectories().get( root );
            if ( dir == null || dir.isEmpty() )
            {
                Resource resource = jar.getResource( root );
                if ( resource != null )
				{
                    process( analyzer, root, resource, headers );
					if (bpHeader.length() > 0) {
						bpHeader += ",";
					}
					bpHeader += root;
				}
                continue;
            }
            for ( Map.Entry<String, Resource> entry : dir.entrySet() )
            {
                String path = entry.getKey();
                Resource resource = entry.getValue();
                if ( PATHS.matcher( path ).matches() )
				{
                    process( analyzer, path, resource, headers );
					if (bpHeader.length() > 0) {
						bpHeader += ",";
					}
					bpHeader += path;
				}
            }
        }
		if( !map.isEmpty() )
		{
			analyzer.setProperty("Bundle-Blueprint", bpHeader);
		}

        // Group and analyze
        Set<String> caps = Create.set();
        Set<String> reqs = Create.set();
        Map<String, Set<Clause>> hdrs = Create.map();
        for ( String str : headers )
        {
            int idx = str.indexOf( ':' );
            if ( idx < 0 )
            {
                analyzer.warning( ( new StringBuilder( "Error analyzing services in blueprint resource: " ) ).append(
                    str ).toString() );
                continue;
            }
            String h = str.substring( 0, idx ).trim();
            String v = str.substring( idx + 1 ).trim();
            Clause[] hc = parseHeader(v);
            // Convert generic caps/reqs
            if ("Import-Service".equals(h))
            {
                if (!"service".equals(mode))
                {
                    Clause clause = hc[0];
                    String multiple = clause.getDirective("multiple");
                    String avail = clause.getDirective("availability");
                    String filter = clause.getAttribute("filter");

                    StringBuilder sb = new StringBuilder();
                    sb.append("osgi.service;effective:=active;");
                    if ("optional".equals(avail)) {
                        sb.append("resolution:=optional;");
                    }
                    if ("true".equals(multiple)) {
                        sb.append("cardinality:=multiple;");
                    }
                    if (filter == null) {
                        filter = "(" + Constants.OBJECTCLASS + "=" + clause.getName() + ")";
                    } else if (!filter.startsWith("(") && !filter.endsWith(")")) {
                        filter = "(&(" + Constants.OBJECTCLASS + "=" + clause.getName() + ")(" + filter + "))";
                    } else {
                        filter = "(&(" + Constants.OBJECTCLASS + "=" + clause.getName() + ")" + filter + ")";
                    }
                    sb.append("filter:=");
                    OSGiHeader.quote(sb, filter);
                    reqs.add(sb.toString());
                }
                else if (!"generic".equals(mode))
                {
                    Set<Clause> clauses = hdrs.get(h);
                    if (clauses == null) {
                        clauses = new HashSet<>();
                        hdrs.put(h, clauses);
                    }
                    clauses.addAll(Arrays.asList(hc));
                }
            }
            else if ("Export-Service".equals(h))
            {
                if (!"service".equals(mode))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("osgi.service;effective:=active;objectClass");
                    if (hc.length > 1) {
                        sb.append(":List<String>=\"");
                    } else {
                        sb.append("=\"");
                    }
                    for (int i = 0; i < hc.length; i++)
                    {
                        if (i > 0)
                        {
                            sb.append(",");
                        }
                        sb.append(hc[i].getName());
                    }
                    sb.append("\"");
                    for (int i = 0; i < hc[0].getAttributes().length; i++)
                    {
                        sb.append(";");
                        sb.append(hc[0].getAttributes()[i].getName());
                        sb.append("=");
                        OSGiHeader.quote(sb, hc[0].getAttributes()[i].getValue());
                    }
                    caps.add(sb.toString());
                }
                else if (!"generic".equals(mode))
                {
                    Set<Clause> clauses = hdrs.get(h);
                    if (clauses == null) {
                        clauses = new HashSet<>();
                        hdrs.put(h, clauses);
                    }
                    clauses.addAll(Arrays.asList(hc));
                }
            }
            else
            {
                Set<Clause> clauses = hdrs.get(h);
                if (clauses == null)
                {
                    clauses = new HashSet<>();
                    hdrs.put(h, clauses);
                }
                clauses.addAll(Arrays.asList( hc ) );
            }
        }
        if (!caps.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            String header = analyzer.getProperty("Provide-Capability");
            if (header != null)
            {
                sb.append(header);
            }
            for (String cap : caps) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(cap);
            }
            analyzer.setProperty("Provide-Capability", sb.toString());
        }
        if (!reqs.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            String header = analyzer.getProperty("Require-Capability");
            if (header != null)
            {
                sb.append(header);
            }
            for (String req : reqs) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(req);
            }
            analyzer.setProperty("Require-Capability", sb.toString());
        }
        // Merge
        for ( String header : hdrs.keySet() )
        {
            if ( "Import-Class".equals( header ) || "Import-Package".equals( header ) )
            {
                Set<Clause> newAttr = hdrs.get(header);
                for ( Clause a : newAttr )
                {
                    String pkg = a.getName();
                    if ( "Import-Class".equals( header ) )
                    {
                        int n = a.getName().lastIndexOf( '.' );
                        if ( n > 0 )
                        {
                            pkg = pkg.subSequence( 0, n ).toString();
                        }
                        else
                        {
                            continue;
                        }
                    }
                    PackageRef pkgRef = analyzer.getPackageRef( pkg );
                    if ( !analyzer.getReferred().containsKey( pkgRef ) )
                    {
                        Attrs attrs = analyzer.getReferred().put(pkgRef);
                        for (Attribute attribute : a.getAttributes())
                        {
                            attrs.put(attribute.getName(), attribute.getValue());
                        }
                    }
                }
            }
            else
            {
                Set<String> merge = Create.set();
                String org = analyzer.getProperty(header);
                if (org != null && !org.isEmpty())
                {
                    // the configured header is bnd syntax (single quotes, \" escapes), so parse it with bnd
                    for (Map.Entry<String, Attrs> clause : OSGiHeader.parseHeader(org, analyzer).entrySet())
                    {
                        merge.add(toHeaderClause(Processor.removeDuplicateMarker(clause.getKey()), clause.getValue()));
                    }
                }
                for (Clause clause : hdrs.get(header))
                {
                    merge.add(toHeaderClause(clause));
                }
                StringBuilder sb = new StringBuilder();
                for (String clause : merge)
                {
                    if ( sb.length() > 0 )
                    {
                        sb.append( "," );
                    }
                    sb.append(clause);

                }
                analyzer.setProperty( header, sb.toString() );
            }
        }
        return false;
    }


    /**
     * Serializes a clause in bnd header syntax. {@link Clause#toString()} only quotes values containing
     * ',', so a service filter such as {@code (ns=http://...)} would be written unquoted, and bnd
     * truncates such a value at the first '=' when it parses the header again. bnd's own quoting is
     * used instead, so bnd reads every value back unchanged.
     */
    static String toHeaderClause( Clause clause )
    {
        Attrs attrs = new Attrs();
        for ( Directive directive : clause.getDirectives() )
        {
            attrs.put( directive.getName() + ":", directive.getValue() );
        }
        for ( Attribute attribute : clause.getAttributes() )
        {
            attrs.put( attribute.getName(), attribute.getValue() );
        }
        return toHeaderClause( clause.getName(), attrs );
    }


    static String toHeaderClause( String name, Attrs attrs )
    {
        // directives first, like Clause.toString(), so that equal clauses are merged
        Attrs ordered = new Attrs();
        for ( String key : attrs.keySet() )
        {
            if ( key.endsWith( ":" ) )
            {
                ordered.put( key, attrs.get( key ) );
            }
        }
        ordered.mergeWith( attrs, false );
        return ordered.isEmpty() ? name : name + ";" + ordered;
    }


    private void process( Analyzer analyzer, String path, Resource resource, Set<String> headers )
    {
        InputStream in = null;
        try
        {
            in = resource.openInputStream();

            // Retrieve headers
            Set<String> set = analyze( in );
            headers.addAll( set );
        }
        catch ( Exception e )
        {
            analyzer.error( ( new StringBuilder( "Unexpected exception in processing spring resources(" ) )
                .append( path ).append( "): " ).append( e ).toString() );
        }
        finally
        {
            try
            {
                if ( in != null )
                {
                    in.close();
                }
            }
            catch ( IOException e )
            {
            }
        }
    }


    public Set<String> analyze( InputStream in ) throws Exception
    {
        Set<String> refers = new HashSet<>();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        javax.xml.transform.Result r = new StreamResult( bout );
        javax.xml.transform.Source s = new StreamSource( in );
        transformer.transform( s, r );
        ByteArrayInputStream bin = new ByteArrayInputStream( bout.toByteArray() );
        bout.close();
        BufferedReader br = new BufferedReader( new InputStreamReader( bin ) );
        for ( String line = br.readLine(); line != null; line = br.readLine() )
        {
            line = line.trim();
            line = line.replace( ";availability:=mandatory", "" );
            if ( line.length() > 0 )
            {
                refers.add( line );
            }
        }

        br.close();
        return refers;
    }


    protected Transformer getTransformer( URL url ) throws Exception
    {
        TransformerFactory tf = TransformerFactory.newInstance();
        javax.xml.transform.Source source = new StreamSource( url.openStream() );
        return tf.newTransformer( source );
    }

}
