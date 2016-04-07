/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/


package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.handle.hdllib.*;
import net.cnri.simplexml.*;
import net.cnri.guiutil.*;
import net.cnri.util.*;

import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.security.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;


public class ManageGroupsWindow 
  extends JDialog
{
  private static X509V1CertificateGenerator v1CertGen = new X509V1CertificateGenerator();
  private static X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();
  
  public static final String GROUP_INFO_ELEMENT = "dosync.group_info";
  public static final String DEFAULT_PUBKEY_ALG = "RSA";
  public static final int DEFAULT_PUBKEY_SIZE = 2048;
  
  private DOClient doClient;
  private AdminToolUI appUI;
  private DigitalObject userObj;
  private XTag groupInfo = null;
  
  
  private JComboBox groupChoice;
  private JList memberList;
  private DOMemberModel listModel;
  private EntityMap entities;
  
  private JButton saveButton, cancelButton;
  private JButton addMemberButton, delMemberButton;
  private JButton addGroupButton, delGroupButton;
  private JButton updateCredsButton, updateAllCredsButton;
  
  static {
    try {
      Security.addProvider((Provider)Class.
                           forName("org.bouncycastle.jce.provider.BouncyCastleProvider").
                           newInstance());
      //Security.removeProvider("BC");
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
  
  
  public ManageGroupsWindow(JFrame parent, AdminToolUI ui, DigitalObject userObj) {
    super(parent, ui.getStr("manage_groups"));
    this.appUI = ui;
    this.userObj = userObj;
    this.entities = ui.getAddressBook();
    
    updateCredsButton = new JButton(new HDLAction(appUI, "update_group_creds", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { generateCreds(); }
    }));
    
    updateAllCredsButton = new JButton(new HDLAction(appUI, "update_all_group_creds", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { generateAllCreds(); }
    }));
    
    saveButton = new JButton(new HDLAction(appUI, "save_member_lists", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { savePressed(); }
    }));
        
    cancelButton = new JButton(new HDLAction(appUI, "cancel", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { cancelPressed(); }
    }));
        
    addGroupButton = new JButton(new HDLAction(null, " + ", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { addGroupPressed(); }
    }));
        
    delGroupButton = new JButton(new HDLAction(null, " - ", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { deleteGroupPressed(); }
    }));
        
    addMemberButton = new JButton(new HDLAction(null, " + ", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { addMemberPressed(); }
    }));
        
    delMemberButton = new JButton(new HDLAction(null, " - ", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { deleteMemberPressed(); }
    }));
    
    addGroupButton.putClientProperty("JButton.buttonType", "gradient");
    delGroupButton.putClientProperty("JButton.buttonType", "gradient");
    addMemberButton.putClientProperty("JButton.buttonType", "gradient");
    delMemberButton.putClientProperty("JButton.buttonType", "gradient");
    
    groupChoice = new JComboBox();
    
    memberList = new JList(listModel = new DOMemberModel());
    
    JPanel p = new JPanel(new GridBagLayout());
    int y = 0;
    p.setBorder(new javax.swing.border.EmptyBorder(10,10,10,10));
    p.add(new JLabel(ui.getStr("group_id")+":"), GridC.getc(0,y).label());
    p.add(groupChoice, GridC.getc(1,y).field());
    p.add(addGroupButton, GridC.getc(2,y));
    p.add(delGroupButton, GridC.getc(3,y++));
    
    JPanel tmpP = new JPanel(new GridBagLayout());
    tmpP.add(new JLabel(ui.getStr("group_members")+": "), 
             GridC.getc(0,0).west().insets(16,0,6,0));
    tmpP.add(new JScrollPane(memberList), 
             GridC.getc(0,1).rowspan(3).wxy(1,1).fillboth());
    tmpP.add(addMemberButton, GridC.getc(1,1).fillx().insets(0,8,0,0));
    tmpP.add(delMemberButton, GridC.getc(1,2).fillx().insets(8,8,0,0));
    tmpP.add(Box.createVerticalStrut(100), GridC.getc(1,3).wy(1));
    tmpP.add(updateCredsButton, GridC.getc(0,4).fillx().insets(8,0,10,0));
    p.add(tmpP, GridC.getc(0,y++).colspan(4).wy(1).fillboth());
    
    JPanel bp = new JPanel(new GridBagLayout());
    bp.add(Box.createHorizontalStrut(40), GridC.getc(0,0).wx(1));
    bp.add(cancelButton, GridC.getc(1,0).insets(10,10,10,0));
    bp.add(saveButton, GridC.getc(2,0).insets(10,10,10,10));
    
    p.add(bp, GridC.getc(0,y).colspan(2).fillx().colspan(4));
    
    getContentPane().add(p, BorderLayout.CENTER);
    getRootPane().setDefaultButton(saveButton);
    
    pack();
    setSize(new Dimension(400, 400));
    
    DefaultListCellRenderer renderer = new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value,
                                                    int index, boolean selected,
                                                    boolean hasFocus) {
        if(value instanceof XTag) {
          String strVal = ((XTag)value).getAttribute("id", null);
          if(strVal!=null) {
            value = entities.getEntityLabel(strVal);
          }
        }
        return super.getListCellRendererComponent(list, value, index, selected, hasFocus);
      }
    };
    
    doClient = new DOClient(appUI.getAuthentication(false));
    
    groupChoice.setRenderer(renderer);
    memberList.setCellRenderer(renderer);
    
    setLocationRelativeTo(parent);
    groupChoice.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent evt) {
        reloadMembers();
      }
    });
    loadGroupInfo();
  }
  
  
  private void loadGroupInfo() {
    groupInfo = new XTag("groupinfo");
    try {
      if(userObj.verifyDataElement(GROUP_INFO_ELEMENT)) {
        DataElement el = userObj.getDataElement(GROUP_INFO_ELEMENT);
        groupInfo = new XParser().parse(new InputStreamReader(el.read(), "UTF8"), false);
      }
    } catch (Exception e) { 
      e.printStackTrace(System.err);
    }
    reloadGroups();
  }
  
  
  void savePressed() {
    // store the addresses and close the window...
    byte groupBytes[] = null;
    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      groupInfo.write(bout);
      groupBytes = bout.toByteArray();
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error")+e);
      return;
    }
    
    try {
      DataElement el = userObj.getDataElement(GROUP_INFO_ELEMENT);
      el.write(new ByteArrayInputStream(groupBytes));
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error")+e);
      return;
    }
    
    appUI.hideManageGroupsWindow();
  }
  
  void cancelPressed() {
    appUI.hideManageGroupsWindow();
  }
  
  void addGroupPressed() {
    // ask for handle
    String groupID = JOptionPane.showInputDialog(this, appUI.getStr("ask_for_new_group_handle"));
    if(groupID==null || groupID.trim().length()<=0) return;
    
    Resolver resolver = DOClient.getResolver();
    HandleValue values[] = null;
    try {
      // do a secure resolution of the group ID to see if it already exists
      values = resolver.resolveHandle(groupID, (String[])null, true);
    } catch (Exception e) {
      // assume that the handle doesn't exist
    }
    
    // generate the key pair
    byte privBytes[] = null;
    byte pubBytes[] = null;
    
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance(DEFAULT_PUBKEY_ALG);
      kpg.initialize(DEFAULT_PUBKEY_SIZE);
      KeyPair keys = kpg.generateKeyPair();
      
      // Get the bytes making up the keys
      privBytes = Util.getBytesFromPrivateKey(keys.getPrivate());
      pubBytes = Util.getBytesFromPublicKey(keys.getPublic());
      
      String encKey = JOptionPane.showInputDialog(this, appUI.getStr("ask_for_privkey_pass"));
      if(encKey==null) return;
      encKey = encKey.trim();
      if (encKey.length()>0) {  // Encrypt the private key bytes
        privBytes = Util.encrypt(privBytes, Util.encodeString(encKey), Common.ENCRYPT_PBKDF2_AES_CBC_PKCS5);
      } else {
        privBytes = Util.encrypt(privBytes, null, Common.ENCRYPT_NONE);
      }
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_generating_keys")+": "+e);
      return;
    }
    
    // select a file in which to save the private key file
    FileDialog fwin = new FileDialog(this, appUI.getStr("select_group_priv_key_file"),
                                     FileDialog.SAVE);
    StreamTable prefs = appUI.getMain().prefs();
    try {
      fwin.setDirectory(prefs.getStr("group_priv_key_dir", System.getProperty("user.home", ".")));
      fwin.setFile(groupID+".privkey");
    } catch (Exception e) {}
    fwin.setVisible(true);
    String newDir = fwin.getDirectory();
    String newFile = fwin.getFile();
    if(newDir==null || newFile==null) return;
    
    File privKeyFile = new File(newDir, newFile);
    
    prefs.put("group_priv_key_dir", newDir);
    prefs.put("group_priv_key_file:"+groupID, privKeyFile.getAbsolutePath());
    
    FileOutputStream privOut = null;
    try {
      privOut = new FileOutputStream(privKeyFile);
      privOut.write(privBytes);
      privOut.close();
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_storing_priv_key")+": "+e);
      return;
    } finally {
      try { if(privOut!=null) privOut.close(); } catch (Throwable t) {}
    }
    
    
    // register handle with public key
    HandleResolver hdlResolver = resolver.getResolver();
    DOAuthentication doAuth = appUI.getAuthentication(false);
    AuthenticationInfo authInfo = doAuth.toHandleAuth();
    
    AbstractRequest req = null;
    if(values==null) { // handle does not already exist, create it
      AdminRecord myAdmin = new AdminRecord(authInfo.getUserIdHandle(), 
                                            authInfo.getUserIdIndex(),
                                            true, true, false, false,
                                            true, true, true, true,
                                            true, true, true, true);
      AdminRecord keyAdmin = new AdminRecord(Util.encodeString(groupID), 300,
                                             true, true, false, false,
                                             true, true, true, true,
                                             true, true, true, true);
      
      values = new HandleValue[] {
        new HandleValue(100, Common.STD_TYPE_HSADMIN, Encoder.encodeAdminRecord(myAdmin)),
        new HandleValue(101, Common.STD_TYPE_HSADMIN, Encoder.encodeAdminRecord(keyAdmin)),
        new HandleValue(300, Common.STD_TYPE_HSPUBKEY, pubBytes),
      };
      req = new CreateHandleRequest(Util.encodeString(groupID), values, authInfo);
    } else { // the handle already exists...
      int pkIdx = 300;
      for(int i=0; i<values.length; i++) {
        if(values[i]==null) continue;
        if(values[i].getIndex()==pkIdx) {
          pkIdx++;
          i = -1;
        }
      }
      HandleValue newValues[] = {
        new HandleValue(pkIdx, Common.STD_TYPE_HSPUBKEY, pubBytes)
      };
      req = new AddValueRequest(Util.encodeString(groupID), newValues, authInfo);
    }
    
    try {
      AbstractResponse response = hdlResolver.processRequest(req);
      if(response.responseCode!=AbstractMessage.RC_SUCCESS) {
        appUI.showErrorMessage(this, appUI.getStr("error_registering_group_hdl")+": "+response);
        return;
      }
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_registering_group_hdl")+": "+e);
      return;
    }
    
    XTag newGroupTag = new XTag("group");
    newGroupTag.setAttribute("id", groupID);
    groupInfo.addSubTag(newGroupTag);
    
    reloadGroups();
    groupChoice.setSelectedItem(newGroupTag);
  }
  
  
  private void reloadGroups() {
    Object selGrp = groupChoice.getSelectedItem();
    while(groupChoice.getItemCount()>0) groupChoice.removeItemAt(0);
    XTag defaultTag = null;
    for(int i=0; i<groupInfo.getSubTagCount(); i++) {
      XTag groupTag = groupInfo.getSubTag(i);
      if(groupTag==null) continue;
      if(defaultTag==null) defaultTag = groupTag;
      if(selGrp==groupTag) defaultTag = groupTag;
      groupChoice.addItem(groupTag);
    }
    groupChoice.setSelectedItem(defaultTag);
    reloadMembers();
  }
  
  private void reloadMembers() {
    Object selGrp = groupChoice.getSelectedItem();
    boolean membersEnabled = selGrp!=null;
    memberList.setEnabled(membersEnabled);
    addMemberButton.setEnabled(membersEnabled);
    delMemberButton.setEnabled(membersEnabled);
    
    listModel.reload();
  }
  
  void deleteGroupPressed() {
    Object selGrp = groupChoice.getSelectedItem();
    if(selGrp==null) {
      getToolkit().beep();
      return;
    }
    groupInfo.removeSubTag((XTag)selGrp);
    reloadGroups();
  }

  void addMemberPressed() {
    XTag groupTag = (XTag)groupChoice.getSelectedItem();
    if(groupTag==null) return;
    
    EntityMap.Entity peeps[] = (EntityMap.Entity[])
      entities.getEntities().toArray(new EntityMap.Entity[entities.getNumEntities()]);
    String peepIDs[] = new String[peeps.length];
    for(int i=0; i<peeps.length; i++) {
      peepIDs[i] = peeps[i].getID();
    }
    
    JComboBox memberChoice = new JComboBox(peepIDs);
    memberChoice.setEditable(true);
    JPanel p = new JPanel(new GridBagLayout());
    p.add(new JLabel(appUI.getStr("add_member_msg")), 
          GridC.getc(0,0).wxy(1,1).fillboth().insets(12,12,12,12));
    p.add(memberChoice, GridC.getc(0,1).insets(0,12,12,12));
    
    int result = JOptionPane.showConfirmDialog(this, p, 
                                               appUI.getStr("add_member"),
                                               JOptionPane.OK_CANCEL_OPTION);
    if(result!=JOptionPane.OK_OPTION) return;
    XTag memberTag = new XTag("member");
    Object memberItem = memberChoice.getSelectedItem();
    String memberID = null;
    if(memberItem instanceof EntityMap.Entity) {
      memberID = ((EntityMap.Entity)memberItem).getID();
    } else {
      memberID = String.valueOf(memberItem);
    }
    memberTag.setAttribute("id", memberID);
    groupTag.addSubTag(memberTag);
    reloadMembers();
  }
  
  
  void deleteMemberPressed() {
    XTag groupTag = (XTag)groupChoice.getSelectedItem();
    if(groupTag==null) return;
    
    Object selMembers[] = memberList.getSelectedValues();
    for(int i=0; selMembers!=null && i<selMembers.length; i++) {
      XTag selMember = (XTag)selMembers[i];
      if(selMember==null) continue;
      groupTag.removeSubTag(selMember);
    }
    reloadMembers();
  }

  
  void generateCreds() {
    XTag groupTag = (XTag)groupChoice.getSelectedItem();
    if(groupTag==null) {
      getToolkit().beep();
      return;
    }
    
    ArrayList memberIDs = new ArrayList();
    String groupID = groupTag.getAttribute("id");
    if(groupID==null || groupID.trim().length()<=0) {
      getToolkit().beep();
      return;
    }
    
    // load the private key for the group
    StreamTable prefs = appUI.getMain().prefs();
    FileDialog fwin = new FileDialog(this, appUI.getStr("select_group_priv_key_file"),
                                     FileDialog.LOAD);
    String keyFileStr = prefs.getStr("group_priv_key_file:"+groupID, null);
    try {
      if(keyFileStr!=null) {
        File tmpFile = new File(keyFileStr);
        fwin.setDirectory(tmpFile.getParent());
        fwin.setFile(tmpFile.getName());
      } else {
        String keyDirStr = prefs.getStr("group_priv_key_dir",
                                        System.getProperty("user.home", "."));
        fwin.setDirectory(keyDirStr);
        fwin.setFile(groupID+".privkey");
      }
    } catch (Exception e) {}
    fwin.setVisible(true);
    String newDir = fwin.getDirectory();
    String newFile = fwin.getFile();
    if(newDir==null || newFile==null) return;
    
    File privKeyFile = new File(newDir, newFile);
    PrivateKey privKey = null;
    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      FileInputStream fin = new FileInputStream(privKeyFile);
      byte buf[] = new byte[1024];
      int r;
      while((r=fin.read(buf))>=0) bout.write(buf, 0, r);
      
      byte privKeyBytes[] = bout.toByteArray();
      
      byte secKey[] = null;
      if(Util.requiresSecretKey(privKeyBytes)) {
        String encKey = JOptionPane.showInputDialog(this, appUI.getStr("ask_for_privkey_decrypt_pass"));
        if(encKey==null) return;
        secKey = Util.encodeString(encKey);
      }
        
      privKeyBytes = Util.decrypt(privKeyBytes, secKey);
      privKey = Util.getPrivateKeyFromBytes(privKeyBytes, 0);
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error")+e);
      return;
    }
    
    prefs.put("group_priv_key_dir", privKeyFile.getParent());
    prefs.put("group_priv_key_file:"+groupID, privKeyFile.getAbsolutePath());
    
    for(int i=0; i<groupTag.getSubTagCount(); i++) {
      XTag memberTag = groupTag.getSubTag(i);
      String memberID = memberTag.getAttribute("id", null);
      if(memberID==null) continue;
      generateGroupCredential(groupID, privKey, memberID);
    }
  }
  
  
  private void generateGroupCredential(String groupID, PrivateKey privKey, String memberID) {
    Hashtable myAttributes = new Hashtable();
    //myAttributes.put("o", DERObjectIdentifier.getInstance(myID));
    myAttributes.put(X509Name.UID, groupID);
    //myAttributes.put(X509Name.OU, "DO Certificate");
    
    Hashtable subjectAttributes = new Hashtable();
    subjectAttributes.put(X509Name.UID, memberID);
    //subjectAttributes.put("ou", "DO Certificate");
    
    PublicKey pubKeys[] = null;
    try {
      pubKeys = DOClient.getResolver().resolvePublicKeys(memberID);
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_granting_key_to")+memberID+": "+e);
      return;
    }
    
    for(int i=0; pubKeys!=null && i<pubKeys.length; i++) {
      PublicKey pubKey = pubKeys[i];
      if(pubKey==null) continue;

      StreamPair io = null;
      try {
        pubKey = (PublicKey)KeyFactory.getInstance(pubKey.getAlgorithm()).translateKey(pubKey);
        
        // create the certificate - version 1
        v3CertGen.setSerialNumber(BigInteger.valueOf(1));
        v3CertGen.setIssuerDN(new X509Principal(myAttributes));
        v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 30));
        v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)));
        v3CertGen.setSubjectDN(new X509Principal(subjectAttributes));
        v3CertGen.setPublicKey(pubKey);
        v3CertGen.setSignatureAlgorithm("SHA1WithRSAEncryption");
        
        
        X509Certificate cert = v3CertGen.generateX509Certificate(privKey);
        
        cert.checkValidity(new Date());
        
        //cert.verify(myPubKey);
        
        // this is actually optional - but if you want to have control
        // over setting the friendly name this is the way to do it...
        PKCS12BagAttributeCarrier bagAttr = (PKCS12BagAttributeCarrier)cert;
        bagAttr.setBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName,
                                new DERBMPString("DO Delegation Certificate"));
        
        byte certBytes[] = cert.getEncoded();
        io = appUI.getObjectReference(null, memberID).
          performOperation(DOConstants.STORE_CREDENTIAL_OP_ID, null);
        io.getOutputStream().write(certBytes);
        io.getOutputStream().close();
        //DigitalObject.checkStatus(io.getInputStream());
      } catch (Exception e) {
        appUI.showErrorMessage(this, appUI.getStr("error_granting_key_to")+memberID+": "+e);
      } finally {
        try { io.close(); } catch (Exception e) {}
        io = null;
      }
    }
  }
  
  void generateAllCreds() {
  }
  
  private class DOMemberModel
    extends AbstractListModel
  {
    public Object getElementAt(int idx) {
      XTag groupTag = (XTag)groupChoice.getSelectedItem();
      if(groupTag==null) return null;
      return groupTag.getSubTag(idx);
    }
    
    public int getSize() {
      XTag groupTag = (XTag)groupChoice.getSelectedItem();
      if(groupTag==null) return 0;
      return groupTag.getSubTagCount();
    }
    
    public void reload() {
      fireContentsChanged(this, -1, -1);
    }
  }
    

}
