/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.webconsole.plugins.memoryusage.internal;

import java.io.File;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

final class MemoryUsageSupport implements NotificationListener, ServiceListener
{

    // This is the name of the HotSpot Diagnostic MBean
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    // to get the LogService
    private final BundleContext context;

    // the default dump location: the dumps folder in the bundle private data
    // or the current working directory
    private final File defaultDumpLocation;

    // the default threshold value
    private final int defaultThreshold;

    // the minimum number of milliseconds between two consecutive memory
    // dumps written. this setting allows limitting the generation of memory
    // dumps if memory consumption is oscillating around the memory
    // threshold value
    private long minDumpInterval;

    // the configured dump location
    private File dumpLocation;

    // the actual threshold (configured or dynamically set in the console UI)
    private int threshold;

    // the system time of the last memory snapshot written; initialized so as
    // at least write one dump
    private long nextDumpTime = -1;

    // log service
    private ServiceReference<?> logServiceReference;
    private Object logService;

    MemoryUsageSupport(final BundleContext context)
    {
        this.context = context;

        // register for the log service
        try
        {
            context.addServiceListener(this, "(" + Constants.OBJECTCLASS + "=org.osgi.service.log.LogService)");
            logServiceReference = context.getServiceReference("org.osgi.service.log.LogService");
            if (logServiceReference != null)
            {
                logService = context.getService(logServiceReference);
            }
        }
        catch (InvalidSyntaxException ise)
        {
            // TODO
        }

        // the default dump location
        String propDumps = context.getProperty(MemoryUsageConstants.PROP_DUMP_LOCATION);
        if (propDumps == null)
        {
            propDumps = "dumps";
        }

        // ensure dump location is an absolute path/location
        File dumps = new File(propDumps);
        if (!dumps.isAbsolute())
        {
            File bundleDumps = context.getDataFile(propDumps);
            if (bundleDumps != null)
            {
                dumps = bundleDumps;
            }
        }
        this.defaultDumpLocation = dumps.getAbsoluteFile();

        // prepare the dump location
        setDumpLocation(null);

        // register for memory threshold notifications
        NotificationEmitter memEmitter = (NotificationEmitter) getMemory();
        memEmitter.addNotificationListener(this, null, null);

        // set the initial automatic dump threshold
        int defaultThreshold;
        String propThreshold = context.getProperty(MemoryUsageConstants.PROP_DUMP_THRESHOLD);
        if (propThreshold != null)
        {
            try
            {
                defaultThreshold = Integer.parseInt(propThreshold);
                setThreshold(defaultThreshold);
            }
            catch (Exception e)
            {
                // NumberFormatException - if propTreshold cannot be parsed to
                // int
                // IllegalArgumentException - if threshold is invalid
                defaultThreshold = -1;
            }
        }
        else
        {
            defaultThreshold = -1;
        }

        // default threshold has not been configured (correctly), assume fixed
        // default
        if (defaultThreshold < 0)
        {
            defaultThreshold = MemoryUsageConstants.DEFAULT_DUMP_THRESHOLD;
            setThreshold(defaultThreshold);
        }

        this.defaultThreshold = defaultThreshold;

        // set the initial automatic dump threshold
        int interval;
        String propInterval = context.getProperty(MemoryUsageConstants.PROP_DUMP_INTERVAL);
        if (propInterval != null)
        {
            try
            {
                interval = Integer.parseInt(propInterval);
            }
            catch (Exception e)
            {
                // NumberFormatException - if propTreshold cannot be parsed to
                // int
                // IllegalArgumentException - if threshold is invalid
                interval = -1;
            }
        }
        else
        {
            interval = -1;
        }
        setInterval(interval);
    }

    void dispose()
    {
        NotificationEmitter memEmitter = (NotificationEmitter) getMemory();
        try
        {
            memEmitter.removeNotificationListener(this);
        }
        catch (ListenerNotFoundException e)
        {
            // don't care
        }

        context.removeServiceListener(this);
        if (logServiceReference != null)
        {
            context.ungetService(logServiceReference);
            logServiceReference = null;
            logService = null;
        }
    }

    public BundleContext getBundleContext()
    {
        return context;
    }

    /**
     * Sets the threshold percentage.
     *
     * @param percentage The threshold as a percentage of memory consumption.
     *            This value may be 0 (zero) to switch off automatic heap dumps
     *            or in the range {@link #MIN_DUMP_THRESHOLD} to
     *            {@link #MAX_DUMP_THRESHOLD}. If set to a negative value,
     *            the default threshold is assumed.
     * @throws IllegalArgumentException if the percentage value is outside of
     *             the valid range of thresholds. The message is the percentage
     *             value which is not accepted.
     */
    final void setThreshold(int percentage)
    {
        if (percentage < 0)
        {
            percentage = defaultThreshold;
        }

        if (MemoryUsageConstants.isThresholdValid(percentage))
        {
            TreeSet<String> thresholdPools = new TreeSet<String>();
            TreeSet<String> noThresholdPools = new TreeSet<String>();
            List<MemoryPoolMXBean> pools = getMemoryPools();
            for (MemoryPoolMXBean pool : pools)
            {
                if (pool.isUsageThresholdSupported())
                {
                    long threshold = pool.getUsage().getMax() * percentage / 100;
                    pool.setUsageThreshold(threshold);
                    thresholdPools.add(pool.getName());
                }
                else
                {
                    noThresholdPools.add(pool.getName());
                }
            }
            this.threshold = percentage;

            log(LogService.LOG_INFO, "Setting Automatic Memory Dump Threshold to %d%% for pools %s", threshold,
                thresholdPools);
            log(LogService.LOG_INFO, "Automatic Memory Dump cannot be set for pools %s", noThresholdPools);
        }
        else
        {
            throw new IllegalArgumentException(String.valueOf(percentage));
        }
    }

    final int getThreshold()
    {
        return threshold;
    }

    final void setInterval(long interval)
    {
        if (interval < 0)
        {
            interval = MemoryUsageConstants.DEFAULT_DUMP_INTERVAL;
        }
        else
        {
            interval = 1000L * interval;
        }
        this.minDumpInterval = interval;
        log(LogService.LOG_INFO, "Setting Automatic Memory Dump Interval to %d seconds", getInterval());
    }

    final long getInterval()
    {
        return minDumpInterval / 1000L;
    }

    final void printMemory(final PrintHelper pw)
    {
        pw.title("Overall Memory Use", 0);
        pw.keyVal("Heap Dump Threshold", getThreshold() + "%");
        pw.keyVal("Heap Dump Interval", getInterval() + " seconds");
        printOverallMemory(pw);

        pw.title("Memory Pools", 0);
        printMemoryPools(pw);

        pw.title("Heap Dumps", 0);
        listDumpFiles(pw);
    }

    final void printOverallMemory(final PrintHelper pw)
    {
        final MemoryMXBean mem = getMemory();

        pw.keyVal("Verbose Memory Output", (mem.isVerbose() ? "yes" : "no"));
        pw.keyVal("Pending Finalizable Objects", mem.getObjectPendingFinalizationCount());

        pw.keyVal("Overall Heap Memory Usage", mem.getHeapMemoryUsage());
        pw.keyVal("Overall Non-Heap Memory Usage", mem.getNonHeapMemoryUsage());
    }

    final void printMemoryPools(final PrintHelper pw)
    {
        final List<MemoryPoolMXBean> pools = getMemoryPools();
        for (MemoryPoolMXBean pool : pools)
        {
            final String title = String.format("%s (%s, %s)", pool.getName(), pool.getType(), (pool.isValid() ? "valid"
                : "invalid"));
            pw.title(title, 1);

            pw.keyVal("Memory Managers", Arrays.asList(pool.getMemoryManagerNames()));

            pw.keyVal("Peak Usage", pool.getPeakUsage());

            pw.keyVal("Usage", pool.getUsage());
            if (pool.isUsageThresholdSupported())
            {
                pw.keyVal("Usage Threshold", String.format("%d, %s, #exceeded=%d", pool.getUsageThreshold(), pool
                    .isUsageThresholdExceeded() ? "exceeded" : "not exceeded", pool.getUsageThresholdCount()));
            }
            else
            {
                pw.val("Usage Threshold: not supported");
            }
            pw.keyVal("Collection Usage", pool.getCollectionUsage());
            if (pool.isCollectionUsageThresholdSupported())
            {
                pw.keyVal("Collection Usage Threshold", String.format("%d, %s, #exceeded=%d", pool
                    .getCollectionUsageThreshold(), pool.isCollectionUsageThresholdExceeded() ? "exceeded"
                    : "not exceeded", pool.getCollectionUsageThresholdCount()));
            }
            else
            {
                pw.val("Collection Usage Threshold: not supported");
            }
        }
    }

    final String getMemoryPoolsJson()
    {
        final StringBuilder buf = new StringBuilder();
        buf.append("[");

        long usedTotal = 0;
        long initTotal = 0;
        long committedTotal = 0;
        long maxTotal = 0;

        final List<MemoryPoolMXBean> pools = getMemoryPools();
        for (MemoryPoolMXBean pool : pools)
        {
            buf.append("{");

            buf.append("'name':'").append(pool.getName().replace("'", "\\'")).append('\'');
            buf.append(",'type':'").append(pool.getType()).append('\'');

            MemoryUsage usage = pool.getUsage();
            final long used = usage.getUsed();
            formatNumber(buf, "used", used);
            if ( used > -1 )
            {
                usedTotal += used;
            }
            final long init = usage.getInit();
            formatNumber(buf, "init", init);
            if ( init > - 1 )
            {
                initTotal +=  init;
            }
            final long committed = usage.getCommitted();
            formatNumber(buf, "committed", committed);
            committedTotal += committed;
            final long max = usage.getMax();
            formatNumber(buf, "max", usage.getMax());

            final long score;
            if ( max == -1 || used == -1 )
            {
                score = 100;
            }
            else
            {
                maxTotal += max;
                score = 100L * used / max;
            }
            buf.append(",'score':'").append(score).append("%'");

            buf.append("},");
        }

        // totalisation
        buf.append("{");
        buf.append("'name':'Total','type':'TOTAL'");
        formatNumber(buf, "used", usedTotal);
        formatNumber(buf, "init", initTotal);
        formatNumber(buf, "committed", committedTotal);
        formatNumber(buf, "max", maxTotal);

        final long score = 100L * usedTotal / maxTotal;
        buf.append(",'score':'").append(score).append("%'");

        buf.append("}");

        buf.append("]");
        return buf.toString();
    }

    void formatNumber(final StringBuilder buf, final String title, final long value)
    {

        final BigDecimal KB = new BigDecimal(1000L);
        final BigDecimal MB = new BigDecimal(1000L * 1000);
        final BigDecimal GB = new BigDecimal(1000L * 1000 * 1000);

        BigDecimal bd = new BigDecimal(value);
        final String suffix;
        if (bd.compareTo(GB) > 0)
        {
            bd = bd.divide(GB);
            suffix = "GB";
        }
        else if (bd.compareTo(MB) > 0)
        {
            bd = bd.divide(MB);
            suffix = "MB";
        }
        else if (bd.compareTo(KB) > 0)
        {
            bd = bd.divide(KB);
            suffix = "kB";
        }
        else if (value >= 0 )
        {
            suffix = "B";
        }
        else
        {
            suffix = null;
        }
        buf.append(",'").append(title).append("':'");
        if ( suffix == null )
        {
            buf.append("unknown");
        }
        else
        {
            bd = bd.setScale(2, RoundingMode.UP);
            buf.append(bd).append(suffix);
        }
        buf.append('\'');
    }

    final String getDefaultDumpLocation()
    {
        return defaultDumpLocation.getAbsolutePath();
    }

    final void setDumpLocation(final String dumpLocation)
    {
        if (dumpLocation == null || dumpLocation.length() == 0)
        {
            this.dumpLocation = defaultDumpLocation;
        }
        else
        {
            this.dumpLocation = new File(dumpLocation).getAbsoluteFile();
        }

        log(LogService.LOG_INFO, "Storing Memory Dumps in %s", this.dumpLocation);
    }

    final File getDumpLocation()
    {
        return dumpLocation;
    }

    final void listDumpFiles(final PrintHelper pw)
    {
        pw.title(dumpLocation.getAbsolutePath(), 1);
        File[] dumps = getDumpFiles();
        if (dumps == null || dumps.length == 0)
        {
            pw.keyVal("-- None", null);
        }
        else
        {
            long totalSize = 0;
            for (File dump : dumps)
            {
                // 32167397 2010-02-25 23:30 thefile
                pw
                    .val(String.format("%10d %tF %2$tR %s", dump.length(), new Date(dump.lastModified()), dump
                        .getName()));
                totalSize += dump.length();
            }
            pw.val(String.format("%d files, %d bytes", dumps.length, totalSize));
        }
    }

    final File getDumpFile(final String name)
    {
        // expect a non-empty string without slash
        if (name == null || name.length() == 0 || name.indexOf('/') >= 0)
        {
            return null;
        }

        File dumpFile = new File(dumpLocation, name);
        if (dumpFile.isFile())
        {
            return dumpFile;
        }

        return null;
    }

    final File[] getDumpFiles()
    {
        return dumpLocation.listFiles();
    }

    final boolean rmDumpFile(final String name)
    {
        if (name == null || name.length() == 0)
        {
            return false;
        }

        final File dumpFile = new File(dumpLocation, name);
        if (!dumpFile.exists())
        {
            return false;
        }

        dumpFile.delete();
        return true;
    }

    /**
     * Dumps the heap to a temporary file
     *
     * @param live <code>true</code> if only live objects are to be returned
     * @return
     * @throws NoSuchElementException If no provided mechanism is successfully
     *             used to create a heap dump
     */
    final File dumpHeap(String name, final boolean live)
    {
        // ensure dumplocation exists
        dumpLocation.mkdirs();

        File dump = dumpSunMBean(name, live);
        if (dump == null)
        {
            dump = dumpIbmDump(name);
        }

        if (dump == null)
        {
            throw new NoSuchElementException();
        }

        return dump;
    }

    final MemoryMXBean getMemory()
    {
        return ManagementFactory.getMemoryMXBean();
    }

    final List<MemoryPoolMXBean> getMemoryPools()
    {
        return ManagementFactory.getMemoryPoolMXBeans();
    }

    public void handleNotification(Notification notification, Object handback)
    {
        String notifType = notification.getType();
        if (notifType.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED))
        {
            if (System.currentTimeMillis() >= nextDumpTime)
            {
                log(LogService.LOG_WARNING, "Received Memory Threshold Exceeded Notification, dumping Heap");
                try
                {
                    File file = dumpHeap(null, true);
                    log(LogService.LOG_WARNING, "Heap dumped to " + file);
                    nextDumpTime = System.currentTimeMillis() + minDumpInterval;
                }
                catch (NoSuchElementException e)
                {
                    log(LogService.LOG_ERROR,
                        "Failed dumping the heap, JVM does not provide known mechanism to create a Heap Dump");
                }
            }
            else
            {
                log(LogService.LOG_WARNING,
                    "Ignoring Memory Threshold Exceeded Notification, minimum dump interval since last dump has not passed yet");
            }
        }
    }

    static interface PrintHelper
    {
        void title(final String title, final int level);

        void val(final String value);

        void keyVal(final String key, final Object value);
    }

    // ---------- Various System Specific Heap Dump mechanisms

    /**
     * @see http://blogs.sun.com/sundararajan/entry/
     *      programmatically_dumping_heap_from_java
     */
    private File dumpSunMBean(String name, boolean live)
    {
        if (name == null)
        {
            name = "heap." + System.currentTimeMillis() + ".hprof";
        }

        File tmpFile = new File(dumpLocation, name);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();

        try
        {
            server.invoke(new ObjectName(HOTSPOT_BEAN_NAME), "dumpHeap", new Object[]
                { tmpFile.getAbsolutePath(), live }, new String[]
                { String.class.getName(), boolean.class.getName() });

            log(LogService.LOG_DEBUG, "dumpSunMBean: Dumped Heap to %s using Sun HotSpot MBean", tmpFile);
            return tmpFile;
        }
        catch (Throwable t)
        {
            log(LogService.LOG_DEBUG, "dumpSunMBean: Dump by Sun HotSpot MBean not working", t);
            tmpFile.delete();
        }

        return null;
    }

    /**
     * @param name
     * @return
     * @see <a href="http://publib.boulder.ibm.com/infocenter/javasdk/v5r0/index.jsp?topic=/com.ibm.java.doc.diagnostics.50/diag/tools/heapdump_enable.html">Getting Heapdumps</a>
     */
    private File dumpIbmDump(String name)
    {
        try
        {
            // to try to indicate which file will contain the heap dump
            long minFileTime = System.currentTimeMillis();

            // call the com.ibm.jvm.Dump.HeapDump() method
            Class<?> c = ClassLoader.getSystemClassLoader().loadClass("com.ibm.jvm.Dump");
            Method m = c.getDeclaredMethod("HeapDump", (Class<?>[]) null);
            m.invoke(null, (Object[]) null);

            // find the file in the current working directory
            File dir = new File("").getAbsoluteFile();
            File[] files = dir.listFiles();
            if (files != null)
            {
                for (File file : files)
                {
                    if (file.isFile() && file.lastModified() > minFileTime)
                    {
                        if (name == null)
                        {
                            name = file.getName();
                        }
                        File target = new File(dumpLocation, name);
                        file.renameTo(target);

                        log(LogService.LOG_DEBUG, "dumpSunMBean: Dumped Heap to %s using IBM Dump.HeapDump()", target);
                        return target;
                    }
                }

                log(LogService.LOG_DEBUG, "dumpIbmDump: None of %d files '%s' is younger than %d", files.length, dir,
                    minFileTime);
            }
            else
            {
                log(LogService.LOG_DEBUG, "dumpIbmDump: Hmm '%s' does not seem to be a directory; isdir=%b ??", dir,
                    dir.isDirectory());
            }

            log(LogService.LOG_WARNING, "dumpIbmDump: Heap Dump has been created but cannot be located");
            return dumpLocation;
        }
        catch (Throwable t)
        {
            log(LogService.LOG_DEBUG, "dumpIbmDump: Dump by IBM Dump class not working", t);
        }

        return null;
    }

    // ---------- Logging support

    public void serviceChanged(ServiceEvent event)
    {
        if (event.getType() == ServiceEvent.REGISTERED && logServiceReference == null)
        {
            logServiceReference = event.getServiceReference();
            logService = context.getService(event.getServiceReference());
        }
        else if (event.getType() == ServiceEvent.UNREGISTERING && logServiceReference == event.getServiceReference())
        {
            logServiceReference = null;
            logService = null;
            context.ungetService(event.getServiceReference());
        }
    }

    void log(int level, String format, Object... args)
    {
        log(level, null, format, args);
    }

    void log(int level, Throwable t, String format, Object... args)
    {
        Object logService = this.logService;
        final String message = String.format(format, args);
        if (logService != null)
        {
            try {
                Method m = logService.getClass()
                        .getDeclaredMethod("log", int.class, String.class, Throwable.class);
                m.setAccessible(true);
                m.invoke(logService, level, message, t);
            } catch (Exception e) {
                logSTD(LogService.LOG_WARNING, e, "Unable to log with the given log service");
                logSTD(level, t, message);
            }
        }
        else
        {
            logSTD(level, t, message);
        }
    }

    private void logSTD(int level, Throwable t, String message) {
        PrintStream out = (level <= LogService.LOG_ERROR) ? System.err : System.out;
        out.printf("%s: %s (%d): %s%n", toLevelString(level), context.getBundle().getSymbolicName(), context
            .getBundle().getBundleId(), message);
        if (t != null)
        {
            t.printStackTrace(out);
        }
    }

    private String toLevelString(int level)
    {
        switch (level)
        {
            case LogService.LOG_DEBUG:
                return "DEBUG";
            case LogService.LOG_INFO:
                return "INFO ";
            case LogService.LOG_WARNING:
                return "WARN ";
            case LogService.LOG_ERROR:
                return "ERROR";
            default:
                return "unknown(" + level + ")";
        }
    }
}
