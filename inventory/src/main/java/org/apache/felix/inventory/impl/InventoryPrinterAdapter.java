/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.inventory.impl;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.zip.ZipOutputStream;

import org.apache.felix.inventory.Format;
import org.apache.felix.inventory.InventoryPrinter;
import org.apache.felix.inventory.ZipAttachmentProvider;
import org.apache.felix.inventory.impl.webconsole.ConsoleConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;

/**
 * Helper class for a inventory printer.
 *
 * The adapter simplifies accessing and working with the inventory printer.
 */
public class InventoryPrinterAdapter implements InventoryPrinterHandler, Comparable<InventoryPrinterAdapter>
{

    /**
     * Comparator for adapters based on the service ranking.
     */
    public static final Comparator<InventoryPrinterAdapter> RANKING_COMPARATOR = new Comparator<InventoryPrinterAdapter>()
    {

        @Override
        public int compare(final InventoryPrinterAdapter o1, final InventoryPrinterAdapter o2)
        {
            return o1.description.compareTo(o2.description);
        }
    };

    /** The Inventory printer service. */
    private final InventoryPrinter printer;

    /** The printer description. */
    private final InventoryPrinterDescription description;

    /** Service registration for the web console. */
    private ServiceRegistration registration;

    /**
     * Constructor.
     */
    public InventoryPrinterAdapter(final InventoryPrinterDescription description, final InventoryPrinter printer)
    {
        this.description = description;
        this.printer = printer;
    }

    public void registerConsole(final BundleContext context, final InventoryPrinterManagerImpl manager)
    {
        if (this.registration == null)
        {
            final Object value = this.description.getServiceReference().getProperty(InventoryPrinter.WEBCONSOLE);
            if (value == null || !"false".equalsIgnoreCase(value.toString()))
            {
                final Dictionary<String, Object> props = new Hashtable<>();
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME, getLabel());
                props.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT,
                        "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME + "="
                                + ConsoleConstants.DEFAULT_CONTEXT_NAME + ")");
                props.put(ConsoleConstants.PLUGIN_LABEL, getLabel());
                props.put(ConsoleConstants.PLUGIN_TITLE, this.description.getTitle());
                props.put(ConsoleConstants.PLUGIN_CATEGORY, ConsoleConstants.WEB_CONSOLE_CATEGORY);
                this.registration = context.registerService(ConsoleConstants.INTERFACE_SERVLET, new WebConsolePlugin(manager, description.getName()), props);
            }
        }
    }

    public void unregisterConsole()
    {
        if (this.registration != null)
        {
            this.registration.unregister();
            this.registration = null;
        }
    }

    /**
     * The human readable title for the inventory printer.
     */
    @Override
    public String getTitle()
    {
        return this.description.getTitle();
    }

    /**
     * The unique name of the printer.
     */
    @Override
    public String getName()
    {
        return this.description.getName();
    }

    /**
     * All supported formats.
     */
    @Override
    public Format[] getFormats()
    {
        return this.description.getFormats();
    }

    /**
     * @see org.apache.felix.inventory.ZipAttachmentProvider#addAttachments(java.util.zip.ZipOutputStream,
     *      java.lang.String)
     */
    @Override
    public void addAttachments(final ZipOutputStream zos, final String namePrefix) throws IOException
    {
        if (printer instanceof ZipAttachmentProvider)
        {
            ((ZipAttachmentProvider) printer).addAttachments(zos, namePrefix);
        }
    }

    /**
     * Whether the printer supports this format.
     */
    @Override
    public boolean supports(final Format format)
    {
        for (int i = 0; i < this.description.getFormats().length; i++)
        {
            if (this.description.getFormats()[i] == format)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @see org.apache.felix.inventory.InventoryPrinter#print(org.apache.felix.inventory.Format,
     *      java.io.PrintWriter)
     */
    @Override
    public void print(final PrintWriter printWriter, final Format format, final boolean isZip)
    {
        if (this.supports(format))
        {
            this.printer.print(printWriter, format, isZip);
        }
    }

    public InventoryPrinterDescription getDescription()
    {
        return this.description;
    }

    private final String getLabel()
    {
        return ("status-" + this.description.getName());
    }    

    @Override
    public int compareTo(final InventoryPrinterAdapter spa)
    {
        return this.description.getSortKey().compareTo(spa.description.getSortKey());
    }

    @Override
    public int hashCode()
    {
        return this.description.getSortKey().hashCode();
    }

    @Override
    public boolean equals(final Object spa)
    {
        if ( !(spa instanceof InventoryPrinterAdapter)) {
            return false;
        }
        return this.description.getSortKey().equals(((InventoryPrinterAdapter) spa).description.getSortKey());
    }

    @Override
    public String toString()
    {
        return printer.getClass() + "(" + super.toString() + ")";
    }
}
