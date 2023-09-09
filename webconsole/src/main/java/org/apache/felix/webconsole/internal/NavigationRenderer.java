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
package org.apache.felix.webconsole.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class NavigationRenderer {

    /**
     * The header fragment read from the templates/main_header.html file
     */
    public static final String HEADER;

    /**
     * The footer fragment read from the templates/main_footer.html file
     */
    public static final String FOOTER;

    static {
        try {
            HEADER = Util.readTemplateFile( NavigationRenderer.class, "/templates/main_header.html" );
            FOOTER = Util.readTemplateFile(NavigationRenderer.class, "/templates/main_footer.html" );
        } catch ( final IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    /**
     * This method is called to generate the top level links with the available plug-ins.
     *
     * @param request the HTTP request coming from the user
     * @param pw the writer, where the HTML data is rendered
     */
    @SuppressWarnings({ "rawtypes" })
    public static void renderTopNavigation( final PrintWriter pw, final String appRoot, final Map menuMap, final Map langMap, final Locale reqLocale) {
        renderMenu( menuMap, appRoot, pw );

        // render lang-box
        if (null != langMap && !langMap.isEmpty()) {
            // determine the currently selected locale from the request and fail-back
            // to the default locale if not set
            // if locale is missing in locale map, the default 'en' locale is used
            String locale = null != reqLocale ? reqLocale.getLanguage() : Locale.getDefault().getLanguage();
            if (!langMap.containsKey(locale)) {
                locale = Locale.getDefault().getLanguage();
            }
            if (!langMap.containsKey(locale)) {
                locale = "en";
            }

            pw.println("<div id='langSelect'>");
            pw.println(" <span>");
            printLocaleElement(pw, appRoot, locale, langMap.get(locale));
            pw.println(" </span>");
            pw.println(" <span class='flags ui-helper-hidden'>");
            for (Iterator li = langMap.keySet().iterator(); li.hasNext();) {
                // <img src="us.gif" alt="en" title="English"/>
                final Object l = li.next();
                if (!l.equals(locale)) {
                    printLocaleElement(pw, appRoot, l, langMap.get(l));
                }
            }

            pw.println(" </span>");
            pw.println("</div>");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static SortedMap sortMenuCategoryMap(final Map map, final String appRoot ) {
        final SortedMap sortedMap = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        final Iterator keys = map.keySet().iterator();
        while ( keys.hasNext() ) {
            final String key = ( String ) keys.next();
            if ( key.startsWith( "category." ) ) {
                final SortedMap categoryMap = sortMenuCategoryMap( ( Map ) map.get( key ), appRoot );
                final String title = key.substring( key.indexOf( '.' ) + 1 );
                if ( sortedMap.containsKey( title ) ) {
                    ( ( MenuItem ) sortedMap.get( title ) ).setSubMenu( categoryMap );
                } else {
                    final String link = "<a href=\"#\">" + title + "</a>";
                    final MenuItem menuItem = new MenuItem( link, categoryMap );
                    sortedMap.put( title, menuItem );
                }
            } else {
                final String title = ( String ) map.get( key );
                final String link = "<a href=\"" + appRoot + "/" + key + "\">" + title + "</a>";
                if ( sortedMap.containsKey( title ) ) {
                    ( ( MenuItem ) sortedMap.get( title ) ).setLink( link );
                } else {
                    final MenuItem menuItem = new MenuItem( link );
                    sortedMap.put( title, menuItem );
                }
            }
        }
        return sortedMap;
    }

    @SuppressWarnings({ "rawtypes" })
    private static void renderMenu(final Map menuMap, final String appRoot, final PrintWriter pw ) {
        if ( menuMap != null ) {
            final SortedMap categoryMap = sortMenuCategoryMap( menuMap, appRoot );
            pw.println( "<ul id=\"navmenu\">" );
            renderSubmenu( categoryMap, appRoot, pw, 0 );
            pw.println("<li class=\"logoutButton navMenuItem-0\">");
            pw.println("<a href=\"" + appRoot + "/logout\">${logout}</a>");
            pw.println("</li>");
            pw.println( "</ul>" );
        }
    }

    @SuppressWarnings({ "rawtypes" })
    private static void renderMenu(final Map menuMap, final String appRoot, final PrintWriter pw, final int level ) {
        pw.println( "<ul class=\"navMenuLevel-" + level + "\">" );
        renderSubmenu( menuMap, appRoot, pw, level );
        pw.println( "</ul>" );
    }

    @SuppressWarnings({ "rawtypes" })
    private static void renderSubmenu(final Map menuMap, final String appRoot, final PrintWriter pw, final int level ) {
        String liStyleClass = " class=\"navMenuItem-" + level + "\"";
        Iterator itr = menuMap.keySet().iterator();
        while ( itr.hasNext() ) {
            String key = ( String ) itr.next();
            MenuItem menuItem = ( MenuItem ) menuMap.get( key );
            pw.println( "<li" + liStyleClass + ">" + menuItem.getLink() );
            Map subMenu = menuItem.getSubMenu();
            if ( subMenu != null ) {
                renderMenu( subMenu, appRoot, pw, level + 1 );
            }
            pw.println( "</li>" );
        }
    }

    private static final void printLocaleElement(final PrintWriter pw, final String appRoot, final Object langCode, final Object langName ) {
        pw.print("  <img src='");
        pw.print(appRoot);
        pw.print("/res/flags/");
        pw.print(langCode);
        pw.print(".gif' alt='");
        pw.print(langCode);
        pw.print("' title='");
        pw.print(langName);
        pw.println("'/>");
    }

    @SuppressWarnings({ "rawtypes" })
    private static class MenuItem {

        private String link;
        private Map subMenu;

        public MenuItem(final String link ) {
            this.link = link;
        }

        public MenuItem(final String link, final Map subMenu ) {
            this.link = link;
            this.subMenu = subMenu;
        }

        public String getLink() {
            return link;
        }


        public void setLink(final String link ) {
            this.link = link;
        }


        public Map getSubMenu() {
            return subMenu;
        }


        public void setSubMenu(final Map subMenu ) {
            this.subMenu = subMenu;
        }
    }
}
