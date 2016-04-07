/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doutilgui;

import net.cnri.cert.CertUtil;
import net.cnri.dobj.*;
import net.cnri.guiutil.GridC;
import net.handle.hdllib.*;
import net.handle.awt.*;
import net.cnri.util.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.security.*;
import java.io.*;
import java.security.cert.*;

/**
 * Window that can be used to gather authentication information from a user
 * such as their identifier and private/secret key.
 */
public class AuthWindow 
  extends JDialog 
  implements ActionListener
{
  private JComboBox authTypeChoice;
  private static final char[] keystorePass =  "-".toCharArray();
  
  private JTextField idField;
  private JPasswordField passField;
  private JCheckBox saveKeyBox;
  private JLabel privKeyLabel;
  private JLabel keyFieldLabel;
  private JLabel passFieldLabel;
  private JButton anonButton;
  private JButton okButton;
  private JButton cancelButton;
  private JButton loadKeyButton;
  private String authRole = "default";
  private ActionListener extraHandler = null;
  
  private File privKeyFile = null;
  private PrivateKey privKey = null;
  private boolean wasCanceled = true;
  private StreamTable storedSettings = null;
  
  private KeyStore keyStore = null;

  public AuthWindow(Frame owner)
    throws HeadlessException
  {
    this(owner, "default");
  }
  
  /** 
   * Constructor for a window object that provides an interface for a user
   * to enter their authentication information.  The given roleID provides
   * a key to the type of authentication that is being performed, so that
   * the appropriate default login and key file are used.
   */
  public AuthWindow(Frame owner, String roleID)
    throws HeadlessException
  {
    super(owner, "Authentication Details", true);
    
    if(roleID!=null)
      authRole = roleID;
    
    storedSettings = new StreamTable();
    
    try {
      storedSettings.readFromFile(System.getProperty("user.home", "")+ File.separator + ".do_authentication");
    } catch (Exception e) {}
    
    String authID = storedSettings.getStr("id_"+authRole, null);
    try {
      String osName = System.getProperty("os.name", "").toUpperCase();
      boolean isWindows = osName.indexOf("WINDOWS")>=0; 
      String keyStoreName = isWindows ? "Windows-MY" : "KeychainStore"; // KeychainStore works on mac... maybe other platforms too?
      if(keyStoreName!=null) {
        keyStore = KeyStore.getInstance(keyStoreName);
        keyStore.load(null, null);
        
        if(authID!=null) {
          //System.err.println(" key:"+keyStore.getKey(keyID, keystorePass));
          System.err.println(" isKeyEntry: "+keyStore.isKeyEntry(authID));
          System.err.println(" isCertEntry: "+keyStore.isCertificateEntry(authID));
          
          if(keyStore.isKeyEntry(authID)) {
            PrivateKey key = (PrivateKey)keyStore.getKey(authID, "-".toCharArray());
            System.err.println("got key: "+key+" class:"+(key==null?"" : key.getClass().toString()));
            privKey = key;
          }
          
          //if(keyStore.isCertificateEntry(authID)) {
            java.security.cert.Certificate cert = keyStore.getCertificate(authID);
            System.err.println("got cert: "+cert+" class:"+(cert==null?"" : cert.getClass().toString()));
          //}

          //KeyStore.Entry entry = keyStore.getEntry(authID, new KeyStore.PasswordProtection("-".toCharArray()));
          //System.err.println("got entry: "+entry+" class:"+(entry==null?"" : entry.getClass().toString()));
          
//          KeyStore.Entry keyEntry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(authID, new KeyStore.PasswordProtection(keystorePass));
//          System.err.println("entry '"+authID+"' loaded successfully: class:"+
//                             (keyEntry==null ? keyEntry : keyEntry.getClass())+"   entry:"+keyEntry);
//          if(keyEntry!=null && keyEntry instanceof KeyStore.PrivateKeyEntry) {
//            this.privKey = ((KeyStore.PrivateKeyEntry)keyEntry).getPrivateKey();
//            this.privKeyFile = null;
//            saveKeyBox.setSelected(false);
//          }
          
        }
      }
    } catch (Exception e) {
      System.err.println("error attempting to load from Mac OS X keychain: "+e);
      e.printStackTrace(System.err);
    }
    
    if(privKey==null) {
      String privKeyFileStr = storedSettings.getStr("keyfile_"+authRole, null);
      if(privKeyFileStr!=null) {
        privKeyFile = new File(privKeyFileStr);
      }
    }
    
    idField = new JTextField(authID==null ? "" : authID, 30);
    passField = new JPasswordField(20);
    okButton = new JButton("OK");
    cancelButton = new JButton("Cancel");
    loadKeyButton = new JButton("Select...");
    
    anonButton = new JButton("Anonymous");
    authTypeChoice = new JComboBox(new String [] {
      "Private Key", "Password"
    });
    if(storedSettings.getStr("authtype", "").equals("seckey")) {
      authTypeChoice.setSelectedIndex(1);
    } else {
      authTypeChoice.setSelectedIndex(0);
    }
    
    privKeyLabel = new JLabel("no key loaded");
    privKeyLabel.setForeground(Color.gray);
    
    saveKeyBox = new JCheckBox("Save in System Keychain");
    saveKeyBox.setSelected(false);
    
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(new javax.swing.border.EmptyBorder(10,10,10,10));
    int y = 0;
    p.add(new JLabel("Your ID: "), GridC.getc(0,y).label()); 
    p.add(idField, GridC.getc(1,y++).field().colspan(2));
    
    p.add(new JLabel("Authentication Type: "), GridC.getc(0,y).label());
    p.add(authTypeChoice, GridC.getc(1,y++).field().colspan(2));
    
    p.add(keyFieldLabel = new JLabel("Private Key File: "), GridC.getc(0,y).label()); 
    p.add(privKeyLabel, GridC.getc(1,y).field());
    p.add(loadKeyButton, GridC.getc(2,y++));
    p.add(saveKeyBox, GridC.getc(1,y++).west());
    
    p.add(passFieldLabel = new JLabel("Password: "), GridC.getc(0,y).label()); 
    p.add(passField, GridC.getc(1,y++).field().colspan(2));
    p.add(Box.createVerticalStrut(10), GridC.getc(1,y++).wy(1));
    JPanel bp = new JPanel(new GridBagLayout());
    bp.add(anonButton, GridC.getc(0,0).west().wx(1).insets(0,0,0,20));
    bp.add(cancelButton, GridC.getc(2,0).insets(0,0,0,20));
    bp.add(okButton, GridC.getc(3,0).insets(0,0,0,20));
    p.add(bp, GridC.getc(0,y++).wx(1).colspan(3).fillboth());
    
    okButton.addActionListener(this);
    cancelButton.addActionListener(this);
    loadKeyButton.addActionListener(this);
    anonButton.addActionListener(this);
    authTypeChoice.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent evt) {
        authTypeSelected();
      }
    });
    
    getContentPane().add(p);
    getRootPane().setDefaultButton(okButton);
    
    //setRootPane().setDefaultButton(okButton);
    pack();
    setSize(getPreferredSize());
    setLocationRelativeTo(null);
    
    authTypeSelected();
  }
  
  
  public void setExtraHandler(String actionName, ActionListener handler) {
    if(actionName==null || handler==null)
      throw new NullPointerException();
    anonButton.setText(actionName);
    this.extraHandler = handler;
  }
  
  private void authTypeSelected() {
    boolean pubkey = authTypeChoice.getSelectedIndex()==0;
    keyFieldLabel.setVisible(pubkey);
    privKeyLabel.setVisible(pubkey);
    saveKeyBox.setVisible(pubkey);
    loadKeyButton.setVisible(pubkey);
    passFieldLabel.setVisible(!pubkey);
    passField.setVisible(!pubkey);
    if(pubkey) {
      updateKeyLabel();
    }
    validate();
  }
  
  private void updateKeyLabel() {
    PrivateKey key = privKey;
    if(key!=null) {
      byte encoded[] = key.getEncoded();
      String encodedStr = "???";
      if(encoded!=null)
        encodedStr = Util.decodeHexString(encoded, false);
      privKeyLabel.setText("Algorithm: "+key.getAlgorithm()+
          "; Format: "+key.getFormat());
    } else if(privKeyFile!=null) {
      privKeyLabel.setText(privKeyFile.getName());
    } else {
      privKeyLabel.setText("no key loaded - press the \"Load\" button");
    }
  }

  private void chooseKey() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select your private key file");
    int returnVal = chooser.showOpenDialog(null);
    if(returnVal != JFileChooser.APPROVE_OPTION) {
      updateKeyLabel();
      return;
    }
    
    File f = chooser.getSelectedFile();
    if(f!=null && (privKeyFile==null || !f.equals(privKeyFile))) {
      // the user selected a different file
      privKey = null;
      privKeyFile = f;
    }
    updateKeyLabel();
    return;
  }
  
  private PrivateKey loadPrivateKeyFromFile() {
    if(privKey!=null) return privKey;
    
    if(privKeyFile!=null) {
      try {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        FileInputStream fin = new FileInputStream(privKeyFile);
        byte buf[] = new byte[256];
        int r = 0;
        while((r=fin.read(buf))>=0)
          bout.write(buf, 0, r);
        buf = bout.toByteArray();
        byte passphrase[] = null;
        if(Util.requiresSecretKey(buf)) {
          // ask the user for their secret key...
          PassphrasePanel pp = new PassphrasePanel();
          int result = 
            JOptionPane.showConfirmDialog(this, pp, "Enter Passphrase", 
                                          JOptionPane.OK_CANCEL_OPTION);
          if(result==JOptionPane.OK_OPTION) {
            passphrase = new String(pp.getPassphrase()).getBytes("UTF8");
          } else {
            return null;
          }
        }
        
        buf = Util.decrypt(buf, passphrase);
        
        return Util.getPrivateKeyFromBytes(buf, 0);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Error: "+e, 
            "Error", JOptionPane.ERROR_MESSAGE);
      }
    } else {
      JOptionPane.showMessageDialog(this, "Error: Please specify a private key file", 
                                    "Error", JOptionPane.ERROR_MESSAGE);
    }
    return null;
  }
  
  private boolean checkInputs() {
    String id = idField.getText().trim();
    if(id==null || id.trim().length()<=0) {
      JOptionPane.showMessageDialog(this, 
                                    "Please enter your identifier",
                                    "No identifier specified",
                                    JOptionPane.ERROR_MESSAGE);
      return false;
    }
    
    boolean pubkey = authTypeChoice.getSelectedIndex()==0;
    if(pubkey) {
      if(privKey==null) {
        privKey = loadPrivateKeyFromFile();
        if(privKey==null) {
          return false;
        }
      }
      // save the private key into the keychain
      if(keyStore!=null && saveKeyBox.isSelected()) {
        try {
          X509Certificate certificate = CertUtil.createClientCert(DOClient.getResolver(), id, privKey, null,
                                                                  DOConstants.DEFAULT_CLIENT_CERT_EXPIRATION_DAYS);
          keyStore.setEntry(id, new KeyStore.PrivateKeyEntry(privKey, new X509Certificate[]{certificate}), 
                            new KeyStore.PasswordProtection(keystorePass));
//          keyStore.setKeyEntry(id, privKey, keystorePass, new X509Certificate[]{certificate});
          keyStore.store(null, null);
          System.err.println("stored key in keychain: " + keyStore);
        } catch (Exception e) {
          System.err.println("error storing key in keychain: "+e);
          e.printStackTrace(System.err);
        }
      }
      
    } else {
      // do nothing
    }
    return true;
  }
  
  
  private void storeValues() {
    storedSettings.put("id_"+authRole, idField.getText().trim());
    storedSettings.put("authtype", authTypeChoice.getSelectedIndex()==0 ? "pubkey" : "seckey");
    try {
      storedSettings.put("keyfile_"+authRole, privKeyFile.getCanonicalPath());
    } catch (Exception e) {}
    try {
      storedSettings.writeToFile(System.getProperty("user.home", "")+
        File.separator + ".do_authentication");
    } catch (Exception e) {}
  }
  
  /** Set the public key authentication information for this window */
  public void setAuthentication(String id, PrivateKey newKey) {
    idField.setText(id);
    this.privKey = newKey;
    updateKeyLabel();
  }
  
  /** Set the secret key authentication information for this window */
  public void setSecretKeyAuthentication(String id, String secretKey) {
    idField.setText(id);
    passField.setText(secretKey);
  }
  
  public boolean wasCanceled() {
    return wasCanceled;
  }
  
  
  public String getID() {
    return idField.getText().trim();
  }
  
  
  public PrivateKey getPrivateKey() {
    return privKey;
  }
  
  public String getSecretKey() {
    return new String(passField.getPassword());
  }
  
  
  public DOAuthentication getAuthentication() {
    if(authTypeChoice.getSelectedIndex()==0) { // return a private key-based authentication
      
      return new PKAuthentication(getID(), privKey);
    } else {
      return new SecretKeyAuthentication(getID(), Util.encodeString(new String(passField.getPassword())));
    }
  }
  
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==okButton) {
      if(checkInputs()) {
        wasCanceled = false;
        setVisible(false);
        storeValues();
      }
    } else if(src==cancelButton) {
      wasCanceled = true;
      setVisible(false);
    } else if(src==anonButton) {
      if(extraHandler!=null) {
        extraHandler.actionPerformed(evt);
      } else {
        try {
          InputStream pkIn = 
          PKAuthentication.class.getResourceAsStream("/net/cnri/dobj/etc/anonymous_privkey.bin");
          ByteArrayOutputStream bout = new ByteArrayOutputStream();
          byte buf[] = new byte[1024];
          int r;
          while((r=pkIn.read(buf))>=0)
            bout.write(buf, 0, r);
          byte keyBytes[] = Util.decrypt(bout.toByteArray(), null);
          privKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
          privKeyFile = null;
          idField.setText(DOConstants.ANONYMOUS_ID);
          wasCanceled = false;
          setVisible(false);
        } catch (Exception e) {
          JOptionPane.showMessageDialog(this, 
                                        "Error loading anonymous authentication: "+e,
                                        "Error", JOptionPane.ERROR_MESSAGE);
          return;
        }
      }
    } else if(src==loadKeyButton) {
      chooseKey();
    }
  }

  
  private class PassphrasePanel
  extends JPanel
  {
    private JPasswordField passField;
    
    PassphrasePanel() {
      setLayout(new GridBagLayout());
      setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
      passField = new JPasswordField("", 30);
      add(new JLabel("Enter the passphrase to decrypt your private key"),
          AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, false, false));
      add(passField, AwtUtil.getConstraints(0,1,1,1,1,1,false,false));
      
      passField.addAncestorListener(new AncestorListener() {
        public void ancestorAdded(AncestorEvent ancestorEvent) {
          passField.requestFocusInWindow();
        }
        public void ancestorRemoved(AncestorEvent ancestorEvent) { }
        public void ancestorMoved(AncestorEvent ancestorEvent) { }
      });
    }
    
    
    char[] getPassphrase() {
      return passField.getPassword();
    }
  }
  

}
