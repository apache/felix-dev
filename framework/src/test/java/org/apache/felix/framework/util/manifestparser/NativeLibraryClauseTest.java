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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NativeLibraryClauseTest {

    @Test
    void normalizeOSName() {
        assertThat(NativeLibraryClause.normalizeOSName("win 32")).isEqualTo("win32");
        assertThat(NativeLibraryClause.normalizeOSName("Win*")).isEqualTo("win32");
        assertThat(NativeLibraryClause.normalizeOSName("Windows NonExistingFutureVersion 4711")).isEqualTo("win32");
        assertThat(NativeLibraryClause.normalizeOSName("Windows 95")).isEqualTo("windows95");
        assertThat(NativeLibraryClause.normalizeOSName("Windows 98")).isEqualTo("windows98");
        assertThat(NativeLibraryClause.normalizeOSName("WinNT")).isEqualTo("windowsnt");
        assertThat(NativeLibraryClause.normalizeOSName("Win2000")).isEqualTo("windows2000");
        assertThat(NativeLibraryClause.normalizeOSName("Win2003")).isEqualTo("windows2003");
        assertThat(NativeLibraryClause.normalizeOSName("Windows Server 2008")).isEqualTo("windowsserver2008");
        assertThat(NativeLibraryClause.normalizeOSName("Windows Server 2012")).isEqualTo("windowsserver2012");
        assertThat(NativeLibraryClause.normalizeOSName("Windows Server 2016")).isEqualTo("windowsserver2016");
        assertThat(NativeLibraryClause.normalizeOSName("WinXP")).isEqualTo("windowsxp");
        assertThat(NativeLibraryClause.normalizeOSName("WinCE")).isEqualTo("windowsce");
        assertThat(NativeLibraryClause.normalizeOSName("WinVista")).isEqualTo("windowsvista");
        assertThat(NativeLibraryClause.normalizeOSName("Windows 7")).isEqualTo("windows7");
        assertThat(NativeLibraryClause.normalizeOSName("Win8")).isEqualTo("windows8");
        assertThat(NativeLibraryClause.normalizeOSName("Windows 10")).isEqualTo("windows10");
        assertThat(NativeLibraryClause.normalizeOSName("Linux1.2.3")).isEqualTo("linux");
        assertThat(NativeLibraryClause.normalizeOSName("AIX-4.5.6")).isEqualTo("aix");
        assertThat(NativeLibraryClause.normalizeOSName("digitalunix_blah")).isEqualTo("digitalunix");
        assertThat(NativeLibraryClause.normalizeOSName("HPUX-999")).isEqualTo("hpux");
        assertThat(NativeLibraryClause.normalizeOSName("Irixxxx")).isEqualTo("irix");
        assertThat(NativeLibraryClause.normalizeOSName("mac OS X")).isEqualTo("macosx");
        assertThat(NativeLibraryClause.normalizeOSName("Netware")).isEqualTo("netware");
        assertThat(NativeLibraryClause.normalizeOSName("OpenBSD-0000")).isEqualTo("openbsd");
        assertThat(NativeLibraryClause.normalizeOSName("netbsd ")).isEqualTo("netbsd");
        assertThat(NativeLibraryClause.normalizeOSName("os/2")).isEqualTo("os2");
        assertThat(NativeLibraryClause.normalizeOSName("procnto")).isEqualTo("qnx");
        assertThat(NativeLibraryClause.normalizeOSName("Solaris 9")).isEqualTo("solaris");
        assertThat(NativeLibraryClause.normalizeOSName("SunOS8")).isEqualTo("sunos");
        assertThat(NativeLibraryClause.normalizeOSName("VxWorks")).isEqualTo("vxworks");

        // Try all the already normalized names
        assertThat(NativeLibraryClause.normalizeOSName("aix")).isEqualTo("aix");
        assertThat(NativeLibraryClause.normalizeOSName("digitalunix")).isEqualTo("digitalunix");
        assertThat(NativeLibraryClause.normalizeOSName("hpux")).isEqualTo("hpux");
        assertThat(NativeLibraryClause.normalizeOSName("irix")).isEqualTo("irix");
        assertThat(NativeLibraryClause.normalizeOSName("linux")).isEqualTo("linux");
        assertThat(NativeLibraryClause.normalizeOSName("macos")).isEqualTo("macos");
        assertThat(NativeLibraryClause.normalizeOSName("netbsd")).isEqualTo("netbsd");
        assertThat(NativeLibraryClause.normalizeOSName("netware")).isEqualTo("netware");
        assertThat(NativeLibraryClause.normalizeOSName("openbsd")).isEqualTo("openbsd");
        assertThat(NativeLibraryClause.normalizeOSName("os2")).isEqualTo("os2");
        assertThat(NativeLibraryClause.normalizeOSName("qnx")).isEqualTo("qnx");
        assertThat(NativeLibraryClause.normalizeOSName("solaris")).isEqualTo("solaris");
        assertThat(NativeLibraryClause.normalizeOSName("sunos")).isEqualTo("sunos");
        assertThat(NativeLibraryClause.normalizeOSName("vxworks")).isEqualTo("vxworks");
        assertThat(NativeLibraryClause.normalizeOSName("windows2000")).isEqualTo("windows2000");
        assertThat(NativeLibraryClause.normalizeOSName("windows2003")).isEqualTo("windows2003");
        assertThat(NativeLibraryClause.normalizeOSName("windows7")).isEqualTo("windows7");
        assertThat(NativeLibraryClause.normalizeOSName("windows8")).isEqualTo("windows8");
        assertThat(NativeLibraryClause.normalizeOSName("windows9")).isEqualTo("windows9");
        assertThat(NativeLibraryClause.normalizeOSName("windows10")).isEqualTo("windows10");
        assertThat(NativeLibraryClause.normalizeOSName("windows95")).isEqualTo("windows95");
        assertThat(NativeLibraryClause.normalizeOSName("windows98")).isEqualTo("windows98");
        assertThat(NativeLibraryClause.normalizeOSName("windowsce")).isEqualTo("windowsce");
        assertThat(NativeLibraryClause.normalizeOSName("windowsnt")).isEqualTo("windowsnt");
        assertThat(NativeLibraryClause.normalizeOSName("windowsserver2008")).isEqualTo("windowsserver2008");
        assertThat(NativeLibraryClause.normalizeOSName("windowsserver2012")).isEqualTo("windowsserver2012");
        assertThat(NativeLibraryClause.normalizeOSName("windowsvista")).isEqualTo("windowsvista");
        assertThat(NativeLibraryClause.normalizeOSName("windowsxp")).isEqualTo("windowsxp");
        assertThat(NativeLibraryClause.normalizeOSName("win32")).isEqualTo("win32");
    }

    @Test
    void testgetOsNameWithAliases() {
        assertThat(NativeLibraryClause.getOsNameWithAliases("win 32")).contains("win32");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Win*")).contains("win32");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows 95")).contains("windows95");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows 98")).contains("windows98");
        assertThat(NativeLibraryClause.getOsNameWithAliases("WinNT")).contains("windowsnt");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Win2000")).contains("windows2000");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Win2003")).contains("windows2003");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows Server 2008")).contains("windowsserver2008");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows Server 2012")).contains("windowsserver2012");
        assertThat(NativeLibraryClause.getOsNameWithAliases("WinXP")).contains("windowsxp");
        assertThat(NativeLibraryClause.getOsNameWithAliases("WinCE")).contains("windowsce");
        assertThat(NativeLibraryClause.getOsNameWithAliases("WinVista")).contains("windowsvista");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows 7")).contains("windows7");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows7")).contains("windows7");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Win8")).contains("windows8");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Windows 10")).contains("windows10");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Linux1.2.3")).contains("linux");
        assertThat(NativeLibraryClause.getOsNameWithAliases("AIX-4.5.6")).contains("aix");
        assertThat(NativeLibraryClause.getOsNameWithAliases("digitalunix_blah")).contains("digitalunix");
        assertThat(NativeLibraryClause.getOsNameWithAliases("HPUX-999")).contains("hpux");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Irixxxx")).contains("irix");
        assertThat(NativeLibraryClause.getOsNameWithAliases("mac OS X")).contains("macosx");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Netware")).contains("netware");
        assertThat(NativeLibraryClause.getOsNameWithAliases("OpenBSD-0000")).contains("openbsd");
        assertThat(NativeLibraryClause.getOsNameWithAliases("netbsd ")).contains("netbsd");
        assertThat(NativeLibraryClause.getOsNameWithAliases("os/2")).contains("os2");
        assertThat(NativeLibraryClause.getOsNameWithAliases("procnto")).contains("qnx");
        assertThat(NativeLibraryClause.getOsNameWithAliases("Solaris 9")).contains("solaris");
        assertThat(NativeLibraryClause.getOsNameWithAliases("SunOS8")).contains("sunos");
        assertThat(NativeLibraryClause.getOsNameWithAliases("VxWorks")).contains("vxworks");

        // Try all the already normalized names
        assertThat(NativeLibraryClause.getOsNameWithAliases("aix")).contains("aix");
        assertThat(NativeLibraryClause.getOsNameWithAliases("digitalunix")).contains("digitalunix");
        assertThat(NativeLibraryClause.getOsNameWithAliases("hpux")).contains("hpux");
        assertThat(NativeLibraryClause.getOsNameWithAliases("irix")).contains("irix");
        assertThat(NativeLibraryClause.getOsNameWithAliases("linux")).contains("linux");
        assertThat(NativeLibraryClause.getOsNameWithAliases("mac os")).contains("macos");
        assertThat(NativeLibraryClause.getOsNameWithAliases("netbsd")).contains("netbsd");
        assertThat(NativeLibraryClause.getOsNameWithAliases("netware")).contains("netware");
        assertThat(NativeLibraryClause.getOsNameWithAliases("openbsd")).contains("openbsd");
        assertThat(NativeLibraryClause.getOsNameWithAliases("os2")).contains("os2");
        assertThat(NativeLibraryClause.getOsNameWithAliases("qnx")).contains("qnx");
        assertThat(NativeLibraryClause.getOsNameWithAliases("solaris")).contains("solaris");
        assertThat(NativeLibraryClause.getOsNameWithAliases("sunos")).contains("sunos");
        assertThat(NativeLibraryClause.getOsNameWithAliases("vxworks")).contains("vxworks");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows2000")).contains("windows2000");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows2003")).contains("windows2003");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows7")).contains("windows7");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows8")).contains("windows8");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows9")).contains("windows9");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows10")).contains("windows10");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows95")).contains("windows95");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windows98")).contains("windows98");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windowsce")).contains("windowsce");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windowsnt")).contains("windowsnt");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windowsserver2008")).contains("windowsserver2008");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windowsserver2012")).contains("windowsserver2012");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windowsvista")).contains("windowsvista");
        assertThat(NativeLibraryClause.getOsNameWithAliases("windowsxp")).contains("windowsxp");
        assertThat(NativeLibraryClause.getOsNameWithAliases("win32")).contains("win32");
    }

    @Test
    void normalizeOSVersion() {
        // valid
        assertThat(NativeLibraryClause.normalizeOSVersion("1")).isEqualTo("1.0.0");
        assertThat(NativeLibraryClause.normalizeOSVersion("1.2")).isEqualTo("1.2.0");
        assertThat(NativeLibraryClause.normalizeOSVersion("1.2.3")).isEqualTo("1.2.3");
        assertThat(NativeLibraryClause.normalizeOSVersion("1.2.3.qualifier")).isEqualTo("1.2.3.qualifier");

        // to normalize
        assertThat(NativeLibraryClause.normalizeOSVersion("1.qualifier")).isEqualTo("1.0.0.qualifier");
        assertThat(NativeLibraryClause.normalizeOSVersion("1.2.qualifier")).isEqualTo("1.2.0.qualifier");

        assertThat(NativeLibraryClause.normalizeOSVersion("3.13.0-39-generic")).isEqualTo("3.13.0.39-generic");

        assertThat(NativeLibraryClause.normalizeOSVersion("3.14.22-100.fc19.i686.PAE")).isEqualTo("3.14.22.100_fc19_i686_PAE");
        assertThat(NativeLibraryClause.normalizeOSVersion("4.9.35+")).isEqualTo("4.9.35");
        assertThat(NativeLibraryClause.normalizeOSVersion("4.9+")).isEqualTo("4.9.0");
        assertThat(NativeLibraryClause.normalizeOSVersion("4+")).isEqualTo("4.0.0");
    }
}
