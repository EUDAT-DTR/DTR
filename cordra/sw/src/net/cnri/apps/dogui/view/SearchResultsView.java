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
import net.cnri.dobj.*;
import net.cnri.guiutil.*;
import net.cnri.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class SearchResultsView 
  extends JPanel
{
  private static final String SEARCH_OP_ID = "1037/search";
  
  private JLabel groupByType;
  private JLabel groupByDate;
  private JLabel groupByOwner;
  
  private JLabel sortByType;
  private JLabel sortByDate;
  private JLabel sortByName;
  
  private Font normalFont;
  private Font boldFont;
  
  private AdminToolUI ui;
  private SearchWindow searchWin;
  private SearchResultsList resultsList;
  private JPanel infoPanel;
  private Searcher searchers[] = {};
  private int numResults = -1;
  private int numSearches = 0;
  
  public SearchResultsView(AdminToolUI ui, SearchWindow searchWin) {
    super(new GridBagLayout());
    this.ui = ui;
    this.searchWin = searchWin;
    
    resultsList = new SearchResultsList(ui);
    infoPanel = new JPanel(new GridBagLayout());
    infoPanel.setBackground(new Color(0xFAFAFA));
    infoPanel.setBorder(new javax.swing.border.EmptyBorder(4,4,4,4));
    
    int y = 0;
    
    infoPanel.add(new JLabel(ui.getStr("group_by")+": "),
                  GridC.getc(0,y++).west());
    infoPanel.add(groupByType = new JLabel(ui.getStr("type")),
                  GridC.getc(0,y++).insets(0,10,0,0).west());
    infoPanel.add(groupByDate = new JLabel(ui.getStr("date")),
                  GridC.getc(0,y++).insets(0,10,0,0).west());
    infoPanel.add(groupByOwner = new JLabel(ui.getStr("owner")),
                  GridC.getc(0,y++).insets(0,10,20,0).west());
    
    infoPanel.add(new JLabel(ui.getStr("sort_group_by")+": "),
                  GridC.getc(0,y++).west());
    infoPanel.add(sortByType = new JLabel(ui.getStr("type")),
                  GridC.getc(0,y++).insets(0,10,0,0).west());
    infoPanel.add(sortByDate = new JLabel(ui.getStr("date")),
                  GridC.getc(0,y++).insets(0,10,0,0).west());
    infoPanel.add(sortByName = new JLabel(ui.getStr("object_name")),
                  GridC.getc(0,y++).insets(0,10,0,0).west());
    infoPanel.add(Box.createVerticalStrut(10),
                  GridC.getc(0,y++).wy(1));
    
    normalFont = infoPanel.getFont().deriveFont(Font.PLAIN);
    boldFont = normalFont.deriveFont(Font.BOLD);
    
    MouseAdapter groupByAdapter = new MouseAdapter() {
      public void mouseClicked(MouseEvent evt) {
        Object src = evt==null ? groupByDate : evt.getSource();
        groupByType.setFont(normalFont);
        groupByDate.setFont(normalFont);
        groupByOwner.setFont(normalFont);
        ((JLabel)src).setFont(boldFont);
        
        if(src==groupByType) resultsList.groupBy(SearchResultsList.GROUP_BY_TYPE);
        else if(src==groupByDate) resultsList.groupBy(SearchResultsList.GROUP_BY_DATE);
        else if(src==groupByOwner) resultsList.groupBy(SearchResultsList.GROUP_BY_OWNER);
      }
    };
    groupByOwner.addMouseListener(groupByAdapter);
    groupByType.addMouseListener(groupByAdapter);
    groupByDate.addMouseListener(groupByAdapter);
    
    MouseAdapter sortByAdapter = new MouseAdapter() {
      public void mouseClicked(MouseEvent evt) {
        Object src = evt==null ? sortByDate : evt.getSource();
        sortByType.setFont(normalFont);
        sortByDate.setFont(normalFont);
        sortByName.setFont(normalFont);
        ((JLabel)src).setFont(boldFont);
        
        if(src==sortByType) {
          resultsList.sortBy(SearchResultsList.SORT_BY_TYPE);
        } else if(src==sortByDate) {
          resultsList.sortBy(SearchResultsList.SORT_BY_DATE);
        } else if(src==sortByName) {
          resultsList.sortBy(SearchResultsList.SORT_BY_NAME);
        }
      }
    };
    
    sortByDate.addMouseListener(sortByAdapter);
    sortByType.addMouseListener(sortByAdapter);
    sortByName.addMouseListener(sortByAdapter);
    
    groupByAdapter.mouseClicked(null);
    sortByAdapter.mouseClicked(null);
    add(resultsList, GridC.getc(0,0).wxy(1,1).fillboth());
    add(infoPanel, GridC.getc(1,0).filly());
  }
  
  
  public int getNumResults() {
    return resultsList.getResultCount();
  }
  
  
  public boolean getPendingSearches() {
    return numSearches>0;
  }
  
  
  /** Begin processing the new search */
  public synchronized void performSearch(DOSearch search, Repository workspace) {
    for(int i=0; i<searchers.length; i++)
      searchers[i].keepGoing = false;
    
    resultsList.clearResults();
    numResults = 0;
    
    DigitalObject userObj = ui.getUserObject();
    String indexIDs[] = null;
    
    ArrayList indexes = new ArrayList();
    if(workspace!=null) {
      indexes.add(workspace);
    } else {
//      try {
//        indexes.add(ui.getRepository(AdminToolUI.LOCAL_INDEX));
//      } catch (Exception e) {
//        System.err.println("Error getting interface to local index: "+e);
//      }
      
      try {
        indexIDs =
        StringUtils.split(userObj.getAttribute(AdminToolUI.INDEXES_ATT, "").trim(),' ');
        for(int i=0; i<indexIDs.length; i++) {
          try {
            String indexID = indexIDs[i].trim();
            if(indexID.equals("")) continue;
            indexes.add(ui.getRepositoryByID(indexID));
          } catch (Exception e) {
            System.err.println("Error getting connection to index: "+e);
          }
        }
      } catch (Exception e) {
        System.err.println("Error getting search indexes: "+indexIDs);
        indexIDs = new String[]{};
      }
    }
      
    searchers = new Searcher[indexes.size()];
    for(int i=0; i<indexes.size(); i++) {
      searchers[i] = new Searcher(search, (Repository)indexes.get(i));
    }
    numSearches = searchers.length;

    for(int i=0; i<searchers.length; i++) {
      searchers[i].start();
    }
  }
  
  private Runnable numResultsUpdater = new Runnable() {
    public void run() {
      searchWin.updateNumResults();
    }
  };
    
  class Searcher
    extends Thread
  {
    boolean keepGoing = true;
    DOSearch search;
    DigitalObject index;
    int count = 0;
    
    public Searcher(DOSearch search, DigitalObject index) {
      this.search = search;
      this.index = index;
    }
    
    
    public void run() {
      if(!keepGoing) return;
      System.err.println("Searching for "+search+" in "+index);
      StreamPair io = null;
      try {
        // search the local index...
        if(!keepGoing) return;
        
        HeaderSet params = new HeaderSet();
        params.addHeader("query", search.toString());
        io = index.performOperation(SEARCH_OP_ID, params);
        if(!keepGoing) return;
        
        BufferedInputStream in = new BufferedInputStream(io.getInputStream());
        while(true) {
          HeaderSet info = new HeaderSet();
          if(!info.readHeaders(in)) break;
          count++;
          if(!keepGoing) return;
          numResults++;
          resultsList.addResult(new DOSearchResult(info));
          SwingUtilities.invokeLater(numResultsUpdater);
        }
        
      } catch (Exception e) {
        System.err.println("Error searching "+index+" for '"+search+"': "+e);
      } finally {
        try { io.close(); } catch (Throwable t) {}
        if(keepGoing) {
          numSearches--;
          SwingUtilities.invokeLater(numResultsUpdater);
        }
        System.err.println("Found "+count+" results from "+index);
      }
    }
    
  }
}
