/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view.util;

/** Listener to catch clicks on JLinkLabel hypertext-style links */
public interface JLinkListener {
  
  public void linkActivated(Object target, java.awt.event.InputEvent evt);
  
}
