/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.apps.dogui.controller.*;
import net.cnri.guiutil.GridC;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class SyncStatusView
  extends JPanel
  implements ActionListener
{
  private AdminToolUI ui;
  
  public static final String GREEN_LIGHT = "/net/cnri/apps/dogui/view/images/green_light.png";
  public static final String RED_LIGHT = "/net/cnri/apps/dogui/view/images/red_light.png";
  public static final String NO_LIGHT = "/net/cnri/apps/dogui/view/images/clear16.png";
  public static final String DOWN_ARROW = "/net/cnri/apps/dogui/view/images/down_arrow.gif";
  
  private DNASynchronizer synchronizer;
  private String syncLabel;
  
  private Icon redLight;
  private Icon greenLight;
  private Icon noLight;
  private Icon downArrow;
  
  private JLabel statusLabel;
  private JButton syncButton;
  private JProgressBar syncProgress;
  private boolean isRunning = false;
  
  private Runnable stateUpdater;
  private Runnable statusUpdater;
  
  public SyncStatusView(AdminToolUI ui, DNASynchronizer synchronizer, String label) {
    super(new GridBagLayout());
    this.syncLabel = label;
    this.ui = ui;
    this.synchronizer = synchronizer;
    setOpaque(false);
    setBackground(Color.white);
    
    redLight = ui.getImages().getIcon(RED_LIGHT);
    greenLight = ui.getImages().getIcon(GREEN_LIGHT);
    noLight = ui.getImages().getIcon(NO_LIGHT);
    downArrow = ui.getImages().getIcon(DOWN_ARROW);
    
    JLabel titleLabel = new JLabel(label);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
    statusLabel = new JLabel(label);
    statusLabel.setForeground(Color.gray);
    statusLabel.setIcon(downArrow);
    
    syncButton = new JButton("-");
    syncProgress = new JProgressBar();
    
    //add(titleLabel, GridC.getc().xy(0,0).wx(1).fillx());
    //add(syncButton, GridC.getc().xy(1,0).rowspan(2).northEast());
    //add(statusLabel, GridC.getc().xy(0,1).fillx());
    add(statusLabel, GridC.getc().xy(0,1).fillx());
    //add(syncProgress, GridC.getc().xy(0,2).fillx().colspan(2));
    
    stateUpdater = new Runnable() {
      public void run() { updateState(); }
    };
    
    statusUpdater = new Runnable() {
      public void run() { updateStatus(); }
    };
    
    synchronizer.addListener(new SynchronizerStatusListener() {
      /** Called when the state (running, stopped, etc) is changed.  The
      * status (progress, message, etc) may also have changed as a result. */
      public void stateUpdated(DNASynchronizer sync) {
        updateState();
      }
      
      /** Called when only the status (progress, message, etc) is changed */
      public void statusUpdated(DNASynchronizer sync) {
        updateStatus();
      }
      
    });
    syncButton.addActionListener(this);
    statusLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent evt) {
        showPopup();
      }
    });
    updateState();
  }

  private JLabel disableLabel(JLabel label) {
    label.setEnabled(false);
    return label;
  }
  
  
  private void showPopup() {
    JPopupMenu popup = new JPopupMenu();
    popup.add(disableLabel(new JLabel(syncLabel)));
    popup.add(disableLabel(new JLabel(ui.getStr("status")+": "+
                                        synchronizer.getMessage())));
    popup.addSeparator();
    if(!isRunning) {
      popup.add(new HDLAction(ui, "sync_start", "sync_start") {
        public void actionPerformed(ActionEvent evt) {
          synchronizer.start();
        }
      });
    } else {
      popup.add(new HDLAction(ui, "sync_stop", "sync_stop") {
        public void actionPerformed(ActionEvent evt) {
          synchronizer.stop();
        }
      });
    }
    Dimension prefsz = popup.getPreferredSize();
    popup.show(this, 0, 0-prefsz.height);
  }
  
  public void setShowProgress(boolean showProgress) {
    syncProgress.setVisible(showProgress);
  }
  
  
  public void actionPerformed(ActionEvent evt) {
    Object src = evt.getSource();
    if(src==syncButton) {
      if(isRunning) {
        synchronizer.stop();
      } else if(synchronizer.getState()!=DNASynchronizer.STATE_BROKEN) {
        synchronizer.start();
      } else {
        System.err.println("Synchronizer is broken...");
      }
    }
  }
  
  private void updateState() {
    updateStatus();
    switch(synchronizer.getState()) {
      case DNASynchronizer.STATE_BROKEN:
        //statusLabel.setIcon(redLight);
        syncButton.setEnabled(false);
        syncButton.setText(ui.getStr("sync_start"));
        syncProgress.setIndeterminate(false);
        syncProgress.setValue(0);
        isRunning = false;
        break;
      case DNASynchronizer.STATE_STOPPED:
        //statusLabel.setIcon(noLight);
        syncButton.setText(ui.getStr("sync_start"));
        syncProgress.setIndeterminate(false);
        syncProgress.setValue(0);
        isRunning = false;
        break;
      case DNASynchronizer.STATE_RUNNING:
      default:
        //statusLabel.setIcon(greenLight);
        syncButton.setText(ui.getStr("sync_stop"));
        isRunning = true;
    }
  }
  
  private void updateStatus() {
    float progress = synchronizer.getProgress();
    syncProgress.setIndeterminate(progress<=-1);
    if(progress>=0) {
      syncProgress.setValue(Math.min(100, Math.round(progress * 100)));
    }
    String msg = synchronizer.getMessage();
    if(msg==null) msg = "";
    //statusLabel.setText(msg);
    
  }
  
}
