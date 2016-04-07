/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.apps.dogui.controller.*;
import net.cnri.apps.dogui.view.resources.Resources;
import net.cnri.apps.doutilgui.AuthWindow;
import net.cnri.do_api.DigitalObject;
import net.cnri.do_api.Repository;
import net.cnri.do_api.EntityMap;
import net.cnri.do_api.DOKeyRing;
import net.cnri.dobj.DOAuthentication;
import net.cnri.dobj.DOClient;
import net.cnri.dobj.PKAuthentication;
import net.cnri.dobj.SecretKeyAuthentication;
import javax.swing.*;
import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.security.PrivateKey;
import java.util.*;

public class AdminToolUI {
  public static final String METADATA_ELEMENT = "metadata.dc";
  
  // object attributes
  public static final String HOME_REPO_ATT = "do.home_repo";
  public static final String SYNC_QUERY_ATT = "do.sync_query";
  public static final String INDEXES_ATT = "do.indexes";
  
  public static final String INBOX = "inbox"; // caching repo for desired objects
  public static final String OUTBOX = "outbox"; // staging area for locally created objects
  //public static final String NET_INDEX = "netidx"; // index repository out on the network
  //public static final String NET_REPO = "netrepo"; // the destination for locally created objects
  public static final String EXTERNAL_HD = "exthd"; // repository on pluggable HD
  public static final String LOCAL_INDEX = "localidx";
  
  private Main main = null;
  private AdminToolUI ui = null;
  private Resources resources = null;
  
  private ArrayList windows = new ArrayList();
  private AddressBookWindow addrWin = null;
  private ManageGroupsWindow manageGroupsWin = null;
  
  private Console consoleWin = null;
  private DOAuthentication auth = null;
  private DigitalObject userObj = null;
  private EntityMap addressBook = null;
  private DOKeyRing keys = null;
  
  //private DNAKeyRing keyRing;
  private HashMap connectionCache = new HashMap();
  private DOImages images = new DOImages();
  
  private PrivateKey privateKey = null;
  private String secretKey = null;
  
  private RepoRunner inboxServer = null;
  private RepoRunner outboxServer = null;
  private RepoRunner indexServer = null;
  
  public static boolean isMacOSX = false;
  
  private HDLAction depositAction;
  private HDLAction newSearchAction;
  private HDLAction editSettingsAction;
  private HDLAction clearCacheAction;
  private HDLAction authenticateAction;
  private HDLAction shutdownAction;
  private HDLAction showConsoleAction;
  private HDLAction showAddressBookAction;
  private HDLAction manageGroupsAction;
  
  public AdminToolUI(Main main) {
    this.main = main;
    this.ui = this;
    
    String osName = System.getProperty("os.name", "");
    isMacOSX = osName.toUpperCase().indexOf("MAC OS X")>=0;
    
    resources = (Resources)ResourceBundle.
      getBundle("net.cnri.apps.dogui.view.resources.Resources",
                Locale.getDefault());

    depositAction = 
      new HDLAction(ui, "deposit", "deposit", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          Component frame = (Component)evt.getSource();
          while(frame!=null && !(frame instanceof Frame)) {
            frame = frame.getParent();
          }
          beginDeposit((Frame)frame);
        }
      });
    
    newSearchAction = 
      new HDLAction(ui, "new_search_win", "new_search_win", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          showNewSearchWindow();
        }
      });
    newSearchAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("meta n"));
    
    editSettingsAction =
      new HDLAction(ui, "settings", "settings", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          showSettingsWindow();
        }
      });
    
    showAddressBookAction =
      new HDLAction(ui, "show_addressbook", "show_addressbook", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          showAddressBook();
        }
      });
    
    clearCacheAction = 
      new HDLAction(ui, "clear_cache", "", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          try {
            DOClient.getResolver().getResolver().clearCaches();
          } catch (Exception e) {
            JOptionPane.showMessageDialog(null, ui.getStr("error_clearing_cache_msg")+e,
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return;
          }
          JOptionPane.showMessageDialog(null, ui.getStr("cache_cleared_msg"),
                                        "Error", JOptionPane.INFORMATION_MESSAGE);
        }
      });
    
    authenticateAction = 
      new HDLAction(ui, "authenticate", "", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          getAuthentication(true);
        }
      });
    
    shutdownAction =
      new HDLAction(ui, "quit", "", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          shutdown();
        }
      });
    
    showConsoleAction =
      new HDLAction(ui, "console", "", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          showConsoleWindow();
        }
      });
    manageGroupsAction = 
      new HDLAction(ui, "manage_groups", "", new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          showManageGroupsWindow();
        }
      });
  }
  
  /** Return a DigitalObject for the given object in the given repository.  This 
    * displays a message and returns null if there was an error. */
  public DigitalObject getObjectReference(String repoID, String objectID) {
    if(objectID==null) throw new NullPointerException();
    DOAuthentication auth = this.auth;
    
    String gateway = null;
    try {
      gateway = getUserObject().getAttribute("do.gateway", null);
    } catch (Exception e) {
      System.err.println("error getting do.gateway attribute from PDO: "+e);
    }
    
    if(repoID==null && gateway==null) {
      try {
        repoID = DOClient.resolveRepositoryID(objectID);
      } catch (Exception e) {
        showErrorMessage(null, "Unable to locate server for "+objectID);
      }
    }
    
    synchronized(connectionCache) {
      Repository repo = null;
      if(gateway!=null) {
        repo = getRepositoryByID(gateway);
      } else {
        repo = (Repository)connectionCache.get(repoID);
      }
      
      if(repo==null) {
        try {
          repo = new Repository(auth, repoID);
          connectionCache.put(repoID, repo);
        } catch (Exception e) {
          showErrorMessage(null, "Unable to connect to repository "+repoID+"\nError: "+e);
          return null;
        }
      }
      
      try {
        return repo.getDigitalObject(objectID);
      } catch (Exception e) {
        e.printStackTrace(System.err);
        showErrorMessage(null, "Unable to access object: "+objectID+" from server: "+repoID);
        return null;
      }
    }
  }
  
  
  /** Initialize (if needed) and return an authenticated connection
    * to the repository with the given role. */
  public Repository getRepository(String repoType) {
    String repoID = getRepoID(repoType);
    if(repoID==null || repoID.trim().length()<=0) {
      return null;
    }
    
    Repository repo = getRepositoryByID(repoID);
    if(repo!=null && (repoType.equals(INBOX) || 
                      repoType.equals(OUTBOX) || 
                      repoType.equals(LOCAL_INDEX))) {
      repo.setUseEncryption(false);
    }
    return repo;
  }
  
  
  /** Initialize (if needed) and return an authenticated connection
    * to the repository with the given identifier.  Displays an error message
    * and returns null if there was a problem. */
  public Repository getRepositoryByID(String repoID) {
    synchronized (connectionCache) {
      Repository repo = (Repository)connectionCache.get(repoID);
      if(repo!=null) return repo;

      DOAuthentication auth = getAuthentication(false);
      if(auth==null) return null;
      try {
        repo = new Repository(auth, repoID);
        connectionCache.put(repoID, repo);
        return repo;
      } catch (Exception e) {
        e.printStackTrace(System.err);
        showErrorMessage(null, "Unable to connect to repository "+repoID+"\nError: "+e);
        return null;
      }
    }
  }
  
  
  
  public void clearConnections() {
    synchronized(connectionCache) {
      HashMap conns = connectionCache;
      connectionCache = new HashMap();
      for(Iterator it=conns.values().iterator(); it.hasNext(); ) {
        Repository repo = (Repository)it.next();
        try {
          // uncomment this to close potentially in use connections
          //repo.getConnection().close();
        } catch (Exception e) {
          System.err.println("Error closing connection: "+e);
        }
      }
    }
  }
  
  /** Set the ID for the repository with the given role */
  public void setRepoID(String repoType, String repoID) {
    getMain().prefs().put(repoType+"_id", repoID);
  }
  
  /** Get the ID of the repository with the given role */
  public String getRepoID(String repoType) {
    return getMain().prefs().getStr(repoType+"_id", null);
  }

  
  private void setupNewUser() {
    NewUserWindow nuw = new NewUserWindow(this);
    nuw.setVisible(true);
  }
  
  public synchronized DOAuthentication getAuthentication(boolean changeAuthentication) {
    if(auth!=null && !changeAuthentication) return auth;
    
    AuthWindow authWin = new AuthWindow(null, "client");
    
    authWin.setExtraHandler(ui.getStr("new_user"), new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        setupNewUser();
      }
    });
    
    if(auth!=null) {
      if(auth instanceof PKAuthentication && privateKey!=null) {
        authWin.setAuthentication(auth.getID(), privateKey);
      } else if(auth instanceof SecretKeyAuthentication && secretKey!=null) {
        authWin.setSecretKeyAuthentication(auth.getID(), secretKey);
      }
    }
    
    try {
      authWin.setVisible(true);
      if(authWin.wasCanceled()) return null;
      auth = authWin.getAuthentication();
      if(auth instanceof PKAuthentication) {
        privateKey = authWin.getPrivateKey();
        secretKey = null;
      } else if(auth instanceof SecretKeyAuthentication) {
        secretKey = authWin.getSecretKey();
        privateKey = null;
      }
      userObj = getObjectReference(null, auth.getID());
      addressBook = null;
      return auth;
    } finally {
      for(int i=windows.size()-1; i>=0; i--) {
        Object obj = windows.get(i);
        if(obj instanceof DOFrame) {
          ((DOFrame)obj).authenticationChanged(auth);
        }
      }
    }
  }
  
  /** Return the digital object that stores information for this user */
  public DigitalObject getUserObject() {
    getAuthentication(false);
    return userObj;
  }
  
  
  /** Return the address book associated with this user */
  public synchronized EntityMap getAddressBook() {
    if(addressBook==null) {
      EntityMap ab = new EntityMap();
      DigitalObject uo = getUserObject();
      if(uo!=null) {
        try {
          ab.loadFromObject(uo);
        } catch (Exception e) {
          e.printStackTrace(System.err);
          showErrorMessage(null, String.valueOf(e));
        }
      }
      this.addressBook = ab;
    }
    return this.addressBook;
  }
  
  
  /** Return the DOKeyRing for this user */
  public synchronized DOKeyRing getKeyRing() {
    if(keys!=null) return keys;
    
    DigitalObject uo = getUserObject();
    if(uo==null) return null;
    
    if(privateKey==null) return null; // no decryption possible without PK
    
    try {
      DOKeyRing tmpKeys = new DOKeyRing(privateKey, uo);
      tmpKeys.loadKeys();
      return keys = tmpKeys;
    } catch (Throwable e) {
      e.printStackTrace(System.err);
      showErrorMessage(null, getStr("err_loading_keyring")+"\n\n  "+e);
    }
    return keys;
  }
  
  
  
  //  public DNAKeyRing getKeyRing() {
  //    if(getAuthentication(false)==null) return null;
  //    return keyRing;
  //  }
  
  //final PrivateKey getPrivateKey() {
  //  if(privateKey==null) getAuthentication(false);
  //  return privateKey;
  //}
  
  public Main getMain() { return main; }

  public synchronized void showAddressBook() {
    if(addrWin==null) {
      DigitalObject userObj = getUserObject();
      if(userObj==null) return;
      addrWin = new AddressBookWindow(null, this, userObj, getAddressBook());
    }
    
    addrWin.setVisible(true);
  }
  
  public synchronized void hideAddressBook() {
    if(addrWin!=null) {
      addrWin.setVisible(false);
      addrWin = null;
    }
  }


  private boolean okButtonPressed = false; // used during startup
  private JDialog waitWin = null; // used during startup
  private JButton okButton = null; // used during startup
  public void go() {
    if(getAuthentication(false)==null) {
      main.shutdown();
      return;
    }
    
    // start up embedded repositories
    File userHome = new File(System.getProperty("user.home", "."));
    File repoDir = new File(userHome, "DigitalObjects");
    
    /*
    
    
    waitWin = new JDialog((JFrame)null, getStr("starting_servers_msg"), false);
    okButton = new JButton(getStr("cancel"));
    JLabel waitLabel = new JLabel(getStr("starting_servers_msg"));
    JPanel p = new JPanel(new GridBagLayout());
    p.add(waitLabel, GridC.getc(0,0).wx(1).fillboth().insets(12,20,12,20));
    p.add(okButton, GridC.getc(0,1).east().insets(0,20,20,20));
    waitWin.getContentPane().add(p);
    okButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        okButton.setEnabled(false);
        okButtonPressed = true;
        waitWin.hide();
      }
    });
    
    waitWin.pack();
    waitWin.setSize(500, waitWin.getPreferredSize().height);
    waitWin.setLocationRelativeTo(null);
    waitWin.show();
    
    // try to get the wait window to appear
    try { Thread.sleep(100); } catch (Exception e) {}
    
    
    
    indexServer = new RepoRunner(new File(repoDir, "index"),
                                 ui.getMain(), AdminToolUI.LOCAL_INDEX,
                                 7, "Local Index", null);
    indexServer.setThreadName("Indexing Repository");
    
    inboxServer = new RepoRunner(new File(repoDir, "inbox"), 
                                 ui.getMain(), AdminToolUI.INBOX,
                                 5, "Local Inbox", indexServer);
    inboxServer.setThreadName("Inbox Repository");
    outboxServer = new RepoRunner(new File(repoDir, "outbox"),
                                  ui.getMain(), AdminToolUI.OUTBOX,
                                  6, "Local Outbox", indexServer);
    outboxServer.setThreadName("Outbox Repository");
    
    inboxServer.start();
    outboxServer.start();
    indexServer.start();
    
    try {
      boolean wasError = false;
      while(!okButtonPressed) {
        if(wasError) {
          // there was an error... just wait for acknowledgement
          try {
            Thread.currentThread().sleep(500);
          } catch (Exception e) { }
          continue;
        }
        int numStarted = 0;
        if(inboxServer.isRunning()) numStarted++;
        if(outboxServer.isRunning()) numStarted++;
        if(indexServer.isRunning()) numStarted++;
        
        if(inboxServer.getState()!=RepoRunner.STATE_RUNNING) {
          waitLabel.setText(getStr("inbox_err")+": "+inboxServer.getMessage());
          okButton.setEnabled(true);
          okButton.setText(getStr("ok"));
          wasError = true;
        } else if(indexServer.getState()!=RepoRunner.STATE_RUNNING) {
          waitLabel.setText(getStr("index_err")+": "+indexServer.getMessage());
          okButton.setEnabled(true);
          okButton.setText(getStr("ok"));
          wasError = true;
        } else if(outboxServer.getState()!=RepoRunner.STATE_RUNNING) {
          waitLabel.setText(getStr("outbox_err")+": "+outboxServer.getMessage());
          okButton.setEnabled(true);
          okButton.setText(getStr("ok"));
          wasError = true;
        } else if(numStarted>=3) {
          break;
        }
        
        try {
          Thread.currentThread().sleep(500);
        } catch (Exception e) {
          System.err.println("Error waiting: "+e);
        }
      }
      
      if(okButtonPressed) {
        main.shutdown();
        return;
      }
      
      waitLabel.setText(getStr("connecting_to_repos"));
      okButton.setEnabled(false);
      System.out.println("connected to inbox: "+getRepository(INBOX));
      System.out.println("connected to outbox: "+getRepository(OUTBOX));
      System.out.println("connected to index: "+getRepository(LOCAL_INDEX));
    } finally {
      waitWin.hide();
    }
    
    // pre-load connections to the repositories
    */
    
    new MainWindow(this).setVisible(true);
    
    //SearchWindow searchWin = new SearchWindow(ui);
    //searchWin.setVisible(true);
  }
  
  private JFrame offscreenWindow = null;
  synchronized void addWindow(DOFrame frame) {
    windows.add(frame);
    if(offscreenWindow!=null) {
      offscreenWindow.setVisible(false);
      offscreenWindow.dispose();
      offscreenWindow = null;
    }
  }
  
  synchronized void removeWindow(DOFrame frame) {
    System.err.println("removing window: "+frame+"; size="+windows.size());
    windows.remove(frame);
    if(windows.size()<=0) {
      // either exit the app or fire up an offscreen window to hold the menubar on mac
      if(System.getProperty("os.name","").toLowerCase().startsWith("mac")) {
        if(offscreenWindow==null) {
          offscreenWindow = new JFrame("");
          offscreenWindow.setJMenuBar(getAppMenu());
          offscreenWindow.setSize(0, 0);
          offscreenWindow.pack();
          offscreenWindow.setBounds(-1000, -1000, 0, 0);
          offscreenWindow.setVisible(true);
        }
      } else {
        shutdown();
      }
    }
  }
  
  public boolean hasStr(String key) {
    return resources.containsKey(key);
  }
  
  public final String getStr(String key) {
    return resources.getString(key);
  }
  
  public DOImages getImages() {
    return images;
  }
  
  synchronized void clearConsole() {
    if(consoleWin!=null) {
      consoleWin = null;
    }
  }
  
  synchronized void beginDeposit(Frame parent) {
    if(parent==null) parent = new JFrame("");
    FileDialog fwin = new FileDialog(parent,
                                     ui.getStr("choose_deposit_file"), 
                                     FileDialog.LOAD);
    JFileChooser chooser = new JFileChooser();
    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    chooser.setMultiSelectionEnabled(true);
    int returnVal = chooser.showOpenDialog(null);
    if(returnVal!=JFileChooser.APPROVE_OPTION) return;
    
    File files[] = chooser.getSelectedFiles();
    if(files==null) return;
    
    PreDepositWindow depWin = new PreDepositWindow(ui, files);
    depWin.setVisible(true);
  }
  
  public void showNewSearchWindow() {
    showNewSearchWindow("");
  }
  
  
  public void showNewSearchWindow(String search) {
    SearchWindow searchWin = new SearchWindow(this);
    searchWin.setVisible(true);
    if(search!=null && search.trim().length()>0) {
      searchWin.performSearch(new DOSearch(search));
    }
  }
  
  
  /** Display the window with the client settings */
  public synchronized void showSettingsWindow() {
    if(getAuthentication(false)==null) {
      Toolkit.getDefaultToolkit().beep();
      return;
    }
    DigitalObject userObj = this.userObj;
    UserInfoPanel uip = new UserInfoPanel(this);
    try {
      uip.loadSettingsFromUser(userObj);
    } catch (Exception e) {
      showErrorMessage(null, "Unable to retrieve settings from user object "+userObj);
      return;
    }
    
    while(true) {
      int result = JOptionPane.showConfirmDialog(null, uip, userObj.getID(), 
                                                 JOptionPane.OK_CANCEL_OPTION,
                                                 JOptionPane.PLAIN_MESSAGE);
      if(result!=JOptionPane.OK_OPTION) break;
      
      try {
        uip.saveSettingsForUser(userObj);
        break;
      } catch (Exception e) {
        showErrorMessage(null, "Error saving settings for "+userObj.getID());
      }
    }
    
  }
  
  /** Show the console window that shows error and informational messages */
  public synchronized void showConsoleWindow() {
    if(consoleWin==null) {
      consoleWin = new Console(this);
    }
    consoleWin.setVisible(true);
    consoleWin.toFront();
  }
  
  /** Show the window used to manage grouups of individuals */
  public synchronized void showManageGroupsWindow() {
    if(manageGroupsWin==null) {
      manageGroupsWin = new ManageGroupsWindow(null, this, getUserObject());
    }
    manageGroupsWin.setVisible(true);
    manageGroupsWin.toFront();
  }
  
  
  public synchronized void hideManageGroupsWindow() {
    if(manageGroupsWin!=null) {
      manageGroupsWin.setVisible(false);
      manageGroupsWin = null;
    }
  }
  
  
  public void shutdown() {
    try {
      getMain().savePreferences();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, getStr("unable_to_save_settings"));
      return;
    }
    System.exit(0);
  }
  
  private static final boolean AWT_MENU = false;
  public JMenuBar getAppMenu() {
    JMenuBar menuBar = new JMenuBar();
    JMenu fileMenu, toolsMenu;
    menuBar.add(fileMenu = new JMenu(new HDLAction(ui, "file", null, null)));
    menuBar.add(toolsMenu = new JMenu(new HDLAction(ui, "tools", null, null)));
    
    //fileMenu.add(clearCacheAction);
    //fileMenu.add(authenticateAction);
    //fileMenu.addSeparator();
    fileMenu.add(editSettingsAction);
    fileMenu.add(showAddressBookAction);
    fileMenu.add(manageGroupsAction);
    fileMenu.addSeparator();
    fileMenu.add(newSearchAction);
    fileMenu.addSeparator();
    fileMenu.add(depositAction);
    
    if(!isMacOSX) {
      fileMenu.addSeparator();
      fileMenu.add(shutdownAction);
    }
    
    toolsMenu.add(showConsoleAction);
    return menuBar;
  }

  DepositTransferHandler depositDropHandler = null;
  public synchronized TransferHandler getDepositDropHandler() {
    if(depositDropHandler!=null) return depositDropHandler;
    return depositDropHandler = new DepositTransferHandler();
  }
  
  public void showErrorMessage(Component parent, String msg) {
    SwingUtilities.invokeLater(new MsgRunner(parent, msg));
  }

  public void showInfoMessage(Component parent, String msg) {
    SwingUtilities.invokeLater(new MsgRunner(parent, msg, JOptionPane.INFORMATION_MESSAGE));
  }

  /** Class that is used to provide drag-and-drop deposit capability to any GUI elements */
  private class DepositTransferHandler 
    extends TransferHandler
  {
    private DataFlavor fileFlavor;
    
    public DepositTransferHandler() {
      super("text");
      fileFlavor = DataFlavor.javaFileListFlavor;
    }
    
    public boolean canImport(JComponent c, DataFlavor[] flavors) {
      if (hasFileFlavor(flavors))   { return true; }
      return false;
    }
    private boolean hasFileFlavor(DataFlavor[] flavors) {
      for (int i=0; i<flavors.length; i++) {
        if (DataFlavor.javaFileListFlavor.equals(flavors[i])) {
          return true;
        }
      }
      return false;
    }
    
    public int getSourceActions(JComponent c) { return COPY; }
    
    
    /** Import the indicated transferable into the digital object */
    public boolean importData(JComponent c, Transferable t) {
      try {
        JLabel tc;
        System.err.println("trying import: "+t);
        if(!canImport(c, t.getTransferDataFlavors())) {
          return false;
        }
        
        if (hasFileFlavor(t.getTransferDataFlavors())) {
          System.err.println("importing file list...");
          java.util.List files = 
          (java.util.List)t.getTransferData(DataFlavor.javaFileListFlavor);
          System.err.println("importing file list: "+files);
          
          File fileArray[] = (File[])files.toArray(new File[files.size()]);
          PreDepositWindow win = new PreDepositWindow(ui, fileArray);
          win.setVisible(true);
          return true;
        }
      } catch (Exception e) {
        System.err.println("Error importing data: "+e);
        e.printStackTrace(System.err);
      }
      return false;
    }
  }
  
  
  
  private class MsgRunner
    implements Runnable
  {
    private Component component;
    private String msg;
    private String title = "";
    private int msgType = JOptionPane.ERROR_MESSAGE;
    
    MsgRunner(String msg) {
      this(null, msg);
    }

    MsgRunner(Component comp, String msg) {
      this.component = comp;
      this.msg = msg;
    }
    
    MsgRunner(Component comp, String msg, int type) {
      this.component = comp;
      this.msg = msg;
      this.msgType = type;
    }
    
    public void run() {
      JOptionPane.showMessageDialog(component, msg);
    }
  }
  

}
