package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.dobj.*;
import net.cnri.guiutil.*;
import net.handle.awt.*;
import javax.swing.*;
import javax.swing.JLabel;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;

/**
 * A GUI panel that displays and allows interaction with a DigitalObject
 * 
 * User: sreilly
 * Date: 5/26/11
 * Time: 2:43 PM
 */
public class DOViewPanel
  extends JPanel
{
  private DOViewPanel thisView;
  private AdminToolUI appUI;
  private final DigitalObject dobj;
  private JPanel elementsContainer;
  private final ArrayList<Runnable> backgroundTasks = new ArrayList<Runnable>();
  private Thread backgroundThread;
  private boolean backgroundThreadShouldStop = false;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss SSS");
  private JButton actionsButton;
  
  private HDLAction actionAction;
  private HDLAction addElementAction;
  private HDLAction deleteObjectAction;
  private HDLAction saveObjectAction;
  private HDLAction restoreObjectAction;
  
  private JPopupMenu actionMenus;
  
  public DOViewPanel(AdminToolUI ui, DigitalObject theObjectOfOurAffection) {
    super(new GridBagLayout());
    this.dobj = theObjectOfOurAffection;
    this.appUI = ui;
    
    addElementAction = new HDLAction(appUI, "add_element", "add_element") {
      public void actionPerformed(ActionEvent evt) {
        addNewElement();
      }
    };
    saveObjectAction = new HDLAction(appUI, "save_object", "save_object") {
      public void actionPerformed(ActionEvent evt) {
        saveObject();
      }
    };
    deleteObjectAction = new HDLAction(appUI, "delete_object", "delete_object") {
      public void actionPerformed(ActionEvent evt) {
        deleteObject();
      }
    };
    actionAction = new HDLAction(appUI, "actions", "show_actions") {
      public void actionPerformed(ActionEvent evt) {
        actionMenus.show(actionsButton, 0, 0);
      }
    };

    refreshDOView();
    
    
    addBackgroundTask(new Runnable() {
      public void run() {
        try {
          // list the elements in the background
          final String elementIDs[] = dobj.listDataElements();

          // add the UI in the GUI thread
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              elementsContainer.removeAll();
              //int row = 0;
              for (String elementID : elementIDs) {
                //elementsContainer.add(Box.createVerticalStrut(200), GridC.getc(0,row));
                elementsContainer.add(new ElementView(elementID), GridC.getc(0, GridBagConstraints.RELATIVE).wx(1).fillboth());
              }
            }
          });
        } catch (final Exception e) {
          e.printStackTrace();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              elementsContainer.removeAll();
              elementsContainer.add(new JLabel("Error listing elements: " +
                                               e), GridC.getc(0, 0).wx(1).insets(8, 8, 8, 8));
            }
          });
        }
      }
    });
    
    backgroundThread = new Thread() {
      public void run() {
        while(!backgroundThreadShouldStop) {
          if(backgroundTasks.size()>0) {
            Runnable task = backgroundTasks.get(0);
            backgroundTasks.remove(0);
            try {
              task.run();
            } catch (Throwable t) {
              System.err.println("error in background task: "+t);
              t.printStackTrace();
            }
          } else {
            synchronized (this) {
              try {
                this.wait(1000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    };
    
    this.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent componentEvent) {
        backgroundThreadShouldStop = false;
        backgroundThread.start();
      }
      
      @Override
      public void componentHidden(ComponentEvent componentEvent) {
        backgroundThreadShouldStop = true;
      }
    });
    
    backgroundThreadShouldStop = false;
    backgroundThread.start();
  }
  
  private void refreshDOView() {
    removeAll();
    actionMenus = new JPopupMenu();
    actionMenus.add(addElementAction);
    actionMenus.addSeparator();
    actionMenus.add(saveObjectAction);
    //actionMenus.add(restoreObjectAction);
    actionMenus.addSeparator();
    actionMenus.add(deleteObjectAction);
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        // need to list the available operations...
        try {
          StreamPair io = dobj.performOperation(DOConstants.LIST_OPERATIONS_OP_ID, new HeaderSet());
          BufferedReader reader = new BufferedReader(new InputStreamReader(io.getInputStream()));
          actionMenus.addSeparator();
          while(true) {
            String opID = reader.readLine();
            if(opID==null) break;
            opID = opID.trim();
            if(opID.length()<=0) continue;
            String opKey = "op_label_"+opID;
            String opLabel =  appUI.hasStr(opKey) ? appUI.getStr(opKey) : appUI.getStr("op_label").replaceAll("\\{opid\\}", opID);
            
            actionMenus.add(new HDLAction(null, opLabel, opID) {
              public void actionPerformed(ActionEvent evt) {
                String opID = this.getCommand();
                JOptionPane.showMessageDialog(thisView, "If Sean weren't so lazy you would be performing operation "+opID+" now");
              }
            });
          }
        } catch (Exception e) {
          System.err.println("Unable to list operations for object "+dobj);
          e.printStackTrace(System.err);
        }
      }
    });
    
    int y = 0;
    JLabel titleLabel = new JLabel(dobj.toString()); 
    add(Box.createHorizontalStrut(500), GridC.getc(0,0));
    add(titleLabel, GridC.getc(0,y++).wx(1).center().insets(0,0,12,0));
    
    JPanel mainContainer = new JPanel(new GridBagLayout());
    
    mainContainer.add(new AttributesView(null), GridC.getc(0,y++).fillboth().wxy(1,1));
    elementsContainer = new JPanel(new GridBagLayout());
    elementsContainer.add(makeIndeterminateProgressBar(), GridC.getc(0, 0));
    mainContainer.add(elementsContainer, GridC.getc(0, y++).fillboth().wx(1));
    //add(smallButton(new JButton(addElementAction)), GridC.getc(0,y).west());
    
    JScrollPane mainScroll = new JScrollPane(mainContainer,
                                                 JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
                                                 JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    mainScroll.setMaximumSize(new Dimension(100000, 700));
    mainScroll.setBorder(BorderFactory.createEmptyBorder());
    
    add(mainScroll, GridC.getc(0,y++).fillboth().wy(1).wx(1));
    add(Box.createVerticalStrut(10), GridC.getc(0, y++));
    add(actionsButton = smallButton(new JButton(actionAction)), GridC.getc(0,y).west());
  }
  
  public JProgressBar makeIndeterminateProgressBar() {
    JProgressBar elementsProgress = new JProgressBar();
    elementsProgress.setIndeterminate(true);
    elementsProgress.putClientProperty("JProgressBar.style", "circular");
    return elementsProgress;
  }
  
  private void addBackgroundTask(Runnable task) {
    synchronized (backgroundTasks) {
      backgroundTasks.add(task);
      backgroundTasks.notifyAll();
    }
  }
  
  private void componentsWereAdded() {
    Window w = SwingUtilities.getWindowAncestor(this);
    if(w==null) return;
    
    w.pack();
    
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
    Dimension prefSize = w.getPreferredSize();
    Dimension setSize = new Dimension(prefSize.width, 
                                      Math.min(screenSize.height-insets.top-insets.bottom, prefSize.height));
    w.setSize(setSize);
  }
  
  
  
  private void saveObject() {
    FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), appUI.getStr("choose_file"), FileDialog.SAVE);
    fwin.setVisible(true);
    
    String dirName = fwin.getDirectory();
    String fileName = fwin.getFile();
    if(dirName==null || fileName==null) return; // user canceled
    
    StreamPair io = null;
    try {
      io = dobj.performOperation(DOConstants.GET_SERIALIZED_FORM_OP_ID, new HeaderSet());
      io.getOutputStream().close();
      FileOutputStream fout = new FileOutputStream(new File(dirName, fileName));
      byte buf[] = new byte[2048];
      int r;
      InputStream in = io.getInputStream();
      while((r=in.read(buf))>=0) fout.write(buf, 0, r);
      fout.close();
    } catch (Exception e) {
      e.printStackTrace(System.err);
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
  }
  
  
  private void deleteObject() {
    int result = JOptionPane.showConfirmDialog(null, appUI.getStr("confirm_delete_obj"),
                                               appUI.getStr("confirm"), 
                                               JOptionPane.OK_CANCEL_OPTION,
                                               JOptionPane.PLAIN_MESSAGE);
    if(result!=JOptionPane.OK_OPTION) return;
    try {
      dobj.deleteObject();
    } catch (Exception e) {
      appUI.showErrorMessage(this, "Error deleting object: "+e);
      e.printStackTrace(System.err);
      return;
    }
    this.removeAll();
    this.add(new JLabel(appUI.getStr("object_was_deleted_msg").replaceAll("\\{objectid\\}",dobj.getID())),
             GridC.getc(0,0));
    componentsWereAdded();
  }
  
  private void addNewElement() {
    String newElementID = UUID.randomUUID().toString();
    newElementID = JOptionPane.showInputDialog(this, appUI.getStr("enter_new_element_id"), newElementID);
    if(newElementID==null || newElementID.trim().length()<=0) return;
    
    boolean exists = false;
    
    try {
      exists = dobj.verifyDataElement(newElementID);
    } catch (Exception e) {
      System.err.println("Error verifying element: "+e);
      e.printStackTrace(System.err);
    }
    if(exists) {
      appUI.showErrorMessage(this, appUI.getStr("error_element_already_exists"));
    } else {
      try {
        loadElementFromFile(dobj.getDataElement(newElementID), null);
      } catch (Exception e) {
        appUI.showErrorMessage(this, appUI.getStr("error_updating_element")+"\n\n  "+e);
        return;
      }
    }
  }
  
  
  private void loadElementFromFile(final DataElement element, ElementView elementView) {
    // ask for the location in which to save this file/element...
    FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), appUI.getStr("choose_file"), FileDialog.LOAD);
    fwin.setVisible(true);
    
    String dirName = fwin.getDirectory();
    String fileName = fwin.getFile();
    if(dirName==null || fileName==null) return; // user canceled
    
    InputStream fin = null;
    InputStream in = null;
    try {
      fin = new ProgressMonitorInputStream(this, appUI.getStr("updating")+": "+element,
                                           new FileInputStream(new File(dirName, fileName)));

      // if the element is encrypted, an encrypting cipher stream should be inserted here
      
      element.write(fin);
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_updating_element")+"\n\n  "+e);
      return;
    } finally {
      try { fin.close(); } catch (Exception e) { }
    }
    
    
    if(elementView!=null) {
      elementView.elementWasUpdated();
    } else {
      SwingUtilities.invokeLater(new Runnable() { public void run() {
        elementsContainer.add(new ElementView(element.getDataElementID()),
                              GridC.getc(0, GridBagConstraints.RELATIVE).wx(1).fillboth());
      }});
    }
    
  }
  
  
  private void deleteDataElement(DataElement element, ElementView elementView) {
    String confirmMsg = appUI.getStr("confirm_delete_element");
    confirmMsg = confirmMsg.replaceAll("\\{elementid\\}", element.getDataElementID());
    confirmMsg = confirmMsg.replaceAll("\\{objectid\\}", dobj.getID());
    
    int result = JOptionPane.showConfirmDialog(null, confirmMsg,
                                               appUI.getStr("confirm"), 
                                               JOptionPane.OK_CANCEL_OPTION,
                                               JOptionPane.PLAIN_MESSAGE);
    if(result==JOptionPane.OK_OPTION) {
      try {
        dobj.deleteDataElement(element.getDataElementID());
        elementsContainer.remove(elementView);
        componentsWereAdded();
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
    }
  }
  
  
  private void viewElementAsText(DataElement element) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    InputStream in = null;
    try {
      in = element.read();
      
      // if the element is encrypted, a decrypting cipher stream should be
      // inserted here
      
      byte buf[] = new byte[10000];
      int r;
      while((r=in.read(buf))>=0) bout.write(buf, 0, r);
      bout.close();
      
      
      JTextArea textArea = new JTextArea(net.handle.hdllib.Util.decodeString(bout.toByteArray()));
      textArea.setBackground(null);
      textArea.setEditable(false);
      JOptionPane.showMessageDialog(this, new JScrollPane(textArea),
                                    element.toString(), JOptionPane.PLAIN_MESSAGE);
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_getting_element")+"\n\n  "+e);
      return;
    } finally {
      try { in.close(); } catch (Exception e) { }
      try { bout.close(); } catch (Exception e) { }
    }
  }

  
  private void editElementAsText(DataElement element) {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    InputStream in = null;
    String elementText = null;
    try {
      in = element.read();
      
      // if the element is encrypted, a decrypting cipher stream should be
      // inserted here
      
      byte buf[] = new byte[10000];
      int r;
      while((r=in.read(buf))>=0) bout.write(buf, 0, r);
      bout.close();
      
      elementText = net.handle.hdllib.Util.decodeString(bout.toByteArray());
    
    } catch (Exception e) {
      appUI.showErrorMessage(this, appUI.getStr("error_getting_element")+"\n\n  "+e);
      return;
    } finally {
      try { in.close(); } catch (Exception e) { }
      try { bout.close(); } catch (Exception e) { }
    }
    
    JTextArea textArea = new JTextArea(elementText);
    JOptionPane editPane = new JOptionPane(new JScrollPane(textArea),
                                           JOptionPane.PLAIN_MESSAGE, 
                                           JOptionPane.OK_CANCEL_OPTION);
    JDialog editWin = editPane.createDialog(this, element.toString());
    
    while(true) {
      try {
        editWin.setVisible(true);
        
        Object selectedOption = editPane.getValue();
        if(selectedOption!=null && selectedOption.equals(Integer.valueOf(JOptionPane.OK_OPTION))) {
          ByteArrayInputStream bin = new ByteArrayInputStream(net.handle.hdllib.Util.encodeString(textArea.getText()));
          element.write(bin);
        }
        return;
      } catch (Exception e) {
        appUI.showErrorMessage(this, appUI.getStr("error_updating_element")+"\n\n  "+e);
        return;
      } finally {
        try { in.close(); } catch (Exception e) { }
        try { bout.close(); } catch (Exception e) { }
      }
    }
  }
  
  
  private void saveElementToFile(DataElement element) {
    // ask for the location in which to save this file/element...
    FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), appUI.getStr("choose_file"), FileDialog.SAVE);
    fwin.setVisible(true);
    
    String dirName = fwin.getDirectory();
    String fileName = fwin.getFile();
    if(dirName==null || fileName==null) return; // user canceled
    
    FileOutputStream fout = null;
    InputStream in = null;
    try {
      in = element.read();
      
      // if the element is encrypted, a decrypting cipher stream should be
      // inserted here
      
      fout = new FileOutputStream(new File(dirName, fileName));
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
  }
  
  private class ElementView extends JPanel implements Runnable {
    private String elementID;
    
    private DataElement element;
    private AttributesView attributesView;
    
    private HDLAction viewTextAction;
    private HDLAction editTextAction;
    private HDLAction uploadAction;
    private HDLAction downloadAction;
    private HDLAction deleteElementAction;
    
    ElementView(String elementID) {
      super(new GridBagLayout());
      this.elementID = elementID;
      this.attributesView = new AttributesView(elementID);
      this.attributesView.taskToRunAfterUpdating = this;
      this.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                                                      elementID));
      
      viewTextAction = new HDLAction(appUI, "view_as_text", "view_text_"+elementID) {
        public void actionPerformed(ActionEvent evt) { viewElement(); }
      };
      
      editTextAction = new HDLAction(appUI, "edit_as_text", "view_text_"+elementID) {
        public void actionPerformed(ActionEvent evt) { editElement(); }
      };
      
      uploadAction = new HDLAction(appUI, "upload", "upload_element_"+elementID) {
        public void actionPerformed(ActionEvent evt) { uploadElement(); }
      };
      
      downloadAction = new HDLAction(appUI, "download", "download_element_"+elementID) {
        public void actionPerformed(ActionEvent evt) { downloadElement(); }
      };
      
      deleteElementAction = new HDLAction(appUI, "delete_element", "delete_element_"+elementID) {
        public void actionPerformed(ActionEvent evt) { deleteElement(); }
      };
      
      add(Box.createRigidArea(new Dimension(300, 30)), GridC.getc(0,0).colspan(4));
      add(attributesView, GridC.getc(0, 0).wx(1).colspan(5).fillboth());
    }
    
    public void run() {
      try {
        element = dobj.getDataElement(elementID);
      } catch (IOException e) {
        e.printStackTrace();
        add(new JLabel("Error loading data element: "+e), GridC.getc(0,1).center());
        return;
      }
      
      add(Box.createHorizontalStrut(100), GridC.getc(0,1).wx(1));
      add(smallButton(new JButton(deleteElementAction)), GridC.getc(1, 1));
      add(smallButton(new JButton(uploadAction)), GridC.getc(2,1));
      add(smallButton(new JButton(downloadAction)), GridC.getc(3,1));
      add(smallButton(new JButton(editTextAction)), GridC.getc(4,1));
      componentsWereAdded();
    }
    
    
    public void uploadElement() {
      loadElementFromFile(element, this);
    }

    public void deleteElement() {
      deleteDataElement(element, this);
    }
    
    public void elementWasUpdated() {
      addBackgroundTask(attributesView);
    }
    
    public void downloadElement() {
      saveElementToFile(element);
    }
    
    public void viewElement() {
      viewElementAsText(element);
    }
    
    public void editElement() {
      editElementAsText(element);
    }
    
    
  }
  
  public static JButton smallButton(JButton button) {
    button.putClientProperty("JButton.buttonType", "roundRect");
    return button;
  } 
  
  private class AttributesView extends JPanel implements Runnable {
    private AttributesView thisAttributesView;
    private String elementID = null;

    private HeaderSet atts;
    private JTable attsTable;
    private DefaultTableModel attsModel;
    private DataElement element;
    
    private ArrayList<String> changedAttributes = new ArrayList<String>();
    private ArrayList<String> removedAttributes = new ArrayList<String>();
    
    Runnable taskToRunAfterUpdating = null;
    
    AttributesView(String theElementID) {
      super(new GridBagLayout());
      this.elementID = theElementID;
      this.thisAttributesView = this;
      //this.setBorder(BorderFactory.createLineBorder(Color.cyan, 1));
      this.add(makeIndeterminateProgressBar(), GridC.getc(0,0).center());
      setBorder(BorderFactory.createEmptyBorder(4, 6, 12, 6));
      addBackgroundTask(new Runnable() {
        public void run() {
          try {
            if(elementID==null) {
              atts = dobj.getAttributes();
            } else {
              element = dobj.getDataElement(elementID);
              atts = element.getAttributes();
            }
          } catch (Exception e) {
            e.printStackTrace();
          }
          SwingUtilities.invokeLater(thisAttributesView);
        }
      });
    }
    
    
    
    
    private class AttributeValueRenderer extends DefaultTableCellRenderer {
    
      public AttributeValueRenderer() {
        setBackground(null);
      }
    
      public Component getTableCellRendererComponent(JTable table, Object object, boolean selected, boolean hasFocus, int row, int col) {
        try {
          HeaderItem item = atts.get(row);
          String attKey = item.getName();
          if(attKey.equals("internal.created") || attKey.equals("internal.modified")) {
            object = dateFormat.format(new Date(Long.parseLong(String.valueOf(object))));
          }
        } catch (Exception e) {
          System.err.println("error: "+e);
          // unable to clean up the value for display.. oh well
        }

        return super.getTableCellRendererComponent(table, object, false, false, row, col);
      }
    
    
    }
    
    
    public void run() {
      this.removeAll();
      attsModel = new DefaultTableModel() {
        public boolean isCellEditable(int row, int col) {
          return col==1 && !this.getValueAt(row, 0).toString().startsWith("internal.");
        }
      };
      attsTable = new JTable(attsModel);
      attsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      attsTable.setBackground(null);
      //attsTable.setShowHorizontalLines(false);
      attsTable.setShowVerticalLines(false);
      attsTable.setRowHeight((int)Math.round(attsTable.getFontMetrics(attsTable.getFont()).getHeight()*1.3));
      
      //attsTable.setColumnModel(columnModel);
      
      loadAttributesIntoModel(attsModel, atts);
      
      // setup the columns, editor and renderers
      DefaultTableCellRenderer attNameRenderer = new DefaultTableCellRenderer() {
        public Component getTableCellRendererComponent(JTable table, Object object, boolean selected, boolean hasFocus, int row, int col) {
          return super.getTableCellRendererComponent(table, object, false, false, row, col);
        }
      };
      attNameRenderer.setBackground(null);
      attNameRenderer.setHorizontalTextPosition(SwingConstants.RIGHT);
      
      attsTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JTextField()));
      attsTable.getColumnModel().getColumn(1).setCellRenderer(new AttributeValueRenderer());
      attsTable.getColumnModel().getColumn(0).setCellEditor(null);
      attsTable.getColumnModel().getColumn(0).setCellRenderer(attNameRenderer);
      
      
      //this.attsTable.setBackground(new Color(1,0,0,0.5f));
      //      add(Box.createVerticalStrut(30), GridC.getc(0,0));
      add(Box.createHorizontalStrut(300), GridC.getc(0, 0));
      add(attsTable, GridC.getc(0,0).wxy(1,1).fillboth());
      invalidate();
      
      componentsWereAdded();
      Runnable task = taskToRunAfterUpdating;
      if(task!=null) task.run();
    }
    
    private void loadAttributesIntoModel(DefaultTableModel attsModel, HeaderSet attributes) {
      attsModel.setColumnCount(2);
      if(attributes==null) return;
      for(HeaderItem att : attributes) {
        attsModel.addRow(new String[] {att.getName(), att.getValue()});
      }
      attsModel.setRowCount(attributes.size());
    }
    
  }

}
