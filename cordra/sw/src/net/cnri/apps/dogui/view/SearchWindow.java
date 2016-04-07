/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.apps.dogui.controller.*;

import net.cnri.do_api.*;
import net.cnri.guiutil.GridC;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.util.HashMap;

public class SearchWindow 
  extends DOFrame
{
  private JButton toggleSearchButton;
  private JLabel numResultsLabel;
  private JComboBox searchTargetBox;
  private DetailedSearchView detailSearchPanel;
  private BasicSearchView basicSearchPanel;
  private SearchResultsView resultsPanel;
  private JProgressBar progressBar;
  private JButton searchButton;
  
  public SearchWindow(AdminToolUI ui) {
    super("", ui);
    
    basicSearchPanel = new BasicSearchView(ui);
    detailSearchPanel = new DetailedSearchView(ui);
    resultsPanel = new SearchResultsView(ui, this);
    toggleSearchButton = new JButton(" ");
    searchButton = new JButton(appUI.getStr("search"));
    searchTargetBox = new JComboBox(new String[] {
                                    ui.getStr("default_workspace"), 
                                    "cnri.test.dor1/dor1",
                                    "cnri.test.dor2/dor2",
                                    "cnri.test.dor3/dor3",
                                    ui.getStr("other_workspace")
                                    });
    numResultsLabel = new JLabel(" ");
    progressBar = new JProgressBar();
    progressBar.setVisible(false);
    
    DigitalObject userObj = appUI.getUserObject();
    String gateway = null;
    try {
      gateway = userObj.getAttribute("do.gateway", null);
    } catch (Exception e) { }
    if(gateway!=null) {
      searchTargetBox.setEditable(true);
      searchTargetBox.setSelectedItem(gateway);
    }
    
    JPanel p = new JPanel(new GridBagLayout());
    p.add(new JLabel(appUI.getStr("gateway")+":"), GridC.getc(0,0).label().insets(10,10,2,2));
    if(gateway==null) {
      p.add(searchTargetBox, GridC.getc(1,0).field().insets(10,2,2,5));
    } else {
      p.add(new JLabel(userObj.getID() + " @ " +gateway), GridC.getc(1,0).field().insets(10,2,2,5));
    }
    p.add(searchButton, GridC.getc(2,1).north().insets(10,5,0,10));
    p.add(basicSearchPanel, GridC.getc(0,1).wx(1).colspan(2).fillboth().insets(10,10,10,5));
    p.add(detailSearchPanel, GridC.getc(0,1).wx(1).colspan(2).fillboth().insets(10,10,10,5));
    
    JPanel tmp = new JPanel(new GridBagLayout());
    tmp.add(toggleSearchButton, GridC.getc(0,0));
    tmp.add(Box.createHorizontalStrut(10), GridC.getc(1,0).wx(1));
    tmp.add(progressBar, GridC.getc(2,0));
    tmp.add(Box.createHorizontalStrut(10), GridC.getc(3,0).wx(1));
    tmp.add(numResultsLabel, GridC.getc(4,0));
    p.add(tmp, GridC.getc(0,2).fillx().colspan(3).insets(0,10,10,10));
    p.add(resultsPanel, GridC.getc(0,3).wxy(1,2).colspan(3).fillboth());
    
    detailSearchPanel.setVisible(false);
    
    basicSearchPanel.setVisible(true);
    detailSearchPanel.setVisible(false);
    toggleSearchButton.setText(appUI.getStr("detailed_search"));
    HashMap textAtts = new HashMap();
    textAtts.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
    textAtts.put(TextAttribute.FOREGROUND, Color.blue);
    
    toggleSearchButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        if(detailSearchPanel.isVisible()) {
          showBasicSearch();
        } else if(basicSearchPanel.isVisible()) {
          showDetailedSearch();
        }
      }
    });
    
    searchButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        performSearch();
      }
    });
    
    searchTargetBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent evt) {
        searchTargetBoxUpdated();
      }
    });
    
    p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).
      put(KeyStroke.getKeyStroke('w', KeyEvent.VK_META), new AbstractAction() {
        public void actionPerformed(ActionEvent evt) { setVisible(false); }
      });
    
    
    getContentPane().add(p, BorderLayout.CENTER);
    getRootPane().setDefaultButton(searchButton);

    p.setTransferHandler(appUI.getDepositDropHandler());
    
    
    pack();
    Dimension prefSz = getPreferredSize();
    setSize(Math.max(450, prefSz.width), prefSz.height + 250);
    setLocationRelativeTo(null);
  }

  private void searchTargetBoxUpdated() {
    int selIdx = searchTargetBox.getSelectedIndex();
    if(selIdx==0) {
      searchTargetBox.setEditable(false);
    } else {
      if(String.valueOf(searchTargetBox.getSelectedItem()).equals(appUI.getStr("other_workspace"))) {
        searchTargetBox.setSelectedItem(appUI.getRepoID(AdminToolUI.LOCAL_INDEX));
      }
      searchTargetBox.setEditable(true);
    }
  } 
    
  void updateNumResults() {
    int numResults = resultsPanel.getNumResults();
    if(numResults<0) {
      numResultsLabel.setText(" ");
    } else {
      numResultsLabel.setText("Total Results: "+numResults);
    }
    if(!resultsPanel.getPendingSearches()) {
      progressBar.setVisible(false);
      progressBar.setIndeterminate(false);
    }
  }
  
  
  private void performSearch() {
    if(detailSearchPanel.isVisible()) {
      performSearch(detailSearchPanel.getSearch());
    } else {
      performSearch(basicSearchPanel.getSearch());
    }
  }
  
  
  public void performSearch(DOSearch search) {
    String searchStr = search.toString();
    setTitle(searchStr);
    
    String workspaceID = null;
    Repository workspace = null;
    if(searchTargetBox.getSelectedIndex()!=0) {
      workspaceID = String.valueOf(searchTargetBox.getSelectedItem());
      workspace = appUI.getRepositoryByID(workspaceID);
    }
    
    
    String directObjectID = null;
    if(searchStr.indexOf(' ')<0 && searchStr.startsWith("hdl:")) {
      try {
        searchStr = searchStr.substring(4);
        DigitalObject obj = workspace==null ?
          appUI.getObjectReference(null, searchStr) : 
          workspace.getDigitalObject(searchStr);
        
        SearchResultInfoWindow objWin = new SearchResultInfoWindow(resultsPanel, appUI, null, obj);
        objWin.setVisible(true);
        return;
      } catch (Exception e) {
        e.printStackTrace(System.err);
        appUI.showErrorMessage(resultsPanel, "Error opening object by ID: "+e);
      }
    }
    
    progressBar.setIndeterminate(true);
    progressBar.setVisible(true);
    detailSearchPanel.setSearch(search);
    basicSearchPanel.setSearch(search);
    
    resultsPanel.performSearch(search, workspace);
  }
  
  public void showDetailedSearch() {
    detailSearchPanel.setSearch(basicSearchPanel.getSearch());
    basicSearchPanel.setVisible(false);
    detailSearchPanel.setVisible(true);
    toggleSearchButton.setText(appUI.getStr("basic_search"));
    invalidate();
  }
  
  public void showBasicSearch() {
    basicSearchPanel.setSearch(detailSearchPanel.getSearch());
    basicSearchPanel.setVisible(true);
    detailSearchPanel.setVisible(false);
    toggleSearchButton.setText(appUI.getStr("detailed_search"));
    invalidate();
  }
  
}
