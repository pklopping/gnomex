package hci.gnomex.controller;

import hci.dictionary.utility.DictionaryManager;
import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.framework.model.DetailObject;
import hci.framework.utilities.XMLReflectException;
import hci.gnomex.model.Application;
import hci.gnomex.model.ApplicationType;
import hci.gnomex.model.NumberSequencingCyclesAllowed;
import hci.gnomex.model.RequestCategory;
import hci.gnomex.model.RequestCategoryApplication;
import hci.gnomex.model.RequestCategoryType;
import hci.gnomex.model.SampleType;
import hci.gnomex.model.SampleTypeApplication;
import hci.gnomex.model.SampleTypeRequestCategory;
import hci.gnomex.model.SeqLibProtocolApplication;
import hci.gnomex.utility.DictionaryHelper;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Query;
import org.hibernate.Session;
import org.jdom.Document;
import org.jdom.Element;


public class GetExperimentPlatformList extends GNomExCommand implements Serializable {
  
  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(GetExperimentPlatformList.class);

  private List<SampleType> sampleTypes = new ArrayList<SampleType>();
  private List <Application> applications = new ArrayList<Application>();
  private HashMap<String, Map<Integer, ?>> sampleTypeMap = new HashMap<String, Map<Integer, ?>>();
  private HashMap<String, Map<String, RequestCategoryApplication>> applicationMap = new HashMap<String, Map<String, RequestCategoryApplication>>();
  private HashMap<Integer, Map<Integer, ?>> sampleTypeXMethodMap = new HashMap<Integer, Map<Integer, ?>>();
  private HashMap<Integer, Map<String, ?>> sampleTypeXApplicationMap = new HashMap<Integer, Map<String, ?>>();
  private HashMap<String, Map<Integer, ?>> applicationXSeqLibProtocolMap = new HashMap<String, Map<Integer, ?>>();
  private HashMap<String, List<NumberSequencingCyclesAllowed>> numberSeqCyclesAllowedMap = new HashMap<String, List<NumberSequencingCyclesAllowed>>();
  private Map<String, List<Element>> applicationToRequestCategoryMap = new HashMap<String, List<Element>>();
  
  
  public void validate() {
  }
  
  public void loadCommand(HttpServletRequest request, HttpSession session) {

    
    if (isValid()) {
      setResponsePage(this.SUCCESS_JSP);
    } else {
      setResponsePage(this.ERROR_JSP);
    }

  }

  public Command execute() throws RollBackCommandException {

    try {

      Session sess = this.getSecAdvisor().getReadOnlyHibernateSession(this.getUsername());

      DictionaryHelper dh = DictionaryHelper.getInstance(sess);
      hashSupportingDictionaries(sess, dh);
      
      Document doc = new Document(new Element("ExperimentPlatformList"));

      List platforms = sess.createQuery("SELECT rc from RequestCategory rc order by rc.requestCategory").list();

      for(Iterator i = platforms.iterator(); i.hasNext();) {
        RequestCategory rc = (RequestCategory)i.next();
        this.getSecAdvisor().flagPermissions(rc);
        Element node = rc.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
        doc.getRootElement().addContent(node);
        
        Element listNode = new Element("sampleTypes");
        node.addContent(listNode);
        for(Iterator i1 = sampleTypes.iterator(); i1.hasNext();) {
          SampleType st = (SampleType)i1.next();
          this.getSecAdvisor().flagPermissions(st);
          Element sampleTypeNode = st.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
          listNode.addContent(sampleTypeNode);
          sampleTypeNode.setAttribute("isSelected", isAssociated(rc, st) ? "Y" : "N");
          sampleTypeNode.setAttribute("codeApplications", getCodeApplications(st));
        }
        
        
        listNode = new Element("applications");
        node.addContent(listNode);
        RequestCategoryType rct = dh.getRequestCategoryType(rc.getType());
        Element parentNode = listNode; 
        String prevTheme = "UnInitialized";
        Integer prevThemeId = -1;
        DictionaryManager dm;
        Boolean foundSelected = false;
        applicationToRequestCategoryMap = this.getApplicationRequestCategoryMap(sess, dh);
        for(Application a : this.getApplications(rc, rct)) {
          this.getSecAdvisor().flagPermissions(a);
          String curTheme = DictionaryManager.getDisplay("hci.gnomex.model.ApplicationTheme", 
              a.getIdApplicationTheme() == null ? "" : a.getIdApplicationTheme().toString());
          Integer curThemeId = a.getIdApplicationTheme();
          if (!prevTheme.equals(curTheme) && rct.getIsIllumina() != null && rct.getIsIllumina().equals("Y")) {
            if (parentNode != listNode) {
              parentNode.setAttribute("isSelected", foundSelected ? "Y" : "N");
            }
            Element themeNode = new Element("ApplicationTheme");
            themeNode.setAttribute("applicationTheme", curTheme == null ? "" : curTheme);
            themeNode.setAttribute("idApplicationTheme", curThemeId == null ? "" : curThemeId.toString());
            parentNode = themeNode;
            listNode.addContent(themeNode);
            prevTheme = curTheme;
            prevThemeId = curThemeId;
            foundSelected = false;
          }
          if (isAssociated(rc, a)) {
            foundSelected = true;
          }
          Element applicationNode = a.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
          parentNode.addContent(applicationNode);
          applicationNode.setAttribute("applicationThemeDisplay", curTheme);
          applicationNode.setAttribute("isSelected", isAssociated(rc, a) ? "Y" : "N");
          applicationNode.setAttribute("idSeqLibProtocols", getIdSeqLibProtocols(a));
          RequestCategoryApplication x = (RequestCategoryApplication)getRequestCategoryApplication(rc, a);
          applicationNode.setAttribute("idLabelingProtocolDefault", x != null && x.getIdLabelingProtocolDefault() != null ? x.getIdLabelingProtocolDefault().toString() : "");
          applicationNode.setAttribute("idHybProtocolDefault", x != null && x.getIdHybProtocolDefault() != null ? x.getIdHybProtocolDefault().toString() : "");
          applicationNode.setAttribute("idScanProtocolDefault", x != null && x.getIdScanProtocolDefault() != null ? x.getIdScanProtocolDefault().toString() : "");
          applicationNode.setAttribute("idFeatureExtractionProtocolDefault", x != null && x.getIdFeatureExtractionProtocolDefault() != null ? x.getIdFeatureExtractionProtocolDefault().toString() : "");
          applicationNode.setAttribute("selectedInOtherCategory", "N");
          
          List<Element> rcAppList = this.applicationToRequestCategoryMap.get(a.getCodeApplication());
          if (rcAppList != null) {
            for(Element rcAppNode : rcAppList) {
              String rcAppCodeRequestCategory = rcAppNode.getAttributeValue("codeRequestCategory");
              String isSelected = rcAppNode.getAttributeValue("isSelected");
              if (!rc.getCodeRequestCategory().equals(rcAppCodeRequestCategory) && isSelected.equals("Y")) {
                applicationNode.setAttribute("selectedInOtherCategory", "Y");
              }
              applicationNode.addContent(rcAppNode);
            }
          }
        }
        if (parentNode != listNode) {
          parentNode.setAttribute("isSelected", foundSelected ? "Y" : "N");
        }

        listNode = new Element("sequencingOptions");
        node.addContent(listNode);
        List<NumberSequencingCyclesAllowed> allowedList = this.numberSeqCyclesAllowedMap.get(rc.getCodeRequestCategory());
        if (allowedList != null) {
          for(NumberSequencingCyclesAllowed c : allowedList) {
            this.getSecAdvisor().flagPermissions(c);
            Element cycleNode = c.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
            listNode.addContent(cycleNode);
          }
        }               
      }

      org.jdom.output.XMLOutputter out = new org.jdom.output.XMLOutputter();
      this.xmlResult = out.outputString(doc);

      setResponsePage(this.SUCCESS_JSP);
    }catch (NamingException e){
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
        
    }catch (SQLException e) {
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (XMLReflectException e){
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } catch (Exception e) {
      log.error("An exception has occurred in GetExperimentPlatformList ", e);
      e.printStackTrace();
      throw new RollBackCommandException(e.getMessage());
    } finally {
      try {
        this.getSecAdvisor().closeReadOnlyHibernateSession();        
      } catch(Exception e) {
        
      }
    }

    if (isValid()) {
      setResponsePage(this.SUCCESS_JSP);
    } else {
      setResponsePage(this.ERROR_JSP);
    }
    
    return this;
  }
  
  private boolean isAssociated(RequestCategory rc, SampleType st) {
    Map idMap = sampleTypeMap.get(rc.getCodeRequestCategory());
    return idMap != null && idMap.containsKey(st.getIdSampleType());
  }
  
  private boolean isAssociated(RequestCategory rc, Application a) {
    if (a.getIsActive() == null || !a.getIsActive().equals("Y")) {
      return false;
    } else {
      Map idMap = applicationMap.get(rc.getCodeRequestCategory());
      return idMap != null && idMap.containsKey(a.getCodeApplication());
    }
  }
  
  private RequestCategoryApplication getRequestCategoryApplication(RequestCategory rc, Application a) {
    Map<String, RequestCategoryApplication> idMap = applicationMap.get(rc.getCodeRequestCategory());
    if (idMap != null && idMap.containsKey(a.getCodeApplication())) {
      return idMap.get(a.getCodeApplication());
    } else {
      return null;
    }
  }
  
  private String getCodeApplications(SampleType st) {
    String buf = "";
    Map idMap = sampleTypeXApplicationMap.get(st.getIdSampleType());
    if (idMap != null) {
      for(Iterator i = idMap.keySet().iterator(); i.hasNext();) {
        String codeApplication = (String)i.next();
        if (buf.length() > 0) {
          buf += ",";
        }
        buf += codeApplication;
      }
    }
    return buf;
  }
  
  private String getIdSeqLibProtocols(Application app) {
    String buf = "";
    Map idMap = applicationXSeqLibProtocolMap.get(app.getCodeApplication());
    if (idMap != null) {
      for(Iterator i = idMap.keySet().iterator(); i.hasNext();) {
        Integer id = (Integer)i.next();
        if (buf.length() > 0) {
          buf += ",";
        }
        buf += id.toString();
      }
    }
    return buf;
  }
  
  private void hashSupportingDictionaries(Session sess, DictionaryHelper dh) throws Exception {
    sampleTypes = sess.createQuery("SELECT st from SampleType st order by st.sampleType").list();
    List sampleTypeXrefs = sess.createQuery("SELECT x from SampleTypeRequestCategory x").list();
    for(Iterator i = sampleTypeXrefs.iterator(); i.hasNext();) {
      SampleTypeRequestCategory x = (SampleTypeRequestCategory)i.next();
      Map idMap = (Map)sampleTypeMap.get(x.getCodeRequestCategory());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getIdSampleType(), null);
      sampleTypeMap.put(x.getCodeRequestCategory(), idMap);
    }
    
    applications = sess.createQuery("SELECT a from Application a order by a.application").list();
    List applicationXrefs = sess.createQuery("SELECT x from RequestCategoryApplication x").list();
    for(Iterator i = applicationXrefs.iterator(); i.hasNext();) {
      RequestCategoryApplication x = (RequestCategoryApplication)i.next();
      Map idMap = (Map)applicationMap.get(x.getCodeRequestCategory());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getCodeApplication(), x);
      applicationMap.put(x.getCodeRequestCategory(), idMap);
    }
    
    List sampleTypeXApplications = sess.createQuery("SELECT x from SampleTypeApplication x").list();
    for(Iterator i = sampleTypeXApplications.iterator(); i.hasNext();) {
      SampleTypeApplication x = (SampleTypeApplication)i.next();
      Map idMap = (Map)sampleTypeXApplicationMap.get(x.getIdSampleType());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getCodeApplication(), null);
      sampleTypeXApplicationMap.put(x.getIdSampleType(), idMap);
    }
    
    List applicationXSeqLibProtocols = sess.createQuery("SELECT x from SeqLibProtocolApplication x").list();
    for(Iterator i = applicationXSeqLibProtocols.iterator(); i.hasNext();) {
      SeqLibProtocolApplication x = (SeqLibProtocolApplication)i.next();
      Map idMap = (Map)applicationXSeqLibProtocolMap.get(x.getCodeApplication());
      if (idMap == null) {
        idMap = new HashMap();
      }
      idMap.put(x.getIdSeqLibProtocol(), null);
      applicationXSeqLibProtocolMap.put(x.getCodeApplication(), idMap);
    }
    
    
    List numberSeqCyclesAllowed = sess.createQuery("SELECT x from NumberSequencingCyclesAllowed x join x.numberSequencingCycles c order by c.numberSequencingCycles").list();
    for(Iterator i = numberSeqCyclesAllowed.iterator(); i.hasNext();) {
      NumberSequencingCyclesAllowed x = (NumberSequencingCyclesAllowed)i.next();
      List<NumberSequencingCyclesAllowed> allowedList = numberSeqCyclesAllowedMap.get(x.getCodeRequestCategory());
      if (allowedList == null) {
        allowedList = new ArrayList<NumberSequencingCyclesAllowed>();
      }
      allowedList.add(x);
      numberSeqCyclesAllowedMap.put(x.getCodeRequestCategory(), allowedList);
    }
  }
  
  private List<Application> getApplications(RequestCategory rc, RequestCategoryType rct) {
    ArrayList<Application> apps = new ArrayList<Application>();
    Map<Integer, Integer> selectedThemes = new HashMap<Integer, Integer>(); 
    for(Iterator i1 = applications.iterator(); i1.hasNext();) {
      Application a = (Application)i1.next();
      // Skip applications not of right application type for this request category.
      if (!a.isApplicableApplication(rct)) {
        continue;
      }

      if (isAssociated(rc, a)) {
        selectedThemes.put(a.getIdApplicationTheme(), a.getIdApplicationTheme());
      }
      
      apps.add(a);
    }
    
    if (rct.getIsIllumina() != null && rct.getIsIllumina().equals("Y")) {
      Collections.sort(apps, new illuminaAppComparator(selectedThemes, applicationMap.get(rc.getCodeRequestCategory())));
    } else {
      Collections.sort(apps, new appComparator(applicationMap.get(rc.getCodeRequestCategory())));
    }
    
    return apps;
  }
  
  private Map<String, List<Element>> getApplicationRequestCategoryMap(Session sess, DictionaryHelper dh) throws XMLReflectException {
    Map<String, List<Element>> arcMap = new HashMap<String, List<Element>>();
    String appQueryString = "from Application";
    Query appQuery = sess.createQuery(appQueryString);
    List apps = appQuery.list();
    
    Map<String, String> selectedCategories = new HashMap<String, String>();
    String rcaQueryString = "from RequestCategoryApplication";
    Query rcaQuery = sess.createQuery(rcaQueryString);
    List rcaQueryList =rcaQuery.list();
    for(RequestCategoryApplication rca : (List<RequestCategoryApplication>)rcaQueryList) {
      String key = rca.getCodeApplication() + "\t" + rca.getCodeRequestCategory();
      selectedCategories.put(key, key);
    }
    
    String rcQueryString = "from RequestCategory";
    Query rcQuery = sess.createQuery(rcQueryString);
    List rcList = rcQuery.list();
    for (RequestCategory rc : (List<RequestCategory>)rcList) {
      Element node = rc.toXMLDocument(null, DetailObject.DATE_OUTPUT_SQL).getRootElement();
      for(Application app : (List<Application>)apps) {
        if (app.isApplicableApplication(rc.getCategoryType())) {
          Element rcaNode = (Element)node.clone();
          rcaNode.setName("RequestCategoryApplication");
          String key = app.getCodeApplication() + "\t" + rc.getCodeRequestCategory();
          rcaNode.setAttribute("isSelected", selectedCategories.containsKey(key) ? "Y" : "N");
          List<Element> appRCList = arcMap.get(app.getCodeApplication());
          if (appRCList == null) {
            appRCList = new ArrayList<Element>();
          }
          appRCList.add(rcaNode);
          arcMap.put(app.getCodeApplication(), appRCList);
        }
      }
    }
    
    return arcMap;
  }
  
  private class appComparator implements Comparator<Application>, Serializable {
    private Map<String, RequestCategoryApplication> appMap;

    public appComparator(Map<String, RequestCategoryApplication> appMap) {
      this.appMap = appMap;
      if (this.appMap == null) {
        this.appMap = new HashMap<String, RequestCategoryApplication>();
      }
    }
    
    public int compare(Application a1, Application a2) {
      
      Integer sort1 = a1.getSortOrder() == null ? -1 : a1.getSortOrder();
      Integer sort2 = a2.getSortOrder() == null ? -1 : a2.getSortOrder();

      if (a1.getNonNullString(a1.getIsActive()).equals("Y") && !a2.getNonNullString(a2.getIsActive()).equals("Y")) {
        return -1;
      } else if (!a1.getNonNullString(a1.getIsActive()).equals("Y") && a2.getNonNullString(a2.getIsActive()).equals("Y")) {
        return 1;
      } else if (appMap.containsKey(a1.getCodeApplication()) && !appMap.containsKey(a2.getCodeApplication())) {
        return -1;
      } else if (!appMap.containsKey(a1.getCodeApplication()) && appMap.containsKey(a2.getCodeApplication())) {
        return 1;
      } else if (sort1.equals(sort2)) {
        return a1.getApplication().compareTo(a2.getApplication());
      } else {
        return sort1.compareTo(sort2);
      }
    }
 }
  
  private class illuminaAppComparator extends appComparator implements Serializable {
    private Map<Integer, Integer> selectedThemes;
    
    public illuminaAppComparator(Map<Integer, Integer>selectedThemes, Map<String, RequestCategoryApplication> appMap) {
      super(appMap);
      this.selectedThemes = selectedThemes;
    }
    
    public int compare(Application a1, Application a2) {
      if (selectedThemes.containsKey(a1.getIdApplicationTheme()) && !selectedThemes.containsKey(a2.getIdApplicationTheme())) {
        return -1;
      } else if (!selectedThemes.containsKey(a1.getIdApplicationTheme()) && selectedThemes.containsKey(a2.getIdApplicationTheme())) {
        return 1;
      } else if (a1.getIdApplicationTheme() == null || a2.getIdApplicationTheme() == null || a1.getIdApplicationTheme().equals(a2.getIdApplicationTheme())) {
        return super.compare(a1, a2);
      } else {
        return a1.getIdApplicationTheme().compareTo(a2.getIdApplicationTheme());
      }
    }
  }
}