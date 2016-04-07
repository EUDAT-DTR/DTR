/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/


package net.cnri.apps.dogui.view;

import net.cnri.guiutil.*;
import net.handle.hdllib.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.awt.datatransfer.StringSelection;

public class NewUserWindow 
  extends JDialog
  implements ActionListener
{
  public static final String DEFAULT_PUBKEY_ALG = "RSA";
  public static final int DEFAULT_PUBKEY_SIZE = 2048;
  
  private AdminToolUI ui;
  private JButton submitKeys;
  private JButton generateKeys;
  private JButton browsePrivKey;
  private JButton browsePubKey;
  private JButton doneButton;
  private JLabel privKeyLabel;
  private JLabel pubKeyLabel;
  private JPasswordField passField;
  private JPasswordField passField2;
  
  public NewUserWindow(AdminToolUI ui) {
    super((JFrame)null, ui.getStr("new_user"), true);
    this.ui = ui;
    
    // get the default key locations
    String privKeyStr = "private_key";
    String pubKeyStr = "public_key";
    try {
      File userHome = new File(System.getProperty("user.home", "."));
      privKeyStr = new File(userHome, privKeyStr).getAbsolutePath();
      pubKeyStr = new File(userHome, pubKeyStr).getAbsolutePath();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
    
    privKeyLabel = new JLabel(privKeyStr);
    pubKeyLabel = new JLabel(pubKeyStr);
    passField = new JPasswordField("");
    passField2 = new JPasswordField("");
    generateKeys = new JButton(ui.getStr("gen_keys"));
    submitKeys = new JButton(ui.getStr("submit_key"));
    doneButton = new JButton(ui.getStr("done"));
    
    browsePrivKey = new JButton(ui.getStr("choose_file"));
    browsePubKey = new JButton(ui.getStr("choose_file"));
    
    JPanel p = new JPanel(new GridBagLayout());
    int y = 0;
    p.add(new JLabel(ui.getStr("new_user_instructions")),
          GridC.getc(0,y++).colspan(3).insets(20,20,12,20).fillboth());
    p.add(new JLabel(ui.getStr("pub_key_file")+": "),
          GridC.getc(0,y).label());
    p.add(pubKeyLabel, GridC.getc(1,y).field());
    p.add(browsePubKey, GridC.getc(2,y++));
    p.add(new JLabel(ui.getStr("priv_key_file")+": "),
          GridC.getc(0,y).label());
    p.add(privKeyLabel, GridC.getc(1,y).field());
    p.add(browsePrivKey, GridC.getc(2,y++));
    p.add(new JLabel(ui.getStr("priv_key_passphrase")+": "),
          GridC.getc(0,y).label());
    p.add(passField, GridC.getc(1,y++).field().colspan(2));
    p.add(new JLabel(ui.getStr("priv_key_passphrase2")+": "),
          GridC.getc(0,y).label());
    p.add(passField2, GridC.getc(1,y++).field().colspan(2));
    p.add(Box.createVerticalStrut(12), GridC.getc(0,y++).wy(1));
    
    JPanel bp = new JPanel(new GridBagLayout());
    bp.add(doneButton, GridC.getc(0,0));
    bp.add(Box.createHorizontalStrut(100), GridC.getc(1,0).wx(1));
    bp.add(submitKeys, GridC.getc(2,0));
    bp.add(Box.createHorizontalStrut(12), GridC.getc(3,0));
    bp.add(generateKeys, GridC.getc(4,0));
    p.add(bp, GridC.getc(0,y++).colspan(3).fillx());
    p.setBorder(new EmptyBorder(12,20,20,20));
    
    getContentPane().add(p);
    
    doneButton.addActionListener(this);
    submitKeys.addActionListener(this);
    generateKeys.addActionListener(this);
    browsePubKey.addActionListener(this);
    browsePrivKey.addActionListener(this);
    
    updateSubmitStatus();
    
    pack();
    Dimension sz = getPreferredSize();
    sz.width = Math.min(sz.width, 500);
    sz.height += 150;
    setSize(sz);
    setLocationRelativeTo(null);
  }

  private void updateSubmitStatus() {
    boolean submittable = false;
    try { 
      File f = new File(pubKeyLabel.getText());
      submittable = f.exists() && f.canRead();
    } catch (Exception e) {}
    submitKeys.setEnabled(submittable);
  }
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==browsePubKey) {
      FileDialog fwin = new FileDialog(this, ui.getStr("pub_key_file"), FileDialog.SAVE);
      try {
        File f = new File(pubKeyLabel.getText());
        fwin.setFile(f.getName());
        fwin.setDirectory(f.getParent());
      } catch (Exception e) {}
      fwin.setVisible(true);
      String newDir = fwin.getDirectory();
      String newFile = fwin.getFile();
      if(newDir==null || newFile==null) return;
      pubKeyLabel.setText(new File(newDir, newFile).getAbsolutePath());
    } else if(src==browsePrivKey) {
      FileDialog fwin = new FileDialog(this, ui.getStr("priv_key_file"), FileDialog.SAVE);
      try {
        File f = new File(privKeyLabel.getText());
        fwin.setFile(f.getName());
        fwin.setDirectory(f.getParent());
      } catch (Exception e) {}
      fwin.setVisible(true);
      String newDir = fwin.getDirectory();
      String newFile = fwin.getFile();
      if(newDir==null || newFile==null) return;
      privKeyLabel.setText(new File(newDir, newFile).getAbsolutePath());
    } else if(src==doneButton) {
      setVisible(false);
    } else if(src==submitKeys) {
      submitKeys();
    } else if(src==generateKeys) {
      generateKeys();
    }
  }
  
  private String nicePKBytes = null;
  
  private void submitKeys() {
    DataInputStream in = null;
    try {
      // read the private key bytes
      File pubKeyFile = new File(pubKeyLabel.getText());
      in = new DataInputStream(new FileInputStream(pubKeyFile));
      byte pubKeyBytes[] = new byte[(int)pubKeyFile.length()];
      in.readFully(pubKeyBytes);
      in.close();
      
      String niceBytes = "---begin DO pubkey---\n" +
        Util.decodeHexString(pubKeyBytes, true) +
        "\n---end DO pubkey---\n";
      nicePKBytes = niceBytes;
      JPanel p = new JPanel(new GridBagLayout()) {
        public Dimension getPreferredSize() {
          Dimension dim = super.getPreferredSize();
          dim.width = 450;
          dim.height = 400;
          return dim;
        }
      };
      JButton clipboardButton = new JButton(ui.getStr("copy_to_clipboard"));
      clipboardButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          StringSelection ss = new StringSelection(nicePKBytes);
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ss, ss);
        }
      });
      p.add(new JLabel(ui.getStr("submit_key_instructions")),
            GridC.getc(0,0).wx(1).fillboth().insets(0,0,12,0));
      p.add(new JScrollPane(new JTextArea(niceBytes)),
            GridC.getc(0,1).wy(1).fillboth());
      p.add(clipboardButton,
            GridC.getc(0,2).center());
      
      JOptionPane.showMessageDialog(this, p, ui.getStr("submit_key"), 
                                    JOptionPane.PLAIN_MESSAGE);
    } catch (Exception e) {
      ui.showErrorMessage(this, "Error submitting keys: "+e);
    } finally {
      try { in.close(); } catch (Exception e) {}
    }
  }
  
  
  private void generateKeys() {
    String privKeyFileStr = privKeyLabel.getText();
    String pubKeyFileStr = pubKeyLabel.getText();
    
    if(privKeyFileStr.trim().length()<=0) {
      ui.showErrorMessage(this, "Please select a file to store your new private key");
      return;
    }
    if(pubKeyFileStr.trim().length()<=0) {
      ui.showErrorMessage(this, "Please select a file to store your new public key");
      return;
    }
    File privKeyFile = new File(privKeyFileStr);
    File pubKeyFile = new File(pubKeyFileStr);
    
    if(privKeyFile.exists() || pubKeyFile.exists()) {
      if(JOptionPane.showConfirmDialog(this, ui.getStr("overwrite_keys_question"),
                                       ui.getStr("question"), JOptionPane.YES_NO_OPTION)
         != JOptionPane.YES_OPTION) {
        return;
      }
    }
    
    try {
      String passphrase = new String(passField.getPassword()).trim();
      String passphrase2 = new String(passField2.getPassword()).trim();
      if(!passphrase.equals(passphrase2)) {
        ui.showErrorMessage(this, ui.getStr("passphrase_mismatch_err"));
        return;
      }

      KeyPairGenerator kpg = KeyPairGenerator.getInstance(DEFAULT_PUBKEY_ALG);
      kpg.initialize(DEFAULT_PUBKEY_SIZE);
      KeyPair keys = kpg.generateKeyPair();
      
      byte secKey[] = null;
      if (passphrase.trim().length()>0) {
        secKey = Util.encodeString(passphrase);
      }
      
      // Get the bytes making up the private key
      byte keyBytes[] = Util.getBytesFromPrivateKey(keys.getPrivate());
      
      if (secKey!=null) {  // Encrypt the private key bytes
        byte oldKeyBytes[] = keyBytes;
        keyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_PBKDF2_AES_CBC_PKCS5);
        for (int i = 0; i < oldKeyBytes.length; i++)
          oldKeyBytes[i] = (byte)0;
        for (int i = 0; i < secKey.length; i++)
          secKey[i] = (byte)0;
      } else {
        keyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_NONE);
      }
      
      // Save the private key to the file
      FileOutputStream keyOut = new FileOutputStream(privKeyFile);
      keyOut.write(keyBytes);
      keyOut.close();
      
      // Save the public key to the file
      keyOut = new FileOutputStream(pubKeyFile);
      keyOut.write(Util.getBytesFromPublicKey(keys.getPublic()));
      keyOut.close();
    } catch (Exception e) {
      e.printStackTrace(System.err);
      ui.showErrorMessage(this, "Error generating, encrypting, or storing keys: "+e);
      return;
    }
    updateSubmitStatus();
    //submitKeys();
  }
  
}
