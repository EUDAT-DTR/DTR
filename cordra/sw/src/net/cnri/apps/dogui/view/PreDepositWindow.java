/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.dobj.*;
import net.cnri.guiutil.GridC;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;

public class PreDepositWindow
  extends JDialog
{
  private AdminToolUI ui;
  
  private File filesToDeposit[];
  private JLabel fileLabel;
  private JTextField creatorField;
  private JButton chooseButton;
  private JComboBox destFolderChoice;
  private JComboBox repoChoice;
  private JCheckBox encryptContentBox;
  private JCheckBox encryptAttributesBox;
  private JList encryptForList;
  private JLabel encryptForLabel;
  private JScrollPane encryptScroll;
  private JTextField destFolderField;
  private JTextField storageHintField;
  private JTextArea notesField;
  private JButton depositButton;
  private DOPermissionEditor permissionsUI;
  
  public PreDepositWindow(AdminToolUI ui, File filesToDeposit[]) {
    super((JFrame)null, ui.getStr("deposit_file_or_folder"), false);
    this.ui = ui;
    this.filesToDeposit = filesToDeposit;
    
    permissionsUI = new DOPermissionEditor(ui);
    
    fileLabel = new JLabel(" ");
    creatorField = new JTextField("", 40);
    creatorField.setEditable(false);
    DOAuthentication auth = ui.getAuthentication(false);
    if(auth!=null) creatorField.setText(auth.getID());
    
    chooseButton = new JButton(ui.getStr("choose_file"));
    notesField = new JTextArea(4, 40);
    depositButton = new JButton(ui.getStr("deposit"));
    encryptContentBox = new JCheckBox(ui.getStr("encrypt_content"));
    encryptAttributesBox = new JCheckBox(ui.getStr("encrypt_attributes"));
    encryptForList = new JList(ui.getAddressBook().getEntities().toArray());
    encryptForLabel = new JLabel(ui.getStr("encrypt_for")+": ");
    
    storageHintField = new JTextField("", 40);
    destFolderField = new JTextField("", 40);
    destFolderChoice = new JComboBox(new String[] {
      ui.getStr("include_src_folder"),
      ui.getStr("relative_to_src_folder"),
      ui.getStr("specify..."),
    });
    repoChoice = new JComboBox();
    String gateway = null;
    try { gateway = ui.getUserObject().getAttribute("do.gateway", null); } catch (Exception e) {}
    if(gateway!=null)  repoChoice.addItem(gateway);
    //repoChoice.addItem(ui.getRepoID(AdminToolUI.OUTBOX));
    try {
      String netRepoID = ui.getUserObject().getAttribute(AdminToolUI.HOME_REPO_ATT, "10551/repo1");
      if(netRepoID!=null) repoChoice.addItem(netRepoID);
    } catch (Exception e) {
      System.err.println("warning: unable to get home repository attribute from PDO: "+e);
    }
    repoChoice.setEditable(true);
    //repoChoice.setSelectedIndex(0);
    
    destFolderChoice.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent evt) {
        destFolderSelected();
      }
    });
    
    JPanel p = new JPanel(new GridBagLayout());
    int y = 0;
    p.add(new JLabel(ui.getStr("deposit_file_or_folder")+": "),
          GridC.getc(0,y).label());
    p.add(fileLabel, GridC.getc(1,y).field());
    p.add(chooseButton, GridC.getc(2,y++).field().wxy(0,0));
    p.add(new JLabel(ui.getStr("repo_id")+": "), GridC.getc(0,y).label());
    p.add(repoChoice, GridC.getc(1,y++).field().colspan(2));
    p.add(new JLabel(ui.getStr("storage_hint")+": "), GridC.getc(0,y).label());
    p.add(storageHintField, GridC.getc(1,y++).field().colspan(2));
    //p.add(new JLabel(ui.getStr("creator")+": "), GridC.getc(0,y).label());
    //p.add(creatorField, GridC.getc(1,y++).field().colspan(2));
    p.add(new JLabel(ui.getStr("dest_folder")+": "), GridC.getc(0,y).label());
    p.add(destFolderChoice, GridC.getc(1,y++).field().colspan(2));
    p.add(destFolderField, GridC.getc(1,y++).field().colspan(2));
    p.add(new JLabel(ui.getStr("notes")+": "), GridC.getc(0,y).label());
    p.add(new JScrollPane(notesField), GridC.getc(1,y++).field().colspan(2).wy(1));
    p.add(encryptContentBox, GridC.getc(1,y++).field().colspan(3));
    //p.add(encryptAttributesBox, GridC.getc(1,y++).field().colspan(2));
    p.add(encryptForLabel, GridC.getc(0,y).label());
    p.add(encryptScroll = new JScrollPane(encryptForList), GridC.getc(1,y++).field().colspan(2));
    p.add(new JLabel(ui.getStr("permissions")), GridC.getc(0,y++).colspan(3).west());
    p.add(permissionsUI, GridC.getc(0,y++).wxy(1,1).fillboth().colspan(3));
    
    JPanel bp = new JPanel(new GridBagLayout());
    bp.add(Box.createHorizontalStrut(30), GridC.getc(0,0).wx(1));
    bp.add(depositButton, GridC.getc(1,0));
    p.add(bp, GridC.getc(0,y++).fillx().colspan(3));
    
    p.setBorder(new EmptyBorder(10,10,10,10));
    setContentPane(p);
    
    destFolderChoice.setSelectedIndex(0);
    destFolderSelected();
    updateFileLabel();

    depositButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        performDeposit();
      }
    });
    encryptContentBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        encryptionUpdated();
      }
    });
    encryptionUpdated();
    
    pack();
    setSize(getPreferredSize());
    setLocationRelativeTo(null);
  }
  
  
  private void encryptionUpdated() {
    boolean encrypt = encryptContentBox.isSelected() || encryptAttributesBox.isSelected();
    encryptForList.setEnabled(encrypt);
    encryptScroll.setVisible(encrypt);
    encryptForList.setVisible(encrypt);
    encryptForLabel.setVisible(encrypt);
    setSize(getPreferredSize());
  }
  
  
  private void destFolderSelected() {
    destFolderField.setEnabled(destFolderChoice.getSelectedIndex()==2);
    updateFileLabel();
  }
  
  private void performDeposit() {
    DepositWindow dw = new DepositWindow(ui, this);
    dw.setFiles(filesToDeposit);
    dw.setNotes(notesField.getText());
    dw.setEncryptContent(encryptContentBox.isSelected());
    dw.setEncryptAttributes(encryptAttributesBox.isSelected());
    dw.setEncryptFor(encryptForList.getSelectedValues());
    
    dw.setRepositoryID(String.valueOf(repoChoice.getSelectedItem()));
    dw.setStorageHint(storageHintField.getText().trim());
    
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    try {
      permissionsUI.writePermissions(bout);
    } catch (Exception e) {
      ui.showErrorMessage(this, String.valueOf(e));
      return;
    }
    dw.setRights(bout.toByteArray());
    permissionsUI.storePermissionsInPreferences();
    
    switch(destFolderChoice.getSelectedIndex()) {
      case 0:
        dw.setUseSourceFolder();
        break;
      case 1:
        dw.setUseSubFolder();
        break;
      case 2:
      default:
        dw.setUseFolder(destFolderField.getText().trim());
        break;
    }
    setVisible(false);
    dw.doDeposit();
  }
  
  
  private void updateFileLabel() {
    int destFolderType = destFolderChoice.getSelectedIndex();
    
    File depFiles[] = filesToDeposit;
    StringBuffer sb = new StringBuffer();
    StringBuffer destFolderSB = new StringBuffer();
    
    for(int i=0; depFiles!=null && i<depFiles.length; i++) {
      if(depFiles[i]==null) continue;
      if(sb.length()>0) sb.append(", ");
      if(sb.length()>50) {
        sb.append("...");
        break;
      }
      sb.append(depFiles[i].getName());
    }
    fileLabel.setText(sb.toString());
    //for(int i=0; depFiles!=null && i<depFiles.length; i++) {
    //  if(depFiles[i]==null) continue;
    //  
    //}
    depositButton.setEnabled(sb.length()>0);
  }
  
  
  
}
