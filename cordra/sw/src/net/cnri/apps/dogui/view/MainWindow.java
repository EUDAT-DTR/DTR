/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.apps.dogui.controller.*;
import net.cnri.dobj.*;
import net.cnri.awt.*;
import net.cnri.guiutil.GridC;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import javax.swing.*;
import java.io.*;

public class MainWindow
  extends DOFrame
  implements ActionListener
{
  private MainWindow thisObj;
  
  private JTextField repoIDField;
  private JTextField objectIDField;
  private JComboBox repoChoice;
  
  private JLabel authLabel;
  
  private DepositTransferHandler xfrHandler = null;
  
  private HDLAction openObjectAction;
  private HDLAction createFromFileAction;
  private HDLAction createObjectAction;
  
  
  public MainWindow(AdminToolUI ui) {
    super(ui.getStr("main_win_title"), ui);
    this.thisObj = this;
    
    repoChoice = new JComboBox(new String[] {"Automatic","Override"});
    repoIDField = new JTextField("",35);
    objectIDField = new JTextField("",35);
    authLabel = new JLabel(" ");
    
    openObjectAction = new HDLAction(ui, "open_obj", "open_obj") {
      public void actionPerformed(ActionEvent evt) {
        openSelectedObject();
      }
    };
    //depositTarget = new JLabel(ui.getStr("deposit_target_label"));
    //depositTarget.setTransferHandler(xfrHandler = new DepositTransferHandler());
    //depositTarget.setIcon(ui.getImages().getIcon(DOImages.DEPOSIT_WELL));
    //depositTarget.addMouseListener(new DragMouseAdapter());
    
    JButton defaultButton = null;
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
    p.setOpaque(false);
    p.add(new JLabel(ui.getStr("repo_id")), GridC.getc(0, 0).label());
    p.add(repoChoice, GridC.getc(1, 0));
    p.add(repoIDField, GridC.getc(2,0).field());
    p.add(new JLabel(ui.getStr("object_id")), GridC.getc(0, 1));
    p.add(objectIDField, GridC.getc(1,1).field().colspan(2));
    p.add(defaultButton = new JButton(openObjectAction), GridC.getc(0,2).colspan(3).east());
    objectIDField.setAction(openObjectAction);
    
    this.setTransferHandler(xfrHandler = new DepositTransferHandler());
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    
    getContentPane().add(p, BorderLayout.CENTER);
    if(defaultButton!=null) getRootPane().setDefaultButton(defaultButton);
    
    pack();
    setSize(getPreferredSize());
    AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);
    
    addWindowListener(new WindowAdapter() {
      public void windowActivated(WindowEvent windowEvent) {
        objectIDField.requestFocus();
      }
    });
    repoChoice.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent itemEvent) {
        repoIDField.setEnabled(!isAutomaticRepoSelected());
      }
    });
    repoChoice.setSelectedIndex(0);
    repoIDField.setEnabled(!isAutomaticRepoSelected());
  }
  
  public boolean isAutomaticRepoSelected() {
    return repoChoice.getSelectedIndex() == 0;
  }
  
  private void openSelectedObject() {
    String repoID = isAutomaticRepoSelected() ? null : repoIDField.getText();
    String doID = objectIDField.getText();
    if(doID.trim().length()<=0) {
      Toolkit.getDefaultToolkit().beep();
      return;
    }
    ProgressMonitor monitor = new ProgressMonitor(this, 
                                                  appUI.getStr("loading_object")+": "+doID,
                                                  "", 0, 0);

    new ObjectOpener(appUI.getAuthentication(false), repoID, doID).start();
  }
  
  
  private class ObjectOpener extends Thread {
    private String objectID;
    private String repoID;
    private DOAuthentication auth;
    
    ObjectOpener(DOAuthentication auth, String repoID, String objectID) {
      this.auth = auth;
      this.objectID = objectID;
      this.repoID = repoID;
    }
    
    public void run() {
      final DigitalObject dobj = appUI.getObjectReference(repoID, objectID);
      if(repoID==null) { // the repository ID was automatically resolved
        SwingUtilities.invokeLater(new Runnable() { 
          public void run() {
            try {
              repoIDField.setText(dobj.getRepository().getObjectID());
            } catch (Exception e) {
              System.err.println("Error obtaining resolved repository ID for handle "+objectID+": "+e);
            }
          }
        });
      }
      if(dobj!=null) {
        SwingUtilities.invokeLater(new ObjectViewer(dobj));
      } else {
        appUI.showErrorMessage(thisObj, "Unable to obtain object reference for "+objectID);
      }
    }
  }
  
  
  
  public class DragMouseAdapter extends MouseAdapter {
    public void mousePressed(MouseEvent e) {
      JComponent c = (JComponent)e.getSource();
      TransferHandler handler = c.getTransferHandler();
      if(handler==null) return;
      handler.exportAsDrag(c, e, TransferHandler.COPY);
    }
  }
  

  private static final String replaceAll(String str, String toReplace, String replaceWith) {
    int toReplaceLen = toReplace.length();
    int i;
    int lastI = 0;
    StringBuffer sb = new StringBuffer(str.length());
    while((i=str.indexOf(toReplace, lastI))>=0) {
      sb.append(str.substring(lastI, i)); // add the text before the matched substring
      sb.append(replaceWith);
      lastI = i + toReplace.length();
    }
    sb.append(str.substring(lastI)); // add the rest
    return sb.toString();
  }
  
    
  public void authenticationChanged(DOAuthentication newAuth) {
    if(newAuth==null) {
      authLabel.setText(" ");
    } else {
      authLabel.setText(String.valueOf(newAuth));
    }
  }
  
  public boolean createObject(String id) {
    Repository repo = appUI.getRepository(AdminToolUI.OUTBOX);
    
    if(repo==null) return false;
    
    try {
      // create the object on the given repository
      appUI.showErrorMessage(this, "Create-object operation not implemented yet ");
    } catch (Exception e) {
      appUI.showErrorMessage(this, "Error creating object: '"+id+"'\n\nMessage: "+e);
      return false;
    }
    return true;
  }
  
  
  
  public boolean viewObject(String objectID) {
    Repository outbox = appUI.getRepository(AdminToolUI.OUTBOX);
    DigitalObject object = null;
    if(outbox!=null) {
      try {
        object = outbox.getDigitalObject(objectID);
      } catch (Exception e) {
        System.err.println("Error checking for object in outbox: "+e);
      }
    }
    
    if(object==null) {
      // it is probably in the inbox
      Repository inbox = appUI.getRepository(AdminToolUI.INBOX);
      if(inbox!=null) {
        try {
          object = inbox.getDigitalObject(objectID);
        } catch (Exception e) {
          System.err.println("Error checking for object in outbox: "+e);
        }
      }
    }
    
    if(object==null) {
      appUI.showErrorMessage(this, replaceAll(appUI.getStr("obj_not_found"),
                                              "{objectid}", objectID));
      return false;
    }
    
    return viewObject(object);
  }
  
  public boolean viewObject(DigitalObject object) {
    if(object==null) return false;
    SwingUtilities.invokeLater(new ObjectViewer(object));
    return true;
  }
  
  private class ObjectViewer
    implements Runnable
  {
    private DigitalObject object;
    
    ObjectViewer(DigitalObject obj) {
      this.object = obj;
    }
    
    public void run() {
      ShowObjectWindow sow = new ShowObjectWindow(appUI);
      sow.setObject(object, ShowObjectWindow.EDIT_MODE);
      sow.setVisible(true);
    }
  }
  
  class SearchResult {
    String objectID;
    String repoID;
    String label;
    String source;

    SearchResult(HeaderSet searchResult, String source) {
      objectID = searchResult.getStringHeader("objectid", "");
      repoID = searchResult.getStringHeader("repoid", null);
      label = searchResult.getStringHeader("desc", null);
      this.source = source;
    }
    
    public String toString() {
      StringBuffer sb = new StringBuffer();
      if(source!=null) {
        sb.append(source);
        sb.append(": ");
      }
      sb.append(String.valueOf(objectID));
      if(repoID!=null) {
        sb.append(" @ ");
        sb.append(repoID);
      }
      if(label!=null) {
        sb.append(":  ");
        sb.append(label);
      }
      return sb.toString();
    }
  }
  
  
  public void deleteObject(String repoID, String id) {
    if(id==null || id.trim().length()<=0) return;
    
    try {
      appUI.showErrorMessage(this, "Delete-object operation not implemented yet ");
    } catch (Exception e) {
      appUI.showErrorMessage(this, "There was an error deleting object '"+id+"': "+e);
    }
  }
  
  /** Returns true if the given string looks like a handle */
  public static final boolean looksLikeID(String id) {
    if(id==null) return false;
    int slashIdx = id.indexOf('/');
    if(slashIdx<0) return false; // no slash
    if(id.indexOf(' ')>=0) return false; // no space
    return true;
  }
  
  
  private void doSearch() {
    String query = objectIDField.getText().trim();
    if(query.length()<=0) {
      appUI.showErrorMessage(this, appUI.getStr("empty_search_err_msg"));
      return;
    }
      
    //if(looksLikeID(query)) {
    //  viewObject(query);
    //} else {
      SearchWindow searchWin = new SearchWindow(appUI);
      searchWin.setVisible(true);
      searchWin.performSearch(new DOSearch(query));
    //}
  }
  
  private void doDeposit() {
    FileDialog fwin = new FileDialog(this, appUI.getStr("choose_deposit_file"),
                                     FileDialog.LOAD);
    fwin.setVisible(true);
    String fileStr = fwin.getFile();
    String dirStr = fwin.getDirectory();
    if(fileStr==null || dirStr==null) return;
    File f[] = new File[] { new File(dirStr, fileStr) };
    PreDepositWindow depWin = new PreDepositWindow(appUI, f);
    depWin.setVisible(true);
  }
  
  
  
  private class DepositTransferHandler 
    extends TransferHandler
  {
    private DataFlavor fileFlavor;
    
    public DepositTransferHandler() {
      super("text");
      fileFlavor = DataFlavor.javaFileListFlavor;
    }
    
    public boolean canImport(JComponent c, DataFlavor[] flavors) {
      if (hasFileFlavor(flavors))   { return true; }
      return false;
    }
    private boolean hasFileFlavor(DataFlavor[] flavors) {
      for (int i=0; i<flavors.length; i++) {
        if (DataFlavor.javaFileListFlavor.equals(flavors[i])) {
          return true;
        }
      }
      return false;
    }
    
    public int getSourceActions(JComponent c) { return COPY; }
    
    
    /** Import the indicated transferable into the digital object */
    public boolean importData(JComponent c, Transferable t) {
      try {
        JLabel tc;
        System.err.println("trying import: "+t);
        if(!canImport(c, t.getTransferDataFlavors())) {
          return false;
        }
        
        if (hasFileFlavor(t.getTransferDataFlavors())) {
          System.err.println("importing file list...");
          java.util.List files = 
            (java.util.List)t.getTransferData(DataFlavor.javaFileListFlavor);
          System.err.println("importing file list: "+files);
          
          File fileArray[] = (File[])files.toArray(new File[files.size()]);
          PreDepositWindow win = new PreDepositWindow(appUI, fileArray);
          win.setVisible(true);
          return true;
        }
      } catch (Exception e) {
        System.err.println("Error importing data: "+e);
        e.printStackTrace(System.err);
      }
      return false;
    }
  }
  
  /*
  private class ShowObjectRunner
    implements Runnable
  {
    private String repoID;
    private String objectID;
    private int mode;
    
    ShowObjectRunner(String repoID, String objectID, int mode) {
      this.repoID = repoID;
      this.objectID = objectID;
      this.mode = mode;
    }
    
    public void run() {
      ShowObjectWindow showObjectWindow = new ShowObjectWindow(ui);
      showObjectWindow.setObject(repoID, objectID, mode);
      AwtUtil.setWindowPosition(showObjectWindow, thisObj);
      showObjectWindow.setVisible(true);
    }
  }
*/
  
}
