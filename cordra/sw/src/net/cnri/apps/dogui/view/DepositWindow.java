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
import net.cnri.guiutil.GridC;
import java.awt.*;
import java.awt.event.*;
import javax.crypto.SecretKey;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import javax.activation.*;


public class DepositWindow
  extends JDialog
  implements Runnable
{
  private static final String CONTENT_ELEMENT_ID = "content";
  private static final String RIGHTS_ELEMENT_ID = "internal.rights";
  
  private static final int FOLDER_INC_SRC = 0;
  private static final int FOLDER_EXC_SRC = 1;
  private static final int FOLDER_SPECIFIED = 2;
  private PreDepositWindow preDepositWin;
  private AdminToolUI ui;
  private Thread depositThread = null;
  private DepositWindow thisWin;
  
  private File filesToDeposit[] = null;
  private int folderType = FOLDER_SPECIFIED;
  private String notes = "";
  private byte rights[] = null;
  private String folderName = "";
  private boolean encryptContent = false;
  private boolean encryptAttributes = false;
  private Object encryptFor[] = {};
  private boolean newKeyForEachObject = false;
  
  private DOKeyRing keyRing = null;
  private DigitalObject userObj = null;
  private boolean finished = false;
  private JLabel progressLabel;
  private JProgressBar progressBar;
  private JButton cancelButton;
  private String progressStr = "";
  private SecretKey encryptionKey = null;
  private float progressVal = 0f;
  private String repositoryID = null;
  private String storageHint = null;
  
  private static FileTypeMap fileTypeMap = null;
  
  
  public DepositWindow(AdminToolUI mainUI, PreDepositWindow depWin) {
    this.ui = mainUI;
    this.preDepositWin = depWin;
    this.thisWin = this;
    this.userObj = ui.getUserObject();
    this.keyRing = ui.getKeyRing();
    
    initFileTypeMap();
    
    progressLabel = new JLabel(" ");
    progressBar = new JProgressBar();
    cancelButton = new JButton(ui.getStr("cancel"));
    
    JPanel p = new JPanel(new GridBagLayout());
    p.add(progressLabel, GridC.getc().xy(0,0).wx(1).fillx());
    p.add(progressBar, GridC.getc().xy(0,1).wx(1).fillx().insets(10,0,0,0));
    p.add(Box.createVerticalStrut(10), GridC.getc().xy(0,2).wy(1));
    p.add(Box.createHorizontalStrut(200), GridC.getc().xy(0,3));
    p.add(cancelButton, GridC.getc().xy(0,4).east().insets(10,0,0,0));
    p.setBorder(new EmptyBorder(10,10,10,10));
    
    setContentPane(p);
    pack();
    Dimension prefSz = getPreferredSize();
    prefSz.width = Math.max(prefSz.width, 500);
    setSize(prefSz);
    setLocationRelativeTo(null);
    
    Runnable depositor = new Runnable() {
      public void run() {
        ArrayList fileList = new ArrayList();
        File filesToDeposit[] = thisWin.filesToDeposit;
        try {
          for(int i=0; filesToDeposit!=null && i<filesToDeposit.length; i++) {
            resolveFiles(fileList, null, filesToDeposit[i]);
          }
        } catch (Exception e) {
          progressStr = ui.getStr("err_reading_files")+": "+e;
          finished = true;
          SwingUtilities.invokeLater(thisWin);
          return;
        }
        
        if(finished) return;
        
        Repository outbox;
        try {
          if(repositoryID!=null) {
            outbox = ui.getRepositoryByID(repositoryID);
          } else {
            outbox = ui.getRepository(AdminToolUI.OUTBOX);
          }
        } catch (Exception e) {
          progressStr = ui.getStr("err_opening_connection")+": "+e;
          finished = true;
          return;
        }
        
        if(finished) return;
        
        // if there is any encryption happening, create the key
        
        
        
        long numBytes = 0;
        long numObjectsDeposited = 0;
        long startTime = System.currentTimeMillis();
        int numFiles = fileList.size();
        for(Iterator it=fileList.iterator(); it.hasNext(); ) {
          if(finished) return;
          
          FileItem fi = (FileItem)it.next();
          
          progressStr = ui.getStr("depositing_file")+": "+fi;
          
          InputStream in = null;
          try {
            if(encryptContent && (encryptionKey==null || newKeyForEachObject)) {
              // create the new encryption key
              SecretKey encKey = keyRing.generateEncryptionKey();
              
              // share the key with ourselves and then the intended recipients
              progressStr = ui.getStr("granting_key_to")+' '+userObj.getID();
              SwingUtilities.invokeLater(thisWin);
              
              DOKeyRing.grantKeyTo(userObj, encKey);
              for(int ri=0; encryptFor!=null && ri < encryptFor.length; ri++) {
                Object recip = encryptFor[ri];
                if(recip==null) continue;
                DigitalObject doRecipient = null;
                System.err.println("sending to recipient: "+recip+"; class="+recip.getClass());
                
                progressStr = ui.getStr("granting_key_to")+' '+recip;
                SwingUtilities.invokeLater(thisWin);
                
                if(recip instanceof DigitalObject) {
                  doRecipient = (DigitalObject)recip;
                } else if(recip instanceof EntityMap.Entity) {
                  doRecipient = 
                    ui.getObjectReference(null, 
                                          ((EntityMap.Entity)recip).
                                          getAttribute(EntityMap.ID_ATTRIBUTE, ""));
                } else {
                  doRecipient = ui.getObjectReference(null, recip.toString());
                }
                DOKeyRing.grantKeyTo(doRecipient, encKey);
              }
              encryptionKey = encKey;
            }
            DigitalObject newObject = outbox.createDigitalObject(null);
            progressStr = ui.getStr("depositing")+' '+(numObjectsDeposited+1)+
              " of "+numFiles+": "+newObject.getID();
            SwingUtilities.invokeLater(thisWin);

            String storageKey = storageHint;
            if(storageKey!=null && storageKey.trim().length()>=0) {
              newObject.setAttribute("doserver.storagekey", storageKey.trim());
            }
            
            // store the access rights, if any
            if(rights!=null && rights.length>0) {
              DataElement rightsElement = newObject.getDataElement(RIGHTS_ELEMENT_ID);
              rightsElement.write(new ByteArrayInputStream(rights));
            }
            
            String mimeType = fileTypeMap.getContentType(fi.file);
            DataElement content = newObject.getDataElement(CONTENT_ELEMENT_ID);
            
            if(userObj!=null) {
              newObject.setAttribute(DOConstants.OWNER_ATTRIBUTE, userObj.getID());
              newObject.setAttribute(DOConstants.CREATOR_ATTRIBUTE, userObj.getID());
            }
            newObject.setAttribute(DOConstants.NOTES_ATTRIBUTE, notes);
            content.setAttribute(DOConstants.FILE_NAME_ATTRIBUTE, fi.file.getName());
            if(mimeType!=null)
              content.setAttribute(DOConstants.MIME_TYPE_ATTRIBUTE, mimeType);
            if(fi.folderName!=null)
              newObject.setAttribute(DOConstants.FOLDER_ATTRIBUTE, fi.folderName);
            
            // if it is big, wrap a ProgressMonitorInputStream around it
            long fileLength = fi.file.length();
            if(fileLength>500000) {
              in = new ProgressMonitorInputStream(thisWin, fi.file.getName(), 
                                                  new FileInputStream(fi.file));
            } else {
              in = new FileInputStream(fi.file);
            }
            if(encryptContent) {
              numBytes += keyRing.writeEncryptedElement(content, encryptionKey, in);
            } else {
              numBytes += content.write(in);
            }
            numObjectsDeposited++;
            if(finished) { break; }
            progressVal = numObjectsDeposited/(float)numFiles;
          } catch (Throwable e) {
            e.printStackTrace(System.err);
            progressStr = ui.getStr("err_not_created")+" "+e;
            finished = true;
            break;
          } finally {
            try { in.close(); } catch (Exception e) {}
          }
        }
        
        if(!finished) {
          long time = System.currentTimeMillis()-startTime;
          String msg = ui.getStr("finished_deposit");
          msg = replaceAll(msg, "{num_objects}", String.valueOf(numObjectsDeposited));
          msg = replaceAll(msg, "{num_bytes}", String.valueOf(numBytes));
          msg = replaceAll(msg, "{time}", getNiceTime(time));
          msg = replaceAll(msg, "{bandwidth}", getNiceBandwidth(Math.round(numBytes/time*1000f)));
          progressStr = msg;
        }
        finished = true;
        SwingUtilities.invokeLater(thisWin);
      }
    };
    depositThread = new Thread(depositor);
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        cancelButtonPressed();
      }
    });
  }
  
  private static final long MILLIS_IN_HOUR = 60000 * 60;
  private static final long MILLIS_IN_MINUTE = 60000;
  private static final long MILLIS_IN_SECOND = 1000;
  
  private final void initFileTypeMap() {
    if(fileTypeMap!=null) return;
    try {
      InputStream in = getClass().
        getResourceAsStream("/net/cnri/apps/dogui/view/resources/mime.types");
      fileTypeMap = new MimetypesFileTypeMap(in);
      FileTypeMap.setDefaultFileTypeMap(fileTypeMap);
    } catch (Exception e) {
      System.err.println("Error initializing mime type map: "+e);
    }
  }
  
  private static String getNiceBandwidth(long numBytes) {
    return DOUtil.niceSize(numBytes)+"/sec";
  }
  
  private String getNiceTime(long millis) {
    StringBuffer sb = new StringBuffer();
    if(millis<1000) return ui.getStr("under_1_sec");
    if(millis > MILLIS_IN_HOUR) {
      long hours = millis/MILLIS_IN_HOUR;
      sb.append(hours+" "+ui.getStr("time_hours"));
      millis -= hours * MILLIS_IN_HOUR;
    }
    
    if(millis > MILLIS_IN_MINUTE) {
      long minutes = millis/MILLIS_IN_MINUTE;
      if(sb.length()>0) sb.append(", ");
      sb.append(""+minutes+" "+ui.getStr("time_minutes"));
      millis -= minutes * MILLIS_IN_MINUTE;
    }
    
    if(millis > MILLIS_IN_SECOND) {
      if(sb.length()>0) sb.append(", ");
      long seconds = millis/MILLIS_IN_SECOND;
      sb.append(" "+seconds+" "+ui.getStr("time_seconds"));
      millis -= seconds * MILLIS_IN_SECOND;
    }
    return sb.toString();
  }
  
  private void cancelButtonPressed() {
    if(finished) {
      setVisible(false);
    } else {
      finished = true;
    }
  }
  
  public void setRepositoryID(String repoID) {
    this.repositoryID = repoID;
  }
  
  public void setStorageHint(String storageHint) {
    if(storageHint!=null && storageHint.trim().length()<=0) {
      storageHint = null;
    }
    this.storageHint = storageHint;
  }
  
  public void setFiles(File toDeposit[]) {
    this.filesToDeposit = toDeposit;
  }
  
  public void setNotes(String notes) {
    this.notes = notes;
  }
  
  public void setEncryptContent(boolean encryptContent) {
    this.encryptContent = encryptContent;
  }
  
  public void setEncryptAttributes(boolean encryptAttributes) {
    this.encryptAttributes = encryptAttributes;
  }
  
  public void setEncryptFor(Object encryptFor[]) {
    this.encryptFor = encryptFor;
  }
  
  
  /** Set the rights/permissions for the objects being deposited.
   * Note: If the given array is null or empty then no access rules will be
   * stored which means that the repository's default access rights will apply
   * to the object. */
  public void setRights(byte rights[]) {
    this.rights = rights;
  }
  
  public void setUseSourceFolder() {
    this.folderType = FOLDER_INC_SRC;
  }
  
  public void setUseSubFolder() {
    this.folderType = FOLDER_EXC_SRC;
  }
  
  public void setUseFolder(String folderName) {
    this.folderName = folderName;
  }
  
  private void resolveFiles(ArrayList allFiles, String folderName, File fileToResolve) 
    throws Exception
  {
    if(finished) return;
    if(fileToResolve==null) return;
    
    if(fileToResolve.isHidden()) {
      return;
    } else if(fileToResolve.isFile()) {
      allFiles.add(new FileItem(fileToResolve, folderName));
    } else if(fileToResolve.isDirectory()) {
      if(folderType==FOLDER_EXC_SRC && folderName==null) {
        folderName = "";
      } else if(folderName!=null) {
        if(folderName.length()>0) folderName += "/";
        folderName += fileToResolve.getName();
      }
      File files[] = fileToResolve.listFiles();
      for(int i=0; files!=null && i<files.length; i++) {
        resolveFiles(allFiles, folderName, files[i]);
      }
    }
  }
  
  public void run() {
    if(finished) {
      progressBar.setVisible(false);
      cancelButton.setText(ui.getStr("close"));
    } else {
      progressBar.setValue(Math.round(progressVal * 100));
    }
    progressLabel.setText("<html>"+progressStr+"</html>");
    repaint();
  }

  private class FileItem {
    File file;
    String folderName;
    
    FileItem(File file, String folderName) {
      this.file = file;
      this.folderName = folderName;
    }
    
    public String toString() {
      return file.getName() + (folderName==null ? "" : " in folder "+folderName);
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

  

  
  
  void doDeposit() {
    setVisible(true);
    depositThread.start();
  }
  
}