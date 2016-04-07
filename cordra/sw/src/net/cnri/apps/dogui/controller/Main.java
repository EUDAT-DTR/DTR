/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.apps.dogui.view.AdminToolUI;
import net.cnri.util.StreamTable;

import java.io.File;

public class Main {
  private static final String VERSION_STRING = "1.0";
  private AdminToolUI ui = null;
  
  public StreamTable preferences = new StreamTable();
  
  public Main() {
    System.setProperty("com.apple.macos.useScreenMenuBar", "true");
    System.setProperty("apple.laf.useScreenMenuBar", "true");
    
    try {
      File prefsFile = new File(System.getProperty("user.home","."), ".dnaguirc");
      if(prefsFile.exists())
        preferences.readFromFile(prefsFile);
    } catch (Throwable t) {
      System.err.println("Error reading preferences file: "+t);
    }
  }
  
  /** Get the preferences table */
  public StreamTable prefs() {
    return preferences;
  }
  
  /** Save the preferences table */
  public void savePreferences() 
    throws Exception
  {
    File prefsFile = new File(System.getProperty("user.home","."), ".dnaguirc");
    preferences.writeToFile(prefsFile);
  }
  
  public AdminToolUI getUI() { return ui; }
  
  synchronized final void go() {
    if(ui==null) ui = new AdminToolUI(this);
    ui.go();
  }

  public void shutdown() {
    System.exit(0);
  }
  
  /** Returns whether or not the given knowbot needs an update if the latest
    * version is the one given. */
  public boolean doesKnowbotNeedUpdate(String repoType, String botType, int latestVersion) {
    return prefs().getInt("knowbot.version."+repoType+"."+botType, 0) < latestVersion;
  }
  
  /** Stores the version of the given knowbot */
  public void setKnowbotUpdated(String repoType, String botType, int version) {
    prefs().put("knowbot.version."+repoType+"."+botType, version);
    try { savePreferences(); } catch (Exception e) {}
  }
  
  public static void main(String argv[]) {
    System.setProperty("apple.awt.window.position.forceSafeProgrammaticPositioning",
                       "false");
    System.setProperty("apple.awt.window.position.forceSafeCreation", "false");
    System.err.println("set properties; starting app");
    Main m = new Main();
    m.go();
  }

}
