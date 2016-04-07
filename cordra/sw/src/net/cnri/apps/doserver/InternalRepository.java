/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;
import net.cnri.do_api.*;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/** This object provides high level access to a digital object repository from
  * within the repository. 
  */
public abstract class InternalRepository 
extends Repository
{
  private final Main mainServer;
  protected final ExecutorService pool;
  
  @Deprecated
  public InternalRepository(Main mainServer) 
    throws DOException
  {
    super(AbstractAuthentication.getAnonymousAuth(),
          mainServer.getServerID());
    this.mainServer = mainServer;
    this.pool = Executors.newCachedThreadPool();
  }
  
  /** Constructor for a repository that interfaces  an object that provides
   * an interface to the with the given authentication information */
  public InternalRepository(Main mainServer, ExecutorService pool) 
    throws DOException
  {
    super(AbstractAuthentication.getAnonymousAuth(),
          mainServer.getServerID());
    this.mainServer = mainServer;
    this.pool = pool;
  }
  
  protected abstract DOServerOperationContext getOpContext(String objectID, 
                                                           String operationID,
                                                           HeaderSet params);
  
  
  public String toString() {
    return "Internal Repository ("+mainServer+")";
  }
  
  
  private class InternalOpRunner
  implements Runnable
  {
    Throwable error = null;
    DOServerOperationContext context = null;
    InputStream in = null;
    OutputStream out = null;
    
    InternalOpRunner(DOServerOperationContext context, InputStream in, OutputStream out) {
      this.context = context;
      this.in = in;
      this.out = out;
    }
    
    public void run() {
      String threadName = Thread.currentThread().getName();
      try {
        Thread.currentThread().setName(context.toString());
        mainServer.performOperation(context, in, out);
      } catch (Throwable t) {
        error = t;
      } finally {
        try { in.close(); } catch (Throwable t) {}
        try { out.close(); } catch (Throwable t) {}
        Thread.currentThread().setName(threadName);
      }
    }
  }
  
  /** Invoke a low-level operation on this object, returning the input and output
   * streams for the operation in a StreamPair.  The caller must be careful to
   * close both streams of the StreamPair when they are no longer used.  For some
   * operations the caller should close the output stream before any input will
   * be received.  */
  public StreamPair performOperation(String objectID, String operationID, HeaderSet parameters) 
    throws DOException, IOException
  {
    final DOServerOperationContext opContext = getOpContext(objectID, operationID, parameters);
    
    final PipedInputStream inputPipeIn = new PipedInputStream();
    final PipedOutputStream inputPipeOut = new PipedOutputStream(inputPipeIn);
    final PipedInputStream outputPipeIn = new PipedInputStream();
    final PipedOutputStream outputPipeOut = new PipedOutputStream(outputPipeIn);
    
    StreamPair io = new StreamPair(inputPipeIn, outputPipeOut);
    try {
      InternalOpRunner runner = new InternalOpRunner(opContext, outputPipeIn, inputPipeOut);
      pool.submit(runner);
      
      HeaderSet response = new HeaderSet();
      if(!response.readHeaders(io.getInputStream())) {
        try { inputPipeIn.close(); } catch (Throwable t) {}
        try { inputPipeOut.close(); } catch (Throwable t) {}
        try { outputPipeIn.close(); } catch (Throwable t) {}
        try { outputPipeOut.close(); } catch (Throwable t) {}
        if(runner.error!=null) {
          if(runner.error instanceof DOException) throw (DOException)runner.error;
          if(runner.error instanceof IOException) throw (IOException)runner.error;
          throw new DOException(DOException.SERVER_ERROR, "Error performing operation",runner.error);
        }
        throw new DOException(DOException.SERVER_ERROR, "Unexpected end of response");
      }
      
      if(response.getStringHeader("status", "").equalsIgnoreCase("error")) {
        try { inputPipeIn.close(); } catch (Throwable t) {}
        try { inputPipeOut.close(); } catch (Throwable t) {}
        try { outputPipeIn.close(); } catch (Throwable t) {}
        try { outputPipeOut.close(); } catch (Throwable t) {}
        throw new DOException(response.getIntHeader("code", DOException.PROTOCOL_ERROR),
                              response.getStringHeader("message",""));
      }
    } catch (Exception e) {
      try { inputPipeIn.close(); } catch (Throwable t) {}
      try { inputPipeOut.close(); } catch (Throwable t) {}
      try { outputPipeIn.close(); } catch (Throwable t) {}
      try { outputPipeOut.close(); } catch (Throwable t) {}
      if(e instanceof DOException) throw (DOException)e;
      if(e instanceof IOException) throw (IOException)e;
      throw new DOException(DOException.SERVER_ERROR, "Error performing operation",e);
    }
    return io;
  }
  
  
  
  /** Internal method to return the underlying connection that is used to 
   * communicate with this Repository. */
  public DOClientConnection getConnection() 
  throws DOException
  {
    throw new DOException(DOException.SERVER_ERROR, 
                          "Whoah... this shouldn't happen.  Check the stack trace.");
  }
  

  
}
