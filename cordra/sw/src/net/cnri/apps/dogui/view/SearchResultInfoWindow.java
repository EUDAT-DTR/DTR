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
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;

public class SearchResultInfoWindow
  extends JDialog
  implements ActionListener
{
  private static ThreadSafeDateFormat dateFmt = new ThreadSafeDateFormat("MMM d yyyy");
  private static ThreadSafeDateFormat timeFmt = new ThreadSafeDateFormat("h:mm a");
  private static ThreadSafeDateFormat longDateFmt = new ThreadSafeDateFormat("d MMM yyyy h:mm a");
  private static HashMap notesFontInfo = new HashMap();
  private static HashMap mimeTypeIcons = new HashMap();
  private javax.swing.border.Border notesBorder = 
    new javax.swing.border.LineBorder(Color.lightGray, 1);
  private Font labelFont = null;
  
  private String outboxRepoID = "";
  private String cacheRepoID = "";
  
  private AdminToolUI appUI;
  private SearchResultInfoWindow thisWin;
  private DOSearchResult result;
  private DigitalObject obj;
  private DataElement content;
  private JLabel storeLabel;
  private JButton saveButton;
  private JButton updateButton;
  private JButton deleteButton;
  private JButton closeButton;
  private JButton permissionsButton;
  private JButton changeStoreButton;
  private JTabbedPane tabPane;
  
  public SearchResultInfoWindow(JComponent parent, AdminToolUI ui, DOSearchResult result,
                                DigitalObject obj) 
    throws Exception
  {
    super((JFrame)null, obj.getID(), false);
    this.appUI = ui;
    this.result = result;
    this.obj = obj;
    
    System.err.println("inspecting object: "+obj);
    System.err.println("  from results: "+result);
    
    content = obj.getDataElement("content");
    thisWin = this;
    
    outboxRepoID = appUI.getRepoID(AdminToolUI.OUTBOX);
    cacheRepoID = appUI.getRepoID(AdminToolUI.INBOX);
  
    tabPane = new JTabbedPane();
    
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(new javax.swing.border.EmptyBorder(10,10,10,10));
    //setOpaque(false);
    saveButton = new JButton(appUI.getStr("retrieve"));
    saveButton.addActionListener(this);
    saveButton.setOpaque(false);
    updateButton = new JButton(appUI.getStr("update"));
    updateButton.addActionListener(this);
    updateButton.setOpaque(false);
    deleteButton = new JButton(appUI.getStr("delete"));
    deleteButton.addActionListener(this);
    deleteButton.setOpaque(false);
    closeButton = new JButton(appUI.getStr("dismiss"));
    closeButton.addActionListener(this);
    closeButton.setOpaque(false);
    permissionsButton = new JButton(appUI.getStr("permissions"));
    permissionsButton.addActionListener(this);
    permissionsButton.setOpaque(false);
    changeStoreButton = new JButton(appUI.getStr("change"));
    changeStoreButton.addActionListener(this);
    changeStoreButton.setOpaque(false);
    storeLabel = new JLabel("");
    
    int y = 0;
    
    
    String title = obj.getAttribute(DOConstants.TITLE_ATTRIBUTE, obj.getID());
    if(title!=null && title.trim().length()>0) {
      title = title.trim();
      if(title.length()>45) title = title.substring(0, 45)+"...";
      p.add(makeLabel("title"), GridC.getc(1,y).label());
      p.add(new JLabel(title), GridC.getc(2,y++).field());
    }
    
    p.add(makeLabel("id"), GridC.getc(1,y).label());
    p.add(new JLabel(obj.getID()), GridC.getc(2,y++).field());
    
    String owner = obj.getAttribute(DOConstants.OWNER_ATTRIBUTE, null);
    if(owner!=null && owner.trim().length()>0) {
      owner = appUI.getAddressBook().getEntityLabel(owner);
      p.add(makeLabel("owner"), GridC.getc(1,y).label());
      p.add(new JLabel(owner), GridC.getc(2,y++).field());
    }
    
    String filename = content.getAttribute(DOConstants.FILE_NAME_ATTRIBUTE, null);
    if(filename!=null) {
      p.add(makeLabel("file_name"), GridC.getc(1,y).label());
      p.add(new JLabel(filename), GridC.getc(2,y++).field());
    }
    
    //    p.add(makeLabel("storage_hint"), GridC.getc(1,y).label());
    //    JPanel storeP = new JPanel(new GridBagLayout());
    //    storeP.add(storeLabel, GridC.getc(0,0));
    //    storeP.add(changeStoreButton, GridC.getc(1,0).insets(0,12,0,0));
    //    storeP.add(Box.createHorizontalStrut(2), GridC.getc(2,0).wx(1));
    //    p.add(storeP, GridC.getc(2,y++).field());
    
    String encryptionKeyID = content.getAttribute(DOKeyRing.KEY_ID_ATTRIBUTE, null);
    if(encryptionKeyID!=null) {
      p.add(makeLabel("encryption_key_id"), GridC.getc(1,y).label());
      p.add(new JLabel(encryptionKeyID.trim()), GridC.getc(2,y++).field());
      String encryptionAlg = content.getAttribute(DOKeyRing.KEY_ALG_ATTRIBUTE, null);
      if(encryptionAlg!=null) {
        p.add(makeLabel("encryption_alg"), GridC.getc(1,y).label());
        p.add(new JLabel(encryptionAlg.trim()), GridC.getc(2,y++).field());
      }
    }
    
    Date dateCreated = obj.getDateCreated();
    if(dateCreated!=null && dateCreated.getTime()>0) {
      p.add(makeLabel("date_created"), GridC.getc(1,y).label());
      p.add(new JLabel(longDateFmt.format(dateCreated)),
          GridC.getc(2,y++).field());
    }
    else {
        Date dateModified = obj.getDateLastModified();
        if(dateModified!=null && dateModified.getTime()>0) {
          p.add(makeLabel("date_modified"), GridC.getc(1,y).label());
          p.add(new JLabel(longDateFmt.format(dateModified)),
                GridC.getc(2,y++).field());
        }
    }
        
    String folder = obj.getAttribute(DOConstants.FOLDER_ATTRIBUTE, null);
    if(folder!=null && folder.trim().length()>0) {
      p.add(makeLabel("folder"), GridC.getc(1,y).label());
      p.add(new JLabel(folder.trim()), GridC.getc(2,y++).field());
    }
    
    long size = content.getSize();
    if(size>0) {
      p.add(makeLabel("size"), GridC.getc(1,y).label());
      p.add(new JLabel(DOUtil.niceSize(size)), GridC.getc(2,y++).field());
    }
    
    
    DigitalObject repoObj = null;
    try {
      repoObj = getRepositoryForObject();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    String repoStr = repoObj!=null ? repoObj.getID() : result!=null ? result.getRepositoryID() : null;
    if(repoStr==null) repoStr = obj.getRepository().getID();
    String storageKey = obj.getAttribute("doserver.storagekey", "<none>");
    changeStoreButton.setText(appUI.getStr("storage_hint")+": "+storageKey);
    if(repoStr.equals(cacheRepoID)) {
      repoStr = repoStr+" ("+appUI.getStr("inbox")+")";
    } else if(repoStr.equals(outboxRepoID)) {
      repoStr = repoStr+" ("+appUI.getStr("outbox")+")";
    } else {
      repoStr = repoStr+" ("+appUI.getStr("loc_network")+")";
    }
    p.add(makeLabel("location"), GridC.getc(1,y).label());
    JPanel tmpP = new JPanel(new GridBagLayout());
    tmpP.add(new JLabel(repoStr), GridC.getc(0,0));
    tmpP.add(changeStoreButton, GridC.getc(1,0).insets(0,10,0,0));
    tmpP.add(Box.createHorizontalStrut(2), GridC.getc(2,0).wx(1));
    p.add(tmpP, GridC.getc(2,y++).field());
    
    
    String note = obj.getAttribute(DOConstants.NOTES_ATTRIBUTE, null);
    if(note!=null && note.trim().length()>0) {
      p.add(makeLabel("notes"), GridC.getc(1,y).label().northEast());
      JTextArea noteArea = new JTextArea(note);
      //noteArea.setBorder(notesBorder);
      noteArea.setBackground(null);
      noteArea.setOpaque(false);
      noteArea.setEditable(false);
      noteArea.setColumns(30);
      noteArea.setLineWrap(true);
      noteArea.setWrapStyleWord(true);
      p.add(Box.createVerticalStrut(100), GridC.getc(2,y));
      p.add(new JScrollPane(noteArea), GridC.getc(2,y++).field().wx(0).wy(1).fillboth());
    }
    
    saveButton.setEnabled(obj!=null);
    updateButton.setEnabled(obj!=null);
    permissionsButton.setEnabled(obj!=null);
    deleteButton.setEnabled(obj!=null);
    changeStoreButton.setEnabled(obj!=null);
    
    //        String rawInfo = result.toString();
    //        p.add(makeLabel("rawinfo"), GridC.getc(1,y).label());
    //        JTextArea rawArea = new JTextArea(rawInfo);
    //        rawArea.setBorder(notesBorder);
    //        p.add(new JScrollPane(rawArea), GridC.getc(2,y++).field());
    
    tabPane.addTab(appUI.getStr("Information"), p);
    
    
    p = new JPanel(new GridBagLayout());
    DefaultTableModel attributeTableModel = new DefaultTableModel(0, 2);
    
    p.add(new JTable(attributeTableModel), GridC.getc(0,0).wxy(1,1).fillboth());
    
    
    JPanel bp = new JPanel(new GridBagLayout());
    bp.setOpaque(false);
    bp.setBackground(null);
    bp.add(saveButton, GridC.getc(0,0).insets(10,0,0,10));
    bp.add(updateButton, GridC.getc(1,0).insets(10,2,0,10));
    bp.add(permissionsButton, GridC.getc(2,0).insets(10,2,0,10));
    bp.add(deleteButton, GridC.getc(3,0).insets(10,2,0,10));
    bp.add(Box.createHorizontalStrut(100), GridC.getc(4,0).wx(1));
    bp.add(closeButton, GridC.getc(5,0).insets(10,2,0,10));
    p.add(bp, GridC.getc(1,y++).colspan(5).fillx());
    
    setContentPane(p);
    getRootPane().setDefaultButton(closeButton);
    
    pack();
    setSize(getPreferredSize());
    setLocationRelativeTo(parent);
  }
  
  /** Return the DigitalObject for the repository that holds this object, which may be
   *  different than the Repository (or gateway) through which we access the object. */
  private DigitalObject getRepositoryForObject() 
  throws Exception
  {
    String repoID = null;
    if(result!=null) repoID = result.getRepositoryID();
    
    if(repoID==null) {
      try {
        repoID = DOClient.resolveRepositoryID(obj.getID());
      } catch (Exception e) {
        appUI.showErrorMessage(thisWin, "Unable to locate repository for "+obj.getID());
      }
    }
    if(repoID!=null) {
      return appUI.getObjectReference(null, repoID);
    }
    
    throw new Exception("Unable to determine repository for object: "+obj.getID());
  }
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==saveButton) {
      new Thread("Retrieve content: "+result) {
        public void run() {
          if(obj==null) return;
          try {
            saveElement(content);
          } catch (Exception e) {
            appUI.showErrorMessage(thisWin,
                                   appUI.getStr("error_getting_element")+"\n\n  "+e);
          }
        }
      }.start();
    } else if(src==updateButton) {
      new Thread("Update content: "+result) {
        public void run() {
          if(obj==null) return;
          try {
            updateElement(content);
          } catch (Exception e) {
            appUI.showErrorMessage(thisWin,
                                   appUI.getStr("error_getting_element")+"\n\n  "+e);
          }
        }
      }.start();
      
    } else if(src==deleteButton) {
      if(obj==null) return;
      if(JOptionPane.YES_OPTION!=
         JOptionPane.showConfirmDialog(this, appUI.getStr("confirm_delete_obj"),
                                       appUI.getStr("confirm_delete_obj"), JOptionPane.YES_NO_OPTION)) {
        return;
      }
      try {
        obj.deleteObject();
        appUI.showInfoMessage(this, appUI.getStr("obj_was_deleted_msg"));
      } catch (Exception e) {
        appUI.showErrorMessage(this, appUI.getStr("error_deleting_obj")+"\n\n  "+e);
      }
      setVisible(false);
      dispose();
    } else if(src==permissionsButton) {
      if(obj==null) return;
      DOPermissionEditor permEditor = new DOPermissionEditor(appUI);
      try {
        if(obj.verifyDataElement(DOConstants.RIGHTS_ELEMENT_ID)) {
          permEditor.readPermissions(obj.getDataElement(DOConstants.RIGHTS_ELEMENT_ID).read());
        } else {
          permEditor.readPermissions(getRepositoryForObject().
                                     getDataElement(DOConstants.REPO_RIGHTS_ELEMENT_ID).read());
        }
      } catch (Exception e) {
        appUI.showErrorMessage(this, appUI.getStr("error_getting_perms_for_editing")+e);
      }
      while(true) {
        String setPerms = appUI.getStr("set_permissions");
        String closeWin = appUI.getStr("close_window");
        JOptionPane pane = new JOptionPane(permEditor, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, 
                                           new Object[] { setPerms, closeWin, });
        pane.setIcon(null);
        pane.createDialog(this, appUI.getStr("permissions")+": "+obj.getID()).setVisible(true);
        
        Object selectedValue = pane.getValue();
        System.err.println("User selected option: "+selectedValue);
        if(selectedValue==null || !selectedValue.equals(setPerms)) {
          System.err.println("not saving permissions");
          break;
        }
        try {
          System.err.println("saving permissions");
          ByteArrayOutputStream bout = new ByteArrayOutputStream();
          permEditor.writePermissions(bout);
          byte bytes[] = bout.toByteArray();
          if(bytes==null || bytes.length<=0) {
            obj.deleteDataElement(DOConstants.RIGHTS_ELEMENT_ID);
          } else {          
            obj.getDataElement(DOConstants.RIGHTS_ELEMENT_ID).write(new ByteArrayInputStream(bytes));
          }
          break;
        } catch (Exception e) {
          appUI.showErrorMessage(this, appUI.getStr("error_settings_perms")+": "+e);
        }
      }
    } else if(src==changeStoreButton) {
      String oldVal = "";
      try { 
        oldVal = obj.getAttribute("doserver.storagekey","");
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
      String newStorageKey = JOptionPane.showInputDialog(this, appUI.getStr("new_storage_key"),
                                                         oldVal);
      if(newStorageKey!=null) {
        newStorageKey = newStorageKey.trim();
        try {
          if(newStorageKey.length()<=0) {
            obj.setAttribute("doserver.storagekey", null);
          } else {
            obj.setAttribute("doserver.storagekey", newStorageKey);
          }
          System.err.println("new storage key: "+newStorageKey);
          changeStoreButton.setText(appUI.getStr("storage_hint")+": "+newStorageKey);
          changeStoreButton.repaint();
          storeLabel.setText(newStorageKey);
          
        } catch (Exception e) {
          appUI.showErrorMessage(this, appUI.getStr("error_setting_attribute")+
                                 " 'doserver.storagekey': "+e);
        }
      }
      
    } else if(src==closeButton) {
      setVisible(false);
    }
  }

  private synchronized JLabel makeLabel(String labelStr) {
    JLabel label = new JLabel(appUI.getStr(labelStr)+": ");
    if(labelFont==null) {
      labelFont = label.getFont().deriveFont(Font.BOLD);
    }
    label.setFont(labelFont);
    return label;
  }
  
  private void saveElement(DataElement element) {
    // ask for the location in which to save this file/element...
    String filename = "";
    try { 
      filename = element.getAttribute(DOConstants.FILE_NAME_ATTRIBUTE, "");
    } catch(Exception e) {}
    
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
    
    
    OutputStream fout = null;
    InputStream in = null;
    try {
      
      DOKeyRing keyRing = appUI.getKeyRing();
      if(keyRing!=null) {
        // if the element is encrypted and we might have the key, decrypt it
        in = keyRing.decryptDataElement(element);
      }
      if(in==null) {
        in = element.read();
      }
      
      ProgressMonitorInputStream pin = new ProgressMonitorInputStream(thisWin,
                                                                      appUI.getStr("reading")+": "+
                                                                      element.getObjectID() + " into "+
                                                                      f.getName(), in);
      ProgressMonitor pm = pin.getProgressMonitor();
      pm.setMinimum(0);
      pm.setMaximum((int)element.getSize());
      pm.setMillisToPopup(10);
      pm.setMillisToDecideToPopup(10);
      in = new BufferedInputStream(pin);
      fout = new BufferedOutputStream(new FileOutputStream(f));
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_getting_element")+"\n\n  "+e);
      return;
    }
    
    new AsyncStreamer(appUI.getStr("file_was_saved_msg")+":\n  "+f.getPath(), in, fout).start();
  }
  
  
  private class AsyncStreamer
  extends Thread
  {
    String finishedMessage = null;
    InputStream in;
    OutputStream out;
    
    AsyncStreamer(String message, InputStream in, OutputStream out) {
      this.finishedMessage = message;
      this.in = in;
      this.out = out;
    }
    
    public void run() {
      try {
        byte buf[] = new byte[100000];
        int r;
        while((r=in.read(buf))>=0) out.write(buf, 0, r);
        out.close();
      } catch (Exception e) {
        appUI.showErrorMessage(thisWin, appUI.getStr("error_getting_element")+"\n\n  "+e);
        return;
      } finally {
        try { in.close(); } catch (Exception e) { }
        try { out.close(); } catch (Exception e) { }
      }
      
      appUI.showInfoMessage(thisWin, finishedMessage);
    }
  }
  

  private void updateElement(DataElement element) {
    String filename = "";
    try { 
      filename = element.getAttribute(DOConstants.FILE_NAME_ATTRIBUTE, "");
    } catch(Exception e) {}
    
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

  
  
}
