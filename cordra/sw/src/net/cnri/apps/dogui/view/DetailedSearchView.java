/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.apps.dogui.controller.*;
import net.cnri.guiutil.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class DetailedSearchView 
  extends JPanel
{
  public static final String LAST_CREATOR_PREF = "search.last_creator";
  public static final String DATE_ARCHIVED_ATTRIBUTE = "objcreated";
  
  private String attributeNames[] = {
    
  };
  private String attributeKeys[] = {
    
  };
  
  private AdminToolUI appUI;
  private JTextField phraseFields[] = { new JTextField("", 35) };
  private JTextField keywordField = new JTextField("", 35);
  private DefaultComboBoxModel creatorModel = new DefaultComboBoxModel();
  private JComboBox creatorChoice = new JComboBox(creatorModel);
  private DefaultComboBoxModel typeModel = new DefaultComboBoxModel();
  private JComboBox typeChoice = new JComboBox(typeModel);
  private DateParameterView dateArchived;
  private EntityMap addressBook = null;
  
  public DetailedSearchView(AdminToolUI ui) {
    super(new GridBagLayout());
    this.appUI = ui;
    this.dateArchived = new DateParameterView();
    this.addressBook = ui.getAddressBook();
    
    creatorChoice.setRenderer(new EntityRenderer());
    creatorChoice.setEditable(true);
    String myID = null;
    try {
      myID = ui.getUserObject().getID();
    } catch (Exception e) {
      System.err.println("Uh oh... I don't know who I am: "+e);
    }
    String lastCreatorStr = ui.getMain().prefs().getStr(LAST_CREATOR_PREF, myID);
    
    creatorModel.addElement("");
    EntityMap.Entity lastCreator = null;
    try {
      EntityMap addrBook = ui.getAddressBook();
      for(Iterator it=addrBook.getEntities().iterator(); it.hasNext(); ) {
        EntityMap.Entity entry = (EntityMap.Entity)it.next();
        creatorModel.addElement(entry.getID());
        if(lastCreatorStr!=null && entry.getID().equals(lastCreatorStr)) {
          lastCreator = entry;
        }
      }
    } catch (Exception e) {
      System.err.println("Error getting address book: "+e);
    }
    if(lastCreator!=null) {
      creatorChoice.setSelectedItem(lastCreator.getID());
    }
    creatorChoice.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent evt) {
        Object obj = creatorChoice.getSelectedItem();
        String selItem = "";
        if(obj==null) {
          selItem = "";
        } else if(obj instanceof EntityMap.Entity) {
          selItem = ((EntityMap.Entity)obj).getID();
          creatorChoice.setSelectedItem(selItem);
        } else {
          selItem = String.valueOf(obj);
        }
        appUI.getMain().prefs().put(LAST_CREATOR_PREF, selItem);
        try {
          appUI.getMain().savePreferences();
        } catch (Exception e) {}
      }
    });    
    typeModel.addElement("");
    typeModel.addElement("application/java");
    typeModel.addElement("application/pdf");
    typeModel.addElement("application/postscript");
    typeModel.addElement("application/msword");
    typeModel.addElement("application/rss+xml");
    typeModel.addElement("text/css");
    typeModel.addElement("text/html");
    typeModel.addElement("text/plain");
    typeModel.addElement("text/richtext");
    typeModel.addElement("text/rtf");
    typeModel.addElement("text/sgml");
    typeModel.addElement("text/tab-separated-values");
    typeModel.addElement("text/vnd.wap.wml");
    typeModel.addElement("text/vnd.wap.wmlscript");
    typeModel.addElement("text/xml");  // html, rss
    typeModel.addElement("text/x-setext");
    typeModel.addElement("application/vnd.ms-excel");
    typeModel.addElement("application/vnd.ms-powerpoint");
    typeModel.addElement("application/vnd.oasis.opendocument.text");
    typeModel.addElement("application/vnd.oasis.opendocument.text-template");
    typeModel.addElement("application/vnd.oasis.opendocument.text-master");
    typeModel.addElement("application/vnd.oasis.opendocument.text-web");
    typeModel.addElement("application/vnd.oasis.opendocument.presentation");
    typeModel.addElement("application/vnd.oasis.opendocument.presentation-template");
    typeModel.addElement("application/vnd.oasis.opendocument.spreadsheet");
    typeModel.addElement("application/vnd.oasis.opendocument.spreadsheet-template");
    typeModel.addElement("application/vnd.sun.xml.calc");
    typeModel.addElement("application/vnd.sun.xml.calc.template");
    typeModel.addElement("application/vnd.sun.xml.impress");
    typeModel.addElement("application/vnd.sun.xml.impress.template");
    typeModel.addElement("application/vnd.sun.xml.writer");
    typeModel.addElement("application/vnd.sun.xml.writer.template");
    typeModel.addElement("application/vnd.wap.wbxml");
    typeModel.addElement("application/vnd.wap.wmlc");
    typeModel.addElement("application/vnd.wap.wmlscriptc");
    typeModel.addElement("application/xhtml+xml");
    typeModel.addElement("application/x-javascript");
    typeModel.addElement("application/x-kword");
    typeModel.addElement("application/x-kspread");
    typeModel.addElement("application/x-latex");
    typeModel.addElement("application/x-netcdf");
    typeModel.addElement("application/x-sh");
    typeModel.addElement("application/x-shockwave-flash");
    typeModel.addElement("application/x-tcl");
    typeModel.addElement("application/x-tex");
    typeModel.addElement("application/x-texinfo");
    typeModel.addElement("application/x-troff");
    typeModel.addElement("application/x-troff-man");
    typeModel.addElement("application/x-troff-me");
    typeModel.addElement("application/x-troff-ms");
    typeModel.addElement("application/zip");
    typeModel.addElement("message/news");
    typeModel.addElement("message/rfc822");
    
    typeChoice.setEditable(true);
    
    rebuildUI();
  }
  
  private void rebuildUI() {
    removeAll();
    int y = 0;
    add(new JLabel(appUI.getStr("keywords")+": "), GridC.getc(0,y).label());
    add(keywordField, GridC.getc(1,y++).field());
    
    for(int i=0; i<phraseFields.length; i++) {
      if(i==0) {
        add(new JLabel(appUI.getStr("phrase")+": "), GridC.getc(0,y).label());
      }
      add(phraseFields[i], GridC.getc(1,y++).field());
    }
    
    add(new JLabel(appUI.getStr("creator")+": "), GridC.getc(0,y).label());
    add(creatorChoice, GridC.getc(1,y++).field());
    
    add(new JLabel(appUI.getStr("orig_file_format")+": "), GridC.getc(0,y).label());
    add(typeChoice, GridC.getc(1,y++).field());
    
    add(new JLabel(appUI.getStr("date_created")+": "), GridC.getc(0,y).label().northEast());
    add(dateArchived, GridC.getc(1,y++).field());
  }
  
  
  /** Return the current search parameters as a DOSearch */
  public DOSearch getSearch() {
    DOSearch search = new DOSearch(keywordField.getText().trim());
    for(int i=0; i<phraseFields.length; i++) {
      String phrase = phraseFields[i].getText().trim();
      if(phrase.length()>0) search.addPhrase(phrase);
    }
    
    String mimeType = String.valueOf(typeChoice.getSelectedItem());
    if(mimeType.trim().length()>0 && !mimeType.equals("null")) {
      search.addAttribute("elatt_content_mimetype", mimeType);
    }
    
    Object creator = creatorChoice.getSelectedItem();
    if(creator==null) {
    } else if(creator instanceof EntityMap.Entity) {
      search.addAttribute("objatt_creator", ((EntityMap.Entity)creator).getID());
    } else {
      String creatorStr = String.valueOf(creator).trim();
      if(creatorStr.length()>0) {
        search.addAttribute("objatt_creator", creatorStr);
      }
    }
    
    dateArchived.addToSearch(search, DATE_ARCHIVED_ATTRIBUTE);
    
    // TODO:  add attributes..
    
    return search;
  }
  
  public void setSearch(DOSearch search) {
    ArrayList phrases = new ArrayList();
    for(Iterator it=search.getPhrases(); it.hasNext(); ) {
      String phrase = (String)it.next();
      phrase = phrase.trim();
      if(phrase.length()>0) {
        phrases.add(new JTextField(phrase, 35));
      }
    }
    if(phrases.size()<=0) phrases.add(new JTextField("", 35));
    phraseFields = (JTextField[])phrases.toArray(new JTextField[phrases.size()]);
    
    StringBuffer kw = new StringBuffer();
    for(Iterator it=search.getKeywords(); it.hasNext(); ) {
      if(kw.length()>0) kw.append(' ');
      kw.append((String)it.next());
    }
    keywordField.setText(kw.toString());
    
    String mimeType = search.getFirstAttribute("elatt_content_mimetype");
    if(mimeType!=null) {
      if(mimeType.startsWith("\"")) mimeType = mimeType.substring(1);
      if(mimeType.endsWith("\"")) mimeType = mimeType.substring(0, mimeType.length()-1);
      typeChoice.setSelectedItem(mimeType);
    } else {
      typeChoice.setSelectedItem("");
    }
    
    dateArchived.setDates(search, DATE_ARCHIVED_ATTRIBUTE);
    
    rebuildUI();
  }

  
  class DateParameterView
    extends JPanel
  {
    private JLabel startLabel;
    private JFormattedTextField startField;
    private JLabel endLabel;
    private JFormattedTextField endField;
    private JComboBox dateChoice;
    
    public DateParameterView() {
      startField = new JFormattedTextField();
      endField = new JFormattedTextField();
      dateChoice = new JComboBox(new String[] {
        appUI.getStr("date_search_any_date"),
        appUI.getStr("date_search_today"),
        appUI.getStr("date_search_this_week"),
        appUI.getStr("date_search_this_month"),
        appUI.getStr("date_search_this_year"),
        appUI.getStr("date_search_custom"),
      });
      
      dateChoice.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent evt) {
          dateChoiceChanged();
        }
      });
      
      setLayout(new GridBagLayout());
      add(dateChoice, GridC.getc(0,0).colspan(2).fillx());
      add(startLabel = new JLabel(appUI.getStr("date_search_from")+": "), GridC.getc(0,1).label());
      add(startField, GridC.getc(1,1).field());
      add(endLabel = new JLabel(appUI.getStr("date_search_to")+": "), GridC.getc(0,2).label());
      add(endField, GridC.getc(1,2).field());
      dateChoiceChanged();
    }
    
    
    /** Set any date constraints on the given search in an attribute with the given name */
    public void setDates(DOSearch search, String attributeName) {
      Date range[] = search.getDateRange(attributeName);
      if(range!=null && range.length>=2) {
        dateChoice.setSelectedIndex(5);
        startField.setValue(range[0]);
        endField.setValue(range[1]);
      } else {
        System.err.println("  no valid date found for attribute in "+search);
        dateChoice.setSelectedIndex(0);
      }
    }
    
    
    public void addToSearch(DOSearch search, String attributeName) {
      if(dateChoice.getSelectedIndex()==0) return; // all-dates is selected
      try {
        startField.commitEdit();
        endField.commitEdit();
        search.addRangeAttribute(attributeName,
                                 (Date)startField.getValue(),
                                 (Date)endField.getValue());
      } catch (Exception e) {
        System.err.println("Error parsing date range: "+e);
      }
    }
    
    private void dateChoiceChanged() {
      boolean editable = false;
      Calendar cal = Calendar.getInstance();
      switch(dateChoice.getSelectedIndex()) {
        case 0: // any date
          cal.setTime(new Date(0));
          cal.set(Calendar.YEAR, 0);
          startField.setValue(cal.getTime());
          endField.setValue(new Date(System.currentTimeMillis()+86400*1000));
          break;
        case 1: // today
          startField.setValue(cal.getTime());
          endField.setValue(cal.getTime());
          break;
        case 2: // this week
          endField.setValue(cal.getTime());
          cal.add(Calendar.DATE, -7);
          startField.setValue(cal.getTime());
          break;
        case 3: // this month
          cal.set(Calendar.DAY_OF_MONTH, 1);
          startField.setValue(cal.getTime());
          int thisMonth = cal.get(Calendar.MONTH);
          while(cal.get(Calendar.MONTH)==thisMonth) cal.add(Calendar.DATE, 1);
          cal.add(Calendar.DATE, -1);
          endField.setValue(cal.getTime());
          break;
        case 4: // this year
          cal.set(Calendar.DAY_OF_YEAR,1);
          startField.setValue(cal.getTime());
          cal.add(Calendar.DATE, 360);
          int thisYear = cal.get(Calendar.YEAR);
          while(cal.get(Calendar.YEAR)==thisYear) cal.add(Calendar.DATE, 1);
          cal.add(Calendar.DATE, -1);
          endField.setValue(cal.getTime());
          break;
        case 5: // custom dates
          editable = true;
          break;
      }
      startField.setEnabled(editable);
      startLabel.setEnabled(editable);
      endField.setEnabled(editable);
      endLabel.setEnabled(editable);
    }
  }

  
  class EntityRenderer
    extends DefaultListCellRenderer 
    //implements ListCellRenderer
  {
    
    public Component getListCellRendererComponent(JList list, Object value,
                                                  int index, boolean isSelected,
                                                  boolean cellHasFocus)
    {
      if(value.equals("")) value = appUI.getStr("anyone");
      if(addressBook!=null && value!=null && value instanceof String) {
        EntityMap.Entity entity = addressBook.getEntityForID(String.valueOf(value));
        if(entity!=null) {
          value = entity;
        }
      }
      
      return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
    
  }
  
}
