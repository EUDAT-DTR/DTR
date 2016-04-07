/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.view;

import net.cnri.do_api.*;
import net.cnri.guiutil.*;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.EmptyBorder;

public class DOPermissionEditor 
  extends JPanel
{
  private static final Integer WRITE_PERMISSION_GROUP = Integer.valueOf(1);
  private static final Integer READ_PERMISSION_GROUP = Integer.valueOf(2);
  
  private static final int ACCEPT_RULE = 0;
  private static final int REJECT_RULE = 1;
  private static final int UNKNOWN_RULE = 2;
  
  private static final EmptyBorder emptyBorder = new EmptyBorder(0,0,0,0);
  
  private static final HashMap readableRules = new HashMap();
  private static final HashMap writableRules = new HashMap();
  private static final ArrayList operations = new ArrayList();
  private static final HashMap knownOps = new HashMap();
  
  static {
    // these are the operations that make up the "can read" permission set
    readableRules.put("1037/0", "list operations");
    readableRules.put("1037/46", "does object exist");
    readableRules.put("1037/5", "get data element");
    readableRules.put("1037/8", "list data elements");
    readableRules.put("1037/50", "get attributes");
    readableRules.put("1037/44", "get repo transactions");
    readableRules.put("1037/48", "get serialized form");
    
    // these are the rules that make up the "can write" permission set
    writableRules.put("1037/46", "does object exist");
    writableRules.put("1037/6", "store data element");
    writableRules.put("1037/7", "delete data element");
    writableRules.put("1037/8", "list data element");
    writableRules.put("1037/47", "push modification transaction");
    writableRules.put("1037/49", "set attributes");
    writableRules.put("1037/51", "delete attributes");
    
    knownOps.put("1037/0", "List Operations");
    knownOps.put("1037/1", "Create Object");
    knownOps.put("1037/2", "Add Type to Object");
    knownOps.put("1037/3", "Remove Type from Object");
    knownOps.put("1037/4", "Ask if Object has Type");
    knownOps.put("1037/5", "Get Data Elements");
    knownOps.put("1037/6", "Store Data Elements");
    knownOps.put("1037/7", "Delete Data Elements");
    knownOps.put("1037/8", "List Data Elements");
    knownOps.put("1037/49", "Set Attributes");
    knownOps.put("1037/50", "Get Attributes");
    knownOps.put("1037/51", "Delete Attributes");
    knownOps.put("1037/9", "List Object Types");
    knownOps.put("1037/10", "List Objects");
    knownOps.put("1037/42", "Retrieve Credentials");
    knownOps.put("1037/43", "Add Credentials");
    knownOps.put("1037/44", "Get Transactions");
    knownOps.put("1037/45", "Delete Object");
    knownOps.put("1037/46", "Ask if Object Exists");
    knownOps.put("1037/47", "Push Transactions");
    knownOps.put("1037/48", "Get Serialized Object");
    knownOps.put("1037/11", "Query Audit Log");
    knownOps.put("1037/12", "Get Audit Log Entry");
    knownOps.put("1037/52", "Grant Encryption Keys");
    knownOps.put("*", "Do Anything");
    knownOps.put(READ_PERMISSION_GROUP, "Read the Object");
    knownOps.put(WRITE_PERMISSION_GROUP, "Modify the Object");
    
    operations.add("*");
    operations.add(READ_PERMISSION_GROUP);
    operations.add(WRITE_PERMISSION_GROUP);
    operations.add("1037/5");
    operations.add("1037/6");
    operations.add("1037/7");
    operations.add("1037/8");
    operations.add("1037/49");
    operations.add("1037/50");
    operations.add("1037/51");
    operations.add("1037/0");
    //operations.add("1037/1");
    //operations.add("1037/2");
    //operations.add("1037/3");
    //operations.add("1037/4");
    //operations.add("1037/9");
    operations.add("1037/10");
    //operations.add("1037/42");
    //operations.add("1037/43");
    operations.add("1037/44");
    operations.add("1037/45");
    operations.add("1037/46");
    operations.add("1037/47");
    operations.add("1037/48");
    //operations.add("1037/11");
    //operations.add("1037/12");
    operations.add("---");
  }
  
  private AdminToolUI appUI;
  private EntityMap entityMap;
  private JPanel rulePanel;
  private ArrayList ruleViews;
  private JScrollPane scrollPane;
  private OperationRenderer operationRenderer;
  private EntityRenderer entityRenderer;
  
  public DOPermissionEditor(AdminToolUI ui) {
    super(new GridBagLayout());
    this.entityMap = ui.getAddressBook();
    this.appUI = ui;
    
    rulePanel = new JPanel(new GridBagLayout());
    ruleViews = new ArrayList();
    operationRenderer = new OperationRenderer();
    entityRenderer = new EntityRenderer();
    
    add(scrollPane = new JScrollPane(rulePanel, 
                                     JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                                     JScrollPane.HORIZONTAL_SCROLLBAR_NEVER),
        GridC.getc(0,0).wxy(1,1).fillboth());
    
    add(Box.createVerticalStrut(150), GridC.getc(1,0));
    // set the default rules
    String defaultPrefs = ui.getMain().prefs().getStr("new_obj_perms", null);
    try {
      if(defaultPrefs!=null) {
        readPermissions(new ByteArrayInputStream(defaultPrefs.getBytes("UTF8")));
      } else {
        DORule defaultRule = new DORule("");
        defaultRule.who = ui.getUserObject().getID();
        defaultRule.operation = "*";
        defaultRule.ruleType = ACCEPT_RULE;
        PermissionComponent permComp = new PermissionComponent(defaultRule);
        ruleViews.add(new AddRemovePanel(permComp));
        rebuildRulePanel();
      }
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
  
  public void storePermissionsInPreferences() {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    try {
      writePermissions(bout);
      appUI.getMain().prefs().put("new_obj_perms", new String(bout.toByteArray(), "UTF8"));
      appUI.getMain().savePreferences();
    } catch (Exception e) {
      System.err.println("Error writing permissions to preferences: "+e);
      e.printStackTrace(System.err);
    }
  }

  public void writePermissions(OutputStream out) 
    throws IOException
  {
    for(Iterator it=ruleViews.iterator(); it.hasNext(); ) {
      AddRemovePanel panel = (AddRemovePanel)it.next();
      PermissionComponent permComp = panel.permComp;
      if(permComp!=null) permComp.writeRule(out);
    }
  }
  
  
  /** Populate the GUI with a user-friendly view of the permissions in
    * the given stream. */
  public void readPermissions(InputStream in) 
    throws IOException
  {
    ArrayList ruleList = new ArrayList();
    
    BufferedReader rdr = new BufferedReader(new InputStreamReader(in, "UTF8"));
    String line;
    String paramRule;
    String opRule;
    String callerRule;
    int ruleType;
    int idx1;
    int idx2;
    while(true) {
      line = rdr.readLine();
      if(line==null) break;
      line = line.trim();
      
      ruleList.add(new DORule(line));
    }
    
    // nothing beyond this point should throw an exception, meaning that if we can't
    // successfully read the permissions from the stream, the previous UI state will
    // remain
    
    DORule rules[] = (DORule[])ruleList.toArray(new DORule[ruleList.size()]);

    ArrayList ruleComps = new ArrayList();
    // scan the rules, aggregating any similar rules into 
    for(int i=0; i<rules.length; i++) {
      DORule rule = rules[i];
      if(rule==null) continue;
      
      PermissionComponent permComp = new PermissionComponent();
      HashMap opsWithSameUser = new HashMap();
      opsWithSameUser.put(rule.operation, rule);
      for(int j=i+1; j<rules.length; j++) {
        if(rule.hasParams()) break;
        DORule rule2 = rules[j];
        if(rule2==null) continue;
        if(rule2.ruleType!=rule.ruleType) break;
        if(rule2.hasParams()) break;
        if(!rule.who.equals(rule2.who)) break;
        opsWithSameUser.put(rule2.operation, rule2);
        
        if(rulesMatch(readableRules, opsWithSameUser)) {
          permComp.setReadableRule(rule);
          ruleComps.add(permComp);
          permComp = null;
          i = j; // skip the grouped rules
          break;
        } else if(rulesMatch(writableRules, opsWithSameUser)) {
          permComp.setWritableRule(rule);
          ruleComps.add(permComp);
          permComp = null;
          i = j;
          break;
        }
      }
      
      if(permComp!=null) { // this rule wasn't added as part of a group
        permComp.setSingleRule(rule);
        ruleComps.add(permComp);
      }
    }
    
    
    rulePanel.removeAll();
    ruleViews.clear();
    
    for(int i=0; i<ruleComps.size(); i++) {
      ruleViews.add(new AddRemovePanel((PermissionComponent)ruleComps.get(i)));
    }
    rebuildRulePanel();
  }

  /** Return true if the given HashMaps have the same keys */
  private boolean rulesMatch(HashMap map1, HashMap map2) {
    for(Iterator it=map1.keySet().iterator(); it.hasNext(); ) {
      if(!map2.containsKey(it.next())) return false;
    }
    for(Iterator it=map2.keySet().iterator(); it.hasNext(); ) {
      if(!map1.containsKey(it.next())) return false;
    }
    return true;
  }
  
  private void rebuildRulePanel() {
    rulePanel.removeAll();
    int numRules = ruleViews.size();
    for(int i=0; i<numRules; i++) {
      rulePanel.add((JComponent)ruleViews.get(i), GridC.getc(0,i).wx(1).fillx());
    }
    if(numRules==0) {
      rulePanel.add(new AddRemovePanel(null), GridC.getc(0,numRules).wxy(1,1).northEast());
    } else if(numRules<50) {
      rulePanel.add(Box.createVerticalStrut(0), GridC.getc(0,50).wxy(1,1));
    }
    rulePanel.validate();
    validate();
    repaint();
  }
  
  
  private class AddRemovePanel
    extends JPanel
    implements ActionListener
  {
    PermissionComponent permComp;
    JButton addButton, delButton;
    
    AddRemovePanel(PermissionComponent permComp) {
      super(new GridBagLayout());
      this.permComp = permComp;
      addButton = new JButton(appUI.getImages().getIcon(DOImages.ADD));
      addButton.setBorder(emptyBorder);
      delButton = new JButton(appUI.getImages().getIcon(DOImages.REMOVE));
      delButton.setBorder(emptyBorder);
      if(permComp!=null) {
        add(permComp, GridC.getc(0,0).wx(1).fillx());
      } else {
        add(new JLabel(" "), GridC.getc(0,0).wx(1).fillx());
        add(Box.createHorizontalStrut(100), GridC.getc(0,0).wx(1).fillx());
        delButton.setEnabled(false);
        delButton.setVisible(false);
      }
      add(delButton, GridC.getc(1,0));
      add(addButton, GridC.getc(2,0));
      addButton.addActionListener(this);
      delButton.addActionListener(this);
    }
    
    public void actionPerformed(ActionEvent evt) {
      Object src = evt.getSource();
      if(src==addButton) {
        PermissionComponent newComp;
        if(permComp!=null) {
          newComp = permComp.cloneRule();
        } else {
          newComp = new PermissionComponent();
        }
        ruleViews.add(ruleViews.indexOf(this)+1, new AddRemovePanel(newComp));
        rebuildRulePanel();
      } else if(src==delButton) {
        ruleViews.remove(this);
        rebuildRulePanel();
      }
    }
    
    
  }
  
  
  /** Store the permissions from the GUI into the given output stream */
  public void storePermissions(OutputStream out) 
    throws IOException
  {
    throw new IOException("Storing permissions not implemented yet");
  }
    
  
  public static class DORule {
    private String origRule = "";
    public int ruleType = UNKNOWN_RULE;
    public String who = "*";
    public String operation = "*";
    public String params = "";
    
    public DORule(String rule) {
      this.origRule = rule;
      segmentRule(rule);      
    }
    
    private static Pattern regexPattern = Pattern.compile("([^:]*+):" + "\\s*+" +
            "([^\"]\\S*+|\"(?>[^\\\\\"]|\\\\.)*+\"|)" + "\\s*+" + 
            "([^\"]\\S*+|\"(?>[^\\\\\"]|\\\\.)*+\"|)" + "\\s*+" + 
            "(.*+)");
    
    private static Pattern unquotePattern = Pattern.compile("\\\\(.)");
    
    private static String unquote(String s) {
        if(s.startsWith("\"")) {
            String unquoted;
            if(s.endsWith("\"")) unquoted = s.substring(1,s.length()-1);
            else unquoted = s.substring(1);
            return unquotePattern.matcher(unquoted).replaceAll("$1");
        }
        else return s.trim();
    }
    
    public void segmentRule(String rule)
    {
      Matcher regexMatcher = regexPattern.matcher(rule);
      
      if(regexMatcher.find()) {
        String prefixGroup = regexMatcher.group(1);
        String operationGroup = regexMatcher.group(2);
        String whoGroup = regexMatcher.group(3);
        String paramGroup = regexMatcher.group(4);
        if(prefixGroup != null && prefixGroup.length() > 0)  {      
          if(prefixGroup.trim().equals("accept")) ruleType=ACCEPT_RULE;
          else if(prefixGroup.trim().equals("reject")) ruleType=REJECT_RULE;
        }
        boolean missing = false;
        if(operationGroup != null && operationGroup.length() > 0) operation = unquote(operationGroup);
        else missing = true;
        if(whoGroup != null && whoGroup.length() >0) who = unquote(whoGroup);
        else missing = true;
        if(paramGroup != null && paramGroup.length() >0) {
            if(missing) {
                // The way this regex is set up, the params will get everything if there are unclosed quotes or backslashes in the operation or who.
                // Treat this as a parse error.
                ruleType = UNKNOWN_RULE;
                return;
            }
            params = unquote(paramGroup);
        }
      }
    }

    public String getOrigRule() {
      return origRule;
    }
    
    public boolean hasParams() {
      return params!=null && params.length()>0 && !params.equals("*");
    }
    
    public String toString() {
      return "rule: type="+ruleType+"; op="+operation+"; who="+who+"; params='"+params+"'";
    }
    
  }
  
  
  public class OperationRenderer 
    extends JLabel 
    implements ListCellRenderer
  {
    OperationRenderer() {
      setOpaque(true);
    }
    
    public Component getListCellRendererComponent(JList list, Object value,
                                                  int index, boolean isSelected,
                                                  boolean cellHasFocus)
    {
      if(index==list.getModel().getSize()-1) {
        setText("Other Permission");
      } else if(knownOps.containsKey(value)) {
        setText(knownOps.get(value).toString());
      } else {
        setText(value.toString());
      }
      setBackground(isSelected ? Color.blue : Color.white);
      setForeground(isSelected ? Color.white : Color.black);
      return this;
    }
    
  }
  
  
  public class EntityRenderer 
    extends JLabel 
    implements ListCellRenderer
  {
    EntityRenderer() {
      setOpaque(true);
    }
    
    public Component getListCellRendererComponent(JList list, Object value,
                                                  int index, boolean isSelected,
                                                  boolean cellHasFocus)
    {
      String entity = value.toString();
      if(entity.equals("*")) entity = appUI.getStr("anyone");
      setText(entity);
      setBackground(isSelected ? Color.blue : Color.white);
      setForeground(isSelected ? Color.white : Color.black);
      return this;
    }
    
  }
  
  
  private void test() {
    
  }
  
  public class PermissionComponent 
    extends JPanel
  {
    JComboBox acceptRejectChoice;
    JComboBox whoChoice;
    JLabel paramsField;
    private JComboBox opChoice;
    private JLabel acceptRejectLabel;
    private DefaultComboBoxModel whoModel;
    private DORule rule = new DORule("");
    private boolean unrecognizedRule = false;
    
    public PermissionComponent(DORule rule) {
      this();
      setSingleRule(rule);
    }
    
    public PermissionComponent() {
      super(new GridBagLayout());
      paramsField = new JLabel("");
      acceptRejectLabel = new JLabel("");
      acceptRejectChoice = new JComboBox(new String[] { 
                                         appUI.getStr("perm_can"), 
                                         appUI.getStr("perm_cannot")
                                         });
      acceptRejectChoice.setSelectedIndex(0);
      whoModel = new DefaultComboBoxModel(new Vector(entityMap.getEntities()));
      whoModel.addElement("*");
      whoChoice = new JComboBox(whoModel);
      whoChoice.setRenderer(entityRenderer);
      
      Vector ops = new Vector(operations);
      opChoice = new JComboBox(ops);
      opChoice.setRenderer(operationRenderer);
      
      opChoice.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent evt) {
          //System.err.println("index "+opChoice.getSelectedIndex()+" of "+opChoice.getItemCount());
          if(opChoice.getSelectedIndex()==opChoice.getItemCount()-1) {
            opChoice.setEditable(true);
            opChoice.setSelectedItem("1037/0");
          } else {
            opChoice.setEditable(false);
          }
        }
      });
      
      updateGUI();
    }
    
    public PermissionComponent cloneRule() {
      PermissionComponent newComp = new PermissionComponent(rule);
      newComp.acceptRejectChoice.setSelectedIndex(acceptRejectChoice.getSelectedIndex());
      newComp.whoChoice.setSelectedItem(whoChoice.getSelectedItem());
      newComp.paramsField.setText(paramsField.getText());
      return newComp;
    }
    
    public void writeRule(OutputStream out) 
      throws IOException
    {
      if(unrecognizedRule) {
        out.write(paramsField.getText().getBytes("UTF8"));
        out.write((byte)'\n');
      } else {
        if(unrecognizedRule) {
          out.write(paramsField.getText().getBytes("UTF8"));
        } else {
          String prefix = "#";
          switch(acceptRejectChoice.getSelectedIndex()) {
            case 0: 
              prefix = "accept:";
              break;
            case 1:
              prefix = "reject:";
              break;
          }
          Object who = whoChoice.getSelectedItem();
          String whoStr;
          if(who instanceof EntityMap.Entity) {
            whoStr = ((EntityMap.Entity)who).getAttribute(EntityMap.ID_ATTRIBUTE, who.toString());
          } else {
            whoStr = who.toString();
          }
          
          String selectedWho = whoStr.replace('\t', ' ');
          String params = paramsField.getText();
          if(params.length()<=0) params = "*";
          
          Object selectedOp = opChoice.getSelectedItem();
          if(selectedOp.equals(WRITE_PERMISSION_GROUP)) {
            for(Iterator it=writableRules.keySet().iterator(); it.hasNext(); ) {
              out.write((prefix+
                         it.next()+'\t'+
                         selectedWho+'\t'+
                         params+
                         "\n").getBytes("UTF8"));
            }
          } else if(selectedOp.equals(READ_PERMISSION_GROUP)) {
            for(Iterator it=readableRules.keySet().iterator(); it.hasNext(); ) {
              out.write((prefix+
                         it.next()+'\t'+
                         selectedWho+'\t'+
                         params+
                         "\n").getBytes("UTF8"));
            }
          } else {
            out.write((prefix+
                       selectedOp.toString().replace('\t',' ')+'\t'+
                       selectedWho+'\t'+
                       params+
                       "\n").getBytes("UTF8"));
          }
        }
      }
    }
    
    public void setReadableRule(DORule rule) {
      setSingleRule(rule);
      opChoice.setSelectedItem(READ_PERMISSION_GROUP);
    }
    
    public void setWritableRule(DORule rule) {
      setSingleRule(rule);
      opChoice.setSelectedItem(WRITE_PERMISSION_GROUP);
    }
    
    public void setSingleRule(DORule rule) {
      this.rule = rule;
      updateGUI();
    }
    
    private void updateGUI() {
      removeAll();
      switch(rule.ruleType) {
        case ACCEPT_RULE:
          acceptRejectChoice.setSelectedIndex(0);
          acceptRejectLabel.setText(appUI.getStr("perm_can"));
          break;
        case REJECT_RULE:
          acceptRejectChoice.setSelectedIndex(1);
          appUI.getStr("perm_cannot");
          break;
        case UNKNOWN_RULE:
        default:
          paramsField.setText(rule.getOrigRule());
          add(paramsField, GridC.getc(0,0).wxy(1,1).fillboth());
          unrecognizedRule = true;
          return;
      }
      
      unrecognizedRule = false;
      opChoice.setSelectedItem(rule.operation);
      
      Object entity = entityMap.getEntityForID(rule.who);
      if(entity==null) entity = rule.who;
      if(whoModel.getIndexOf(entity)<0) {
        whoModel.addElement(entity);
      }
      whoChoice.setSelectedItem(entity);
      
      add(whoChoice, GridC.getc(0,0));
      //add(acceptRejectChoice, GridC.getc(1,0));
      add(acceptRejectLabel, GridC.getc(1,0));
      add(opChoice, GridC.getc(2,0));
      add(paramsField, GridC.getc(3,0).wx(1));
    }
    
    
  }
  
  
}
