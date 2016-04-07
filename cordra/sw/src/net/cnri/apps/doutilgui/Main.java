/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doutilgui;

import net.cnri.dobj.*;
import net.handle.awt.*;
import net.handle.hdllib.*;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.security.*;
import java.util.*;

/**
 * Main class for a simple GUI that provides raw access to DO operations
 */
public class Main
  extends JFrame
  implements ActionListener,
             DOConstants
{
  private JTextField serverIDField;
  private JTextField objectField;
  private JTextField myAuthField;
  private JComboBox operationChoice;
  private DefaultComboBoxModel operationModel;
  private JTextArea outputField;
  private JTextArea inputField;
  private JComboBox inputFormatChoice;
  private JComboBox outputFormatChoice;
  private JLabel opDescriptionLabel;
  private JButton authButton;
  private JButton goButton;
  private JButton selectInputFileButton;
  private JButton saveOutputFileButton;
  private JLabel statusLabel;
  private boolean outputChanged = true;

  private JTable paramTable;
  private ParamTableModel paramTableModel;
  private JButton addParamButton;
  private JButton delParamButton;
  
  private DOClientConnection connection = null;
  private String lastRepoID = null;
  
  private PrivateKey privateKey = null;
  private DOAuthentication auth = null;
  
  private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

  private static Hashtable knownOperations = new Hashtable();
  static {
    knownOperations.put(LIST_OPERATIONS_OP_ID, "List operations for object");
    knownOperations.put(CREATE_OBJ_OP_ID, "Create a new object");
    knownOperations.put(ADD_TYPE_OP_ID, "add a type to the object");
    knownOperations.put(REMOVE_TYPE_OP_ID, "remove a type from the object");
    knownOperations.put(HAS_TYPE_OP_ID, "ask if the object has the given type");
    knownOperations.put(GET_DATA_OP_ID, "get a named element from the object");
    knownOperations.put(STORE_DATA_OP_ID, "store a named element in the object");
    knownOperations.put(DELETE_DATA_OP_ID, "delete a named element in the object");
    knownOperations.put(LIST_DATA_OP_ID, "list the named elements in the object");
  }
  
  /**
   * @throws java.awt.HeadlessException
   */
  public Main() {
    super("DO Utility");

    outputStream = new ByteArrayOutputStream();

    objectField = new JTextField("cnri.test.sean/do-123", 40);
    myAuthField = new JTextField("", 40);
    serverIDField = new JTextField("200/10", 40);
    opDescriptionLabel = new JLabel("");
    operationModel = new DefaultComboBoxModel();
    for(Enumeration keys=knownOperations.keys(); keys.hasMoreElements(); ) {
      operationModel.addElement(keys.nextElement());
    }
    operationChoice = new JComboBox(operationModel);
    operationChoice.setEditable(true);

    paramTableModel = new ParamTableModel();
    paramTable = new JTable(paramTableModel);
    addParamButton = new JButton("Add Parameter");
    delParamButton = new JButton("Remove Parameter");

    outputField = new JTextArea("", 10, 35);
    outputField.setEditable(false);
    inputField = new JTextArea("", 10, 35);
    inputFormatChoice = new JComboBox(new String[] { "UTF8", "HEX"});
    outputFormatChoice = new JComboBox(new String[] { "UTF8", "HEX"});
    authButton = new JButton("My ID:");
    goButton = new JButton("Perform Operation");
    selectInputFileButton = new JButton("Select Input File");
    saveOutputFileButton = new JButton("Save Output to File");
    
    myAuthField.setEditable(false);

    int y = 0;
    JPanel p = new JPanel(new GridBagLayout());
    p.add(authButton,
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    p.add(myAuthField,
          AwtUtil.getConstraints(1,y++,1,0,2,1,true,false));
    
    p.add(new JLabel("Server ID: ", JLabel.RIGHT),
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    p.add(serverIDField,
          AwtUtil.getConstraints(1,y++,1,0,2,1,true,false));
    
    p.add(new JLabel("Object ID: ", JLabel.RIGHT),
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    p.add(objectField,
          AwtUtil.getConstraints(1,y++,1,0,2,1,true,false));
    
    p.add(new JLabel("Operation ID: ", JLabel.RIGHT),
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    p.add(operationChoice,
          AwtUtil.getConstraints(1,y,0,0,1,1,true,false));
    p.add(opDescriptionLabel,
          AwtUtil.getConstraints(2,y++,1,0,1,1,true,false));
    
    p.add(new JLabel("Parameters: ", JLabel.RIGHT),
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    JPanel pp = new JPanel(new GridBagLayout());
    pp.add(new JScrollPane(paramTable),
           AwtUtil.getConstraints(0,0,1,1,1,3,true,true));
    pp.add(addParamButton,
           AwtUtil.getConstraints(1,0,0,0,1,1,true,false));
    pp.add(delParamButton,
           AwtUtil.getConstraints(1,1,0,0,1,1,true,false));
    pp.add(new JLabel(" "),
           AwtUtil.getConstraints(1,2,0,1,1,1,true,false));
    p.add(pp,
          AwtUtil.getConstraints(1,y++,1,0,2,1,true,false));
    
    JPanel inP = new JPanel(new GridBagLayout());
    inP.add(inputFormatChoice,
            AwtUtil.getConstraints(0,0,0,0,1,1,false,false));
    inP.add(new JLabel(" "),
            AwtUtil.getConstraints(1,0,1,0,1,1,false,false));
    inP.add(selectInputFileButton,
            AwtUtil.getConstraints(2,0,0,0,1,1,false,false));
    
    p.add(new JLabel("Input Format: ", JLabel.RIGHT),
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    p.add(inP,
          AwtUtil.getConstraints(1,y++,1,0,2,1,true,false));
    p.add(new JScrollPane(inputField),
          AwtUtil.getConstraints(0,y++,1,1,3,1,true,true));
    
    p.add(new JLabel("Output Format: ", JLabel.RIGHT),
          AwtUtil.getConstraints(0,y,0,0,1,1,true,false));
    p.add(outputFormatChoice,
          AwtUtil.getConstraints(1,y++,1,0,2,1,true,false));
    p.add(new JScrollPane(outputField),
          AwtUtil.getConstraints(0,y++,1,1,3,1,true,true));
    
    p.add(saveOutputFileButton,
          AwtUtil.getConstraints(0,y,0,0,1,1,new Insets(10,10,10,10),
                                 GridBagConstraints.WEST, false,false));
    p.add(goButton, 
          AwtUtil.getConstraints(2,y++,0,0,1,1,new Insets(10,10,10,10),
                                 GridBagConstraints.EAST, false,false));
    
    getContentPane().add(p, BorderLayout.CENTER);
    
    authButton.addActionListener(this);
    operationChoice.addActionListener(this);
    inputFormatChoice.addActionListener(this);
    outputFormatChoice.addActionListener(this);
    goButton.addActionListener(this);
    selectInputFileButton.addActionListener(this);
    saveOutputFileButton.addActionListener(this);
    addParamButton.addActionListener(this);
    delParamButton.addActionListener(this);
    
    updateOperationDescription();
    pack();
    setSize(getPreferredSize());
  }

  private void updateOperationDescription() {
    String op = String.valueOf(operationChoice.getSelectedItem());
    Object desc = knownOperations.get(op);
    if(desc!=null)
      opDescriptionLabel.setText(String.valueOf(desc));
    else
      opDescriptionLabel.setText("");
  }
  
  private void updateOutput() {
    if(!outputChanged) return;
    
    byte bytes[] = this.outputStream.toByteArray();
    if(outputFormatChoice.getSelectedIndex()<=0) {
      try {
        outputField.setText(new String(bytes, "UTF8"));
      } catch (Exception e) {
        outputFormatChoice.setSelectedIndex(1);
        outputField.setText(Util.decodeHexString(bytes, true));
      }
    } else {
      outputField.setText(Util.decodeHexString(bytes, true));
    }
    outputChanged = false;
  }
  
  private byte[] getInputBytes() {
    switch(inputFormatChoice.getSelectedIndex()) {
      case 1: return Util.encodeHexString(inputField.getText());
       default: return Util.encodeString(inputField.getText());
    }
  }
  
  private void performOperation() {
    try {
      if(paramTable.getEditingRow()>=0 && paramTable.getEditingColumn()>=0) {
        if(!paramTable.getCellEditor().stopCellEditing())
          return;
      }
      
      outputStream.reset();
      outputChanged = true;
      updateOutput();
      
      if(auth==null) {
        changeAuthentication();
        if(auth==null) return;
      }
      
      String repoID = serverIDField.getText();
      
      DOClientConnection doConn = connection;
      if(doConn==null || lastRepoID==null || !doConn.isOpen() || !lastRepoID.equals(repoID)) {
        doConn = new DOClientConnection(auth);
        doConn.connect(repoID);
      }
      connection = doConn;
      lastRepoID = repoID;
      
      HeaderSet params = paramTableModel.getParameters();
      StreamPair io = doConn.performOperation(objectField.getText(), 
                                              String.valueOf(operationChoice.getSelectedItem()),
                                              params);
      
      System.err.println("writing request bytes");
      byte inputBytes[] = getInputBytes();
      io.getOutputStream().write(inputBytes);
      io.getOutputStream().flush();
      System.err.println(" wrote "+inputBytes.length+" bytes");
      io.getOutputStream().close();
      System.err.println("done writing request bytes");
      
      System.err.println("reading response bytes");
      InputStream in = io.getInputStream();
      byte buf[] = new byte[2048];
      int r;
      int numBytes = 0;
      while((r=in.read(buf, 0, buf.length))>=0) {
        outputStream.write(buf, 0, r);
        numBytes += r;
        outputChanged = true;
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            updateOutput();
          }
        });
      }
      System.err.println("done reading "+numBytes+" response bytes");
      
      //outputStream.flush();
      //outputStream.close();
    } catch (Exception e) {
      e.printStackTrace(System.err);
      JOptionPane.showMessageDialog(this, "Error: "+e, 
                                    "Error", JOptionPane.ERROR_MESSAGE);
    } finally {
      goButton.setText("Perform Operation");
      goButton.setEnabled(true);
    }

    //updateOutput();
  }
  
  private void changeAuthentication() {
    AuthWindow authWin = new AuthWindow(this, "client");
    if(auth!=null && privateKey!=null && !auth.getID().equals(DOConstants.ANONYMOUS_ID))
      authWin.setAuthentication(auth.getID(), privateKey);
    authWin.setVisible(true);
    if(!authWin.wasCanceled()) {
      auth = new PKAuthentication(authWin.getID(), authWin.getPrivateKey());
      myAuthField.setText(String.valueOf(auth));
    }
  }

  private void saveOutputToFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select an output file");
    int returnVal = chooser.showSaveDialog(null);
    if(returnVal != JFileChooser.APPROVE_OPTION)
      return;
    
    File f = chooser.getSelectedFile();
    if(f!=null) {
      try {
        byte output[] = outputStream.toByteArray();
        FileOutputStream fout = new FileOutputStream(f);
        fout.write(output);
        fout.close();
      } catch (Exception e) {
        e.printStackTrace(System.err);
        JOptionPane.showMessageDialog(this, "Error: "+e, 
                                      "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void readInputFromFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Select an input file");
    int returnVal = chooser.showOpenDialog(null);
    if(returnVal != JFileChooser.APPROVE_OPTION)
      return;
    
    File f = chooser.getSelectedFile();
    if(f!=null) {
      try {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[1024];
        int r;
        FileInputStream fin = new FileInputStream(f);
        int numBytes = 0;
        while((r=fin.read(buf))>=0) {
          bout.write(buf, 0, r);
          numBytes+=r;
        }
        System.err.println(" "+numBytes+" bytes read from file");
        inputField.setText(Util.decodeHexString(bout.toByteArray(), true));
      } catch (Exception e) {
        e.printStackTrace(System.err);
        JOptionPane.showMessageDialog(this, "Error: "+e, 
                                      "Error", JOptionPane.ERROR_MESSAGE);
        
      }
      validate();
    }
  }
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==operationChoice) {
      updateOperationDescription();
    } else if(src==inputFormatChoice) {
      // nothing to do with current implementation
    } else if(src==outputFormatChoice) {
      outputChanged = true;
      updateOutput();
    } else if(src==authButton) {
      changeAuthentication();
    } else if(src==addParamButton) {
      paramTableModel.addParameter("New Parameter", "Enter a Value");
    } else if(src==delParamButton) {
      int selectedRow = paramTable.getSelectedRow();
      if(selectedRow>=0)
        paramTableModel.deleteParameter(selectedRow);
    } else if(src==selectInputFileButton) {
      readInputFromFile();
    } else if(src==saveOutputFileButton) {
      saveOutputToFile();
    } else if(src==goButton) {
      goButton.setText("Processing....");
      goButton.setEnabled(false);
      new Thread(new Runnable() {
        public void run() {
          performOperation();
        }
      }).start();
    } else if(src==objectField) {
    }
  }


  private class ParamTableModel
    extends AbstractTableModel
  {
    private Vector params = new Vector();

    public String getColumnName(int column) {
      switch(column) {
        case 0: return "Key";
        case 1: return "Value";
        default: return "";
      }
    }

    public HeaderSet getParameters() {
      HeaderSet headers = new HeaderSet();
      for(int i=0; i<params.size(); i++) {
        String param[] = (String[])params.elementAt(i);
        headers.addHeader(param[0], param[1]);
      }
      return headers;
    }
    
    public boolean isCellEditable(int row, int col) {
      return true;
    }
    
    /** Add a new parameter key-value pair and return the index of it */
    public int addParameter(String key, String value) {
      if(key==null) key = "";
      if(value==null) value = "";
      String param[] = new String[] {key, value};
      params.addElement(param);
      int idx = params.size()-1;
      fireTableRowsInserted(idx, idx);
      return idx;
    }

    public void deleteParameter(int row) {
      if(row<0) return;
      params.removeElementAt(row);
      fireTableRowsDeleted(row, row);
    }
    
    public int getRowCount() {
      return params.size();
    }
    
    public int getColumnCount() { return 2; }
    
    public Object getValueAt(int row, int column) {
      String param[] = (String[])params.elementAt(row);
      return param[column];
    }

    public void setValueAt(Object obj, int row, int col) {
      String param[] = (String[])params.elementAt(row);
      param[col] = String.valueOf(obj);
    }
     
  }

  
  public static void main(String[] args) {
    Main m = new Main();
    m.setVisible(true);
  }
}
