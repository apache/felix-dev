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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.osgi.AbstractResource;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;

/**
 * Checks that the headers BlueprintPlugin writes are read back unchanged by bnd's header parser.
 */
public class BlueprintPluginTest
{

    private static final String BP_START = "<blueprint xmlns='http://www.osgi.org/xmlns/blueprint/v1.0.0'>\n";
    private static final String BP_END = "</blueprint>";


    @Test
    public void testFilterWithBackslashIsKept() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<reference interface='p.Foo' filter='(name=a\\*b)'/>\n" + BP_END );

        assertEquals( Arrays.asList( "(name=a\\*b)" ), values( analyzer, "Import-Service", "p.Foo", "filter" ) );
    }


    @Test
    public void testFilterWithQuoteIsKept() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<reference interface='p.Foo' filter='(a=\"x\")'/>\n" + BP_END );

        assertEquals( Arrays.asList( "(a=\"x\")" ), values( analyzer, "Import-Service", "p.Foo", "filter" ) );
    }


    @Test
    public void testFilterWithComponentName() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<reference interface='p.Foo' filter='(a=b)' component-name='c'/>\n" + BP_END );

        assertEquals( Arrays.asList( "(&(a=b)(osgi.service.blueprint.compname=c))" ),
            values( analyzer, "Import-Service", "p.Foo", "filter" ) );
    }


    @Test
    public void testConfiguredHeaderWithSingleQuotes() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<reference interface='p.Foo'/>\n" + BP_END,
            "Import-Service", "p.Bar;filter='(a=b)'" );

        assertEquals( Arrays.asList( "(a=b)" ), values( analyzer, "Import-Service", "p.Bar", "filter" ) );
        assertEquals( 1, values( analyzer, "Import-Service", "p.Foo", "multiple:" ).size() );
    }


    @Test
    public void testConfiguredHeaderWithEscapedQuote() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<reference interface='p.Foo'/>\n" + BP_END,
            "Import-Service", "p.Bar;filter=\"(a=\\\"x\\\")\"" );

        assertEquals( Arrays.asList( "(a=\"x\")" ), values( analyzer, "Import-Service", "p.Bar", "filter" ) );
    }


    @Test
    public void testConfiguredClauseIsMerged() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<reference interface='p.Foo' filter='(a=b)'/>\n" + BP_END,
            "Import-Service", "p.Foo;filter=\"(a=b)\";multiple:=false" );

        assertEquals( Arrays.asList( "(a=b)" ), values( analyzer, "Import-Service", "p.Foo", "filter" ) );
    }


    @Test
    public void testConfiguredHeaderKeepsTypedAttribute() throws Exception
    {
        Analyzer analyzer = analyze( "service",
            BP_START + "<service interface='p.Foo' ref='b'/>\n" + BP_END,
            "Export-Service", "p.Bar;k:List<String>=\"x,y\"" );

        String header = analyzer.getProperty( "Export-Service" );
        Attrs attrs = OSGiHeader.parseHeader( header ).get( "p.Bar" );
        assertNotNull( header, attrs );
        assertEquals( "x,y", attrs.get( "k" ) );
        assertEquals( "List<String>", attrs.getType( "k" ).toString() );
    }


    @Test
    public void testGenericFilterWithBackslashIsKept() throws Exception
    {
        Analyzer analyzer = analyze( "generic",
            BP_START + "<reference interface='p.Foo' filter='(name=a\\*b)'/>\n" + BP_END );

        assertEquals( Arrays.asList( "(&(objectClass=p.Foo)(name=a\\*b))" ),
            values( analyzer, "Require-Capability", "osgi.service", "filter:" ) );
    }


    @Test
    public void testGenericFilterWithQuoteIsKept() throws Exception
    {
        Analyzer analyzer = analyze( "generic",
            BP_START + "<reference interface='p.Foo' filter='(a=\"x\")'/>\n" + BP_END );

        assertEquals( Arrays.asList( "(&(objectClass=p.Foo)(a=\"x\"))" ),
            values( analyzer, "Require-Capability", "osgi.service", "filter:" ) );
    }


    @Test
    public void testGenericServicePropertyWithQuoteIsKept() throws Exception
    {
        Analyzer analyzer = analyze( "generic",
            BP_START
                + "<service interface='p.Foo' ref='b'>\n"
                + "  <service-properties><entry key='k' value='say \"hi\"'/></service-properties>\n"
                + "</service>\n"
                + BP_END );

        assertEquals( Arrays.asList( "say \"hi\"" ), values( analyzer, "Provide-Capability", "osgi.service", "k" ) );
    }


    @Test
    public void testGenericServicePropertyWithBackslashIsKept() throws Exception
    {
        Analyzer analyzer = analyze( "generic",
            BP_START
                + "<service interface='p.Foo' ref='b'>\n"
                + "  <service-properties><entry key='k' value='a\\b'/></service-properties>\n"
                + "</service>\n"
                + BP_END );

        assertEquals( Arrays.asList( "a\\b" ), values( analyzer, "Provide-Capability", "osgi.service", "k" ) );
    }


    private static Analyzer analyze( String mode, final String blueprint, String... properties ) throws Exception
    {
        Analyzer analyzer = new Analyzer();
        Jar jar = new Jar( "the-jar" );
        jar.putResource( "OSGI-INF/blueprint/bp.xml", new AbstractResource( 0 )
        {
            @Override
            protected byte[] getBytes() throws Exception
            {
                return blueprint.getBytes( "UTF-8" );
            }
        } );
        analyzer.setJar( jar );
        analyzer.setProperty( "service_mode", mode );
        for ( int i = 0; i < properties.length; i += 2 )
        {
            analyzer.setProperty( properties[i], properties[i + 1] );
        }

        new BlueprintPlugin().analyzeJar( analyzer );
        return analyzer;
    }


    private static List<String> values( Analyzer analyzer, String header, String name, String key )
    {
        String value = analyzer.getProperty( header );
        assertNotNull( header + " not set", value );
        return values( value, name, key );
    }


    /**
     * Parses the header with bnd and returns the given attribute (or directive, with a trailing ':')
     * of every clause with the given name.
     */
    static List<String> values( String header, String name, String key )
    {
        List<String> result = new ArrayList<>();
        for ( Map.Entry<String, Attrs> clause : OSGiHeader.parseHeader( header ).entrySet() )
        {
            if ( Processor.removeDuplicateMarker( clause.getKey() ).equals( name ) && clause.getValue().containsKey( key ) )
            {
                result.add( clause.getValue().get( key ) );
            }
        }
        return result;
    }
}
