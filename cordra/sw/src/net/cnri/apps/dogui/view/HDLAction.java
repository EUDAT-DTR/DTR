/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;

public class HDLAction
  extends AbstractAction
{
  private static final String ICON_DIR = "/net/handle/apps/admintool/view/resources/";
  
  private AdminToolUI ui;
  private String labelKey;
  private ActionListener callback;
  private String cmd;
  private Object targetObject;
  private boolean usesKey;

  public HDLAction(AdminToolUI ui, String labelKey, ActionListener callback) {
    this(ui, labelKey, labelKey, callback);
  }
  
  public HDLAction(AdminToolUI ui, String labelKey, String commandKey) {
    this(ui, labelKey, commandKey, null);
  }
  
  public HDLAction(AdminToolUI ui, String labelKey,
                   String commandKey, ActionListener callback) {
    super(ui==null ? labelKey : ui.getStr(labelKey));
    usesKey = ui!=null;
    this.ui = ui;
    this.labelKey = labelKey;
    this.callback = callback;
    this.cmd = commandKey;
    
    super.putValue(ACTION_COMMAND_KEY, commandKey);
    super.putValue(SMALL_ICON, getIcon(ICON_DIR+labelKey));
    preferencesUpdated();
  }

  public void setTargetObject(Object obj) {
    this.targetObject = obj;
  }
  
  public Object getTargetObject() {
    return targetObject;
  }

  public String getCommand() {
    return cmd;
  }

  public String getName() {
    return String.valueOf(getValue(NAME));
  }

  public void preferencesUpdated() {
    if(usesKey)
      super.putValue(NAME, ui.getStr(labelKey));
  }
  
  public void setMnemonicKey(int key) {
    putValue(MNEMONIC_KEY, new Integer(key));
  }
    
  public void setAccelerator(KeyStroke ks) {
    putValue(ACCELERATOR_KEY, ks);
  }

  public void setShortDescription(String shortDesc) {
    putValue(SHORT_DESCRIPTION, shortDesc);
  }

  public void setAccelerator(String ksString) {
    setAccelerator(KeyStroke.getKeyStroke(ksString));
  }
  
  public ImageIcon getIcon(String path)  {
    URL url = this.getClass().getResource(path);
    if (url != null)  {
      return new ImageIcon(url);
    }
    return null;
  }

  public boolean matchesCommand(ActionEvent evt) {
    if(evt.getSource()==this)  return true;
    if(getCommand().equals(evt.getActionCommand())) return true;
    if(getName().equals(evt.getActionCommand())) return true;
    return false;
  }

  public void actionPerformed(ActionEvent evt) {
    if(callback!=null)
      callback.actionPerformed(evt);
  }
  
  // DEFAULT, LONG_DESCRIPTION

}

