/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view.util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** A JLinkLabel is a component that resembles and behaves like a hypertext link */
public class JLinkLabel 
  extends JLabel
  implements MouseListener
{
  private Vector linkListeners = new Vector();
  private Object linkTarget;
  private Color origFG;
  private static final Map fontAttributes;
  static {
    fontAttributes = new HashMap();
    fontAttributes.put(java.awt.font.TextAttribute.UNDERLINE, 
                       java.awt.font.TextAttribute.UNDERLINE_ON);
  }
  
  public JLinkLabel(String text, Object linkTarget, int alignment) {
    super(text, alignment);
    this.linkTarget = linkTarget;
    addMouseListener(this);
  }
  
  public void setFont(Font f) {
    super.setFont(f.deriveFont(fontAttributes));
  }
  
  private void notifyLinkListeners(InputEvent evt) {
    for(Enumeration en=linkListeners.elements(); en.hasMoreElements(); ) {
      try {
        ((JLinkListener)en.nextElement()).linkActivated(linkTarget, evt);
      } catch (Exception e) {
        System.err.println("Exception notifying link listeners: "+e);
        e.printStackTrace(System.err);
      }
    }
  }

  public void addLinkListener(JLinkListener listener) {
    linkListeners.addElement(listener);
  }
  public void removeLinkListener(JLinkListener listener) {
    linkListeners.removeElement(listener);
  }



  private boolean working=false;

  public void mouseClicked(MouseEvent evt) {
    origFG = getForeground();
    setForeground(Color.red);
    repaint();
    working=true;
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    SwingUtilities.invokeLater(new LinkNotifier(evt));
  }

  private class LinkNotifier extends Thread {
    private InputEvent evt;
    LinkNotifier(InputEvent evt) {
      this.evt = evt;
    }
    public void run() {
      repaint();
      notifyLinkListeners(evt);
      setForeground(origFG);
      working=false;
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }
  }
  public void mouseReleased(MouseEvent evt) { }
  public void mousePressed(MouseEvent evt) { }
  public void mouseEntered(MouseEvent evt) {
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  public void mouseExited(MouseEvent evt) {
    if(!working)
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }
}



