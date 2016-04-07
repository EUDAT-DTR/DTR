/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/


package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.guiutil.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AddressBookWindow 
  extends JDialog
{
  private AdminToolUI appUI;
  private DigitalObject userObj;
  private EntityMap entities;
  private JList addressList;
  private AddressListModel listModel;
  private JButton saveButton, cancelButton, addButton, delButton, editButton;
  
  public AddressBookWindow(JFrame parent, AdminToolUI ui, DigitalObject userObj,
                           EntityMap entities) {
    super(parent, ui.getStr("known_entities"));
    this.appUI = ui;
    this.userObj = userObj;
    this.entities = entities;
    
    saveButton = new JButton(new HDLAction(appUI, "save", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { savePressed(); }
    }));
        
    cancelButton = new JButton(new HDLAction(appUI, "cancel", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { cancelPressed(); }
    }));
        
    addButton = new JButton(new HDLAction(appUI, "add_address", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { addPressed(); }
    }));
        
    delButton = new JButton(new HDLAction(appUI, "del_address", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { deletePressed(); }
    }));
    
    editButton = new JButton(new HDLAction(appUI, "edit_address", new ActionListener() {
      public void actionPerformed(ActionEvent evt) { editPressed(); }
    }));
    
    addressList = new JList(listModel = new AddressListModel());
    
    JPanel p = new JPanel(new GridBagLayout());
    p.setBorder(new javax.swing.border.EmptyBorder(10,10,10,10));
    p.add(new JScrollPane(addressList),
          GridC.getc(0,1).wxy(1,1).fillboth().rowspan(5).insets(0,0,0,10));
    p.add(Box.createHorizontalStrut(60), GridC.getc(0,1));
    p.add(addButton, GridC.getc(1,1).insets(0,0,10,0).fillx());
    p.add(editButton, GridC.getc(1,2).insets(0,0,10,0).fillx());
    p.add(delButton, GridC.getc(1,3).insets(0,0,10,0).fillx());
    p.add(Box.createVerticalStrut(60), GridC.getc(1,4));
    
    JPanel bp = new JPanel(new GridBagLayout());
    bp.add(Box.createHorizontalStrut(40), GridC.getc(0,0).wx(1));
    bp.add(cancelButton, GridC.getc(1,0).insets(10,10,10,10));
    bp.add(saveButton, GridC.getc(2,0).insets(10,10,10,10));
    
    p.add(bp, GridC.getc(0,10).colspan(2).fillx());
    
    getContentPane().add(p, BorderLayout.CENTER);
    getRootPane().setDefaultButton(saveButton);
    
    pack();
    setSize(new Dimension(400, 400));
    
    setLocationRelativeTo(parent);
  }
  
  
  void savePressed() {
    // store the addresses and close the window...
    try {
      entities.saveToObject(userObj);
      appUI.hideAddressBook();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
  
  void cancelPressed() {
    appUI.hideAddressBook();
  }
  
  void addPressed() {
    EntityMap.Entity newEntity = entities.new Entity();
    AddressEditor editor = new AddressEditor();
    while (true) {
      int result = JOptionPane.showConfirmDialog(this, editor, 
                                                 appUI.getStr("add_address"),
                                                 JOptionPane.OK_CANCEL_OPTION);
      if(result!=JOptionPane.OK_OPTION) return;
      try {
        entities.addEntity(editor.saveToEntity());
        break;
      } catch (Exception e) {
        e.printStackTrace(System.err);
        appUI.showErrorMessage(this, String.valueOf(e));
      }
    }
    listModel.reload();
  }
  
  void editPressed() {
    EntityMap.Entity entity = (EntityMap.Entity)addressList.getSelectedValue();
    if(entity==null) {
      Toolkit.getDefaultToolkit().beep();
      return;
    }
    AddressEditor editor = new AddressEditor(entity);
    int result = JOptionPane.showConfirmDialog(this, editor, 
                                               appUI.getStr("edit_address"),
                                               JOptionPane.OK_CANCEL_OPTION);
    if(result!=JOptionPane.OK_OPTION) return;
    
    try {
      editor.saveToEntity();
    } catch (Exception e) {
      e.printStackTrace(System.err);
      appUI.showErrorMessage(this, String.valueOf(e));
    }
    listModel.reload();
  }
  
  
  void deletePressed() {
    Object selAddresses[] = addressList.getSelectedValues();
    if(selAddresses==null || selAddresses.length<=0) {
      Toolkit.getDefaultToolkit().beep();
      return;
    }
    
    int result = JOptionPane.showConfirmDialog(this, appUI.getStr("confirm_del_addr"),
                                               appUI.getStr("confirm_win_title"),
                                               JOptionPane.YES_NO_OPTION);
    if(result!=JOptionPane.YES_OPTION) return;
    
    for(int i=0; i<selAddresses.length; i++) {
      entities.removeEntity((EntityMap.Entity)selAddresses[i]);
    }
    listModel.reload();
  }
  
  
  public class AddressEditor
    extends JPanel
  {
    private EntityMap.Entity entity;
    private boolean isNew;
    private JTextField idField;
    private JTextField nameField;
    private JComboBox typeField;
    
    AddressEditor(EntityMap.Entity entity) {
      this.entity = entity;
      this.isNew = entity.getAttribute(EntityMap.ID_ATTRIBUTE, null)==null;
      idField = new JTextField(entity.getAttribute(EntityMap.ID_ATTRIBUTE, ""));
      idField.setEditable(isNew);
      nameField = new JTextField(entity.getAttribute(EntityMap.NAME_ATTRIBUTE, ""));
      
      int y = 0;
      setLayout(new GridBagLayout());
      add(new JLabel(appUI.getStr("entity_id")+": "), GridC.getc(0,y).label());
      add(idField, GridC.getc(1,y++).field());
      add(new JLabel(appUI.getStr("entity_name")+": "), GridC.getc(0,y).label());
      add(nameField, GridC.getc(1,y++).field());
      add(Box.createHorizontalStrut(200), GridC.getc(1,y++));
    }
    
    AddressEditor() {
      this(entities.new Entity());
    }
    
    EntityMap.Entity saveToEntity() {
      if(isNew) entity.setAttribute(EntityMap.ID_ATTRIBUTE, idField.getText());
      entity.setAttribute(EntityMap.NAME_ATTRIBUTE, nameField.getText());
      //entity.setAttribute(EntityMap.TYPE_ATTRIBUTE, getSelectedType());
      return entity;
    }
    
  }
  
  private class AddressListModel
    extends AbstractListModel
  {
    public Object getElementAt(int idx) {
      return entities.getEntity(idx);
    }
    
    public int getSize() {
      return entities.getNumEntities();
    }
    
    public void reload() {
      fireContentsChanged(this, -1, -1);
    }
  }
    

}
