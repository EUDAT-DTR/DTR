/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;


public interface SynchronizerStatusListener {
  
  /** Called when the state (running, stopped, etc) is changed.  The
    * status (progress, message, etc) may also have changed as a result. */
  public void stateUpdated(DNASynchronizer sync);
  
  /** Called when only the status (progress, message, etc) is changed */
  public void statusUpdated(DNASynchronizer sync);
    
}
