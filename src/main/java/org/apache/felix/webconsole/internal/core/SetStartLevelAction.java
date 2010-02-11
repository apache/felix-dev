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


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.Action;
import org.apache.felix.webconsole.WebConsoleUtil;
import org.apache.felix.webconsole.internal.BaseManagementPlugin;
import org.osgi.service.startlevel.StartLevel;


/**
 * The <code>SetStartLevelAction</code> TODO
 */
public class SetStartLevelAction extends BaseManagementPlugin implements Action
{

    public static final String NAME = "setStartLevel";

    public static final String LABEL = "Set Start Level";


    public String getName()
    {
        return NAME;
    }


    public String getLabel()
    {
        return LABEL;
    }


    public boolean performAction( HttpServletRequest request, HttpServletResponse response )
    {

        StartLevel sl = getStartLevel();
        if ( sl != null )
        {
            int bundleSL = WebConsoleUtil.getParameterInt( request, "bundleStartLevel", -1 );
            if ( bundleSL > 0 && bundleSL != sl.getInitialBundleStartLevel() )
            {
                sl.setInitialBundleStartLevel( bundleSL );
            }

            int systemSL = WebConsoleUtil.getParameterInt( request, "systemStartLevel", -1 );
            if ( systemSL > 0 && systemSL != sl.getStartLevel() )
            {
                sl.setStartLevel( systemSL );
            }
        }

        return true;
    }


}
