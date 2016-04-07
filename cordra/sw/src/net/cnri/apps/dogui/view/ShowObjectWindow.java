/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.apps.dogui.view.util.JLinkListener;
import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.cnri.guiutil.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;

public class ShowObjectWindow
  extends DOFrame
  implements ActionListener,
             ViewConstants,
             JLinkListener,
             DOConstants
{
  public static final String KEY_ELEMENT_PREFIX = "dockey";
  
  private int mode = VIEW_MODE;
  private ShowObjectWindow thisObj;
  private DigitalObject obj;
  
  //private AdminToolUI ui;
  
  private JLabel objLabel;
  private JLabel infoLabel;
  private JPanel objectPanel;
  private JButton doneButton;
  private JButton updateButton;
  private JButton deleteButton;
  private JButton encryptButton;
  private JButton saveButton;
  
  private boolean canceled = true;
  
  public ShowObjectWindow(AdminToolUI ui) {
    super("View Object", ui);
    this.thisObj = this;
    
    setJMenuBar(appUI.getAppMenu());
    
    objLabel = new JLabel(" ");
    objLabel.setVerticalAlignment(SwingConstants.TOP);
    objLabel.setTransferHandler(new DepositTransferHandler());
    objLabel.addMouseListener(new DragMouseAdapter());
    infoLabel = new JLabel(" ");
    objectPanel = new JPanel(new GridBagLayout());
    
    doneButton = new JButton(appUI.getStr("close"));
    updateButton = new JButton(appUI.getStr("update"));
    deleteButton = new JButton(appUI.getStr("delete"));
    encryptButton = new JButton(appUI.getStr("encrypt"));
    saveButton = new JButton(appUI.getStr("save_to_file"));
    
    int y = 0;
    JPanel p = new JPanel(new GridBagLayout());
    //p.add(objLabel, GridC.getc().xy(0,y++).wx(1).insets(15,15,15,15).fillboth());
    //p.add(infoLabel, GridC.getc().xy(0,y++).wx(1).insets(0,15,15,15).fillboth());
    p.add(objectPanel, GridC.getc(0, y++).wxy(1, 1).insets(15,15,15,15).fillboth());
    
    JPanel bp = new JPanel(new GridBagLayout());
//    //bp.add(updateButton, GridC.getc().xy(0,0).insets(0,0,0,10));
//    //bp.add(encryptButton, GridC.getc().xy(1,0).insets(0,0,0,10));
//    bp.add(deleteButton, GridC.getc(2, 0).insets(0,0,0,10));
//    bp.add(Box.createHorizontalStrut(40), GridC.getc(3, 0).wx(1));
//    //bp.add(saveButton, GridC.getc(4, 0));
//    p.add(bp, GridC.getc(0, y++).wx(1).insets(10,10,10,10).fillboth());
    
    getContentPane().add(p);
    
    doneButton.addActionListener(this);
    updateButton.addActionListener(this);
    encryptButton.addActionListener(this);
    deleteButton.addActionListener(this);
    saveButton.addActionListener(this);
    
    setWindowLocation();
    //setSize(300, getPreferredSize().height);
  }
  
  
  public boolean importFiles(java.util.List files) {
    if(obj==null) {
      System.err.println("Error, attempted drag-import with no object specified");
      return false;
    }
    
    StringBuffer sb = new StringBuffer();
    sb.append(appUI.getStr("confirm_store_obj"));
    sb.append("\n");
    for(int i=0; i<files.size(); i++) {
      sb.append(appUI.getStr("file"));
      sb.append(((File)files.get(i)).getName());
    }
    JOptionPane.showConfirmDialog(this, appUI.getStr("confirm_store_obj"));
    
    for (int i = 0; i < files.size(); i++) {
      File file = (File)files.get(i);
      String elementID = i==0 ? "content" : "content-"+i;
      FileInputStream in = null;
      try {
        obj.getDataElement(elementID).write(in = new FileInputStream(file));
      } catch (Exception e) {
        System.err.println("Unable to read from file " +
                           file.getName()+"; error: "+e);
        return false;
      } finally {
        try { in.close(); } catch (IOException e) { }
      }
    }
    return true;
  }
  
  
  public void setObject(DigitalObject obj, int mode) {
    this.obj = obj;
    this.mode = mode;
    System.err.println("Setting object: "+obj);
    objLabel.setText(obj.getID());
    //infoLabel.setText(appUI.getStr("inspecting_obj"));
    objectPanel.removeAll();
    objectPanel.add(new DOViewPanel(appUI, obj), GridC.getc(0,0).wxy(1,1).fillboth());
    
    pack();
    setSize(300, getPreferredSize().height);
    
    // TODO: set an appropriate label
    //objLabel.setIcon(appUI.getImages().getIcon(DOImages.DOCUMENT));
    setTitle(String.valueOf(obj));
  }
  
  
  private void getElement(String elementID) {
    DigitalObject obj = this.obj;
    
    System.err.println("Getting element "+elementID+" of object "+obj);
    if(obj==null || elementID==null) return;
    
    //PrivateKey myKeys[] = new PrivateKey[] { appUI.getPrivateKey() };
    //if(myKeys[0]==null) {
    //  System.err.println("No private keys available!");
    //  return;
    //}

    /*
    SecretKey docKey = null;
    if(elementID.endsWith(".encrypted")) {
      // we'll need a key to open this... get it
      
      System.err.println("TODO:  Get the key to unlock the data...");
      
      // look in the object itself for any keys that may have been left for us
      String elements[] = null;
      try {
        elements = obj.listElements();
        ArrayList potentialKeys = new ArrayList();
        for(int i=0; i<elements.length; i++) {
          // look for the element that contains a key we can use
          if(elements[i].startsWith(KEY_ELEMENT_PREFIX)) {
            // check read the key in case it belongs to us!
            try {
              DocumentKey keyElement = 
                DocumentKey.readKeyFromStream(obj.getDataElement(elements[i]));
              docKey = keyElement.extractSecretKey(appUI.getAuthentication(false).getID(),
                                                   myKeys);
              break;
            } catch (Exception e) {
              // unable to open key... it probably wasn't ours
            }
          }
        }
      } catch (Exception e) {
        System.err.println("Unable to list elements for object "+obj);
      }
      
      if(docKey==null) {
        // there wasn't a key for us in the object itself;  check our keyring
        DNAKeyRing keyring = appUI.getKeyRing();
        
        // TODO: scan our keyring for the key that is to be used
      }
    }
    */
    
    // ask for the location in which to save this file/element...
    JFileChooser fwin = new JFileChooser();
    //fwin.setSelectedFile(new File(elementID));
    fwin.setFileFilter(new javax.swing.filechooser.FileFilter() {
      public boolean accept(File f) {
        return !f.isDirectory() && f.canRead();
      }
      public String getDescription() {
        return "Readable Files";
      }
    });
    int returnVal = fwin.showSaveDialog(this);
    if(returnVal!=JFileChooser.APPROVE_OPTION) return;
    
    
    File f = fwin.getSelectedFile();
    FileOutputStream fout = null;
    InputStream in = null;
    try {
      in = obj.getDataElement(elementID).read();
      
      // if the element is encrypted, a decrypting cipher stream should be
      // inserted here
      
      fout = new FileOutputStream(f);
      byte buf[] = new byte[10000];
      int r;
      while((r=in.read(buf))>=0) fout.write(buf, 0, r);
      fout.close();
    } catch (Exception e) {
      appUI.showErrorMessage(thisObj, appUI.getStr("error_getting_element")+"\n\n  "+e);
      return;
    } finally {
      try { in.close(); } catch (Exception e) { }
      try { fout.close(); } catch (Exception e) { }
    }
  }
  
  private void doneButtonPressed() {
    canceled = false;
    setVisible(false);
  }
  
  private void saveObject() {
    getElement("content");
  }
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==doneButton) {
      doneButtonPressed();
    } else if(src==saveButton) {
      saveObject();
    } else if(src==updateButton) {
      JOptionPane.showMessageDialog(this, "Not Implemented Yet");
    } else if(src==deleteButton) {
      int result = JOptionPane.showConfirmDialog(null, appUI.getStr("confirm_delete_obj"),
                                                 appUI.getStr("confirm"), 
                                                 JOptionPane.OK_CANCEL_OPTION,
                                                 JOptionPane.PLAIN_MESSAGE);
      if(result==JOptionPane.OK_OPTION) {
        try {
          obj.getRepository().deleteDigitalObject(obj.getID());
        } catch (Throwable t) {
          appUI.showErrorMessage(this, appUI.getStr("del_obj_err"));
        }
      }
    } else if(src==encryptButton) {
      JOptionPane.showMessageDialog(this, "Not Implemented Yet");
    }
  }
  
  /** Process a click on a link */
  public void linkActivated(Object target, java.awt.event.InputEvent evt) {
    if(target==null) return;
    String targetStr = String.valueOf(target);
    if(targetStr.startsWith("element:")) {
      String elementID = targetStr.substring("element:".length());
      getElement(elementID);
    }
  }
  
  public class DragMouseAdapter extends MouseAdapter {
    public void mousePressed(MouseEvent e) {
      JComponent c = (JComponent)e.getSource();
      TransferHandler handler = c.getTransferHandler();
      if(handler==null) return;
      handler.exportAsDrag(objLabel, e, TransferHandler.COPY);
    }
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
        System.err.println("Got import operation: "+t+" on component "+c);
        if(!canImport(c, t.getTransferDataFlavors())) {
          return false;
        }
        
        if (hasFileFlavor(t.getTransferDataFlavors())) {
          java.util.List files = (java.util.List)t.getTransferData(DataFlavor.javaFileListFlavor);
          return importFiles(files);
        }
      } catch (Exception e) {
        System.err.println("error importing: "+e);
      }
      return false;
    }
  }

  
}
