/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;

import java.util.ArrayList;
import java.util.Iterator;

public abstract class DNASynchronizer {
  private static final int WAIT_SECS = 5;
  
  public static final int STATE_STOPPED = 0;
  public static final int STATE_RUNNING = 1;
  public static final int STATE_BROKEN = 2;
  
  private SyncThread runner = null;
  private Object threadStateLock = new Object();
  private boolean keepRunning = true;
  private boolean isRunning = true;
  private int state = STATE_STOPPED;
  private float progress = 0f;
  private String statusMsg = "";
  private ArrayList listeners = new ArrayList();
  
  
  /** Copy all digital objects listed in the sourceList from the given source
    * to the destination. */
  public DNASynchronizer() {
    this.runner = new SyncThread();
    this.runner.setDaemon(true);
    this.runner.start();
  }
  
  public int getState() { return this.state; }
  public float getProgress() { return this.progress; }
  public String getMessage() { return this.statusMsg; }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(runner);
    switch(getState()) {
      case STATE_STOPPED:
        sb.append(" status=stopped; ");
        break;
      case STATE_RUNNING:
        sb.append(" status=running; ");
        break;
      case STATE_BROKEN:
        sb.append(" status=broken; ");
        break;
      default:
        sb.append(" status=UNDEFINED! ");
        break;
    }
    sb.append("progress=");
    sb.append(progress);
    sb.append("; message=");
    sb.append(statusMsg);
    return sb.toString();
  }
  
  /** Set the name of the thread */
  public void setThreadName(String threadName) {
    this.runner.setName(String.valueOf(threadName));
  }
  
  public void start() {
    synchronized(threadStateLock) {
      if(!keepRunning)
        throw new RuntimeException("Error:  synchronizer has been killed - cannot restart");
      if(state==STATE_RUNNING) return; // already running
      changeState(STATE_RUNNING, -1, statusMsg);
    }
  }
  
  public void stop() {
    synchronized(threadStateLock) {
      if(!keepRunning) return; // already killed
      if(state==STATE_STOPPED || state==STATE_BROKEN) return; // already stopped
      changeState(STATE_STOPPED, -1, null);
    }
  }
  
  /** Tell the worker flag that it is time to finish up */
  public void kill() {
    keepRunning = false;
    changeState(STATE_STOPPED, -1, "Synchronizer was killed");
  }
  
  /** Return whether or not this synchronizer has been killed.  If it has
    * been killed, it can't restart. */
  public boolean hasBeenKilled() {
    return !keepRunning;
  }
  
  /** Return whether or not the worker thread has actually completed */
  public boolean isRunning() {
    return this.isRunning;
  }
  
  private class SyncThread
    extends Thread
  {
    
    public void run() {
      boolean isBroken = false;
      try {
        while(keepRunning) {
          if(state==STATE_RUNNING)
            performSynchronizationTask();
          
          if(!keepRunning) break;
          waitABit();
        }
      } catch (Throwable t) {
        isBroken = true;
        t.printStackTrace();
        changeState(STATE_BROKEN, -1, String.valueOf(t));
      } finally {
        keepRunning = false;
        isRunning = false;
        if(!isBroken) changeState(STATE_STOPPED, -1, null);
      }
    }
  }
  
  /** Perform synchronization.  This is called from a separate thread.  If this
    * is a long-running method then it should occasionally call getState() to see
    * if it should keep running. */
  abstract void performSynchronizationTask();
  
  
  protected final void updateStatus(float progress, String statusMsg) {
    boolean valuesChanged = false;
    synchronized(threadStateLock) {
      if(this.progress!=progress) {
        valuesChanged = true;
        this.progress = progress;
      }
      if((this.statusMsg==null && statusMsg!=null) ||
         (this.statusMsg!=null && statusMsg==null) ||
         (this.statusMsg!=null && statusMsg!=null && !this.statusMsg.equals(statusMsg))) {
        valuesChanged = true;
        this.statusMsg = statusMsg;
      }
    }
    if(!valuesChanged) return; // no notification needed
    
    for(Iterator it=listeners.iterator(); it.hasNext(); ) {
      try {
        ((SynchronizerStatusListener)it.next()).statusUpdated(this); 
      } catch (Throwable t) {
        t.printStackTrace(System.err);
      }
    }
    
  }
  
  protected final void changeState(int nextState, float progress, String statusMsg) {
    boolean valuesChanged = false;
    synchronized(threadStateLock) {
      if(this.progress!=progress) {
        valuesChanged = true;
        this.progress = progress;
      }
      if((this.statusMsg==null && statusMsg!=null) ||
         (this.statusMsg!=null && statusMsg==null) ||
         (this.statusMsg!=null && statusMsg!=null && !this.statusMsg.equals(statusMsg))) {
        valuesChanged = true;
        this.statusMsg = statusMsg;
      }
      if(this.state!=nextState) {
        valuesChanged = true;
        this.state = nextState;
      }
    }
    if(!valuesChanged) return; // no notification needed
    for(Iterator it=listeners.iterator(); it.hasNext(); ) {
      try {
        ((SynchronizerStatusListener)it.next()).stateUpdated(this); 
      } catch (Throwable t) {
        t.printStackTrace(System.err);
      }
    }
  }

  private void waitABit() {
    try {
      synchronized(threadStateLock) {
        threadStateLock.wait(WAIT_SECS * 1000);
      }
    } catch (Throwable t) { }
  }
  
  /** Add a listener that will be notified of changes in the status of the 
    * synchronizer. */
  public void addListener(SynchronizerStatusListener l) {
    listeners.add(l);
  }

  /** Remove a listener so that it will no longer be notified of changes in the
    * status of the  synchronizer. */
  public void removeListener(SynchronizerStatusListener l) {
    listeners.remove(l);
  }
}
