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
import net.cnri.util.ThreadSafeDateFormat;
import net.cnri.awt.AwtUtil;
import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.event.*;
import java.util.*;
import java.io.*;

public class SearchResultsList
  extends JPanel
{
  private static String EXPAND_ICON = "/net/cnri/apps/dogui/view/images/RightArrow.png";
  private static String COLLAPSE_ICON = "/net/cnri/apps/dogui/view/images/DownArrow.png";
  private static String INFO_ICON = "/net/cnri/apps/dogui/view/images/i_in_circle.png";
  private static String LOCK_ICON = "/net/cnri/apps/dogui/view/images/lock_icon.png";
  
  private static ThreadSafeDateFormat dateFmt = new ThreadSafeDateFormat("MMM d yyyy");
  private static ThreadSafeDateFormat timeFmt = new ThreadSafeDateFormat("h:mm a");
  private static ThreadSafeDateFormat longDateFmt = new ThreadSafeDateFormat("d MMM yyyy h:mm a");
  private static HashMap notesFontInfo = new HashMap();
  private static HashMap mimeTypeIcons = new HashMap();
  private static Icon defaultIcon = null;
  private static boolean iconsLoaded = false;
  
  public static final int GROUP_BY_DATE = 0;
  public static final int GROUP_BY_TYPE = 1;
  public static final int GROUP_BY_OWNER = 2;
  
  public static final int SORT_BY_DATE = 0;
  public static final int SORT_BY_TYPE = 1;
  public static final int SORT_BY_NAME = 2;
  
  static {
    notesFontInfo.put(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE);
  }
  
  private AdminToolUI appUI;
  private JPanel resultsPanel;
  private JScrollPane scrollPane;
  private SearchResultInfoPanel infoPanel;
  private ArrayList results = new ArrayList();
  private HashMap resultsMap = new HashMap();
  private ArrayList groups = new ArrayList();
  
  private int groupBy = GROUP_BY_DATE;
  private int sortBy = SORT_BY_DATE;
  
  private Font labelFont = null;
  private Icon expandIcon;
  private Icon collapseIcon;
  private javax.swing.border.Border notesBorder;
  private Component spacer = null;
  
  private String outboxRepoID = "";
  private String cacheRepoID = "";
  private ResultRenderer renderer;
  
  private Comparator dateComparator = new DateComparator();
  private Comparator nameComparator = new NameComparator();
  private Comparator typeComparator = new TypeComparator();
  private Comparator sortComparator = dateComparator;
  
  public SearchResultsList(AdminToolUI ui) {
    super(new GridBagLayout());
    this.appUI = ui;
    
    initIcons(ui.getImages());
    this.renderer = new ResultRenderer();
    setBackground(Color.white);
    resultsPanel = new JPanel(new GridBagLayout());
    resultsPanel.setOpaque(false);
    expandIcon = ui.getImages().getIcon(EXPAND_ICON);
    collapseIcon = ui.getImages().getIcon(COLLAPSE_ICON);
    notesBorder = new javax.swing.border.LineBorder(Color.lightGray, 1);
    
    resultsPanel.add(spacer = Box.createVerticalStrut(1), GridC.getc(0,100).wy(1));
    scrollPane = new JScrollPane(resultsPanel,
                                 JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                 JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    add(scrollPane, GridC.getc(0,0).wxy(1,1).fillboth());
    
    infoPanel = new SearchResultInfoPanel();
  }
  
  
  public void clearResults() {
    resultsPanel.removeAll();
    if(spacer==null) spacer = Box.createVerticalStrut(1);
    resultsPanel.add(spacer, GridC.getc(0,100).wy(1));
    results.clear();
    resultsMap.clear();
    groups.clear();
    outboxRepoID = appUI.getRepoID(AdminToolUI.OUTBOX);
    cacheRepoID = appUI.getRepoID(AdminToolUI.INBOX);
    invalidate();
    repaint();
  }

  public int getResultCount() {
    return results.size();
  }
  
  public void sortBy(int newSortBy) {
    if(newSortBy==sortBy) return; // no change
    switch(newSortBy) {
      case SORT_BY_DATE:
        sortComparator = dateComparator;
        break;
      case SORT_BY_NAME:
        sortComparator = nameComparator;
        break;
      case SORT_BY_TYPE:
        sortComparator = typeComparator;
        break;
      default:
        System.err.println("Invalid sort field: "+newSortBy);
        return;
    }
    
    this.sortBy = newSortBy;
    
    // tell all of the lists to re-sort themselves
    for(int i=groups.size()-1; i>=0; i--) {
      SearchResultGroupView gv = (SearchResultGroupView)groups.get(i);
      gv.sortResults();
    }
  }
  
  
  public void groupBy(int newGroupBy) {
    if(newGroupBy==groupBy) return; // no change
    this.groupBy = newGroupBy;
    
    resultsPanel.removeAll();
    if(spacer==null) spacer = Box.createVerticalStrut(1);
    resultsPanel.add(spacer, GridC.getc(0,100).wy(1));
    groups.clear();
    
    int numItems = results.size();
    for(int i=0; i<numItems; i++) {
      addResultToGUI((DOSearchResult)results.get(i));
    }
    validate();
    repaint();
  }

  

  
  public synchronized void addResult(DOSearchResult result) {
    String id = result.getID();
    if(id==null || id.trim().length()<=0) return; // ignore results without object IDs
    
    DOSearchResult match = (DOSearchResult)resultsMap.get(id);
    if(match!=null) {
      // we already have a result with the same ID
      // if this result is "better" replace the existing result with it
      match.mergeValues(result, isBetterResult(result, match));
      updateResultInGUI(match);
    } else {
      // we don't already have this result.  add it
      resultsMap.put(id, result);
      results.add(result);
      addResultToGUI(result);
    }
  }
  
  private void updateResultInGUI(DOSearchResult result) {
    for(int i=groups.size()-1; i>=0; i--) {
      SearchResultGroupView gv = (SearchResultGroupView)groups.get(i);
      if(gv.containsResult(result)) {
        gv.refreshResult(result);
        break;
      }
    }
  }
  
  
  private void addResultToGUI(DOSearchResult result) {
    if(groups.size()<=0) {
      // initialize the groups
      switch(groupBy) {
        case GROUP_BY_TYPE:
        case GROUP_BY_OWNER:
          break;
        case GROUP_BY_DATE:
        default:
          Calendar cal = Calendar.getInstance();

          SearchResultGroupSpec gs;
          SearchResultGroupView gv;
          gs = new SearchResultGroupSpec();
          gs.startDate = cal.getTime().getTime(); // the future starts now!
          gs.groupName = appUI.getStr("future_items");
          groups.add(gv = new SearchResultGroupView(gs));
          resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
          
          
          gs = new SearchResultGroupSpec();
          gs.endDate = cal.getTime().getTime(); // today ends now. see future above
          cal.set(Calendar.HOUR_OF_DAY, 0);
          cal.set(Calendar.MINUTE, 1);
          cal.set(Calendar.SECOND, 1);
          gs.startDate = cal.getTime().getTime();
          gs.groupName = appUI.getStr("today");
          groups.add(gv = new SearchResultGroupView(gs));
          resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
          
          gs = new SearchResultGroupSpec();
          gs.endDate = cal.getTime().getTime();
          cal.add(Calendar.HOUR, -24);
          gs.startDate = cal.getTime().getTime();
          gs.groupName = appUI.getStr("yesterday");
          groups.add(gv = new SearchResultGroupView(gs));
          resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
          
          gs = new SearchResultGroupSpec();
          gs.endDate = cal.getTime().getTime();
          cal.add(Calendar.DATE, -7);
          gs.startDate = cal.getTime().getTime();
          gs.groupName = appUI.getStr("prev_7_days");
          groups.add(gv = new SearchResultGroupView(gs));
          resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
          
          gs = new SearchResultGroupSpec();
          gs.endDate = cal.getTime().getTime();
          cal.add(Calendar.DATE, -30);
          gs.startDate = cal.getTime().getTime();
          gs.groupName = appUI.getStr("prev_30_days");
          groups.add(gv = new SearchResultGroupView(gs));
          resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
          
          gs = new SearchResultGroupSpec();
          gs.endDate = cal.getTime().getTime();
          gs.startDate = -1;
          gs.groupName = appUI.getStr("earlier");
          groups.add(gv = new SearchResultGroupView(gs));
          resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
      }
    }
    
    boolean foundGroup = false;
    for(int i=groups.size()-1; i>=0; i--) {
      SearchResultGroupView gv = (SearchResultGroupView)groups.get(i);
      if(gv.group.matches(result)) {
        gv.addResult(result);
        foundGroup = true;
        break;
      }
    }
    
    if(!foundGroup) { // add the result to a new group
      SearchResultGroupSpec gs = new SearchResultGroupSpec();
      SearchResultGroupView gv = null;
      switch(groupBy) {
        case GROUP_BY_TYPE:
          gs.mimeTypes = new HashMap();
          gs.mimeTypes.put(result.getContentMimeType(), "");
          gs.groupName = result.getContentMimeType();
          groups.add(gv = new SearchResultGroupView(gs));
          break;
        case GROUP_BY_OWNER:
          gs.creator = result.getOwner();
          gs.groupName = appUI.getAddressBook().getEntityLabel(result.getOwner());
          groups.add(gv = new SearchResultGroupView(gs));
          break;
        default:
          gs.groupName = "None of the above";
          groups.add(gv = new SearchResultGroupView(gs));
      }
      gv.addResult(result);
      resultsPanel.add(gv, GridC.getc(0, groups.size()).wx(1).fillboth());
    }
  }
  
  private boolean isBetterResult(DOSearchResult sr1, DOSearchResult sr2) {
    String r1 = sr1.getRepositoryID();
    String r2 = sr2.getRepositoryID();
    if(r1==null) return false;
    if(r2==null) return true;
    if(r1.equals(outboxRepoID)) return true;
    if(r2.equals(outboxRepoID)) return false;
    if(r1.equals(cacheRepoID)) return true;
    if(r2.equals(cacheRepoID)) return false;
    return false;  // by default, use the first result found
  }
  
  /** Return a DigitalObject interface to the object contained in the
    * given search result.  Displays an error message and returns null
    * if we couldn't connect to the object. */
  public DigitalObject objectForResult(DOSearchResult result) {
    try {
      String resultID = result.getID();
      String repoID = result.getRepositoryID();
      if(repoID==null) {
        repoID = DOClient.resolveRepositoryID(resultID);
      }
      Repository repo = appUI.getRepositoryByID(repoID);
      return repo.getDigitalObject(resultID);
    } catch (Exception e) {
      appUI.showErrorMessage(resultsPanel,
                             appUI.getStr("error_getting_element")+"\n\n  "+e);
      return null;
    }
  }
  
  
  
  private void saveElement(DataElement element, String filename) {
    // ask for the location in which to save this file/element...
    FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), 
                                     appUI.getStr("choose_file"),
                                     FileDialog.SAVE);
    if(filename!=null) {
      File f = new File(filename);
      fwin.setDirectory(f.getParent());
      fwin.setFile(f.getName());
    }
    fwin.setVisible(true);
    String dirStr = fwin.getDirectory();
    String fileStr = fwin.getFile();
    if(dirStr==null || fileStr==null) return;
    File f = new File(dirStr + fileStr);
    
    FileOutputStream fout = null;
    InputStream in = null;
    try {
      in = new ProgressMonitorInputStream(this, appUI.getStr("reading")+": "+
                                          f.getName(), element.read());
      
      // if the element is encrypted, a decrypting cipher stream should be
      // inserted here
      
      fout = new FileOutputStream(f);
      byte buf[] = new byte[10000];
      int r;
      while((r=in.read(buf))>=0) fout.write(buf, 0, r);
      fout.close();
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_getting_element")+"\n\n  "+e);
      return;
    } finally {
      try { in.close(); } catch (Exception e) { }
      try { fout.close(); } catch (Exception e) { }
    }
    
    appUI.showInfoMessage(this, appUI.getStr("file_was_saved_msg")+":\n  "+f.getPath());
  }
  
  private void updateElement(DataElement element, String filename) {
    // ask for the location in which to save this file/element...
    FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), 
                                     appUI.getStr("choose_file"),
                                     FileDialog.LOAD);
    if(filename!=null) {
      File f = new File(filename);
      fwin.setDirectory(f.getParent());
      fwin.setFile(f.getName());
    }
    fwin.setVisible(true);
    String dirStr = fwin.getDirectory();
    String fileStr = fwin.getFile();
    if(dirStr==null || fileStr==null) return;
    File f = new File(dirStr + fileStr);
    
    InputStream in = null;
    long bytesWritten = 0;
    try {
      in = new ProgressMonitorInputStream(this, appUI.getStr("updating")+": "+
                                          f.getName(),
                                          new java.io.FileInputStream(f));
      bytesWritten = element.write(in);
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_updating_element")+"\n\n  "+e);
      return;
    } finally {
      try { in.close(); } catch (Exception e) { }
    }
    
    appUI.showInfoMessage(this, appUI.getStr("obj_was_updated_msg")+":\n  "+f.getPath());
  }
  
  public void showItemDetails(DOSearchResult result) {
    if(result==null) return;
    
    try {
      DigitalObject obj = appUI.getObjectReference(result.getRepositoryID(), result.getID());
      if(obj==null) return;
      ShowObjectWindow objWin = new ShowObjectWindow(appUI);
      objWin.setObject(obj, ShowObjectWindow.VIEW_MODE);
      objWin.setVisible(true);
//      
//      SearchResultInfoWindow infoWin = 
//        new SearchResultInfoWindow(resultsPanel, appUI, result, obj);
//      infoWin.setVisible(true);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      appUI.showErrorMessage(this, String.valueOf(e));
    }
    //infoPanel.showResult(result);
  }
  
  private synchronized JLabel makeLabel(String labelStr) {
    JLabel label = new JLabel(appUI.getStr(labelStr)+": ");
    if(labelFont==null) {
      labelFont = label.getFont().deriveFont(Font.BOLD);
    }
    label.setFont(labelFont);
    return label;
  }
  
  
  class SearchResultInfoPanel
    extends JPanel
    implements ActionListener
  {
    private DOSearchResult result;
    private JButton saveButton;
    private JButton updateButton;
    private JButton deleteButton;
    private JButton closeButton;
    
    SearchResultInfoPanel() {
      super(new GridBagLayout());
      setBorder(new javax.swing.border.EmptyBorder(10,10,10,10));
      //setOpaque(false);
      setBackground(new Color(0f, 0f, 0f, 0.85f));
      setForeground(new Color(1f,1f,1f));
      saveButton = new JButton(appUI.getStr("retrieve"));
      saveButton.addActionListener(this);
      saveButton.setOpaque(false);
      updateButton = new JButton(appUI.getStr("update"));
      updateButton.addActionListener(this);
      updateButton.setOpaque(false);
      deleteButton = new JButton(appUI.getStr("delete"));
      deleteButton.addActionListener(this);
      deleteButton.setOpaque(false);
      closeButton = new JButton(appUI.getStr("close"));
      closeButton.addActionListener(this);
      closeButton.setOpaque(false);
    }
    

    public void actionPerformed(ActionEvent evt) {
      Object src = evt.getSource();
      if(src==saveButton) {
        new Thread("Retrieve content: "+result) {
          public void run() {
            DOSearchResult r = result;
            if(r==null) return;
            DigitalObject dob = objectForResult(r);
            if(dob==null) return;
            try {
              saveElement(dob.getDataElement("content"), 
                          r.getContentFileName());
            } catch (Exception e) {
              appUI.showErrorMessage(resultsPanel,
                                     appUI.getStr("error_getting_element")+"\n\n  "+e);
            }
          }
        }.start();
      } else if(src==updateButton) {
        new Thread("Update content: "+result) {
          public void run() {
            DOSearchResult r = result;
            if(r==null) return;
            DigitalObject dob = objectForResult(r);
            if(dob==null) return;
            try {
              updateElement(dob.getDataElement("content"), 
                            r.getContentFileName());
            } catch (Exception e) {
              appUI.showErrorMessage(resultsPanel,
                                     appUI.getStr("error_getting_element")+"\n\n  "+e);
            }
          }
        }.start();
        
      } else if(src==deleteButton) {
        DigitalObject dob = objectForResult(result);
        if(dob==null) return;
        if(JOptionPane.YES_OPTION!=
           JOptionPane.showConfirmDialog(this, appUI.getStr("confirm_delete_obj"))) {
          return;
        }
        try {
          dob.getRepository().deleteDigitalObject(dob.getID());
          appUI.showInfoMessage(this, appUI.getStr("obj_was_deleted_msg"));
        } catch (Exception e) {
          appUI.showErrorMessage(this, appUI.getStr("error_deleting_obj")+"\n\n  "+e);
        }
      } else if(src==closeButton) {
        hidePane();
      }
    }
    
    public void showResult(DOSearchResult newResult) {
      setResult(newResult);
      showPane();
    }
    
    
    public void hidePane() {
      JFrame f = (JFrame)AwtUtil.getFrame(this);
      if(f==null) return;
      JLayeredPane lp = f.getLayeredPane();
      if(lp!=null && lp.isAncestorOf(this)) {
        lp.remove(this);
      }
      lp.repaint();
    }
    
    
    private void showPane() {
      JFrame f = (JFrame)AwtUtil.getFrame(scrollPane);
      if(f==null) return;
      JLayeredPane lp = f.getLayeredPane();
      if(lp==null) {
        f.setLayeredPane(lp = new JLayeredPane());
      }
      if(!lp.isAncestorOf(this)) {
        lp.add(this, JLayeredPane.MODAL_LAYER);
      }
      Point spPoint = scrollPane.getLocation();
      spPoint = SwingUtilities.convertPoint(scrollPane,
                                            spPoint,
                                            lp);
      Dimension myDim = getPreferredSize();
      Dimension spDim = scrollPane.getSize();
      int xdiff = Math.max(0, spDim.width - myDim.width);
      int ydiff = Math.max(0, spDim.height - myDim.height);
      setBounds(spPoint.x + xdiff/2, spPoint.y + ydiff/2,
                Math.min(spDim.width, myDim.width), 
                Math.min(spDim.height, myDim.height));
      
      lp.repaint();
    }
    
    
    public void setResult(DOSearchResult newResult) {
      this.result = newResult;
      rebuildPanel();
    }

    
    
    private JComponent sc(JComponent c) {
      c.setForeground(Color.white);
      return c;
    }
    
    private void rebuildPanel() {
      removeAll();
      DOSearchResult result = this.result;
      if(result==null) return;
      
      int y = 0;
      
      long dateCreated = result.getDateCreated();
      long dateModified = result.getDateModified();
      if(dateModified==0) dateModified = result.getContentDateModified();
      long date = (dateModified==0) ? dateCreated : dateModified;
      
      
      String title = result.getTitle();
      if(title!=null && title.trim().length()>0) {
        title = title.trim();
        if(title.length()>45) title = title.substring(0, 45)+"...";
        add(sc(makeLabel("title")), GridC.getc(1,y).label());
        add(sc(new JLabel(title)), GridC.getc(2,y++).field());
      }
      
      add(sc(makeLabel("id")), GridC.getc(1,y).label());
      add(sc(new JLabel(result.getID())), GridC.getc(2,y++).field());
      
      String owner = result.getOwner();
      if(owner!=null && owner.trim().length()>0) {
        owner = appUI.getAddressBook().getEntityLabel(owner);
        add(sc(makeLabel("owner")), GridC.getc(1,y).label());
        add(sc(new JLabel(owner)), GridC.getc(2,y++).field());
      }
      
      String filename = result.getContentFileName();
      if(filename!=null) {
        add(sc(makeLabel("file_name")), GridC.getc(1,y).label());
        add(sc(new JLabel(filename)), GridC.getc(2,y++).field());
      }
      
      String repoID = result.getRepositoryID();
      if(repoID!=null) {
        add(sc(makeLabel("location")), GridC.getc(1,y).label());
        if(repoID.equals(cacheRepoID)) {
          add(sc(new JLabel(repoID+" ("+appUI.getStr("inbox")+")")),
                GridC.getc(2,y++).field());
        } else if(repoID.equals(outboxRepoID)) {
          add(sc(new JLabel(repoID+" ("+appUI.getStr("outbox")+")")),
                GridC.getc(2,y++).field());
        } else {
          add(sc(new JLabel(repoID+" ("+appUI.getStr("loc_network")+")")),
                GridC.getc(2,y++).field());
        }
      }
      
      if(dateCreated!=0) {
        add(sc(makeLabel("date_created")), GridC.getc(1,y).label());
        add(sc(new JLabel(longDateFmt.format(new Date(dateCreated)))),
              GridC.getc(2,y++).field());
      }
      
      if(dateModified!=0) {
        add(sc(makeLabel("date_modified")), GridC.getc(1,y).label());
        add(sc(new JLabel(longDateFmt.format(new Date(dateModified)))),
            GridC.getc(2,y++).field());
      }
      String folder = result.getFolder();
      if(folder!=null && folder.trim().length()>0) {
        add(sc(makeLabel("folder")), GridC.getc(1,y).label());
        add(sc(new JLabel(folder.trim())), GridC.getc(2,y++).field());
      }
      
      long size = result.getContentSize();
      if(size>0) {
        add(sc(makeLabel("size")), GridC.getc(1,y).label());
        add(sc(new JLabel(DOUtil.niceSize(size))), GridC.getc(2,y++).field());
      }
      String note = result.getNotes();
      if(note!=null && note.trim().length()>0) {
        add(sc(makeLabel("notes")), GridC.getc(1,y).label().northEast());
        JTextArea noteArea = new JTextArea(note);
        noteArea.setBorder(notesBorder);
        noteArea.setBackground(null);
        noteArea.setOpaque(false);
        noteArea.setEditable(false);
        noteArea.setColumns(30);
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        add(new JScrollPane(sc(noteArea)), GridC.getc(2,y++).field().wx(0));
      }
      
      //        String rawInfo = result.toString();
      //        add(makeLabel("rawinfo"), GridC.getc(1,y).label());
      //        JTextArea rawArea = new JTextArea(rawInfo);
      //        rawArea.setBorder(notesBorder);
      //        add(new JScrollPane(rawArea), GridC.getc(2,y++).field());
      
      JPanel bp = new JPanel(new GridBagLayout());
      bp.setOpaque(false);
      bp.setBackground(null);
      bp.add(saveButton, GridC.getc(0,0).insets(10,0,0,10));
      bp.add(updateButton, GridC.getc(1,0).insets(10,2,0,10));
      bp.add(deleteButton, GridC.getc(2,0).insets(10,2,0,10));
      bp.add(Box.createHorizontalStrut(10), GridC.getc(3,0).wx(1));
      bp.add(closeButton, GridC.getc(4,0));
      add(bp, GridC.getc(1,y++).colspan(4).fillx());

      validate();
      setSize(getPreferredSize());
    }
  }
  
  class SearchResultGroupSpec {
    long startDate = -1;  // match objects on or after this date
    long endDate = -1;    // match objects on or before this date
    HashMap mimeTypes = null; // match objects with types in the map
    String creator = null; // match objects created by this person
    
    String groupName = "";
    
    /** Returns true if the given search result matches the parameters of this
      * group. */
    public boolean matches(DOSearchResult result) {
      if(startDate!=-1 || endDate!=-1) {
        long dc = result.getDateModified();
        if(dc==0) dc = result.getDateCreated();
        if(startDate!=-1 && dc < startDate) return false;
        if(endDate!=-1 && dc > endDate) return false;
      }
      if(mimeTypes!=null) {
        String mt = result.getContentMimeType();
        if(mt!=null && !mimeTypes.containsKey(mt)) return false;
      }
      if(creator!=null) {
        String cr = result.getOwner();
        if(cr!=null && !creator.equals(cr)) return false;
      }
      return true;
    }
  }
  
  
  public static Color viewHeaderColor1 = new Color(0.4157f, 0.6235f, 0.9373f);
  public static Color viewHeaderColor2 = new Color(0.2824f, 0.5451f, 0.9569f);
  public static Color toggleAllLinkColor = new Color(0.2275f, 0.4706f, 0.8588f);
  public static final int NUM_ITEMS_IN_SHORT_LIST = 5;
  
  class SearchResultGroupView
    extends JPanel
  {
    SearchResultGroupSpec group;
    private String groupName = "?";
    private JLabel label;
    private JLabel toggleAllLink;
    private JLabel numItemsLabel;
    private boolean expanded = true;
    private boolean showingAll = false;
    private JList list;
    private SearchResultListModel items;
    
    public SearchResultGroupView(SearchResultGroupSpec group) {
      super(new GridBagLayout());
      this.group = group;
      setOpaque(false);
      label = new JLabel(group.groupName);
      label.setBackground(viewHeaderColor1);
      label.setForeground(Color.white);
      label.setOpaque(true);
      label.setFont(label.getFont().deriveFont(Font.BOLD));
      numItemsLabel = new JLabel(" ");
      numItemsLabel.setBackground(viewHeaderColor1);
      numItemsLabel.setForeground(Color.white);
      numItemsLabel.setOpaque(true);
      
      items = new SearchResultListModel();
      list = new JList(items);
      list.setCellRenderer(renderer);
      toggleAllLink = new JLabel(" ");
      toggleAllLink.setForeground(toggleAllLinkColor);
      toggleAllLink.setOpaque(false);
      
      add(label, GridC.getc(0,0).wx(1).fillboth());
      add(numItemsLabel, GridC.getc(1,0).fillboth());
      add(list, GridC.getc(0,1).wxy(1,1).colspan(2).fillboth());
      add(toggleAllLink, GridC.getc(0,2).wx(1).colspan(2).fillx().insets(0,16,0,0));
      
      toggleAllLink.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent evt) {
          toggleAllClicked();
        }
      });
      label.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent evt) {
          toggleExpander();
        }
      });
      numItemsLabel.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent evt) {
          toggleExpander();
        }
      });
      list.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent evt) {
          Point p = evt.getPoint();
          if(evt.getClickCount()<2 && p.x < list.getWidth() - renderer.infoIconWidth)
            return;
          int index = list.locationToIndex(p);
          if(index<0) return;
          DOSearchResult item = (DOSearchResult)items.getElementAt(index);
          showItemDetails(item);
        }
      });
      updateView();
    }
    
    boolean containsResult(DOSearchResult result) {
      return items.contains(result);
    }
    
    void addResult(DOSearchResult result) {
      int numItems = items.getSize();
      items.insertResult(result);
      if(numItems==NUM_ITEMS_IN_SHORT_LIST) updateView();
    }
    
    void sortResults() {
      items.sortResults();
    }
    
    void refreshResult(DOSearchResult result) {
      items.refreshResult(result);
    }
    
    private class SearchResultListModel
      extends AbstractListModel
    {
      private ArrayList items = new ArrayList();
      
      SearchResultListModel() {}
      
      public Object getElementAt(int idx) {
        return items.get(idx);
      }
      
      public boolean contains(DOSearchResult result) {
        return items.contains(result);
      }
      
      public int realSize() { return items.size(); }
      
      public int getSize() {
        return showingAll ? realSize() : Math.min(NUM_ITEMS_IN_SHORT_LIST, realSize());
      }
      
      public void insertResult(DOSearchResult result) {
        int idx = Collections.binarySearch(items, result, sortComparator);
        if(idx<0) idx = items.size();
        items.add(idx, result);
        fireIntervalAdded(this, idx, idx);
      }
      
      public void sortResults() {
        Collections.sort(items, sortComparator);
        fireContentsChanged(this, -1, -1);
      }
      
      public void refreshResult(DOSearchResult result) {
        int index = items.indexOf(result);
        if(index>=0) fireContentsChanged(this, index, index);
      }
      
      void showingAllUpdated() {
        fireContentsChanged(this, -1, -1);
      }

      
    }
    
    private void toggleExpander() {
      expanded = !expanded;
      updateView();
    }
    
    private void toggleAllClicked() {
      showingAll = !showingAll;
      items.showingAllUpdated();
      updateView();
    }
    
    private void updateView() {
      label.setIcon(expanded ? collapseIcon : expandIcon);
      list.setVisible(expanded);
      numItemsLabel.setText(""+items.realSize()+" "+appUI.getStr("_items"));
      if(!expanded) {
        toggleAllLink.setVisible(false);
      } else {
        int numItems = items.realSize();
        if(numItems <= NUM_ITEMS_IN_SHORT_LIST) {
          toggleAllLink.setVisible(false);
        } else {
          toggleAllLink.setVisible(true);
          if(showingAll) {
            toggleAllLink.setText(appUI.getStr("show_top_")+" "+NUM_ITEMS_IN_SHORT_LIST);
          } else {
            toggleAllLink.setText(""+(numItems - NUM_ITEMS_IN_SHORT_LIST) +
                                  " " +appUI.getStr("_more_items"));
          }
        }
      }
    }
    
  }
  
  
  private static final int DOC_TYPE_ICON_WIDTH = 20;
  class ResultRenderer
    extends Component
    implements ListCellRenderer
  {
    private Color normalBG = Color.white;
    private Color selectedBG = new Color(0.5059f, 0.7098f, 0.1765f);
    private Color normalFG = Color.black;
    private Color selectedFG = Color.white;
    private ImageIcon infoIcon = null;
    private ImageIcon lockIcon = null;
    
    private DOSearchResult result = null;
    private boolean isSelected = false;
    private Font f;
    private FontMetrics fm = null;
    private int dateWidth = 20;
    private int timeWidth = 20;
    int infoIconWidth = 20;
    int lockIconWidth = 20;
    
    ResultRenderer() {
      f = new JLabel().getFont().deriveFont(10f);
    }
    
    public Component getListCellRendererComponent(JList list, Object value,
                                                  int index, boolean isSelected,
                                                  boolean hasFocus) 
    {
      this.result = (DOSearchResult)value;
      this.isSelected = isSelected;
      return this;
    }
    
    
    public Dimension getPreferredSize() {
      return new Dimension(50, 20);
    }
    
    public void paint(Graphics g) {
      g.setFont(f);
      if(fm==null) {
        fm = g.getFontMetrics();
        dateWidth = fm.stringWidth("XXX 88 8888") + 10;
        timeWidth = fm.stringWidth("33:33 AM") + 10;
        infoIcon = (ImageIcon)appUI.getImages().getIcon(INFO_ICON);
        infoIconWidth = infoIcon.getIconWidth();
        lockIcon = (ImageIcon)appUI.getImages().getIcon(LOCK_ICON);
        lockIconWidth = lockIcon.getIconWidth();
      }
      
      Rectangle bounds = getBounds();
      int x = bounds.x + 8;
      bounds.y = 0;
      g.setColor(isSelected ? selectedBG : normalBG);
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      g.setColor(isSelected ? selectedFG : normalFG);
      
      ImageIcon docTypeIcon = (ImageIcon)iconForMimeType(result.getContentMimeType());
      if(docTypeIcon!=null) g.drawImage(docTypeIcon.getImage(), x, bounds.y + 1, null);
      x += DOC_TYPE_ICON_WIDTH;
      
      if(result.getContentEncryptionKeyID()!=null) {
        if(lockIcon!=null) g.drawImage(lockIcon.getImage(), x, bounds.y + 1, null);
      }
      x += lockIconWidth + 2;
      
      String title = result.getTitle();
      if(title==null || title.trim().length()<=0) {
        title = result.getContentFileName();
        if(title!=null) {
          int dotIdx = title.lastIndexOf('.');
          if(dotIdx>0 && title.length()-dotIdx<=4) {
            title = title.substring(0, dotIdx);
          }
        }
        if(title==null) title = result.getID();
      }
      if(title!=null) title = title.trim();
      if(title==null || title.length()<=0) title = "????";
      
      
      Shape clip = g.getClip();
      g.clipRect(x, bounds.y, 
                 bounds.width - x - timeWidth - dateWidth -infoIconWidth - 10,
                 bounds.height);
      g.drawString(title, x, bounds.y + bounds.height - 4);
      
      
      String subtitle = result.getNotes();
      if(subtitle!=null) {
        int titleWidth = g.getFontMetrics().stringWidth(title) + 15;
        Color oldColor = g.getColor();
        g.setColor(Color.gray);
        g.drawString(subtitle.trim(), x + titleWidth, bounds.y + bounds.height - 4);
        g.setColor(oldColor);
      }
      
      
      long dateCreated = result.getDateCreated();
      long dateModified = result.getDateModified();
      if(dateModified==0) dateModified = result.getContentDateModified();
      Date date = new Date((dateModified==0) ? dateCreated : dateModified);
      g.setClip(clip);
      x = bounds.x + bounds.width - dateWidth - timeWidth - infoIconWidth;
      g.clipRect(x, bounds.y, dateWidth, bounds.height);
      g.drawString(dateFmt.format(date),
                   x, bounds.y + bounds.height - 4);
      g.setClip(clip);
      
      x += dateWidth;
      g.clipRect(x, bounds.y, timeWidth, bounds.height);
      String timeStr = timeFmt.format(date);
      g.drawString(timeStr, 
                   bounds.x + bounds.width - infoIconWidth - fm.stringWidth(timeStr) - 4,
                   bounds.y + bounds.height - 4);
      g.setClip(clip);
      
      x += timeWidth;
      
      g.drawImage(infoIcon.getImage(), x, bounds.y + 1, null);
      
      /*
       long dateCreated = result.getDateCreated();
      long dateModified = result.getDateModified();
      if(dateModified==0) dateModified = result.getContentDateModified();
      long date = (dateCreated==0) ? dateModified : dateCreated;
      if(date!=0) {
        add(new JLabel(dateFmt.format(new Date(date))), GridC.getc(2,0));
      }
      */
    }
    
  }
  

  private static void initIcons(DOImages images) {
    defaultIcon = addIcon(images, "default", "doc_icon_default");
    addIcon(images, "text/plain", "doc_icon_text");
    addIcon(images, "application/java", "doc_icon_text");
    addIcon(images, "application/pdf", "doc_icon_pdf");
    addIcon(images, "application/postscript", "doc_icon_pdf");
    addIcon(images, "application/vnd.ms-excel", "doc_icon_msexcel");
    addIcon(images, "application/msword", "doc_icon_msword");
    addIcon(images, "application/vnd.ms-powerpoint", "doc_icon_msppt");
    iconsLoaded = true;
  }
  
  
  private static Icon addIcon(DOImages images, String type, String iconName) {
    iconName = "/net/cnri/apps/dogui/view/images/"+iconName+".png";
    Icon icon = images.getIcon(iconName);
    if(icon==null) return null;
    mimeTypeIcons.put(type, icon);
    return icon;
  }

  public static Icon iconForMimeType(String mimeType) {
    if(mimeType==null) mimeType = "default";
    
    Icon icon = (Icon)mimeTypeIcons.get(mimeType.trim().toLowerCase());
    if(icon==null) return defaultIcon;
    return icon;
  }

  public class DateComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      DOSearchResult do1 = (DOSearchResult)o1;
      DOSearchResult do2 = (DOSearchResult)o2;
      long date1 = do1.getDateModified();
      if(date1==0) date1 = do1.getDateCreated();
      long date2 = do2.getDateModified();
      if(date2==0) date2 = do2.getDateCreated();
      return (int)((date1 - date1)/1000);
    }
    
    public boolean equals(Object o) {
      return o==this;
    }
  }
  
  
  public class NameComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      String label1 = ((DOSearchResult)o1).getLabel();
      String label2 = ((DOSearchResult)o2).getLabel();
      int cmp = label1.compareToIgnoreCase(label2);
      if(cmp!=0) return cmp;
      return dateComparator.compare(o1, o2);
    }
    
    public boolean equals(Object o) {
      return o==this;
    }
  }
  
  
  public class TypeComparator implements Comparator {
    public int compare(Object o1, Object o2) {
      DOSearchResult do1 = (DOSearchResult)o1;
      DOSearchResult do2 = (DOSearchResult)o2;
      String type1 = do1.getContentMimeType();
      String type2 = do2.getContentMimeType();
      if(type1==null) type1 = "None";
      if(type2==null) type2 = "None";
      int cmp = type1.compareToIgnoreCase(type2);
      if(cmp!=0) return cmp;
      return dateComparator.compare(o1, o2);
    }
    
    public boolean equals(Object o) {
      return o==this;
    }
  }
  
}
