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
// DWB8: throw IllegatlStateException if session used after closed (as per rfc132)
// DWB9: there is no API to list all variables: https://www.osgi.org/bugzilla/show_bug.cgi?id=49
// DWB10: add SCOPE support: https://www.osgi.org/bugzilla/show_bug.cgi?id=51
package org.apache.felix.gogo.runtime;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.felix.service.command.Job;
import org.apache.felix.service.command.Job.Status;
import org.apache.felix.service.command.JobListener;
import org.apache.felix.service.command.Process;
import org.apache.felix.gogo.runtime.Pipe.Result;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Converter;
import org.apache.felix.service.command.Function;
import org.apache.felix.service.threadio.ThreadIO;

public class CommandSessionImpl implements CommandSession, Converter
{
   public static final String SESSION_CLOSED = "session is closed";
   public static final String VARIABLES = ".variables";
   public static final String COMMANDS = ".commands";
   public static final String CONSTANTS = ".constants";
   private static final String COLUMN = "%-20s %s\n";

   // Streams and channels
   protected InputStream in;
   protected OutputStream out;
   protected PrintStream pout;
   protected OutputStream err;
   protected PrintStream perr;
   protected Channel[] channels;

   private final CommandProcessorImpl processor;
   protected final ConcurrentMap<String, Object> variables = new ConcurrentHashMap<>();
   private volatile boolean closed;
   private final List<JobImpl> jobs = new ArrayList<>();
   private JobListener jobListener;
   private final List<WeakReference<Runnable>> onClose = new ArrayList<>();

   private final ExecutorService executor;

   private Path currentDir;
   private ClassLoader classLoader;

   protected CommandSessionImpl(CommandProcessorImpl shell, CommandSessionImpl parent)
   {
      this.currentDir = parent.currentDir;
      this.executor = Executors.newCachedThreadPool(ThreadUtils.namedThreadFactory("session"));
      this.processor = shell;
      this.channels = parent.channels;
      this.in = parent.in;
      this.out = parent.out;
      this.err = parent.err;
      this.pout = parent.pout;
      this.perr = parent.perr;
   }

   protected CommandSessionImpl(CommandProcessorImpl shell, InputStream in, OutputStream out, OutputStream err)
   {
      this.currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
      this.executor = Executors.newCachedThreadPool(ThreadUtils.namedThreadFactory("session"));
      this.processor = shell;
      ReadableByteChannel inCh = Channels.newChannel(in);
      WritableByteChannel outCh = Channels.newChannel(out);
      WritableByteChannel errCh = out == err ? outCh : Channels.newChannel(err);
      this.channels = new Channel[] {inCh, outCh, errCh};
      this.in = in;
      this.out = out;
      this.err = err;
      this.pout = out instanceof PrintStream ? (PrintStream) out : new PrintStream(out, true);
      this.perr = out == err ? pout : err instanceof PrintStream ? (PrintStream) err : new PrintStream(err, true);
   }

   ThreadIO threadIO()
   {
      return processor.threadIO;
   }

   public CommandProcessor processor()
   {
      return processor;
   }

   public ConcurrentMap<String, Object> getVariables()
   {
      return variables;
   }

   public Path currentDir()
   {
      return currentDir;
   }

   public void currentDir(Path path)
   {
      currentDir = path;
   }

   public ClassLoader classLoader()
   {
      return classLoader != null ? classLoader : getClass().getClassLoader();
   }

   public void classLoader(ClassLoader classLoader)
   {
      this.classLoader = classLoader;
   }

   public void close()
   {
      if (!this.closed)
      {
         for (WeakReference<Runnable> r : onClose)
         {
            Runnable runnable = r.get();
            if (runnable != null)
               try
               {
                  runnable.run();
               }
               catch (Exception e)
               {
                  // ignore, best effort
               }
         }
         this.closed = true;
         this.processor.closeSession(this);
         executor.shutdownNow();
      }
   }

   public Object execute(CharSequence commandline) throws Exception
   {
      assert processor != null;

      if (closed)
      {
         throw new IllegalStateException(SESSION_CLOSED);
      }

      processor.beforeExecute(this, commandline);

      try
      {
         Closure impl = new Closure(this, null, commandline);
         Object result = impl.execute(this, null);
         processor.afterExecute(this, commandline, result);
         return result;
      }
      catch (Exception e)
      {
         processor.afterExecute(this, commandline, e);
         throw e;
      }
   }

   public InputStream getKeyboard()
   {
      return in;
   }

   public Object get(String name)
   {
      // there is no API to list all variables, so overload name == null
      if (name == null || VARIABLES.equals(name))
      {
         return Collections.unmodifiableSet(variables.keySet());
      }

      if (COMMANDS.equals(name))
      {
         return processor.getCommands();
      }

      if (CONSTANTS.equals(name))
      {
         return Collections.unmodifiableSet(processor.constants.keySet());
      }

      Object val = processor.constants.get(name);
      if (val != null)
      {
         return val;
      }

      val = variables.get("#" + name);
      if (val instanceof Function)
      {
         try
         {
            val = ((Function) val).execute(this, null);
         }
         catch (Exception e)
         {
            // Ignore
         }
         return val;
      }
      else if (val != null)
      {
         return val;
      }

      val = variables.get(name);
      if (val != null)
      {
         return val;
      }

      return processor.getCommand(name, variables.get("SCOPE"));
   }

   public Object put(String name, Object value)
   {
      if (value != null)
      {
         return variables.put(name, value);
      }
      else
      {
         return variables.remove(name);
      }
   }

   public PrintStream getConsole()
   {
      return pout;
   }

   @SuppressWarnings("unchecked")
   public CharSequence format(Object target, int level, Converter escape) throws Exception
   {
      if (target == null)
      {
         return "null";
      }

      if (target instanceof CharSequence)
      {
         return (CharSequence) target;
      }

      for (Converter c : processor.converters)
      {
         CharSequence s = c.format(target, level, this);
         if (s != null)
         {
            return s;
         }
      }

      if (target.getClass().isArray())
      {
         if (target.getClass().getComponentType().isPrimitive())
         {
            if (target.getClass().getComponentType() == boolean.class)
            {
               return Arrays.toString((boolean[]) target);
            }
            else
            {
               if (target.getClass().getComponentType() == byte.class)
               {
                  return Arrays.toString((byte[]) target);
               }
               else
               {
                  if (target.getClass().getComponentType() == short.class)
                  {
                     return Arrays.toString((short[]) target);
                  }
                  else
                  {
                     if (target.getClass().getComponentType() == int.class)
                     {
                        return Arrays.toString((int[]) target);
                     }
                     else
                     {
                        if (target.getClass().getComponentType() == long.class)
                        {
                           return Arrays.toString((long[]) target);
                        }
                        else
                        {
                           if (target.getClass().getComponentType() == float.class)
                           {
                              return Arrays.toString((float[]) target);
                           }
                           else
                           {
                              if (target.getClass().getComponentType() == double.class)
                              {
                                 return Arrays.toString((double[]) target);
                              }
                              else
                              {
                                 if (target.getClass().getComponentType() == char.class)
                                 {
                                    return Arrays.toString((char[]) target);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         target = Arrays.asList((Object[]) target);
      }
      if (target instanceof Collection)
      {
         if (level == Converter.INSPECT)
         {
            StringBuilder sb = new StringBuilder();
            Collection<?> c = (Collection<?>) target;
            for (Object o : c)
            {
               sb.append(format(o, level + 1, this));
               sb.append("\n");
            }
            return sb;
         }
         else
         {
            if (level == Converter.LINE)
            {
               StringBuilder sb = new StringBuilder();
               Collection<?> c = (Collection<?>) target;
               sb.append("[");
               for (Object o : c)
               {
                  if (sb.length() > 1)
                  {
                     sb.append(", ");
                  }
                  sb.append(format(o, level + 1, this));
               }
               sb.append("]");
               return sb;
            }
         }
      }
      if (target instanceof Dictionary)
      {
         Map<Object, Object> result = new HashMap<>();
         for (Enumeration<Object> e = ((Dictionary<Object, Object>) target).keys(); e.hasMoreElements();)
         {
            Object key = e.nextElement();
            result.put(key, ((Dictionary<Object, Object>) target).get(key));
         }
         target = result;
      }
      if (target instanceof Map)
      {
         if (level == Converter.INSPECT)
         {
            StringBuilder sb = new StringBuilder();
            Map<?, ?> c = (Map<?, ?>) target;
            for (Map.Entry<?, ?> entry : c.entrySet())
            {
               CharSequence key = format(entry.getKey(), level + 1, this);
               sb.append(key);
               for (int i = key.length(); i < 20; i++)
               {
                  sb.append(' ');
               }
               sb.append(format(entry.getValue(), level + 1, this));
               sb.append("\n");
            }
            return sb;
         }
         else
         {
            if (level == Converter.LINE)
            {
               StringBuilder sb = new StringBuilder();
               Map<?, ?> c = (Map<?, ?>) target;
               sb.append("[");
               for (Map.Entry<?, ?> entry : c.entrySet())
               {
                  if (sb.length() > 1)
                  {
                     sb.append(", ");
                  }
                  sb.append(format(entry, level + 1, this));
               }
               sb.append("]");
               return sb;
            }
         }
      }
      if (target instanceof Path)
      {
         return target.toString();
      }
      if (level == Converter.INSPECT)
      {
         return inspect(target);
      }
      else
      {
         return target.toString();
      }
   }

   CharSequence inspect(Object b)
   {
      boolean found = false;
      try (Formatter f = new Formatter();)
      {
         Method methods[] = b.getClass().getMethods();
         for (Method m : methods)
         {
            try
            {
               String name = m.getName();
               if (m.getName().startsWith("get") && !m.getName().equals("getClass") && m.getParameterTypes().length == 0
                        && Modifier.isPublic(m.getModifiers()))
               {
                  found = true;
                  name = name.substring(3);
                  m.setAccessible(true);
                  Object value = m.invoke(b, (Object[]) null);
                  f.format(COLUMN, name, format(value, Converter.LINE, this));
               }
            }
            catch (Exception e)
            {
               // Ignore
            }
         }
         if (found)
         {
            return (StringBuilder) f.out();
         }
         else
         {
            return b.toString();
         }
      }
   }

   public Object convert(Class<?> desiredType, Object in)
   {
      return processor.convert(this, desiredType, in);
   }

   public Object doConvert(Class<?> desiredType, Object in)
   {
      if (desiredType == Class.class)
      {
         try
         {
            return Class.forName(in.toString(), true, classLoader());
         }
         catch (ClassNotFoundException e)
         {
            return null;
         }
      }
      return processor.doConvert(desiredType, in);
   }

   public CharSequence format(Object result, int inspect)
   {
      try
      {
         return format(result, inspect, this);
      }
      catch (Exception e)
      {
         return "<can not format " + result + ":" + e;
      }
   }

   public Object expr(CharSequence expr)
   {
      return processor.expr(this, expr);
   }

   public Object invoke(Object target, String name, List<Object> args) throws Exception
   {
      return processor.invoke(this, target, name, args);
   }

   public Path redirect(Path path, int mode)
   {
      return processor.redirect(this, path, mode);
   }

   @Override
   public List<Job> jobs()
   {
      synchronized (jobs)
      {
         return Collections.<Job>unmodifiableList(jobs);
      }
   }

   public static JobImpl currentJob()
   {
      return (JobImpl) Job.Utils.current();
   }

   @Override
   public JobImpl foregroundJob()
   {
      List<JobImpl> jobs;
      synchronized (this.jobs)
      {
         jobs = new ArrayList<>(this.jobs);
      }
      for (JobImpl j : jobs)
      {
         if (j.parent == null && j.status() == Status.Foreground)
         {
            return j;
         }
      }
      return null;
   }

   @Override
   public void setJobListener(JobListener listener)
   {
      synchronized (jobs)
      {
         jobListener = listener;
      }
   }

   public JobImpl createJob(CharSequence command)
   {
      synchronized (jobs)
      {
         int id = 1;

         boolean found;
         do
         {
            found = false;
            for (Job job : jobs)
            {
               if (job.id() == id)
               {
                  found = true;
                  id++;
                  break;
               }
            }
         }
         while (found);

         JobImpl cur = currentJob();
         JobImpl job = new JobImpl(id, cur, command);
         if (cur == null)
         {
            jobs.add(job);
         }
         else
         {
            cur.add(job);
         }
         return job;
      }
   }

   class JobImpl implements Job, Runnable
   {
      private final int id;
      private final JobImpl parent;
      private final CharSequence command;
      private final List<Pipe> pipes = new ArrayList<>();
      private final List<Job> children = new ArrayList<>();
      private Status status = Status.Created;
      private Future<?> future;
      private Result result;

      public JobImpl(int id, JobImpl parent, CharSequence command)
      {
         this.id = id;
         this.parent = parent;
         this.command = command;
      }

      void addPipe(Pipe pipe)
      {
         pipes.add(pipe);
      }

      @Override
      public int id()
      {
         return id;
      }

      public CharSequence command()
      {
         return command;
      }

      @Override
      public synchronized Status status()
      {
         return status;
      }

      @Override
      public synchronized void suspend()
      {
         if (status == Status.Done)
         {
            throw new IllegalStateException("Job is finished");
         }
         if (status != Status.Suspended)
         {
            setStatus(Status.Suspended);
         }
      }

      @Override
      public synchronized void background()
      {
         if (status == Status.Done)
         {
            throw new IllegalStateException("Job is finished");
         }
         if (status != Status.Background)
         {
            setStatus(Status.Background);
         }
      }

      @Override
      public synchronized void foreground()
      {
         if (status == Status.Done)
         {
            throw new IllegalStateException("Job is finished");
         }
         JobImpl cr = CommandSessionImpl.currentJob();
         JobImpl fg = foregroundJob();
         if (parent == null && fg != null && fg != this && fg != cr)
         {
            throw new IllegalStateException("A job is already in foreground");
         }
         if (status != Status.Foreground)
         {
            setStatus(Status.Foreground);
         }
      }

      @Override
      public void interrupt()
      {
         Future<?> future;
         List<Job> children;
         synchronized (this)
         {
            future = this.future;
         }
         synchronized (this.children)
         {
            children = new ArrayList<>(this.children);
         }
         for (Job job : children)
         {
            job.interrupt();
         }
         if (future != null)
         {
            future.cancel(true);
         }
      }

      protected synchronized void done()
      {
         if (status == Status.Done)
         {
            throw new IllegalStateException("Job is finished");
         }
         setStatus(Status.Done);
      }

      private void setStatus(Status newStatus)
      {
         setStatus(newStatus, true);
      }

      private void setStatus(Status newStatus, boolean callListeners)
      {
         Status previous;
         synchronized (this)
         {
            previous = this.status;
            status = newStatus;
         }
         if (callListeners)
         {
            JobListener listener;
            synchronized (jobs)
            {
               listener = jobListener;
               if (newStatus == Status.Done)
               {
                  jobs.remove(this);
               }
            }
            if (listener != null)
            {
               listener.jobChanged(this, previous, newStatus);
            }
         }
         synchronized (this)
         {
            JobImpl.this.notifyAll();
         }
      }

      @Override
      public synchronized Result result()
      {
         return result;
      }

      @Override
      public Job parent()
      {
         return parent;
      }

      /**
       * Start the job. If the job is started in foreground, waits for the job to finish or to be suspended or moved to
       * background.
       *
       * @param status the desired job status
       * @return <code>null</code> if the job has been suspended or moved to background,
       *
       */
      public synchronized Result start(Status status) throws InterruptedException
      {
         if (status == Status.Created || status == Status.Done)
         {
            throw new IllegalArgumentException("Illegal start status");
         }
         if (this.status != Status.Created)
         {
            throw new IllegalStateException("Job already started");
         }
         switch (status)
         {
            case Suspended:
               suspend();
               break;
            case Background:
               background();
               break;
            case Foreground:
               foreground();
               break;
            case Created:
            case Done:
         }
         future = executor.submit(this);
         while (this.status == Status.Foreground)
         {
            JobImpl.this.wait();
         }
         return result;
      }

      public List<Process> processes()
      {
         return Collections.unmodifiableList((List<? extends Process>) pipes);
      }

      @Override
      public CommandSession session()
      {
         return CommandSessionImpl.this;
      }

      public void run()
      {
         Thread thread = Thread.currentThread();
         String name = thread.getName();
         try
         {
            thread.setName("job controller " + id);

            List<Callable<Result>> wrapped = new ArrayList<Callable<Result>>(pipes);
            List<Future<Result>> results = executor.invokeAll(wrapped);

            // Get pipe exceptions
            Exception pipeException = null;
            for (int i = 0; i < results.size() - 1; i++)
            {
               Future<Result> future = results.get(i);
               Throwable e;
               try
               {
                  Result r = future.get();
                  e = r.exception;
               }
               catch (ExecutionException ee)
               {
                  e = ee.getCause();
               }
               if (e != null)
               {
                  if (pipeException == null)
                  {
                     pipeException = new Exception("Exception caught during pipe execution");
                  }
                  pipeException.addSuppressed(e);
               }
            }
            put(Closure.PIPE_EXCEPTION, pipeException);

            result = results.get(results.size() - 1).get();
         }
         catch (Exception e)
         {
            result = new Result(e);
         }
         catch (Throwable t)
         {
            result = new Result(new ExecutionException(t));
         }
         finally
         {
            done();
            thread.setName(name);
         }
      }

      public void add(Job child)
      {
         synchronized (children)
         {
            children.add(child);
         }
      }
   }

   /**
    * Register a runnable that is run when the session is closed. This should be a quick running method.
    * <p>
    * The session will maintain a weak reference to this runnable. This means that if you do not hold a reference to it,
    * it will be garbage collected to prevent class leaks when bundles are stopped. So make sure to hold a strong reference.
    */
   @Override
   public void onClose(Runnable runnable)
   {
      onClose.add(new WeakReference<>(runnable));
   }

}
