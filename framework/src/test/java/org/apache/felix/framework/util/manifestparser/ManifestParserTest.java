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
package org.apache.felix.framework.util.manifestparser;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.framework.BundleRevisionImpl;
import org.apache.felix.framework.cache.ConnectContentContent;
import org.apache.felix.framework.cache.Content;
import org.apache.felix.framework.util.FelixConstants;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.NativeNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

import junit.framework.TestCase;

public class ManifestParserTest extends TestCase
{
    public void testIdentityCapabilityMinimal() throws BundleException
    {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
        headers.put(Constants.BUNDLE_SYMBOLICNAME, "foo.bar");
        ManifestParser mp = new ManifestParser(null, null, null, headers);

        BundleCapability ic = findCapability(mp.getCapabilities(), IdentityNamespace.IDENTITY_NAMESPACE);
        assertEquals("foo.bar", ic.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE));
        assertEquals(IdentityNamespace.TYPE_BUNDLE, ic.getAttributes().get(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE));
        assertEquals(0, ic.getDirectives().size());
    }

    public void testIdentityCapabilityFull() throws BundleException
    {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
        headers.put(Constants.BUNDLE_SYMBOLICNAME, "abc;singleton:=true;foo=bar;" + IdentityNamespace.CAPABILITY_TAGS_ATTRIBUTE + "=test");
        headers.put(Constants.BUNDLE_VERSION, "1.2.3.something");
        String copyright = "(c) 2013 Apache Software Foundation";
        headers.put(Constants.BUNDLE_COPYRIGHT, copyright);
        String description = "A bundle description";
        headers.put(Constants.BUNDLE_DESCRIPTION, description);
        String docurl = "http://felix.apache.org/";
        headers.put(Constants.BUNDLE_DOCURL, docurl);
        String license = "http://www.apache.org/licenses/LICENSE-2.0";
        headers.put("Bundle-License", license);

        BundleRevisionImpl mockBundleRevision = mock(BundleRevisionImpl.class);

        Content connectContent = mock(ConnectContentContent.class);
        when(mockBundleRevision.getContent()).thenReturn(connectContent);

        ManifestParser mp = new ManifestParser(null, null, mockBundleRevision, headers);

        BundleCapability ic = findCapability(mp.getCapabilities(), IdentityNamespace.IDENTITY_NAMESPACE);
        assertEquals("abc", ic.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE));
        assertEquals(new Version("1.2.3.something"), ic.getAttributes().get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE));
        assertEquals(IdentityNamespace.TYPE_BUNDLE, ic.getAttributes().get(IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE));
        assertEquals(copyright, ic.getAttributes().get(IdentityNamespace.CAPABILITY_COPYRIGHT_ATTRIBUTE));
        assertEquals(description, ic.getAttributes().get(IdentityNamespace.CAPABILITY_DESCRIPTION_ATTRIBUTE));
        assertEquals(docurl, ic.getAttributes().get(IdentityNamespace.CAPABILITY_DOCUMENTATION_ATTRIBUTE));
        assertEquals(license, ic.getAttributes().get(IdentityNamespace.CAPABILITY_LICENSE_ATTRIBUTE));
        assertEquals(Arrays.asList("test", ConnectContent.TAG_OSGI_CONNECT), ic.getAttributes().get(IdentityNamespace.CAPABILITY_TAGS_ATTRIBUTE));
        assertEquals("bar", ic.getAttributes().get("foo"));

        assertEquals(1, ic.getDirectives().size());
        assertEquals("true", ic.getDirectives().get(IdentityNamespace.CAPABILITY_SINGLETON_DIRECTIVE));
    }
    
    @SuppressWarnings("unchecked")
	public void testNativeCapability() throws BundleException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.BUNDLE_MANIFESTVERSION,  "2");
        headers.put(Constants.BUNDLE_SYMBOLICNAME, FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME);
        headers.put(Constants.PROVIDE_CAPABILITY, " osgi.native;" +
        		"osgi.native.osname:List<String>=\"Windows7,Windows 7,Win7,Win32\";"+
        		"osgi.native.osversion:Version=\"7.0\";"+
        		"osgi.native.processor:List<String>=\"x86-64,amd64,em64t,x86_64\";"+
        		"osgi.native.language=\"en\"");

        BundleRevisionImpl mockBundleRevision = mock(BundleRevisionImpl.class);

        when(mockBundleRevision.getContent()).thenReturn(null);
        when(mockBundleRevision.getSymbolicName()).thenReturn(FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME);
    	
        ManifestParser mp = new ManifestParser(null, null, mockBundleRevision, headers);

        BundleCapability ic = findCapability(mp.getCapabilities(), NativeNamespace.NATIVE_NAMESPACE);
    	
        assertEquals("en", ic.getAttributes().get(NativeNamespace.CAPABILITY_LANGUAGE_ATTRIBUTE));
        List<String> osList = (List<String>) ic.getAttributes().get(NativeNamespace.CAPABILITY_OSNAME_ATTRIBUTE);
        assertEquals(4, osList.size());
        assertEquals(new Version("7.0"), ic.getAttributes().get(NativeNamespace.CAPABILITY_OSVERSION_ATTRIBUTE));
        List<String> nativeProcesserList = (List<String>) ic.getAttributes().get(NativeNamespace.CAPABILITY_PROCESSOR_ATTRIBUTE);
        assertEquals(4, nativeProcesserList.size());
    
    }
    
    @SuppressWarnings("unchecked")
    public void testAttributes() throws BundleException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Constants.BUNDLE_MANIFESTVERSION,  "2");
        headers.put(Constants.BUNDLE_SYMBOLICNAME,"com.example.test.sample");
        headers.put(Constants.PROVIDE_CAPABILITY,
                "com.example;theList:List<String>=\"red,green,blue\";theLong:Long=111");
        headers.put(Constants.REQUIRE_CAPABILITY,
                "com.example.other;theList:List<String>=\"one,two,three\";theLong:Long=999");

        BundleRevisionImpl mockBundleRevision = mock(BundleRevisionImpl.class);

        when(mockBundleRevision.getContent()).thenReturn(null);

        when(mockBundleRevision.getSymbolicName()).thenReturn("com.example.test.sample");

        ManifestParser mp = new ManifestParser(null, null, mockBundleRevision, headers);

        BundleCapability bc = findCapability(mp.getCapabilities(), "com.example");
        Long cLong = (Long) bc.getAttributes().get("theLong");
        assertEquals(Long.valueOf(111), cLong);
        List<String> cList = (List<String>)
                bc.getAttributes().get("theList");
        assertEquals(3, cList.size());
        assertTrue(cList.contains("red"));

        BundleRequirement br = findRequirement(mp.getRequirements(), "com.example.other");
        Long rLong = (Long) br.getAttributes().get("theLong");
        assertEquals(Long.valueOf(999), rLong);
        List<String> rList = (List<String>) br.getAttributes().get("theList");
        assertEquals(3, rList.size());
    }
    
    public void testConvertNativeCode() throws InvalidSyntaxException
    {
        List<NativeLibraryClause> nativeLibraryClauses = new ArrayList<NativeLibraryClause>();
        String[] libraryFiles = {"lib/http.dll", "lib/zlib.dll"};
        String[] osNames = {"Windows95", "Windows98", "WindowsNT"};
        String[] processors = {"x86"};
        String[] osVersions = null;
        String[] languages = {"en", "se"};
        String selectionFilter = "(com.acme.windowing=win32)";
        NativeLibraryClause clause = new NativeLibraryClause(libraryFiles, osNames, processors, osVersions, languages, selectionFilter);

        BundleRevisionImpl owner = mock(BundleRevisionImpl.class);

        when(owner.getContent()).thenReturn(null);
        nativeLibraryClauses.add(clause);
        
        List<BundleRequirement> nativeBundleReq = ManifestParser.convertNativeCode(owner, nativeLibraryClauses, false);
        
        BundleRequirement ir = findRequirement(nativeBundleReq, NativeNamespace.NATIVE_NAMESPACE);
        
        String filterStr = (String)ir.getDirectives().get(NativeNamespace.REQUIREMENT_FILTER_DIRECTIVE);
        
        Filter actualFilter = FrameworkUtil.createFilter(filterStr);
        
        Filter expectedFilter = FrameworkUtil.createFilter("(&(|" + 
                "(osgi.native.osname~=windows95)(osgi.native.osname~=windows98)(osgi.native.osname~=windowsnt)" +
                ")" + 
                "(osgi.native.processor~=x86)" + 
                "(|(osgi.native.language~=en)" + 
                "(osgi.native.language~=se)" + 
                ")"+
                "(com.acme.windowing=win32))");
        assertEquals("Filter Should contain native requirements", expectedFilter, actualFilter);
        
    }

    private BundleCapability findCapability(Collection<BundleCapability> capabilities, String namespace)
    {
        for (BundleCapability capability : capabilities)
        {
            if (namespace.equals(capability.getNamespace()))
            {
                return capability;
            }
        }
        return null;
    }
    
    private BundleRequirement findRequirement(Collection<BundleRequirement> requirements, String namespace)
    {
        for(BundleRequirement requirement: requirements)
        {
            if(namespace.equals(requirement.getNamespace()))
            {
                return requirement;
            }
        }
        return null;
    }
}
