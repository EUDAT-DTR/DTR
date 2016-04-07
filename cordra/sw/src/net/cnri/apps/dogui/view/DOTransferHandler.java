/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.*;


public class DOTransferHandler 
extends TransferHandler
{
  private AdminToolUI ui;
  private DataFlavor fileFlavor;
  private ShowObjectWindow sow = null;
  
  public DOTransferHandler(AdminToolUI ui, ShowObjectWindow sow) {
    super("text");
    this.ui = ui;
    this.sow = sow;
    fileFlavor = DataFlavor.javaFileListFlavor;
  }
  
  public boolean canImport(JComponent c, DataFlavor[] flavors) {
    if (hasFileFlavor(flavors))   { return true; }
    return false;
  }

  private boolean hasFileFlavor(DataFlavor[] flavors) {
    for (int i=0; i<flavors.length; i++) {
      if (fileFlavor.equals(flavors[i])) {
        return true;
      }
    }
    return false;
  }
  
  
  public int getSourceActions(JComponent c) {
    return COPY;
  }
  
  
  /** Import the indicated transferable into the digital object */
  public boolean importData(JComponent c, Transferable t) {
    JLabel tc;
    System.err.println("Got import operation: "+t+" on component "+c);
    if(!canImport(c, t.getTransferDataFlavors())) {
      return false;
    }
    
    try {
      if (hasFileFlavor(t.getTransferDataFlavors())) {
        java.util.List files = (java.util.List)t.getTransferData(fileFlavor);
        sow.importFiles(files);
        return true;
      }
    } catch (UnsupportedFlavorException ufe) {
      System.err.println("importData: unsupported data flavor");
    } catch (IOException ieo) {
      System.err.println("importData: I/O exception");
    }
    return false;
  }
  
}
