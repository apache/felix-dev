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

/***
 * Java TelnetD library (embeddable telnet daemon)
 * Copyright (c) 2000-2005 Dieter Wimberger
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * <p/>
 * Neither the name of the author nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER AND CONTRIBUTORS ``AS
 * IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ***/

package org.apache.felix.gogo.jline.telnet;


/**
 * Interface to be implemented if a class wants to
 * qualify as a ConnectionListener.<br>
 * Note that a Shell is per contract also forced to
 * implement this interface.
 *
 * @author Dieter Wimberger
 * @version 2.0 (16/07/2006)
 * @see ConnectionEvent
 */
public interface ConnectionListener {

    /**
     * Called when a CONNECTION_IDLE event occurred.
     *
     * @param ce ConnectionEvent instance.
     * @see ConnectionEvent.Type#CONNECTION_IDLE
     */
    void connectionIdle(ConnectionEvent ce);

    /**
     * Called when a CONNECTION_TIMEDOUT event occurred.
     *
     * @param ce ConnectionEvent instance.
     * @see ConnectionEvent.Type#CONNECTION_TIMEDOUT
     */
    void connectionTimedOut(ConnectionEvent ce);

    /**
     * Called when a CONNECTION_LOGOUTREQUEST occurred.
     *
     * @param ce ConnectionEvent instance.
     * @see ConnectionEvent.Type#CONNECTION_LOGOUTREQUEST
     */
    void connectionLogoutRequest(ConnectionEvent ce);

    /**
     * Called when a CONNECTION_BREAK event occurred.
     *
     * @param ce ConnectionEvent instance.
     * @see ConnectionEvent.Type#CONNECTION_BREAK
     */
    void connectionSentBreak(ConnectionEvent ce);

    /**
     * Called when a CONNECTION_TERMINAL_GEOMETRY_CHANGED event occurred.
     *
     * @param ce ConnectionEvent instance.
     * @see ConnectionEvent.Type#CONNECTION_TERMINAL_GEOMETRY_CHANGED
     */
    void connectionTerminalGeometryChanged(ConnectionEvent ce);

}//interface ConnectionListener