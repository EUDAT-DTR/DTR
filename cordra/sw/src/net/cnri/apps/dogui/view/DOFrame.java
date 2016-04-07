/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.dobj.DOAuthentication;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DOFrame 
  extends JFrame
  implements ActionListener
{
  protected AdminToolUI appUI;
  protected DOFrame thisFrame;
  
  private HDLAction closeWindowAction;
  private HDLAction minimizeWindowAction;
  
  public DOFrame(AdminToolUI ui) {
    this("", ui);
  }
  
  public DOFrame(String title, AdminToolUI ui) {
    super(title);
    this.appUI = ui;
    this.thisFrame = this;
    
    closeWindowAction = new HDLAction(ui, "close_window", "close_window", this);
    minimizeWindowAction = new HDLAction(ui, "minimize_window", "minimize_window", this);
    
    JMenuBar menuBar = appUI.getAppMenu();
    
    setJMenuBar(menuBar);
    
    getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).
      put(KeyStroke.getKeyStroke('w', KeyEvent.VK_META), new AbstractAction() {
        public void actionPerformed(ActionEvent evt) { setVisible(false); }
      });
    
    addWindowListener(new DOWindowListener());
  }
  
  /** Default implementation of authenticationChanged method.  Windows that
   *  would like notification of authentication changes should override this 
   *  method. */
  public void authenticationChanged(DOAuthentication newAuth) {
    // do nothing... this is for subclasses that would like auth change notification
  }
  
  private class DOWindowListener 
    implements WindowListener
  {
    
    public void windowActivated(WindowEvent e) {
    }
    
    public void windowDeactivated(WindowEvent e) {
    }
    
    public void windowClosed(WindowEvent e) {
      appUI.removeWindow(thisFrame);
    }
    
    public void windowOpened(WindowEvent e) {
      appUI.addWindow(thisFrame);
    }
    
    
    public void windowClosing(WindowEvent e) { 
      appUI.removeWindow(thisFrame);
    }
    
    public void windowDeiconified(WindowEvent e) { }
    
    public void windowIconified(WindowEvent e) { }
    
  }
  
  
  protected void setWindowLocation() {
    pack();
    Dimension prefSz = getPreferredSize();
    setSize(Math.max(450, prefSz.width), prefSz.height + 400);
    setLocationRelativeTo(null);
  }
  
  
  public void actionPerformed(ActionEvent evt) {
    if(closeWindowAction.matchesCommand(evt)) {
      setVisible(false);
    } else if(minimizeWindowAction.matchesCommand(evt)) {
      setState(ICONIFIED);
    }
  }
  

}
