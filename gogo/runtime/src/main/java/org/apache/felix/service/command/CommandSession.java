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
package org.apache.felix.service.command;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

public interface CommandSession extends AutoCloseable
{

    /*
     * Variable name to disable glob (filename) expansion
     */
    String OPTION_NO_GLOB = "gogo.option.noglob";

    Path currentDir();

    void currentDir(Path path);

    ClassLoader classLoader();

    void classLoader(ClassLoader classLoader);

    /**
     * Execute a program in this session.
     *
     * @param commandline the commandline
     * @return the result of the execution
     * @throws Exception on exception
     */
    Object execute(CharSequence commandline) throws Exception;

    /**
     * Close this command session. After the session is closed, it will throw
     * IllegalStateException when it is used.
     */
    void close();

    /**
     * Return the input stream that is the first of the pipeline. This stream is
     * sometimes necessary to communicate directly to the end user. For example,
     * a "less" or "more" command needs direct input from the keyboard to
     * control the paging.
     *
     * @return InpuStream used closest to the user or null if input is from a
     *         file.
     */
    InputStream getKeyboard();

    /**
     * Return the PrintStream for the console. This must always be the stream
     * "closest" to the user. This stream can be used to post messages that
     * bypass the piping. If the output is piped to a file, then the object
     * returned must be null.
     *
     * @return PrintStream the console print stream
     */
    PrintStream getConsole();

    /**
     * Get the value of a variable.
     *
     * @param name the name
     * @return Object
     */
    Object get(String name);

    /**
     * Set the value of a variable.
     *
     * @param name  Name of the variable.
     * @param value Value of the variable
     * @return Object
     */
    Object put(String name, Object value);

    /**
     * Convert an object to string form (CharSequence). The level is defined in
     * the Converter interface, it can be one of INSPECT, LINE, PART. This
     * function always returns a non null value. As a last resort, toString is
     * called on the Object.
     *
     * @param target the target
     * @param level the level
     * @return CharSequence
     */
    CharSequence format(Object target, int level);

    /**
     * Convert an object to another type.
     * @param type the type
     * @param instance the instance
     * @return Object
     */
    Object convert(Class<?> type, Object instance);
    
    /**
     * When this session is stopped, execute the runnable.
     * @param runnable the runnable to run
     */
    void onClose( Runnable runnable);

    //
    // Job support
    //

    /**
     * List jobs. Always return a non-null list.
     * @return List&lt;Job&gt;
     */
    List<Job> jobs();

    /**
     * Get the current foreground job or null.
     * @return Job
     */
    Job foregroundJob();

    /**
     * Set the job listener for this session.
     * @param listener the listener
     */
    void setJobListener(JobListener listener);

    /**
     * Return the current session.
     * Available inside from a command call.
     */
    class Utils {
        public static CommandSession current() {
            Job j = Job.Utils.current();
            return j != null ? j.session() : null;
        }
    }

}
