/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.apps.dogui.controller.DOSearch;
import net.cnri.guiutil.GridC;

import javax.swing.*;
import java.awt.*;

public class BasicSearchView 
  extends JPanel
{
  private AdminToolUI ui;
  private JTextField queryField;
  
  public BasicSearchView(AdminToolUI ui) {
    super(new GridBagLayout());
    this.ui = ui;
    queryField = new JTextField("", 35);
    int y = 0;
    add(new JLabel(ui.getStr("keywords")+": "), GridC.getc(0,y).label());
    add(queryField, GridC.getc(1,y++).field());
  }
  
  /** Return the current search parameters as a DOSearch */
  public DOSearch getSearch() {
    return new DOSearch(queryField.getText());
  }
  
  public void setSearch(DOSearch search) {
    if(search==null) {
      queryField.setText("");
    } else {
      queryField.setText(search.toString());
    }
  }
  
}
