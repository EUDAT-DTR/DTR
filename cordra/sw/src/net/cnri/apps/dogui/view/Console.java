/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.guiutil.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;


public class Console
  extends JFrame
  implements ActionListener
{
  private AdminToolUI ui;
  private PrintStream oldErr;
  private PrintStream oldOut;
  private ConsolePanel console;
  private JButton clearButton;
  private JButton saveButton;
  private JButton closeButton;
  private JButton copyToClipboardButton;
  
  public Console(AdminToolUI ui) {
    super(ui.getStr("console"));
    this.ui = ui;

    setJMenuBar(ui.getAppMenu());
    
    clearButton = new JButton(ui.getStr("clear_console"));
    saveButton = new JButton(ui.getStr("save_console"));
    closeButton = new JButton(ui.getStr("dismiss"));
    copyToClipboardButton = new JButton(ui.getStr("copy_to_clipboard"));
    console = new ConsolePanel();
    
    JPanel p = new JPanel(new GridBagLayout());
    p.add(console, GridC.getc(0,0).wxy(1,1).fillboth().colspan(5));
    p.add(clearButton, GridC.getc(0,1).insets(5,10,10,5));
    p.add(copyToClipboardButton, GridC.getc(1,1).insets(5,5,10,10));
    p.add(saveButton, GridC.getc(2,1).insets(5,5,10,10));
    p.add(Box.createHorizontalStrut(50), GridC.getc(3,1).wx(1));
    p.add(closeButton, GridC.getc(4,1).insets(5,10,10,10));
    
    getContentPane().add(p);
    setSize(new Dimension(700, 200));
    
    this.oldErr = System.err;
    this.oldOut = System.out;
    System.setErr(new PrintStream(console.getOutputStream(), true));
    System.setOut(new PrintStream(console.getOutputStream(), true));

    clearButton.addActionListener(this);
    closeButton.addActionListener(this);
    saveButton.addActionListener(this);
    copyToClipboardButton.addActionListener(this);
  }

  public void setVisible(boolean vis) {
    super.setVisible(vis);
    if(!vis) {
      System.setErr(oldErr);
      System.setOut(oldOut);
      ui.clearConsole();
    }
  }
    
  
  private void saveConsole() {
    FileDialog fwin = new FileDialog(this,
                                     ui.getStr("choose_console_file"),
                                     FileDialog.SAVE);
    fwin.setVisible(true);
    String fileStr = fwin.getFile();
    String dirStr = fwin.getDirectory();
    if(fileStr==null || dirStr==null)
      return;
    File saveFile = new File(dirStr + fileStr);
    try {
      if(saveFile.exists() && !saveFile.canWrite()) {
        JOptionPane.showMessageDialog(this,
                                      "The selected file: \n  "+
                                      dirStr+fileStr+
                                      "\nis not writeable.");
        return;
      }

      FileWriter fout = new FileWriter(saveFile);
      console.writeConsoleContents(fout);
      fout.close();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this,
                                    "Error saving file:\n  "+dirStr+fileStr+
                                    "\n\nError Message: "+e);
    }
      
  }
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==clearButton) {
      console.clear();
    } else if(src==closeButton) {
      setVisible(false);
    } else if(src==saveButton) {
      saveConsole();
    } else if(src==copyToClipboardButton) {
      console.copyContentsToClipboard();
    }
  }

}
