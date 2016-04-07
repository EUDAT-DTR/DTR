/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/


package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.guiutil.*;
import net.cnri.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class UserInfoPanel 
  extends JPanel
  implements ActionListener
{
  private AdminToolUI ui;
  
  private JTextField repoField = new JTextField("", 35);
  private JTextField queryField = new JTextField("", 35);
  private DefaultListModel indexes = new DefaultListModel();
  private JList indexList = new JList(indexes);
  private JPasswordField passField1 = new JPasswordField();
  private JPasswordField passField2 = new JPasswordField();
  
  private HDLAction addIndexAction;
  private HDLAction delIndexAction;
  
  public UserInfoPanel(AdminToolUI ui) {
    super(new GridBagLayout());
    this.ui = ui;
    
    addIndexAction = new HDLAction(ui, "add", "add_idx", this);
    delIndexAction = new HDLAction(ui, "delete", "delete_idx", this);
    
    int y = 0;
    add(new JLabel(ui.getStr("net_repo_id")+": "), GridC.getc(0,y).label());
    add(repoField, GridC.getc(1,y++).field());

    add(new JLabel(ui.getStr("sync_query")+": "), GridC.getc(0,y).label());
    add(queryField, GridC.getc(1,y++).field());
    
    add(new JLabel(ui.getStr("indexes")+": "), GridC.getc(0,y).label().north());
    JPanel idxP = new JPanel(new GridBagLayout());
    idxP.add(new JScrollPane(indexList), GridC.getc(0,0).wxy(1,1).fillboth().rowspan(3));
    idxP.add(new JButton(addIndexAction), GridC.getc(1,0).insets(0,12,12,0).fillx());
    idxP.add(new JButton(delIndexAction), GridC.getc(1,1).insets(0,12,42,0).fillx());
    add(idxP, GridC.getc(1,y++).field().wy(1).fillboth());
  }
  
  public void loadSettingsFromUser(DigitalObject userObj) 
    throws Exception
  {
    // clear any previous values
    repoField.setText("");
    queryField.setText("");
    indexes.removeAllElements();
    
    repoField.setText(userObj.getAttribute(AdminToolUI.HOME_REPO_ATT, ""));
    queryField.setText(userObj.getAttribute(AdminToolUI.SYNC_QUERY_ATT, ""));
    String tmp[] =
      StringUtils.split(userObj.getAttribute(AdminToolUI.INDEXES_ATT, ""), ' ');
    for(int i=0; i<tmp.length; i++) {
      if(tmp[i].trim().length()>0)
        indexes.addElement(tmp[i].trim());
    }
  }
  
  public void saveSettingsForUser(DigitalObject userObj) 
    throws Exception
  {
    userObj.setAttribute(AdminToolUI.HOME_REPO_ATT, repoField.getText().trim());
    userObj.setAttribute(AdminToolUI.SYNC_QUERY_ATT, queryField.getText().trim());
    StringBuffer sb = new StringBuffer();
    for(int i=0; i<indexes.getSize(); i++) {
      if(i!=0) sb.append(' ');
      sb.append(String.valueOf(indexes.get(i)).trim());
    }
    userObj.setAttribute(AdminToolUI.INDEXES_ATT, sb.toString());
  }
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(addIndexAction.matchesCommand(evt)) {
      String newIdx = JOptionPane.showInputDialog(this, ui.getStr("enter_index_id"));
      if(newIdx!=null) {
        indexes.addElement(newIdx);
      }
    } else if(delIndexAction.matchesCommand(evt)) {
      int selRow = indexList.getSelectedIndex();
      if(selRow>=0) indexes.remove(selRow);
    }
  }
  
}
