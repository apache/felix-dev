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
package org.apache.felix.webconsole.plugins.event.internal;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.felix.utils.json.JSONWriter;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * The Event Plugin
 */
public class PluginServlet extends HttpServlet
{

    private static final long serialVersionUID = -8601361741848077124L;

    private static final String ACTION_POST = "post";
    private static final String ACTION_SEND = "send";
    private static final String ACTION_CLEAR = "clear";

    private static final String PARAMETER_ACTION = "action";

    /** The event collector. */
    private final EventCollector collector;

    /** Is the config admin available? */
    private volatile boolean configAdminAvailable = false;

    private EventAdmin eventAdmin;

    private final String TEMPLATE;

    public PluginServlet()
    {
        this.collector = new EventCollector();
        TEMPLATE = readTemplateFile(getClass(), "/res/events.html");
    }

    private final String readTemplateFile(final Class<?> clazz, final String templateFile)
    {
        InputStream templateStream = getClass().getResourceAsStream(templateFile);
        if (templateStream != null)
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] data = new byte[1024];
            try
            {
                int len = 0;
                while ((len = templateStream.read(data)) > 0)
                {
                    baos.write(data, 0, len);
                }
                return baos.toString("UTF-8");
            }
            catch (IOException e)
            {
                // don't use new Exception(message, cause) because cause is 1.4+
                throw new RuntimeException("readTemplateFile: Error loading "
                    + templateFile + ": " + e);
            }
            finally
            {
                try
                {
                    templateStream.close();
                }
                catch (IOException e)
                {
                    /* ignore */
                }

            }
        }

        // template file does not exist, return an empty string
        log("readTemplateFile: File '" + templateFile + "' not found through class "
            + clazz);
        return "";
    }

    private static final Event newEvent(HttpServletRequest request)
    {
        String topic = request.getParameter("topic");

        return new Event(topic, PropertiesEditorSupport.convertProperties(request));
    }


    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        final String action = req.getParameter( PARAMETER_ACTION );
        if ( ACTION_POST.equals(action) ) {
            final Event event = newEvent(req);
            eventAdmin.postEvent(event);
        } else if (ACTION_SEND.equals(action)) {
            final Event event = newEvent(req);
            eventAdmin.sendEvent(event);
        } else if ( ACTION_CLEAR.equals( action ) ) {
            this.collector.clear();
        }
        // we always send back the json data
        resp.setContentType( "application/json" );
        resp.setCharacterEncoding( "utf-8" );

        renderJSON( resp.getWriter() );
    }

    private void renderJSON( final PrintWriter pw ) throws IOException
    {
        List<EventInfo> events = this.collector.getEvents();

        StringBuffer statusLine = new StringBuffer();
        statusLine.append( events.size() );
        statusLine.append( " Event");
        if ( events.size() != 1 )
        {
            statusLine.append('s');
        }
        statusLine.append( " received" );
        if ( !events.isEmpty() )
        {
            statusLine.append( " since " );
            Date d = new Date();
            d.setTime( events.get( 0 ).received );
            statusLine.append( d );
        }
        statusLine.append( ". (Event admin: " );
        if ( this.eventAdmin == null )
        {
            statusLine.append("un");
        }
        statusLine.append("available; Config admin: ");
        if ( !this.configAdminAvailable )
        {
            statusLine.append("un");
        }
        statusLine.append("available)");

        // Compute scale: startTime is 0, lastTimestamp is 100%
        final long startTime = this.collector.getStartTime();
        final long endTime = (events.size() == 0 ? startTime : events.get(events.size() - 1).received);
        final float scale = (endTime == startTime ? 100.0f : 100.0f / (endTime - startTime));

        final JSONWriter writer = new JSONWriter(pw);
        writer.object();
        writer.key( "status" );
        writer.value( statusLine.toString() );

        writer.key( "data" );
        writer.array();

        // display list in reverse order
        for ( int index = events.size() - 1; index >= 0; index-- )
        {
            eventJson( writer, events.get( index ), index, startTime, scale );
        }

        writer.endArray();

        writer.endObject();
    }

    @Override
    public void doGet( HttpServletRequest request, HttpServletResponse response )
    throws ServletException, IOException {
        final String info = request.getPathInfo();
        if ( info.endsWith( ".json" ) )  {
            response.setContentType( "application/json" ); 
            response.setCharacterEncoding( "UTF-8" );

            PrintWriter pw = response.getWriter();
            this.renderJSON( pw );

            // nothing more to do
            return;
        }

        final PrintWriter pw = response.getWriter();
        //final String appRoot = ( String ) request.getAttribute( "felix.webconsole.appRoot" );
        //pw.println( "<script src='" + appRoot + "/events/res/ui/" + "events.js" + "' type='text/javascript'></script>" );
        pw.print(TEMPLATE);
    }

    private void eventJson( JSONWriter jw, EventInfo info, int index, final long start, final float scale )
    throws IOException
    {
        final long msec = info.received - start;

        // Compute color bar size and make sure the bar is visible
        final int percent = Math.max((int)(msec * scale), 2);

        jw.object();
        jw.key( "id" );
        jw.value( String.valueOf( index ) );

        jw.key( "offset" );
        jw.value( msec );

        jw.key( "width" );
        jw.value( percent );

        jw.key( "category" );
        jw.value( info.category );

        jw.key( "received" );
        jw.value( info.received );

        jw.key( "topic" );
        jw.value( info.topic );

        if ( info.info != null )
        {
            jw.key( "info" );
            jw.value( info.info );
        }

        jw.key( "properties" );
        jw.object();
        if ( info.properties != null && info.properties.size() > 0 )
        {
            final Iterator<Map.Entry<String, Object>> i = info.properties.entrySet().iterator();
            while ( i.hasNext() )
            {
                final Map.Entry<String, Object> current = i.next();
                jw.key( current.getKey().toString() );
                jw.value(current.getValue());
            }
        }
        jw.endObject();

        jw.endObject();
    }

    public void updateConfiguration( Dictionary<String, ?> dict)
    {
        this.collector.updateConfiguration(dict);
    }

    public EventCollector getCollector()
    {
        return this.collector;
    }

    public void setEventAdmin(final EventAdmin eventAdmin)
    {
        this.eventAdmin = eventAdmin;
    }

    public void setConfigAdminAvailable(final boolean flag)
    {
        this.configAdminAvailable = flag;
    }
}
