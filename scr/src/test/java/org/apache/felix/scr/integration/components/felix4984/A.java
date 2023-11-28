/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.integration.components.felix4984;

import java.util.ArrayList;
import java.util.List;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.log.LogService;

/**
 * @version $Rev: 1350816 $ $Date: 2012-06-15 23:37:30 +0200 (Fri, 15 Jun 2012) $
 */

@SuppressWarnings("unused")
public class A
{

    private List<B> bs = new ArrayList<B>();
    private List<Exception> bsStackTraces = new ArrayList<>();


    private void activate(ComponentContext cc)
    {
    }

    private void setB(B b)
    {
        bs.add( b );
        bsStackTraces.add(new Exception());
    }

    private void unsetB(B b)
    {
        bs.remove( b );
        bsStackTraces.remove(bsStackTraces.size()-1);
    }

    public List<B> getBs()
    {
        return bs;
    }

    @SuppressWarnings("deprecation")
    public void dumpStackTracesWhenBWasBound(LogService log) {
        log.log(LogService.LOG_WARNING, "Stack traces when B was bound:");
        for (Exception e : bsStackTraces) {
            log.log(LogService.LOG_WARNING, "stack trace:", e);
        }
    }

}
