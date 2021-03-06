package hci.gnomex.lucene;

import hci.dictionary.model.DictionaryEntry;
import hci.framework.model.DetailObject;
import hci.gnomex.model.DataTrackFolder;
import hci.gnomex.model.Lab;
import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.model.PropertyEntry;
import hci.gnomex.model.PropertyEntryValue;
import hci.gnomex.model.PropertyOption;
//import hci.gnomex.model.RequestCategory;
import hci.gnomex.model.RequestCategoryType;
import hci.gnomex.model.Visibility;
import hci.gnomex.utility.BatchDataSource;
//import hci.gnomex.utility.DictionaryHelper;
import hci.gnomex.utility.PropertyDictionaryHelper;

import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.hibernate.Session;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;



/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class BuildSearchIndex extends DetailObject {


  private BatchDataSource dataSource;
  private Session sess;

  private String orionPath = "";
  private String schemaPath = "";

  private PropertyDictionaryHelper propertyHelper;
  private Map dictionaryMap;
  private IndexWriter globalIndexWriter;

  private String serverName;

  private Map projectRequestMap;
  private Map projectAnnotationMap;
  private Map analysisGroupMap;
  private Map sampleAnnotationMap;
  private Map requestCollaboratorMap;
  private Map analysisCollaboratorMap;
  private Map codeExperimentFactorMap;
  private Map codeExperimentDesignMap;
  private Map protocolMap;
  private Map analysisFileCommentsMap;
  private Map analysisAnnotationMap;
  private Map datatrackMap;
  private Map datatrackCollaboratorMap;
  private Map datatrackAnnotationMap;
  private Map<Integer, DataTrackFolderPath> dataTrackFolderMap;
  private Map topicMap;
  private Map<Integer, List<Integer>> labCoreFacilityMap;



  private static final String          KEY_DELIM = "&-&-&";

  public BuildSearchIndex(String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-server")) {
        serverName = args[++i];
        System.out.println("servername = " + serverName);
      } else if (args[i].equals ("-orionPath")) {
        orionPath = args[++i];
      } else if (args[i].equals ("-schemaPath")) {
        schemaPath = args[++i];
      }
    }
    if(orionPath.length() > 0 && schemaPath.length() > 0) {
      dataSource = new BatchDataSource(orionPath, schemaPath);      
    } else {
      dataSource = new BatchDataSource();
    }

  }
  public static void main(String[] args)
  {
    BuildSearchIndex app = new BuildSearchIndex(args);
    try
    {
      System.out.println(new Date() + " connecting...");
      app.connect();

      System.out.println(new Date() + " initializing...");
      app.init();

      System.out.println(new Date() + " building lucene experiment index...");
      app.buildExperimentIndex();

      System.out.println(new Date() + " building lucene protocol index...");
      app.buildProtocolIndex();

      System.out.println(new Date() + " building lucene analysis index...");
      app.buildAnalysisIndex();

      System.out.println(new Date() + " building lucene datatrack index...");
      app.buildDataTrackIndex();

      System.out.println(new Date() + " building lucene topic index...");
      app.buildTopicIndex();

      System.out.println(new Date() + " writing lucene global index...");
      app.writeGlobalIndex();

      System.out.println(new Date() + " disconnecting...");
      System.out.println();
      app.disconnect();
    }
    catch( Exception e )
    {
      System.out.println( e.toString() );
      e.printStackTrace();
    }
    
    System.exit(0);
  }


  private void connect()
  throws Exception
  {
    dataSource.connect();
    sess = dataSource.getSession();
  }

  private void disconnect() 
  throws Exception {
    dataSource.close();
  }

  private void init() throws Exception {

    propertyHelper = PropertyDictionaryHelper.getInstance(sess);

    // Cache dictionary value-to-display
    dictionaryMap = new HashMap();
    cacheDictionary("hci.gnomex.model.SampleType", "SampleType");
    cacheDictionary("hci.gnomex.model.Organism", "Organism");
    cacheDictionary("hci.gnomex.model.RequestCategory", "RequestCategory");
    cacheDictionary("hci.gnomex.model.Application", "Application");
    cacheDictionary("hci.gnomex.model.AnalysisType", "AnalysisType");
    cacheDictionary("hci.gnomex.model.AnalysisProtocol", "AnalysisProtocol");

    globalIndexWriter = new IndexWriter(propertyHelper.getQualifiedProperty(PropertyDictionary.LUCENE_GLOBAL_INDEX_DIRECTORY, serverName),   new StandardAnalyzer(), true);
  }

  private void cacheDictionary(String className, String objectName) {
    List entries = sess.createQuery("SELECT de from " + objectName + " as de ").list();
    for(Iterator i = entries.iterator(); i.hasNext();) {
      DictionaryEntry de = (DictionaryEntry)i.next();
      dictionaryMap.put(className + "-" + de.getValue(), de.getDisplay());
    }
  }

  private String getDictionaryDisplay(String className, String value) {
    String display =  (String)dictionaryMap.get(className + "-" + value);
    if (display == null) {
      return "";
    } else {
      return display;
    }
  }


  private void buildExperimentIndex() throws Exception{

    IndexWriter experimentIndexWriter = new IndexWriter(propertyHelper.getQualifiedProperty(PropertyDictionary.LUCENE_EXPERIMENT_INDEX_DIRECTORY, serverName), new StandardAnalyzer(), true);

    // Get basic project/request data
    getProjectRequestData(sess);

    // Get project annotations (experiment design and factors)
    getProjectAnnotations(sess);

    // Get sample annotations (sample characteristics)
    getSampleAnnotations(sess);

    // Get collaborators 
    getRequestCollaborators(sess);

    // Get core facilities for labs
    getLabCoreFacilities(sess);

    //
    // Write Experiment Lucene Index.
    // (A document for each request)
    //
    for(Iterator i = projectRequestMap.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();

      Object[] keyTokens = key.split(KEY_DELIM);
      Integer idProject = new Integer((String)keyTokens[0]);
      Integer idRequest = keyTokens.length == 2 && keyTokens[1] != null ? new Integer((String)keyTokens[1]) : null;
      List rows = (List)projectRequestMap.get(key);


      addExperimentDocument(experimentIndexWriter, idProject, idRequest, rows);
    }
    experimentIndexWriter.optimize();
    experimentIndexWriter.close();
  }

  private void buildProtocolIndex() throws Exception{

    IndexWriter protocolIndexWriter   = new IndexWriter(propertyHelper.getQualifiedProperty(PropertyDictionary.LUCENE_PROTOCOL_INDEX_DIRECTORY, serverName),   new StandardAnalyzer(), true);

    // Get basic protocol data
    getProtocolData(sess);


    //
    // Write Protocol Lucene Index.
    // (A document for each protocol)
    //
    for( Iterator i = protocolMap.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      Object[] keyTokens = key.split(KEY_DELIM);
      String  protocolType = (String)keyTokens[0];
      Integer idProtocol  = new Integer((String)keyTokens[1]);
      Object[] row = (Object[])protocolMap.get(key);

      buildProtocolDocument(protocolIndexWriter, protocolType, idProtocol, row);
    }
    protocolIndexWriter.optimize();
    protocolIndexWriter.close();
  }

  private void buildAnalysisIndex() throws Exception{

    IndexWriter analysisIndexWriter   = new IndexWriter(propertyHelper.getQualifiedProperty(PropertyDictionary.LUCENE_ANALYSIS_INDEX_DIRECTORY, serverName),   new StandardAnalyzer(), true);

    // Get analysis data
    getAnalysisData(sess);

    // Get collaborators of analysis
    getAnalysisCollaborators(sess);

    // Analysis properties
    getAnalysisAnnotations(sess);

    //
    // Write Analysis Lucene Index.
    // (A document for each protocol)
    //
    for( Iterator i = analysisGroupMap.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      Object[] keyTokens = key.split(KEY_DELIM);
      Integer idAnalysisGroup = new Integer((String)keyTokens[0]);
      Integer idAnalysis = keyTokens.length == 2 && keyTokens[1] != null ? new Integer((String)keyTokens[1]) : null;
      Object[] row = (Object[])analysisGroupMap.get(key);

      StringBuffer analysisFileComments = (StringBuffer)analysisFileCommentsMap.get(idAnalysis);

      buildAnalysisDocument(analysisIndexWriter, idAnalysisGroup, idAnalysis, row, analysisFileComments);
    }
    analysisIndexWriter.optimize();
    analysisIndexWriter.close();
  }

  private void buildDataTrackIndex() throws Exception{

    IndexWriter datatrackIndexWriter   = new IndexWriter(propertyHelper.getQualifiedProperty(PropertyDictionary.LUCENE_DATATRACK_INDEX_DIRECTORY, serverName),   new StandardAnalyzer(), true);

    // Get the data track folder paths
    getDataTrackFolderPaths(sess);

    // Get data track data
    getDataTrackData(sess);

    // Get collaborators of data track
    getDataTrackCollaborators(sess);

    // DataTrack properties
    getDataTrackAnnotations(sess);

    //
    // Write Data Track Lucene Index.
    // (A document for each protocol)
    //
    for( Iterator i = datatrackMap.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      Object[] keyTokens = key.split(KEY_DELIM);
      Integer idDataTrackFolder = new Integer((String)keyTokens[0]);
      Integer idDataTrack = keyTokens.length == 2 && keyTokens[1] != null ? new Integer((String)keyTokens[1]) : null;
      Object[] row = (Object[])datatrackMap.get(key);

      buildDataTrackDocument(datatrackIndexWriter, idDataTrackFolder, idDataTrack, row);
    }
    datatrackIndexWriter.optimize();
    datatrackIndexWriter.close();
  }


  private void buildTopicIndex() throws Exception{

    IndexWriter topicIndexWriter = new IndexWriter(propertyHelper.getQualifiedProperty(PropertyDictionary.LUCENE_TOPIC_INDEX_DIRECTORY, serverName),   new StandardAnalyzer(), true);

    // Get data track data
    getTopicData(sess);

    //
    // Write Topic Lucene Index.
    // (A document for each protocol)
    //
    for( Iterator i = topicMap.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      Integer idTopic = new Integer(key);
      Object[] row = (Object[])topicMap.get(key);

      buildTopicDocument(topicIndexWriter, idTopic, row);
    }
    topicIndexWriter.optimize();
    topicIndexWriter.close();
  }

  private void writeGlobalIndex() throws Exception {
    globalIndexWriter.optimize();
    globalIndexWriter.close();
  }

  private void getProjectRequestData(Session sess) throws Exception{
    //
    // Microarray experiments
    //
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT proj.id, ");
    buf.append("       req.id, ");
    buf.append("       req.number, ");
    buf.append("       proj.name, ");
    buf.append("       proj.description, ");
    buf.append("       hyb.notes, ");
    buf.append("       s1.name, ");
    buf.append("       s1.description, ");
    buf.append("       s1.idOrganism, ");
    buf.append("       '', ");
    buf.append("       s2.name, ");
    buf.append("       s2.description,  ");
    buf.append("       s2.idOrganism,  ");
    buf.append("       '', ");
    buf.append("       req.idSlideProduct,  ");
    buf.append("       slideProd.idOrganism,  ");
    buf.append("       req.codeRequestCategory,  ");
    buf.append("       proj.idLab,  ");
    buf.append("       labProj.lastName,  ");
    buf.append("       labProj.firstName,  ");
    buf.append("       req.idLab,  ");
    buf.append("       labReq.lastName,  ");
    buf.append("       labReq.firstName,  ");
    buf.append("       req.codeApplication, ");
    buf.append("       reqOwner.firstName, ");
    buf.append("       reqOwner.lastName, ");
    buf.append("       '', ");
    buf.append("       req.codeVisibility, ");
    buf.append("       req.createDate, ");
    buf.append("       s1.idSampleType, ");
    buf.append("       slideProd.name, ");
    buf.append("       req.idAppUser, ");
    buf.append("       '', ");
    buf.append("       s1.otherSamplePrepMethod, ");
    buf.append("       '', ");
    buf.append("       s2.otherSamplePrepMethod, ");
    buf.append("       req.idInstitution, ");
    buf.append("       req.name, ");
    buf.append("       s1.otherOrganism, ");
    buf.append("       s2.otherOrganism, ");
    buf.append("       req.idCoreFacility, ");
    buf.append("       req.idSubmitter, ");
    buf.append("       reqSubmitter.firstName, ");
    buf.append("       reqSubmitter.lastName ");

    buf.append("FROM        Project as proj ");
    buf.append("LEFT JOIN   proj.requests as req ");
    buf.append("LEFT JOIN   proj.lab as labProj ");
    buf.append("LEFT JOIN   req.lab as labReq ");
    buf.append("LEFT JOIN   req.requestCategory as reqCat ");
    buf.append("LEFT JOIN   req.slideProduct as slideProd ");
    buf.append("LEFT JOIN   req.appUser as reqOwner ");
    buf.append("LEFT JOIN   req.submitter as reqSubmitter ");
    buf.append("LEFT JOIN   req.hybridizations as hyb ");
    buf.append("LEFT JOIN   hyb.labeledSampleChannel1 as ls1 ");
    buf.append("LEFT JOIN   ls1.sample as s1 ");
    buf.append("LEFT JOIN   hyb.labeledSampleChannel1 as ls2 ");
    buf.append("LEFT JOIN   ls2.sample as s2 ");
    buf.append("WHERE       reqCat.type = '" + RequestCategoryType.TYPE_MICROARRAY + "' ");
    buf.append("      AND case when reqCat.isClinicalResearch is null then 'N' else reqCat.isClinicalResearch end = 'N'");
    buf.append("ORDER BY proj.idProject, req.idRequest ");

    List results = sess.createQuery(buf.toString()).list();
    projectRequestMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idProject = (Integer)row[0];
      Integer idRequest = (Integer)row[1];
      String key = idProject + KEY_DELIM + (idRequest != null ? idRequest.toString() : "");

      List rows = (List)projectRequestMap.get(key);
      if (rows == null) {
        rows = new ArrayList();
        projectRequestMap.put(key, rows);
      }
      rows.add(row);
    }    

    //
    // Sample quality, DNA Seq core experiments
    //
    buf = new StringBuffer();
    buf.append("SELECT proj.id, ");
    buf.append("       req.id, ");
    buf.append("       req.number, ");
    buf.append("       proj.name, ");
    buf.append("       proj.description, ");
    buf.append("       '', ");
    buf.append("       s1.name, ");
    buf.append("       s1.description, ");
    buf.append("       s1.idOrganism, ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       req.codeRequestCategory,  ");
    buf.append("       proj.idLab,  ");
    buf.append("       labProj.lastName,  ");
    buf.append("       labProj.firstName,  ");
    buf.append("       req.idLab,  ");
    buf.append("       labReq.lastName,  ");
    buf.append("       labReq.firstName,  ");
    buf.append("       req.codeApplication, ");
    buf.append("       reqOwner.firstName, ");
    buf.append("       reqOwner.lastName, ");
    buf.append("       '', ");
    buf.append("       req.codeVisibility, ");
    buf.append("       req.createDate, ");
    buf.append("       s1.idSampleType, ");
    buf.append("       '', ");
    buf.append("       req.idAppUser, ");
    buf.append("       '', ");
    buf.append("       s1.otherSamplePrepMethod, ");
    buf.append("       '', ");
    buf.append("       '',  ");
    buf.append("       req.idInstitution, ");
    buf.append("       req.name, ");
    buf.append("       '', ");
    buf.append("       '',  ");
    buf.append("       req.idCoreFacility, ");
    buf.append("       req.idSubmitter, ");
    buf.append("       reqSubmitter.firstName, ");
    buf.append("       reqSubmitter.lastName ");

    buf.append("FROM        Project as proj ");
    buf.append("LEFT JOIN   proj.requests as req ");
    buf.append("LEFT JOIN   proj.lab as labProj ");
    buf.append("LEFT JOIN   req.lab as labReq ");
    buf.append("LEFT JOIN   req.requestCategory as reqCat ");
    buf.append("LEFT JOIN   req.appUser as reqOwner ");
    buf.append("LEFT JOIN   req.submitter as reqSubmitter ");
    buf.append("LEFT JOIN   req.samples as s1 ");
    buf.append("WHERE       reqCat.type in ('" + RequestCategoryType.TYPE_QC + "', '" + RequestCategoryType.TYPE_CAP_SEQ + "', '" + RequestCategoryType.TYPE_MITOCHONDRIAL_DLOOP + "', '" + RequestCategoryType.TYPE_FRAGMENT_ANALYSIS + "', '" + RequestCategoryType.TYPE_CHERRY_PICKING + "') ");
    buf.append("      AND case when reqCat.isClinicalResearch is null then 'N' else reqCat.isClinicalResearch end = 'N'");
    buf.append("ORDER BY proj.idProject, req.idRequest ");

    results = sess.createQuery(buf.toString()).list();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idProject = (Integer)row[0];
      Integer idRequest = (Integer)row[1];
      String key = idProject + KEY_DELIM + (idRequest != null ? idRequest.toString() : "");

      List rows = (List)projectRequestMap.get(key);
      if (rows == null) {
        rows = new ArrayList();
        projectRequestMap.put(key, rows);
      }
      rows.add(row);
    }    


    //
    // Illumina experiments
    //
    buf = new StringBuffer();
    buf.append("SELECT proj.id, ");
    buf.append("       req.id, ");
    buf.append("       req.number, ");
    buf.append("       proj.name, ");
    buf.append("       proj.description, ");
    buf.append("       '', ");
    buf.append("       s1.name, ");
    buf.append("       s1.description, ");
    buf.append("       s1.idOrganism, ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '',  ");
    buf.append("       '',  ");
    buf.append("       '', ");
    buf.append("       '',  ");
    buf.append("       '',  ");
    buf.append("       req.codeRequestCategory,  ");
    buf.append("       proj.idLab,  ");
    buf.append("       labProj.lastName,  ");
    buf.append("       labProj.firstName,  ");
    buf.append("       req.idLab,  ");
    buf.append("       labReq.lastName,  ");
    buf.append("       labReq.firstName,  ");
    buf.append("       req.codeApplication, ");
    buf.append("       reqOwner.firstName, ");
    buf.append("       reqOwner.lastName, ");
    buf.append("       '', ");
    buf.append("       req.codeVisibility, ");
    buf.append("       req.createDate, ");
    buf.append("       s1.idSampleType, ");
    buf.append("       '', ");
    buf.append("       req.idAppUser, ");
    buf.append("       '', ");
    buf.append("       s1.otherSamplePrepMethod, ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       req.idInstitution, ");
    buf.append("       req.name, ");
    buf.append("       s1.otherOrganism, ");
    buf.append("       '', ");
    buf.append("       req.idCoreFacility, ");
    buf.append("       req.idSubmitter, ");
    buf.append("       reqSubmitter.firstName, ");
    buf.append("       reqSubmitter.lastName ");


    buf.append("FROM        Project as proj ");
    buf.append("LEFT JOIN   proj.requests as req ");
    buf.append("LEFT JOIN   proj.lab as labProj ");
    buf.append("LEFT JOIN   req.lab as labReq ");
    buf.append("LEFT JOIN   req.requestCategory as reqCat ");
    buf.append("LEFT JOIN   reqCat.categoryType as reqType ");
    buf.append("LEFT JOIN   req.appUser as reqOwner ");
    buf.append("LEFT JOIN   req.submitter as reqSubmitter ");
    buf.append("LEFT JOIN   req.sequenceLanes as lane ");
    buf.append("LEFT JOIN   lane.sample as s1 ");
    buf.append("WHERE       reqType.isIllumina = 'Y' ");
    buf.append("      AND case when reqCat.isClinicalResearch is null then 'N' else reqCat.isClinicalResearch end = 'N'");
    buf.append("ORDER BY proj.idProject, req.idRequest ");

    results = sess.createQuery(buf.toString()).list();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idProject = (Integer)row[0];
      Integer idRequest = (Integer)row[1];
      String key = idProject + KEY_DELIM + (idRequest != null ? idRequest.toString() : "");

      List rows = (List)projectRequestMap.get(key);
      if (rows == null) {
        rows = new ArrayList();
        projectRequestMap.put(key, rows);
      }
      rows.add(row);
    } 




    //
    // "Empty" projects
    //
    buf = new StringBuffer();
    buf.append("SELECT proj.id, ");
    buf.append("       req.id, ");
    buf.append("       req.number, ");
    buf.append("       proj.name, ");
    buf.append("       proj.description, ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       -99, ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '',  ");
    buf.append("       '',  ");
    buf.append("       '', ");
    buf.append("       '',  ");
    buf.append("       '',  ");
    buf.append("       '',  ");
    buf.append("       proj.idLab,  ");
    buf.append("       labProj.lastName,  ");
    buf.append("       labProj.firstName,  ");
    buf.append("       -99,  ");
    buf.append("       '',  ");
    buf.append("       '',  ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       -99, ");
    buf.append("       '', ");
    buf.append("       -99, ");
    buf.append("       -99, ");
    buf.append("      '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       -99, ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       '', ");
    buf.append("       req.idCoreFacility, ");
    buf.append("       req.idSubmitter, ");
    buf.append("       '', ");
    buf.append("       '' ");

    buf.append("FROM        Project as proj ");
    buf.append("LEFT JOIN   proj.requests as req ");
    buf.append("LEFT JOIN   proj.lab as labProj ");
    buf.append("WHERE      req.idRequest is NULL ");
    buf.append("ORDER BY proj.idProject");

    results = sess.createQuery(buf.toString()).list();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idProject = (Integer)row[0];
      Integer idRequest = (Integer)row[1];
      String key = idProject + KEY_DELIM + (idRequest != null ? idRequest.toString() : "");

      List rows = (List)projectRequestMap.get(key);
      if (rows == null) {
        rows = new ArrayList();
        projectRequestMap.put(key, rows);
      }
      rows.add(row);
    }  
  }

  private void getAnalysisData(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT ag.id, ");
    buf.append("       a.id, ");
    buf.append("       ag.name, ");
    buf.append("       ag.description, ");
    buf.append("       '', ");
    buf.append("       ag.idLab, ");
    buf.append("       agLab.lastName, ");
    buf.append("       agLab.firstName, ");
    buf.append("       owner.firstName, ");
    buf.append("       owner.lastName, ");
    buf.append("       lab.lastName,  ");
    buf.append("       lab.firstName,  ");
    buf.append("       a.number, ");
    buf.append("       a.name, ");
    buf.append("       a.description, ");
    buf.append("       a.idAnalysisType, ");
    buf.append("       a.idAnalysisProtocol, ");
    buf.append("       a.idOrganism, ");
    buf.append("       a.idLab,  ");
    buf.append("       a.createDate, ");
    buf.append("       a.codeVisibility, ");
    buf.append("       a.idAppUser,  ");
    buf.append("       a.idInstitution ");

    buf.append("FROM        AnalysisGroup as ag ");
    buf.append("LEFT JOIN   ag.lab as agLab ");
    buf.append("LEFT JOIN   ag.analysisItems as a ");
    buf.append("LEFT JOIN   a.lab as lab ");
    buf.append("LEFT JOIN   a.appUser as owner ");

    buf.append("ORDER BY ag.name, a.number, a.name ");

    List results = sess.createQuery(buf.toString()).list();
    analysisGroupMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idAnalysisGroup = (Integer)row[0];
      Integer idAnalysis = (Integer)row[1];
      String key = idAnalysisGroup + KEY_DELIM + (idAnalysis != null ? idAnalysis.toString() : "");

      analysisGroupMap.put(key, row);
    }

    // Get analysis file comments
    buf = new StringBuffer();
    buf.append("SELECT a.id, ");
    buf.append("       af.fileName, ");
    buf.append("       af.comments ");
    buf.append("FROM        Analysis as a ");
    buf.append("LEFT JOIN   a.files as af ");

    results = sess.createQuery(buf.toString()).list();
    analysisFileCommentsMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idAnalysis = (Integer)row[0];
      String fileName  = (String)row[1];
      String comments = (String)row[2];

      StringBuffer analysisFileComments = (StringBuffer)analysisFileCommentsMap.get(idAnalysis);
      if (analysisFileComments == null) {
        analysisFileComments = new StringBuffer();
      }
      analysisFileComments.append(comments);
      analysisFileComments.append(" ");


      analysisFileCommentsMap.put(idAnalysis, analysisFileComments);
    }    

  }

  private void getDataTrackFolderPaths(Session sess) throws Exception {
    this.dataTrackFolderMap = new HashMap<Integer, DataTrackFolderPath>();

    List folderList = (List)sess.createQuery("from DataTrackFolder").list();
    for(Iterator i = folderList.iterator(); i.hasNext();) {
      DataTrackFolder f = (DataTrackFolder)i.next();
      DataTrackFolderPath p = new DataTrackFolderPath();
      p.idDataTrackFolder = f.getIdDataTrackFolder();
      p.idParentDataTrackFolder = f.getIdParentDataTrackFolder();
      p.name = f.getName();
      if (p.idParentDataTrackFolder == null) {
        p.dataTrackFolderPath = f.getName();
        p.pathComplete = true;
      }
      dataTrackFolderMap.put(p.idDataTrackFolder, p);
    }

    Boolean changed = true;
    Integer cnt = 0;
    while(changed && cnt < 1000) {
      changed = false;
      cnt++;
      for(Iterator j = dataTrackFolderMap.keySet().iterator(); j.hasNext();) {
        DataTrackFolderPath path = dataTrackFolderMap.get(j.next());
        if (path.pathComplete) {
          continue;
        }
        DataTrackFolderPath parent = dataTrackFolderMap.get(path.idParentDataTrackFolder);
        if (parent.pathComplete) {
          path.dataTrackFolderPath = parent.dataTrackFolderPath + "/" + path.name;
          path.pathComplete = true;
          changed = true;
        }
      }
    }
  }

  private void getDataTrackData(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT dtf.idDataTrackFolder, ");
    buf.append("       dt.idDataTrack, ");
    buf.append("       dtf.name, ");
    buf.append("       dtf.description, ");
    buf.append("       '', ");
    buf.append("       dtf.idLab, ");
    buf.append("       dtfLab.lastName, ");
    buf.append("       dtfLab.firstName, ");
    buf.append("       owner.firstName, ");
    buf.append("       owner.lastName, ");
    buf.append("       lab.lastName,  ");
    buf.append("       lab.firstName,  ");
    buf.append("       dt.name, ");
    buf.append("       dt.description, ");
    buf.append("       dt.fileName, ");
    buf.append("       dt.summary, ");
    buf.append("       dt.idLab,  ");
    buf.append("       dt.createDate, ");
    buf.append("       dt.codeVisibility, ");
    buf.append("       dt.idAppUser,  ");
    buf.append("       dt.idInstitution, ");
    buf.append("       org.idOrganism ");

    buf.append("FROM        DataTrackFolder as dtf ");
    buf.append("LEFT JOIN   dtf.lab as dtfLab ");
    buf.append("JOIN   dtf.dataTracks as dt ");
    buf.append("LEFT JOIN   dt.lab as lab ");
    buf.append("LEFT JOIN   dt.appUser as owner ");
    buf.append("LEFT JOIN   dtf.genomeBuild as gen ");
    buf.append("LEFT JOIN   gen.organism as org ");

    buf.append("ORDER BY dtf.name, dt.name ");

    List results = sess.createQuery(buf.toString()).list();
    datatrackMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idDataTrackFolder = (Integer)row[0];
      Integer idDataTrack = (Integer)row[1];
      String key = idDataTrackFolder + KEY_DELIM + (idDataTrack != null ? idDataTrack.toString() : "");

      datatrackMap.put(key, row);
    }
  }

  private void getTopicData(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT t.idTopic, ");
    buf.append("       t.name, ");
    buf.append("       t.description, ");
    buf.append("       t.codeVisibility, ");
    buf.append("       t.idAppUser,  ");
    buf.append("       t.idInstitution, ");
    buf.append("       lab.lastName,  ");
    buf.append("       lab.firstName,  ");
    buf.append("       owner.firstName, ");
    buf.append("       owner.lastName, ");
    buf.append("       t.createDate, ");
    buf.append("       t.idLab  ");


    buf.append("FROM        Topic as t ");
    buf.append("LEFT JOIN   t.lab as lab ");
    buf.append("LEFT JOIN   t.appUser as owner ");


    buf.append("ORDER BY t.name");

    List results = sess.createQuery(buf.toString()).list();
    topicMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idTopic = (Integer)row[0];
      String key = idTopic.toString();

      topicMap.put(key, row);
    }
  }

  private void getProjectAnnotations(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT ede.idProject, ");
    buf.append("       ed.experimentDesign, ");
    buf.append("       ede.otherLabel,  ");
    buf.append("       ede.codeExperimentDesign ");
    buf.append("FROM   ExperimentDesignEntry as ede, ExperimentDesign ed ");
    buf.append("WHERE  ede.value = 'Y' ");
    buf.append("AND    ede.codeExperimentDesign = ed.codeExperimentDesign ");
    buf.append("ORDER BY ede.idProject ");

    List results = sess.createQuery(buf.toString()).list();
    projectAnnotationMap = new HashMap();
    codeExperimentDesignMap = new HashMap();
    codeExperimentFactorMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idProject = (Integer)row[0];
      String code = (String)row[3];

      List rows = (List)projectAnnotationMap.get(idProject);
      if (rows == null) {
        rows = new ArrayList();
        projectAnnotationMap.put(idProject, rows);
      }
      rows.add(row);

      List codes = (List)codeExperimentDesignMap.get(idProject);
      if (codes == null) {
        codes = new ArrayList();
        codeExperimentDesignMap.put(idProject, codes);
      }
      codes.add(code);
    }   

    buf = new StringBuffer();
    buf.append("SELECT efe.idProject, ");
    buf.append("       ef.experimentFactor, ");
    buf.append("       efe.otherLabel,  ");
    buf.append("       efe.codeExperimentFactor ");
    buf.append("FROM   ExperimentFactorEntry as efe, ExperimentFactor ef ");
    buf.append("WHERE  efe.value = 'Y' ");
    buf.append("AND    efe.codeExperimentFactor = ef.codeExperimentFactor ");
    buf.append("ORDER BY efe.idProject ");

    results = sess.createQuery(buf.toString()).list();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idProject = (Integer)row[0];
      String code = (String)row[3];

      List rows = (List)projectAnnotationMap.get(idProject);
      if (rows == null) {
        rows = new ArrayList();
        projectAnnotationMap.put(idProject, rows);
      }
      rows.add(row);

      List codes = (List)codeExperimentFactorMap.get(idProject);
      if (codes == null) {
        codes = new ArrayList();
        codeExperimentFactorMap.put(idProject, codes);
      }
      codes.add(code);

    }    
  }


  private void getSampleAnnotations(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT s.idRequest, ");
    buf.append("       p.name, ");
    buf.append("       pe,  ");
    buf.append("       pe.otherLabel  ");
    buf.append("FROM   Sample s, PropertyEntry as pe, Property p ");
    buf.append("WHERE  pe.value is not NULL ");
    buf.append("AND    s.idSample = pe.idSample ");
    buf.append("AND    pe.idProperty = p.idProperty ");
    buf.append("ORDER BY s.idRequest ");

    List results = sess.createQuery(buf.toString()).list();
    sampleAnnotationMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idRequest = (Integer)row[0];

      List rows = (List)sampleAnnotationMap.get(idRequest);
      if (rows == null) {
        rows = new ArrayList();
        sampleAnnotationMap.put(idRequest, rows);
      }
      rows.add(row);
    }   
  }  

  private void getRequestCollaborators(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT r.idRequest, ");
    buf.append("       collab.idAppUser ");
    buf.append("FROM   Request r ");
    buf.append("JOIN   r.collaborators as collab ");

    List results = sess.createQuery(buf.toString()).list();
    requestCollaboratorMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idRequest = (Integer)row[0];

      List rows = (List)requestCollaboratorMap.get(idRequest);
      if (rows == null) {
        rows = new ArrayList();
        requestCollaboratorMap.put(idRequest, rows);
      }
      rows.add(row);
    }   
  }

  private void getLabCoreFacilities(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT l.idLab, ");
    buf.append("       c.idCoreFacility ");
    buf.append("FROM   Lab l ");
    buf.append("JOIN   l.coreFacilities as c ");

    List results = sess.createQuery(buf.toString()).list();
    this.labCoreFacilityMap = new HashMap<Integer, List<Integer>>();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idLab = (Integer)row[0];

      List<Integer> rows = labCoreFacilityMap.get(idLab);
      if (rows == null) {
        rows = new ArrayList<Integer>();
        labCoreFacilityMap.put(idLab, rows);
      }
      rows.add((Integer)row[1]);
    }   
  }

  private void getAnalysisCollaborators(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT a.idAnalysis, ");
    buf.append("       collab.idAppUser ");
    buf.append("FROM   Analysis a ");
    buf.append("JOIN   a.collaborators as collab ");

    List results = sess.createQuery(buf.toString()).list();
    analysisCollaboratorMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idAnalysis = (Integer)row[0];

      List rows = (List)analysisCollaboratorMap.get(idAnalysis);
      if (rows == null) {
        rows = new ArrayList();
        analysisCollaboratorMap.put(idAnalysis, rows);
      }
      rows.add(row);
    }   
  }


  private void getAnalysisAnnotations(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT a.idAnalysis, ");
    buf.append("       p.name, ");
    buf.append("       pe,  ");
    buf.append("       pe.otherLabel  ");
    buf.append("FROM   Analysis a, PropertyEntry as pe, Property p ");
    buf.append("WHERE  pe.value is not NULL ");
    buf.append("AND    a.idAnalysis = pe.idAnalysis ");
    buf.append("AND    pe.idProperty = p.idProperty ");
    buf.append("ORDER BY a.idAnalysis ");

    List results = sess.createQuery(buf.toString()).list();
    analysisAnnotationMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idAnalysis = (Integer)row[0];

      List rows = (List)analysisAnnotationMap.get(idAnalysis);
      if (rows == null) {
        rows = new ArrayList();
        analysisAnnotationMap.put(idAnalysis, rows);
      }
      rows.add(row);
    }   
  }

  private void getDataTrackCollaborators(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT dt.idDataTrack, ");
    buf.append("       collab.idAppUser ");
    buf.append("FROM   DataTrack dt ");
    buf.append("JOIN   dt.collaborators as collab ");

    List results = sess.createQuery(buf.toString()).list();
    datatrackCollaboratorMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idDataTrack = (Integer)row[0];

      List rows = (List)datatrackCollaboratorMap.get(idDataTrack);
      if (rows == null) {
        rows = new ArrayList();
        datatrackCollaboratorMap.put(idDataTrack, rows);
      }
      rows.add(row);
    }   
  }


  private void getDataTrackAnnotations(Session sess) throws Exception{
    StringBuffer buf = new StringBuffer();
    buf.append("SELECT dt.idDataTrack, ");
    buf.append("       p.name, ");
    buf.append("       pe,  ");
    buf.append("       pe.otherLabel  ");
    buf.append("FROM   DataTrack dt, PropertyEntry as pe, Property p ");
    buf.append("WHERE  pe.value is not NULL ");
    buf.append("AND    dt.idDataTrack = pe.idDataTrack ");
    buf.append("AND    pe.idProperty = p.idProperty ");
    buf.append("ORDER BY dt.idDataTrack ");

    List results = sess.createQuery(buf.toString()).list();
    datatrackAnnotationMap = new HashMap();
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();

      Integer idDataTrack = (Integer)row[0];

      List rows = (List)datatrackAnnotationMap.get(idDataTrack);
      if (rows == null) {
        rows = new ArrayList();
        datatrackAnnotationMap.put(idDataTrack, rows);
      }
      rows.add(row);
    }   
  }


  private void getProtocolData(Session sess) throws Exception {
    protocolMap = new HashMap();

    StringBuffer buf = new StringBuffer();
    buf.append("SELECT prot.idLabelingProtocol, ");
    buf.append("       prot.labelingProtocol, ");
    buf.append("       prot.description, ");
    buf.append("       'hci.gnomex.model.LabelingProtocol' ");
    buf.append("FROM   LabelingProtocol as prot");

    List results = sess.createQuery(buf.toString()).list();    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();      
      String key = "Labeling Protocol" + KEY_DELIM + row[0];      
      protocolMap.put(key, row);      
    }

    buf = new StringBuffer();
    buf.append("SELECT prot.idHybProtocol, ");
    buf.append("       prot.hybProtocol, ");
    buf.append("       prot.description, ");
    buf.append("       'hci.gnomex.model.HybProtocol' ");
    buf.append("FROM   HybProtocol as prot");

    results = sess.createQuery(buf.toString()).list();    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();      
      String key = "Hyb Protocol" + KEY_DELIM + row[0];      
      protocolMap.put(key, row);      
    }

    buf = new StringBuffer();
    buf.append("SELECT prot.idScanProtocol, ");
    buf.append("       prot.scanProtocol, ");
    buf.append("       prot.description, ");
    buf.append("       'hci.gnomex.model.ScanProtocol' ");
    buf.append("FROM   ScanProtocol as prot");

    results = sess.createQuery(buf.toString()).list();    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();      
      String key = "Scan Protocol"  + KEY_DELIM + row[0];      
      protocolMap.put(key, row);      
    }

    buf = new StringBuffer();
    buf.append("SELECT prot.idFeatureExtractionProtocol, ");
    buf.append("       prot.featureExtractionProtocol, ");
    buf.append("       prot.description, ");
    buf.append("       'hci.gnomex.model.FeatureExtractionProtocol' ");
    buf.append("FROM   FeatureExtractionProtocol as prot");

    results = sess.createQuery(buf.toString()).list();    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();      
      String key = "Feature Extraction Protocol"  + KEY_DELIM + row[0];      
      protocolMap.put(key, row);      
    }

    buf = new StringBuffer();
    buf.append("SELECT prot.idAnalysisProtocol, ");
    buf.append("       prot.analysisProtocol, ");
    buf.append("       prot.description, ");
    buf.append("       'hci.gnomex.model.AnalysisProtocol' ");
    buf.append("FROM   AnalysisProtocol as prot");

    results = sess.createQuery(buf.toString()).list();    
    for(Iterator i = results.iterator(); i.hasNext();) {
      Object[] row = (Object[])i.next();      
      String key = "Analysis Protocol"  + KEY_DELIM + row[0];      
      protocolMap.put(key, row);      
    }



  }


  private void addExperimentDocument(IndexWriter experimentIndexWriter, Integer idProject, Integer idRequest, List rows) throws IOException {

    Document doc = new Document();
    //
    // Obtain basic project and request text fields
    //
    String  requestNumber = null;
    String  projectName = null;
    String  projectDescription = null;


    StringBuffer hybNotes = new StringBuffer();
    StringBuffer sampleNames = new StringBuffer();
    StringBuffer sampleDescriptions = new StringBuffer();
    StringBuffer samplePrepMethods = new StringBuffer();
    StringBuffer sampleOrganisms = new StringBuffer();
    HashMap      idOrganismSampleMap = new HashMap();
    HashMap      idSampleTypeMap = new HashMap();
    StringBuffer sampleTypes = new StringBuffer();
    HashMap      idSampleSourceMap = new HashMap();
    StringBuffer sampleSources = new StringBuffer();
    Integer      idSlideProduct = null;
    String       slideProduct = null;
    Integer      idOrganismSlideProduct = null;
    String       slideProductOrganism = null;
    String       codeRequestCategory = null;
    String       requestCategory = null;
    Integer      idLabProject = null;
    String       labLastNameProject = null;
    String       labFirstNameProject = null;
    Integer      idLabRequest = null;
    String       labLastNameRequest = null;
    String       labFirstNameRequest = null;
    String       codeApplication = null;
    String       application = null;
    String       requestOwnerFirstName = null;
    String       requestOwnerLastName = null;
    String       requestCodeVisibility = null;
    String       requestPublicNote = null;
    java.util.Date requestCreateDate = null;
    StringBuffer requestDisplayName = null;
    String       labProject = null;
    String       labRequest = null;
    Integer      idAppUser = null;
    String       samplePrepMethod = null;
    String       otherOrganism = null;
    String       organism = null;
    Integer      idInstitution = null;
    String       experimentName = null;
    Integer      idCoreFacility = null;
    String       idProjectCoreFacility = null;
    Integer      idSubmitter = null;
    String       submitterFirstName = null;
    String       submitterLastName = null;

    for(Iterator i1 = rows.iterator(); i1.hasNext();) {
      Object[] row = (Object[])i1.next();

      idProject           = (Integer)row[0];
      idRequest           = (Integer)row[1];
      requestNumber       = (String) row[2];
      projectName         = (String) row[3];
      projectDescription  = (String) row[4];

      String hybNote = (String) row[5];
      hybNotes.append(hybNote          != null ? hybNote + " " : "");

      //
      // sample 1
      //
      String  sampleName      = (String) row[6];
      String  sampleDesc      = (String) row[7];
      Integer idOrganism      = (Integer)row[8];
      Integer idSampleType    = (Integer)row[29];
      samplePrepMethod        = (String)row[33];
      otherOrganism           = (String)row[38];



      if (idOrganism != null) {
        idOrganismSampleMap.put(idOrganism, null);            
      }

      if (idOrganism != null) {
        organism = getDictionaryDisplay("hci.gnomex.model.Organism", idOrganism.toString());        
        if (organism.equals("Other")) {
          organism = otherOrganism;
        }
      }
      if (idSampleType != null) {
        idSampleTypeMap.put(idSampleType, null);
      }      

      sampleNames.append       (sampleName    != null ? sampleName + " " : "");
      sampleDescriptions.append(sampleDesc    != null ? sampleDesc + " " : "");
      samplePrepMethods.append(samplePrepMethod    != null ? samplePrepMethod + " " : "");
      sampleOrganisms.append(organism    != null ? organism + " " : "");
      sampleTypes.append(     idSampleType != null    ? getDictionaryDisplay("hci.gnomex.model.SampleType", idSampleType.toString()) + " " : "");

      //
      // sample 2
      //
      sampleName           = (String) row[10];
      sampleDesc           = (String) row[11];
      idOrganism           = row[12] instanceof Integer ? (Integer)row[12] : null;
      samplePrepMethod     = (String)row[35];
      otherOrganism        = (String)row[39];

      if (idOrganism != null) {
        idOrganismSampleMap.put(idOrganism, null);            
      }

      if (idOrganism != null) {
        organism = getDictionaryDisplay("hci.gnomex.model.Organism", idOrganism.toString());        
        if (organism.equals("Other")) {
          organism = otherOrganism;
        }
      }

      sampleNames.append       (sampleName    != null ? sampleName + " " : "");
      sampleDescriptions.append(sampleDesc    != null ? sampleDesc + " " : "");
      sampleOrganisms.append(organism    != null ? organism + " " : "");
      samplePrepMethods.append(samplePrepMethod    != null ? samplePrepMethod + " " : "");

      // more request data
      idSlideProduct           = row[14] instanceof Integer ? (Integer)row[14] : null;
      idOrganismSlideProduct   = row[14] instanceof Integer ? (Integer)row[15] : null;
      codeRequestCategory      = (String) row[16];
      idLabProject             = (Integer)row[17];
      labLastNameProject       = (String) row[18];
      labFirstNameProject      = (String) row[19];
      idLabRequest             = (Integer)row[20];
      labLastNameRequest       = (String) row[21];
      labFirstNameRequest      = (String) row[22];
      codeApplication          = (String) row[23];
      requestOwnerFirstName    = (String) row[24];
      requestOwnerLastName     = (String) row[25];      
      requestCodeVisibility    = (String) row[27];
      requestCreateDate        = row[28] instanceof java.util.Date ? (java.util.Date) row[28] : null;
      slideProduct             = (String) row[30];
      idAppUser                = (Integer)row[31];
      idInstitution            = (Integer)row[36];
      experimentName           = (String) row[37];
      idCoreFacility           = (Integer)row[40];
      idSubmitter              = (Integer)row[41];
      submitterFirstName       = (String)row[42];
      submitterLastName        = (String)row[43];

      // Don't index rows with no labs.
      if (idLabProject == null &&  (idLabRequest == null || idLabRequest.equals(-99))) {
        return;
      }

      slideProductOrganism     = idOrganismSlideProduct != null ? getDictionaryDisplay("hci.gnomex.model.Organism", idOrganismSlideProduct.toString()) : null;
      requestCategory          = getDictionaryDisplay("hci.gnomex.model.RequestCategory", codeRequestCategory);
      application              = getDictionaryDisplay("hci.gnomex.model.Application", codeApplication);
      if (requestCodeVisibility != null && requestCodeVisibility.equals(Visibility.VISIBLE_TO_PUBLIC)) {
        requestPublicNote = "(Public) ";
      }

      labProject = Lab.formatLabName(labLastNameProject, labFirstNameProject);
      labRequest = Lab.formatLabName(labLastNameRequest, labFirstNameRequest);

      requestDisplayName = new StringBuffer();
      requestDisplayName.append(requestNumber);
      if (codeApplication != null && !codeApplication.equals("")) {
        requestDisplayName.append(" - ");
        requestDisplayName.append(getDictionaryDisplay("hci.gnomex.model.Application", codeApplication));                
      }
      if (slideProduct != null && !slideProduct.equals("")) {
        requestDisplayName.append(" - ");
        requestDisplayName.append(slideProduct);                
      }
      requestDisplayName.append(" - ");
      requestDisplayName.append(requestOwnerFirstName);
      requestDisplayName.append(" ");
      requestDisplayName.append(requestOwnerLastName);
      requestDisplayName.append(" ");
      if (requestCreateDate != null) {
        requestDisplayName.append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(requestCreateDate));                
      }

    }

    // Concatenate string of distinct idOrganism for samples
    StringBuffer idOrganismSamples = new StringBuffer();
    for(Iterator i2 = idOrganismSampleMap.keySet().iterator(); i2.hasNext();) {
      Integer idOrganismSample = (Integer)i2.next();
      idOrganismSamples.append(idOrganismSample.toString());
      if (i2.hasNext()) {
        idOrganismSamples.append(" ");
      }
    }
    //  Concatenate string of distinct idSampleTypes for samples
    StringBuffer idSampleTypes = new StringBuffer();
    for(Iterator i2 = idSampleTypeMap.keySet().iterator(); i2.hasNext();) {
      Integer idSampleType = (Integer)i2.next();
      idSampleTypes.append(idSampleType.toString());
      if (i2.hasNext()) {
        idSampleTypes.append(" ");
      }
    }

    //  Concatenate string of distinct idSampleSource for samples
    StringBuffer idSampleSources = new StringBuffer();
    for(Iterator i2 = idSampleSourceMap.keySet().iterator(); i2.hasNext();) {
      Integer idSampleSource = (Integer)i2.next();
      idSampleSources.append(idSampleSource.toString());
      if (i2.hasNext()) {
        idSampleSources.append(" ");
      }
    }


    //
    // Obtain experiment design entries and experiment factor entries
    // on project
    //
    StringBuffer projectAnnotations = new StringBuffer();
    List projectAnnotationRows = (List)projectAnnotationMap.get(idProject);
    if (projectAnnotationRows != null) {
      for(Iterator i1 = projectAnnotationRows.iterator(); i1.hasNext();) {
        Object[] row = (Object[])i1.next();
        projectAnnotations.append((String)row[1] != null && !((String)row[1]).trim().equals("") ? (String)row[1] + " " : "");
        projectAnnotations.append((String)row[2] != null && !((String)row[2]).trim().equals("") ? (String)row[2] + " " : "");
      }          
    }
    StringBuffer codeExperimentDesigns = new StringBuffer();
    List codes = (List)codeExperimentDesignMap.get(idProject);
    if (codes != null) {
      for(Iterator i1 = codes.iterator(); i1.hasNext();) {
        String code = (String)i1.next();
        codeExperimentDesigns.append(code + " ");
      }          
    }
    StringBuffer codeExperimentFactors = new StringBuffer();
    codes = (List)codeExperimentFactorMap.get(idProject);
    if (codes != null) {
      for(Iterator i1 = codes.iterator(); i1.hasNext();) {
        String code = (String)i1.next();
        codeExperimentFactors.append(code + " ");
      }          
    }


    //
    // Obtain sample annotations on samples of request
    //
    StringBuffer sampleAnnotations = new StringBuffer();
    HashMap<String, StringBuffer> sampleAnnotationsByProperty = new HashMap<String, StringBuffer>();
    if (idRequest != null) {
      List sampleAnnotationRows = (List)sampleAnnotationMap.get(idRequest);
      if (sampleAnnotationRows != null) {
        for(Iterator i1 = sampleAnnotationRows.iterator(); i1.hasNext();) {
          Object[] row = (Object[])i1.next();
          String sampleCharactersticName = (String)row[1];
          String luceneSampleCharacteristicName = sampleCharactersticName.replaceAll("[^A-Za-z0-9]", "");

          StringBuffer propertyString = sampleAnnotationsByProperty.get(luceneSampleCharacteristicName);
          if (propertyString == null) {
            propertyString = new StringBuffer();
          }
          PropertyEntry entry = (PropertyEntry)row[2];
          String otherLabel = (String)row[3];
          sampleAnnotations.append(sampleCharactersticName != null && !sampleCharactersticName.trim().equals("") ? sampleCharactersticName + " " : "");
          if (entry != null) {
            if (entry.getOptions() != null && entry.getOptions().size() > 0) {
              for (Iterator i2 = entry.getOptions().iterator(); i2.hasNext();) {
                PropertyOption option = (PropertyOption)i2.next();
                sampleAnnotations.append(option.getOption() != null && !option.getOption().trim().equals("") ? option.getOption() + " " : "");
                propertyString.append(option.getValue() != null && !option.getValue().trim().equals("") ? option.getValue() + " " : "");
              }

            } else if (entry.getValues() != null && entry.getValues().size() > 0) {
              for (Iterator i2 = entry.getValues().iterator(); i2.hasNext();) {
                PropertyEntryValue entryValue = (PropertyEntryValue)i2.next();
                sampleAnnotations.append(entryValue.getValue() != null && !entryValue.getValue().trim().equals("") ? entryValue.getValue() + " " : "");
                propertyString.append(entryValue.getValue() != null && !entryValue.getValue().trim().equals("") ? entryValue.getValue() + " " : "");
              }

            } else {
              sampleAnnotations.append(entry.getValue() != null && !entry.getValue().trim().equals("") ? entry.getValue() + " " : "");
              propertyString.append(entry.getValue() != null && !entry.getValue().trim().equals("") ? entry.getValue() + " " : "");              
            }

          }
          sampleAnnotations.append(otherLabel != null && !otherLabel.trim().equals("") ? otherLabel + " " : "");
          propertyString.append(otherLabel != null && !otherLabel.trim().equals("") ? otherLabel + " " : "");
          sampleAnnotationsByProperty.put(luceneSampleCharacteristicName, propertyString);
        }          
      }

    }

    // Obtain collaborators of request
    StringBuffer collaborators = new StringBuffer();
    if (idRequest != null) {
      List requestCollaboratorRows = (List)requestCollaboratorMap.get(idRequest);
      if (requestCollaboratorRows != null) {
        for(Iterator i1 = requestCollaboratorRows.iterator(); i1.hasNext();) {
          Object[] row = (Object[])i1.next();
          collaborators.append(row[1] != null  ? " COLLAB-" + ((Integer)row[1]).toString() + "-COLLAB " : "");
        }          
      }
    } 

    // Obtain core facility ids for the project lab
    StringBuffer idProjectCoreFacilityBuf = new StringBuffer();
    if (idLabProject != null) {
      List<Integer> facilities = this.labCoreFacilityMap.get(idLabProject);
      if (facilities != null) {
        for(Integer f:facilities) {
          idProjectCoreFacilityBuf.append(f.toString()).append(" ");
        }
      }
    }
    idProjectCoreFacility = idProjectCoreFacilityBuf.toString();

    /* RC_8.2
    // Get the current request (if applicable) to obtain the list of topics it belongs to
    StringBuffer requestTopics = new StringBuffer();
    if(idRequest != null) {
      Map topicsUsedMap = new HashMap();    // Keep track of topics already found for this request

      Request request = (Request)sess.get(Request.class, idRequest);
      Hibernate.initialize(request.getTopics());
      if(request.getTopics() != null) {
        // If topics exist for this request then iterate through the list
        Iterator<?> it = request.getTopics().iterator();
        while(it.hasNext()) {
          Topic t = (Topic) it.next();
          Integer topicInt = (Integer)topicsUsedMap.get(t.getIdTopic());
          if(topicInt == null) {
            // If this topic not yet used, add it's name and description field (if present)
            topicsUsedMap.put(t.getIdTopic(), new Integer(1));
            requestTopics.append(t.getName() + " ");
            if(t.getDescription() != null) {
              String str = Constants.HTML_BRACKETS.matcher(t.getDescription()).replaceAll("");
              if(str.length() > 0) {
                requestTopics.append(str + " ");
              }
            }
            while(t.getParentTopic() != null) {
              // Repeat for any parents that are not already on the list
              t = t.getParentTopic();
              topicInt = (Integer)topicsUsedMap.get(t.getIdTopic());
              if(topicInt == null) {
                topicsUsedMap.put(t.getIdTopic(), new Integer(1));
                requestTopics.append(t.getName() + " ");
                if(t.getDescription() != null) {
                  String str = Constants.HTML_BRACKETS.matcher(t.getDescription()).replaceAll("");
                  if(str.length() > 0) {
                    requestTopics.append(str + " ");
                  }
                }              
              }         
            }
          }
        }
      }  
    } 
     */

    // Combine all text into one search field
    StringBuffer text = new StringBuffer();
    text.append(experimentName);
    text.append(" ");
    text.append(requestOwnerFirstName);
    text.append(" ");
    text.append(requestOwnerLastName);
    text.append(" ");
    text.append(projectName);
    text.append(" ");
    text.append(projectDescription);
    text.append(" ");
    text.append(hybNotes.toString());
    text.append(" ");
    text.append(sampleNames.toString());
    text.append(" ");
    text.append(sampleDescriptions.toString());
    text.append(" ");
    text.append(projectAnnotations.toString());
    text.append(" ");
    text.append(sampleAnnotations.toString());
    text.append(" ");        
    text.append(sampleOrganisms.toString());
    text.append(" ");        
    text.append(samplePrepMethods.toString());
    text.append(" ");        
    text.append(sampleSources.toString());
    text.append(" ");        
    text.append(slideProduct);
    text.append(" ");        
    text.append(slideProductOrganism);
    text.append(" ");        
    text.append(labProject);
    text.append(" ");        
    text.append(labRequest);
    text.append(" ");        
    text.append(requestNumber);
    text.append(" ");
    text.append(submitterFirstName);
    text.append(" ");
    text.append(submitterLastName);
    text.append(" ");

    /* RC_8.2
    text.append(requestTopics.toString());
    text.append(" "); 
     */       

    // Build organism list for global index
    String globalIdOrganism = "";
    if (idOrganismSamples.length() > 0) {
      globalIdOrganism = idOrganismSamples.toString();
    }
    if (idOrganismSlideProduct != null) {
      if (globalIdOrganism.length() > 0) {
        globalIdOrganism += " ";
      }
      globalIdOrganism += idOrganismSlideProduct.toString();
    }

    Map nonIndexedFieldMap = new HashMap();
    nonIndexedFieldMap.put(ExperimentIndexHelper.ID_PROJECT, idProject.toString());
    nonIndexedFieldMap.put(ExperimentIndexHelper.REQUEST_NUMBER, requestNumber);
    nonIndexedFieldMap.put(ExperimentIndexHelper.DISPLAY_NAME, requestDisplayName.toString());
    nonIndexedFieldMap.put(ExperimentIndexHelper.OWNER_FIRST_NAME, requestOwnerFirstName);
    nonIndexedFieldMap.put(ExperimentIndexHelper.OWNER_LAST_NAME, requestOwnerLastName);

    nonIndexedFieldMap.put(ExperimentIndexHelper.CREATE_DATE, requestCreateDate != null ? this.formatDate(requestCreateDate, this.DATE_OUTPUT_SQL) : null);
    nonIndexedFieldMap.put(ExperimentIndexHelper.APPLICATION, application);
    nonIndexedFieldMap.put(ExperimentIndexHelper.PROJECT_PUBLIC_NOTE, "");
    nonIndexedFieldMap.put(ExperimentIndexHelper.PUBLIC_NOTE, requestPublicNote);

    Map indexedFieldMap = new HashMap();

    indexedFieldMap.put(ExperimentIndexHelper.ID_REQUEST, idRequest != null ? idRequest.toString() : "unknown");      
    indexedFieldMap.put(ExperimentIndexHelper.ID_CORE_FACILITY, idCoreFacility != null ? idCoreFacility.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.EXPERIMENT_NAME, experimentName);
    indexedFieldMap.put(ExperimentIndexHelper.PROJECT_NAME, projectName);
    indexedFieldMap.put(ExperimentIndexHelper.PROJECT_DESCRIPTION, projectDescription);
    indexedFieldMap.put(ExperimentIndexHelper.HYB_NOTES, hybNotes.toString());
    indexedFieldMap.put(ExperimentIndexHelper.SAMPLE_NAMES, sampleNames.toString());
    indexedFieldMap.put(ExperimentIndexHelper.SAMPLE_DESCRIPTIONS, sampleDescriptions.toString());
    indexedFieldMap.put(ExperimentIndexHelper.SAMPLE_ORGANISMS, sampleOrganisms.toString());
    indexedFieldMap.put(ExperimentIndexHelper.SAMPLE_PREP_METHODS, samplePrepMethods.toString());
    indexedFieldMap.put(ExperimentIndexHelper.ID_ORGANISM_SAMPLE, idOrganismSamples.toString());
    indexedFieldMap.put(ExperimentIndexHelper.SAMPLE_SOURCES, sampleSources.toString());
    indexedFieldMap.put(ExperimentIndexHelper.ID_SAMPLE_TYPES, idSampleTypes.toString());
    indexedFieldMap.put(ExperimentIndexHelper.REQUEST_CATEGORY, requestCategory);
    indexedFieldMap.put(ExperimentIndexHelper.CODE_REQUEST_CATEGORY, codeRequestCategory);
    indexedFieldMap.put(ExperimentIndexHelper.CODE_APPLICATION, codeApplication);
    indexedFieldMap.put(ExperimentIndexHelper.ID_SLIDE_PRODUCT, idSlideProduct != null ? idSlideProduct.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.SLIDE_PRODUCT, slideProduct);
    indexedFieldMap.put(ExperimentIndexHelper.SLIDE_PRODUCT_ORGANISM, slideProductOrganism);
    indexedFieldMap.put(ExperimentIndexHelper.ID_ORGANISM_SLIDE_PRODUCT, idOrganismSlideProduct != null ? idOrganismSlideProduct.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.REQUEST_CATEGORY, requestCategory);
    indexedFieldMap.put(ExperimentIndexHelper.ID_LAB_PROJECT, idLabProject != null ? idLabProject.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.PROJECT_LAB_NAME, labProject);
    indexedFieldMap.put(ExperimentIndexHelper.ID_LAB, idLabRequest != null ? idLabRequest.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.COLLABORATORS, collaborators != null ? collaborators.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.LAB_NAME, labRequest);
    indexedFieldMap.put(ExperimentIndexHelper.CODE_VISIBILITY, requestCodeVisibility);
    indexedFieldMap.put(ExperimentIndexHelper.ID_INSTITUTION, idInstitution !=  null ? idInstitution.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.PROJECT_ANNOTATIONS, projectAnnotations.toString());
    indexedFieldMap.put(ExperimentIndexHelper.CODE_EXPERIMENT_DESIGNS, codeExperimentDesigns.toString());
    indexedFieldMap.put(ExperimentIndexHelper.CODE_EXPERIMENT_FACTORS, codeExperimentFactors.toString());
    indexedFieldMap.put(ExperimentIndexHelper.ID_PROJECT_CORE_FACILITY, idProjectCoreFacility);
    indexedFieldMap.put(ExperimentIndexHelper.ID_SUBMITTER, idSubmitter != null ? idSubmitter.toString() : null);
    indexedFieldMap.put(ExperimentIndexHelper.SUBMITTER_FIRST_NAME, submitterFirstName != null ? submitterFirstName : null);
    indexedFieldMap.put(ExperimentIndexHelper.SUBMITTER_LAST_NAME, submitterLastName != null ? submitterLastName : null);

    // Output the annotation properties.
    for(Iterator i = sampleAnnotationsByProperty.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      StringBuffer values = (StringBuffer)sampleAnnotationsByProperty.get(key);
      indexedFieldMap.put(key, values.toString());
    }
    indexedFieldMap.put(ExperimentIndexHelper.TEXT, text.toString());

    ExperimentIndexHelper.build(doc, nonIndexedFieldMap, indexedFieldMap);

    experimentIndexWriter.addDocument(doc);

    Map globalNonIndexedFieldMap = new HashMap();
    Map globalIndexedFieldMap = new HashMap();
    if (requestCategory == null || requestCategory.length() == 0) {
      globalNonIndexedFieldMap.put(GlobalIndexHelper.NUMBER, "");
      globalNonIndexedFieldMap.put(GlobalIndexHelper.NAME, projectName);

      globalIndexedFieldMap.put(GlobalIndexHelper.OBJECT_TYPE, GlobalIndexHelper.PROJECT_FOLDER);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID, "unknown");
      globalIndexedFieldMap.put(GlobalIndexHelper.CODE_VISIBILITY, requestCodeVisibility != null ? requestCodeVisibility : "");
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB, idLabProject != null ? idLabProject.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_ORGANISM, "");
      globalIndexedFieldMap.put(GlobalIndexHelper.LAB_NAME, labProject != null ? labProject : "");
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_PROJECT, idProject.toString());
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB_FOLDER, idLabProject != null ? idLabProject.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_PROJECT_CORE_FACILITY, idProjectCoreFacility);
      globalIndexedFieldMap.put(GlobalIndexHelper.TEXT, text.toString());
    } else {
      globalNonIndexedFieldMap.put(GlobalIndexHelper.NUMBER, requestNumber != null ? requestNumber : "");
      globalNonIndexedFieldMap.put(GlobalIndexHelper.NAME, experimentName != null ? experimentName : "");
      globalNonIndexedFieldMap.put(GlobalIndexHelper.CODE_REQUEST_CATEGORY, codeRequestCategory);

      globalIndexedFieldMap.put(GlobalIndexHelper.OBJECT_TYPE, requestCategory);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID, idRequest != null ? idRequest.toString() : "unknown");
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB, idLabRequest != null ? idLabRequest.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_ORGANISM, globalIdOrganism != null ? globalIdOrganism : "");
      globalIndexedFieldMap.put(GlobalIndexHelper.LAB_NAME, labRequest != null ? labRequest : "");
      globalIndexedFieldMap.put(GlobalIndexHelper.CODE_VISIBILITY, requestCodeVisibility != null ? requestCodeVisibility : "");
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_INSTITUTION, idInstitution !=  null ? idInstitution.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_CORE_FACILITY, idCoreFacility != null ? idCoreFacility.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.COLLABORATORS, collaborators != null ? collaborators.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB_FOLDER, idLabProject != null ? idLabProject.toString() : null);
      globalIndexedFieldMap.put(GlobalIndexHelper.ID_PROJECT_CORE_FACILITY, idProjectCoreFacility);
      globalIndexedFieldMap.put(GlobalIndexHelper.TEXT, text.toString());

    }

    Document globalDoc = new Document();
    GlobalIndexHelper.build(globalDoc, globalNonIndexedFieldMap, globalIndexedFieldMap);
    globalIndexWriter.addDocument(globalDoc);
  }

  private void buildProtocolDocument(IndexWriter protocolIndexWriter, String protocolType, Integer idProtocol, Object[] row) throws IOException {

    Document doc = new Document();

    String name        = (String)row[1];
    String description = (String)row[2];
    String className   = (String)row[3];

    Map nonIndexedFieldMap = new HashMap();
    nonIndexedFieldMap.put(ProtocolIndexHelper.ID_PROTOCOL, idProtocol.toString());
    nonIndexedFieldMap.put(ProtocolIndexHelper.PROTOCOL_TYPE, protocolType);
    nonIndexedFieldMap.put(ProtocolIndexHelper.CLASS_NAME, className);


    Map indexedFieldMap = new HashMap();
    indexedFieldMap.put(ProtocolIndexHelper.NAME, name);
    indexedFieldMap.put(ProtocolIndexHelper.DESCRIPTION, description);
    indexedFieldMap.put(ProtocolIndexHelper.TEXT, name + " " + description);

    ProtocolIndexHelper.build(doc, nonIndexedFieldMap, indexedFieldMap);

    protocolIndexWriter.addDocument(doc);


    Map globalNonIndexedFieldMap = new HashMap();
    Map globalIndexedFieldMap = new HashMap();
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NUMBER, "");
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NAME, name);
    globalNonIndexedFieldMap.put(GlobalIndexHelper.PROTOCOL_CLASS_NAME, className);

    globalIndexedFieldMap.put(GlobalIndexHelper.OBJECT_TYPE, GlobalIndexHelper.PROTOCOL);
    globalIndexedFieldMap.put(GlobalIndexHelper.ID, idProtocol.toString());
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB, "g1");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_ORGANISM, "g1");
    globalIndexedFieldMap.put(GlobalIndexHelper.LAB_NAME,"");
    globalIndexedFieldMap.put(GlobalIndexHelper.CODE_VISIBILITY, "");
    globalIndexedFieldMap.put(GlobalIndexHelper.TEXT, name + " " + description);

    Document globalDoc = new Document();
    GlobalIndexHelper.build(globalDoc, globalNonIndexedFieldMap, globalIndexedFieldMap);
    globalIndexWriter.addDocument(globalDoc);
  }

  private void buildAnalysisDocument(IndexWriter analysisIndexWriter, Integer idAnalysisGroup, Integer idAnalysis, Object[] row, StringBuffer analysisFileComments) throws IOException {

    Document doc = new Document();


    String agName                 = (String)row[2];
    String agDesc                 = (String)row[3];
    Integer agIdLab               = (Integer)row[5];
    String agLabLastName          = (String)row[6];
    String agLabFirstName         = (String)row[7];
    String ownerFirstName         = (String)row[8];
    String ownerLastName          = (String)row[9];
    String labLastName            = (String)row[10];
    String labFirstName           = (String)row[11];
    String number                 = (String)row[12];
    String name                   = (String)row[13];
    String desc                   = (String)row[14];
    Integer idAnalysisType        = (Integer)row[15];
    Integer idAnalysisProtocol    = (Integer)row[16];
    Integer idOrganism            = (Integer)row[17];
    Integer idLab                 = (Integer)row[18];
    java.sql.Date createDate      = (java.sql.Date)row[19];
    String codeVisibility         = (String)row[20];
    String publicNote             = ""; 
    Integer idAppUser             = (Integer)row[21];
    Integer idInstitution         = (Integer)row[22];

    //
    // Obtain annotations on analysis
    //
    StringBuffer analysisAnnotations = new StringBuffer();
    HashMap<String, StringBuffer> annotationsByProperty = new HashMap<String, StringBuffer>();
    if (idAnalysis != null) {
      List analysisAnnotationRows = (List)analysisAnnotationMap.get(idAnalysis);
      if (analysisAnnotationRows != null) {
        for(Iterator i1 = analysisAnnotationRows.iterator(); i1.hasNext();) {
          Object[] analysisRow = (Object[])i1.next();
          String analysisCharactersticName = (String)analysisRow[1];
          String luceneSampleCharacteristicName = analysisCharactersticName.replaceAll("[^A-Za-z0-9]", "");

          StringBuffer propertyString = annotationsByProperty.get(luceneSampleCharacteristicName);
          if (propertyString == null) {
            propertyString = new StringBuffer();
          }
          PropertyEntry entry = (PropertyEntry)analysisRow[2];
          String otherLabel = (String)analysisRow[3];
          analysisAnnotations.append(analysisCharactersticName != null && !analysisCharactersticName.trim().equals("") ? analysisCharactersticName + " " : "");
          if (entry != null) {
            if (entry.getOptions() != null && entry.getOptions().size() > 0) {
              for (Iterator i2 = entry.getOptions().iterator(); i2.hasNext();) {
                PropertyOption option = (PropertyOption)i2.next();
                analysisAnnotations.append(option.getOption() != null && !option.getOption().trim().equals("") ? option.getOption() + " " : "");
                propertyString.append(option.getValue() != null && !option.getValue().trim().equals("") ? option.getValue() + " " : "");
              }

            } else if (entry.getValues() != null && entry.getValues().size() > 0) {
              for (Iterator i2 = entry.getValues().iterator(); i2.hasNext();) {
                PropertyEntryValue entryValue = (PropertyEntryValue)i2.next();
                analysisAnnotations.append(entryValue.getValue() != null && !entryValue.getValue().trim().equals("") ? entryValue.getValue() + " " : "");
                propertyString.append(entryValue.getValue() != null && !entryValue.getValue().trim().equals("") ? entryValue.getValue() + " " : "");
              }

            } else {
              analysisAnnotations.append(entry.getValue() != null && !entry.getValue().trim().equals("") ? entry.getValue() + " " : "");
              propertyString.append(entry.getValue() != null && !entry.getValue().trim().equals("") ? entry.getValue() + " " : "");

            }

          }
          analysisAnnotations.append(otherLabel != null && !otherLabel.trim().equals("") ? otherLabel + " " : "");
          propertyString.append(otherLabel != null && !otherLabel.trim().equals("") ? otherLabel + " " : "");

          annotationsByProperty.put(luceneSampleCharacteristicName, propertyString);
        }          
      }

    }


    // Obtain collaborators of analysis
    StringBuffer collaborators = new StringBuffer();
    if (idAnalysis != null) {
      List analysisCollaboratorRows = (List)analysisCollaboratorMap.get(idAnalysis);
      if (analysisCollaboratorRows != null) {
        for(Iterator i1 = analysisCollaboratorRows.iterator(); i1.hasNext();) {
          Object[] collabRow = (Object[])i1.next();
          collaborators.append(collabRow[1] != null  ? " COLLAB-" + ((Integer)collabRow[1]).toString() + "-COLLAB " : "");
        }          
      }
    }

    /*  RC_8.2
    // Get the current analysis (if applicable) to obtain the list of topics it belongs to
    StringBuffer analysisTopics = new StringBuffer();
    if(idAnalysis != null) {
      Map topicsUsedMap = new HashMap();    // Keep track of topics already found for this request

      Analysis analysis = (Analysis)sess.get(Analysis.class, idAnalysis);
      Hibernate.initialize(analysis.getTopics());
      if(analysis.getTopics() != null) {
        // If topics exist for this analysis then iterate through the list
        Iterator<?> it = analysis.getTopics().iterator();
        while(it.hasNext()) {
          Topic t = (Topic) it.next();
          Integer topicInt = (Integer)topicsUsedMap.get(t.getIdTopic());
          if(topicInt == null) {
            // If this topic not yet used, add it's name and description field (if present)
            topicsUsedMap.put(t.getIdTopic(), new Integer(1));
            analysisTopics.append(t.getName() + " ");
            if(t.getDescription() != null) {
              String str = Constants.HTML_BRACKETS.matcher(t.getDescription()).replaceAll("");
              if(str.length() > 0) {
                analysisTopics.append(str + " ");
              }
            }
            while(t.getParentTopic() != null) {
              // Repeat for any parents that are not already on the list
              t = t.getParentTopic();
              topicInt = (Integer)topicsUsedMap.get(t.getIdTopic());
              if(topicInt == null) {
                topicsUsedMap.put(t.getIdTopic(), new Integer(1));
                analysisTopics.append(t.getName() + " ");
                if(t.getDescription() != null) {
                  String str = Constants.HTML_BRACKETS.matcher(t.getDescription()).replaceAll("");
                  if(str.length() > 0) {
                    analysisTopics.append(str + " ");
                  }
                }              
              }         
            }
          }
        }
      }  
    }
     */

    String agLabName = Lab.formatLabName(agLabLastName, agLabFirstName);
    String labName   = Lab.formatLabName(labLastName, labFirstName);

    if (codeVisibility != null && codeVisibility.equals(Visibility.VISIBLE_TO_PUBLIC)) {
      publicNote = "(Public) ";
    }


    Map nonIndexedFieldMap = new HashMap();
    nonIndexedFieldMap.put(AnalysisIndexHelper.ID_ANALYSISGROUP, idAnalysisGroup.toString());
    nonIndexedFieldMap.put(AnalysisIndexHelper.ID_LAB_ANALYSISGROUP, agIdLab.toString());
    nonIndexedFieldMap.put(AnalysisIndexHelper.LAB_NAME_ANALYSISGROUP, agLabName);
    nonIndexedFieldMap.put(AnalysisIndexHelper.ANALYSIS_NUMBER, number);
    nonIndexedFieldMap.put(AnalysisIndexHelper.OWNER_FIRST_NAME, ownerFirstName);
    nonIndexedFieldMap.put(AnalysisIndexHelper.OWNER_LAST_NAME, ownerLastName);
    nonIndexedFieldMap.put(AnalysisIndexHelper.CREATE_DATE, createDate != null ? this.formatDate(createDate, this.DATE_OUTPUT_SQL) : null);
    nonIndexedFieldMap.put(AnalysisIndexHelper.PUBLIC_NOTE, publicNote);


    Map indexedFieldMap = new HashMap();
    indexedFieldMap.put(AnalysisIndexHelper.ID_ANALYSIS, idAnalysis != null ? idAnalysis.toString() : "unknown");
    indexedFieldMap.put(AnalysisIndexHelper.ID_LAB_ANALYSISGROUP, agIdLab);
    indexedFieldMap.put(AnalysisIndexHelper.ANALYSIS_GROUP_NAME, agName);
    indexedFieldMap.put(AnalysisIndexHelper.ANALYSIS_GROUP_DESCRIPTION, agDesc);
    indexedFieldMap.put(AnalysisIndexHelper.ANALYSIS_NAME, name);
    indexedFieldMap.put(AnalysisIndexHelper.DESCRIPTION, desc);
    indexedFieldMap.put(AnalysisIndexHelper.ID_ORGANISM, idOrganism);
    indexedFieldMap.put(AnalysisIndexHelper.ORGANISM, idOrganism != null ? getDictionaryDisplay("hci.gnomex.model.Organism", idOrganism.toString()) : "");
    indexedFieldMap.put(AnalysisIndexHelper.ID_ANALYSIS_TYPE, idAnalysisType);
    indexedFieldMap.put(AnalysisIndexHelper.ANALYSIS_TYPE, idAnalysisType != null ? getDictionaryDisplay("hci.gnomex.model.AnalysisType", idAnalysisType.toString()) : "");
    indexedFieldMap.put(AnalysisIndexHelper.ID_ANALYSIS_PROTOCOL, idAnalysisProtocol);
    indexedFieldMap.put(AnalysisIndexHelper.ANALYSIS_PROTOCOL, idAnalysisProtocol != null ? getDictionaryDisplay("hci.gnomex.model.AnalysisProtocol", idAnalysisProtocol.toString()) : "");
    indexedFieldMap.put(AnalysisIndexHelper.ID_LAB, idLab != null ? idLab.toString() : "");
    indexedFieldMap.put(AnalysisIndexHelper.ID_INSTITUTION, idInstitution != null ? idInstitution.toString() : "");
    indexedFieldMap.put(AnalysisIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : "");
    indexedFieldMap.put(AnalysisIndexHelper.COLLABORATORS, collaborators != null ? collaborators.toString() : "");
    indexedFieldMap.put(AnalysisIndexHelper.LAB_NAME, labName != null ? labName : "");
    indexedFieldMap.put(AnalysisIndexHelper.CODE_VISIBILITY, codeVisibility != null ? codeVisibility : "");
    // Output the annotation properties.
    for(Iterator i = annotationsByProperty.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      StringBuffer values = (StringBuffer)annotationsByProperty.get(key);
      indexedFieldMap.put(key, values.toString());
    }



    StringBuffer buf = new StringBuffer();
    buf.append(name);
    buf.append(" ");
    buf.append(desc);
    buf.append(" ");
    buf.append(agName);
    buf.append(" ");
    buf.append(agDesc);
    buf.append(" ");
    buf.append(analysisFileComments != null ? analysisFileComments.toString() : "");
    buf.append(" ");
    buf.append(analysisAnnotations.toString());
    buf.append(" ");
    buf.append(number);
    buf.append(" ");
    /* RC_8.2
    buf.append(analysisTopics.toString());
    buf.append(" ");
     */
    indexedFieldMap.put(AnalysisIndexHelper.TEXT, buf.toString());

    AnalysisIndexHelper.build(doc, nonIndexedFieldMap, indexedFieldMap);

    analysisIndexWriter.addDocument(doc);


    Map globalNonIndexedFieldMap = new HashMap();
    Map globalIndexedFieldMap = new HashMap();
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NUMBER, number);
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NAME, name);

    globalIndexedFieldMap.put(GlobalIndexHelper.OBJECT_TYPE, GlobalIndexHelper.ANALYSIS);
    globalIndexedFieldMap.put(GlobalIndexHelper.ID, idAnalysis != null ? idAnalysis.toString() : "unknown");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB, idLab != null ? idLab.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_ORGANISM, idOrganism != null ? idOrganism.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.LAB_NAME,labName != null ? labName : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.CODE_VISIBILITY, codeVisibility != null ? codeVisibility : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_INSTITUTION, idInstitution != null ? idInstitution.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.COLLABORATORS, collaborators != null ? collaborators.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB_FOLDER, agIdLab.toString());
    globalIndexedFieldMap.put(GlobalIndexHelper.TEXT, buf.toString());

    Document globalDoc = new Document();
    GlobalIndexHelper.build(globalDoc, globalNonIndexedFieldMap, globalIndexedFieldMap);
    globalIndexWriter.addDocument(globalDoc);

  }

  private void buildDataTrackDocument(IndexWriter datatrackIndexWriter, Integer idDataTrackFolder, Integer idDataTrack, Object[] row) throws IOException {

    Document doc = new Document();


    String dtfName                = (String)row[2];
    String dtfDesc                = (String)row[3];
    Integer dtfIdLab              = (Integer)row[5];
    String dtfLabLastName         = (String)row[6];
    String dtfLabFirstName        = (String)row[7];
    String ownerFirstName         = (String)row[8];
    String ownerLastName          = (String)row[9];
    String labLastName            = (String)row[10];
    String labFirstName           = (String)row[11];
    String name                   = (String)row[12];
    String desc                   = (String)row[13];
    String fileName               = (String)row[14];
    String summary                = (String)row[15];
    Integer idLab                 = (Integer)row[16];
    java.sql.Date createDate      = (java.sql.Date)row[17];
    String codeVisibility         = (String)row[18];
    String publicNote             = ""; 
    Integer idAppUser             = (Integer)row[19];
    Integer idInstitution         = (Integer)row[20];
    Integer idOrganism            = (Integer)row[21];



    //
    // Obtain annotations on data track
    //
    StringBuffer dataTrackAnnotations = new StringBuffer();
    HashMap<String, StringBuffer> annotationsByProperty = new HashMap<String, StringBuffer>();
    if (idDataTrack != null) {
      List dataTrackAnnotationRows = (List)datatrackAnnotationMap.get(idDataTrack);
      if (dataTrackAnnotationRows != null) {
        for(Iterator i1 = dataTrackAnnotationRows.iterator(); i1.hasNext();) {
          Object[] dataTrackRow = (Object[])i1.next();
          String dataTrackCharactersticName = (String)dataTrackRow[1];
          String luceneCharacteristicName = dataTrackCharactersticName.replaceAll("[^A-Za-z0-9]", "");

          StringBuffer propertyString = annotationsByProperty.get(luceneCharacteristicName);
          if (propertyString == null) {
            propertyString = new StringBuffer();
          }
          PropertyEntry entry = (PropertyEntry)dataTrackRow[2];
          String otherLabel = (String)dataTrackRow[3];
          dataTrackAnnotations.append(dataTrackCharactersticName != null && !dataTrackCharactersticName.trim().equals("") ? dataTrackCharactersticName + " " : "");
          if (entry != null) {
            if (entry.getOptions() != null && entry.getOptions().size() > 0) {
              for (Iterator i2 = entry.getOptions().iterator(); i2.hasNext();) {
                PropertyOption option = (PropertyOption)i2.next();
                dataTrackAnnotations.append(option.getOption() != null && !option.getOption().trim().equals("") ? option.getOption() + " " : "");
                propertyString.append(option.getValue() != null && !option.getValue().trim().equals("") ? option.getValue() + " " : "");
              }

            } else if (entry.getValues() != null && entry.getValues().size() > 0) {
              for (Iterator i2 = entry.getValues().iterator(); i2.hasNext();) {
                PropertyEntryValue entryValue = (PropertyEntryValue)i2.next();
                dataTrackAnnotations.append(entryValue.getValue() != null && !entryValue.getValue().trim().equals("") ? entryValue.getValue() + " " : "");
                propertyString.append(entryValue.getValue() != null && !entryValue.getValue().trim().equals("") ? entryValue.getValue() + " " : "");
              }

            } else {
              dataTrackAnnotations.append(entry.getValue() != null && !entry.getValue().trim().equals("") ? entry.getValue() + " " : "");
              propertyString.append(entry.getValue() != null && !entry.getValue().trim().equals("") ? entry.getValue() + " " : "");

            }

          }
          dataTrackAnnotations.append(otherLabel != null && !otherLabel.trim().equals("") ? otherLabel + " " : "");
          propertyString.append(otherLabel != null && !otherLabel.trim().equals("") ? otherLabel + " " : "");

          annotationsByProperty.put(luceneCharacteristicName, propertyString);
        }          
      }

    }


    // Obtain collaborators of data track
    StringBuffer collaborators = new StringBuffer();
    if (idDataTrack != null) {
      List dataTrackCollaboratorRows = (List)datatrackCollaboratorMap.get(idDataTrack);
      if (dataTrackCollaboratorRows != null) {
        for(Iterator i1 = dataTrackCollaboratorRows.iterator(); i1.hasNext();) {
          Object[] collabRow = (Object[])i1.next();
          collaborators.append(collabRow[1] != null  ? " COLLAB-" + ((Integer)collabRow[1]).toString() + "-COLLAB " : "");
        }          
      }
    }

    /*  RC_8.2
    // Get the current data track (if applicable) to obtain the list of topics it belongs to
    StringBuffer dataTrackTopics = new StringBuffer();
    if(idDataTrack != null) {
      Map topicsUsedMap = new HashMap();    // Keep track of topics already found for this request

      DataTrack dt = (DataTrack)sess.get(DataTrack.class, idDataTrack);
      Hibernate.initialize(dt.getTopics());
      if(dt.getTopics() != null) {
        // If topics exist for this data track then iterate through the list
        Iterator<?> it = dt.getTopics().iterator();
        while(it.hasNext()) {
          Topic t = (Topic) it.next();
          Integer topicInt = (Integer)topicsUsedMap.get(t.getIdTopic());
          if(topicInt == null) {
            // If this topic not yet used, add it's name and description field (if present)
            topicsUsedMap.put(t.getIdTopic(), new Integer(1));
            dataTrackTopics.append(t.getName() + " ");
            if(t.getDescription() != null) {
              String str = Constants.HTML_BRACKETS.matcher(t.getDescription()).replaceAll("");
              if(str.length() > 0) {
                dataTrackTopics.append(str + " ");
              }
            }
            while(t.getParentTopic() != null) {
              // Repeat for any parents that are not already on the list
              t = t.getParentTopic();
              topicInt = (Integer)topicsUsedMap.get(t.getIdTopic());
              if(topicInt == null) {
                topicsUsedMap.put(t.getIdTopic(), new Integer(1));
                dataTrackTopics.append(t.getName() + " ");
                if(t.getDescription() != null) {
                  String str = Constants.HTML_BRACKETS.matcher(t.getDescription()).replaceAll("");
                  if(str.length() > 0) {
                    dataTrackTopics.append(str + " ");
                  }
                }            
              }         
            }
          }
        }
      }  
    } 
     */

    String dtfLabName = Lab.formatLabName(dtfLabLastName, dtfLabFirstName);
    String labName   = Lab.formatLabName(labLastName, labFirstName);

    if (codeVisibility != null && codeVisibility.equals(Visibility.VISIBLE_TO_PUBLIC)) {
      publicNote = "(Public) ";
    }

    DataTrackFolderPath path = this.dataTrackFolderMap.get(idDataTrackFolder);

    Map nonIndexedFieldMap = new HashMap();
    nonIndexedFieldMap.put(DataTrackIndexHelper.ID_DATATRACKFOLDER, idDataTrackFolder.toString());
    nonIndexedFieldMap.put(DataTrackIndexHelper.ID_LAB_DATATRACKFOLDER, dtfIdLab == null ? "" : dtfIdLab.toString());
    nonIndexedFieldMap.put(DataTrackIndexHelper.LAB_NAME_DATATRACKFOLDER, dtfLabName == null ? "" : dtfLabName);
    nonIndexedFieldMap.put(DataTrackIndexHelper.OWNER_FIRST_NAME, ownerFirstName);
    nonIndexedFieldMap.put(DataTrackIndexHelper.OWNER_LAST_NAME, ownerLastName);
    nonIndexedFieldMap.put(DataTrackIndexHelper.CREATE_DATE, createDate != null ? this.formatDate(createDate, this.DATE_OUTPUT_SQL) : null);
    nonIndexedFieldMap.put(DataTrackIndexHelper.PUBLIC_NOTE, publicNote);
    nonIndexedFieldMap.put(DataTrackIndexHelper.DATA_TRACK_FOLDER_PATH, path.dataTrackFolderPath);

    Map indexedFieldMap = new HashMap();
    indexedFieldMap.put(DataTrackIndexHelper.ID_DATATRACK, idDataTrack != null ? idDataTrack.toString() : "unknown");
    indexedFieldMap.put(DataTrackIndexHelper.ID_LAB_DATATRACKFOLDER, dtfIdLab);
    indexedFieldMap.put(DataTrackIndexHelper.DATATRACK_FOLDER_NAME, dtfName);
    indexedFieldMap.put(DataTrackIndexHelper.DATATRACK_FOLDER_DESCRIPTION, dtfDesc);
    indexedFieldMap.put(DataTrackIndexHelper.DATATRACK_NAME, name);
    indexedFieldMap.put(DataTrackIndexHelper.DESCRIPTION, desc);
    indexedFieldMap.put(DataTrackIndexHelper.FILE_NAME, fileName);
    indexedFieldMap.put(DataTrackIndexHelper.SUMMARY, summary);
    indexedFieldMap.put(DataTrackIndexHelper.ID_LAB, idLab != null ? idLab.toString() : "");
    indexedFieldMap.put(DataTrackIndexHelper.ID_INSTITUTION, idInstitution != null ? idInstitution.toString() : "");
    indexedFieldMap.put(DataTrackIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : "");
    indexedFieldMap.put(DataTrackIndexHelper.COLLABORATORS, collaborators != null ? collaborators.toString() : "");
    indexedFieldMap.put(DataTrackIndexHelper.LAB_NAME, labName != null ? labName : "");
    indexedFieldMap.put(DataTrackIndexHelper.CODE_VISIBILITY, codeVisibility != null ? codeVisibility : "");
    indexedFieldMap.put(DataTrackIndexHelper.ID_ORGANISM, idOrganism != null ? idOrganism.toString() : "");

    // Output the annotation properties.
    for(Iterator i = annotationsByProperty.keySet().iterator(); i.hasNext();) {
      String key = (String)i.next();
      StringBuffer values = (StringBuffer)annotationsByProperty.get(key);
      indexedFieldMap.put(key, values.toString());
    }



    StringBuffer buf = new StringBuffer();
    buf.append(name);
    buf.append(" ");
    buf.append(desc);
    buf.append(" ");
    buf.append(dtfName);
    buf.append(" ");
    buf.append(dtfDesc);
    buf.append(" ");
    buf.append(dataTrackAnnotations.toString());
    buf.append(" ");
    buf.append(fileName);
    buf.append(" ");
    /* RC_8.2
    buf.append(dataTrackTopics.toString());
    buf.append(" ");
     */
    indexedFieldMap.put(DataTrackIndexHelper.TEXT, buf.toString());

    DataTrackIndexHelper.build(doc, nonIndexedFieldMap, indexedFieldMap);

    datatrackIndexWriter.addDocument(doc);

    Map globalNonIndexedFieldMap = new HashMap();
    Map globalIndexedFieldMap = new HashMap();
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NUMBER, fileName);
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NAME, name);

    globalIndexedFieldMap.put(GlobalIndexHelper.OBJECT_TYPE, GlobalIndexHelper.DATA_TRACK);
    globalIndexedFieldMap.put(GlobalIndexHelper.ID, idDataTrack != null ? idDataTrack.toString() : "unknown");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB, idLab != null ? idLab.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_ORGANISM, idOrganism != null ? idOrganism.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.LAB_NAME,labName != null ? labName : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.CODE_VISIBILITY, codeVisibility != null ? codeVisibility : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_INSTITUTION, idInstitution != null ? idInstitution.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.COLLABORATORS, collaborators != null ? collaborators.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB_FOLDER, dtfIdLab == null ? "" : dtfIdLab.toString());
    globalIndexedFieldMap.put(GlobalIndexHelper.TEXT, buf.toString());

    Document globalDoc = new Document();
    GlobalIndexHelper.build(globalDoc, globalNonIndexedFieldMap, globalIndexedFieldMap);
    globalIndexWriter.addDocument(globalDoc);
  }

  private void buildTopicDocument(IndexWriter topicIndexWriter, Integer idTopic, Object[] row) throws IOException {

    Document doc = new Document();

    String topicName              = (String)row[1];
    String topicDesc              = (String)row[2];
    String codeVisibility         = (String)row[3];
    Integer idAppUser             = (Integer)row[4];
    Integer idInstitution         = (Integer)row[5];
    String labLastName            = (String)row[6];
    String labFirstName           = (String)row[7];
    String ownerFirstName         = (String)row[8];
    String ownerLastName          = (String)row[9];
    java.sql.Date createDate      = (java.sql.Date)row[10];
    Integer idLab                 = (Integer)row[11];    

    String labName   = Lab.formatLabName(labLastName, labFirstName);

    //if (codeVisibility != null && codeVisibility.equals(Visibility.VISIBLE_TO_PUBLIC)) {
    //    publicNote = "(Public) ";
    //}


    Map nonIndexedFieldMap = new HashMap();
    nonIndexedFieldMap.put(TopicIndexHelper.OWNER_FIRST_NAME, ownerFirstName);
    nonIndexedFieldMap.put(TopicIndexHelper.OWNER_LAST_NAME, ownerLastName);
    nonIndexedFieldMap.put(TopicIndexHelper.CREATE_DATE, createDate != null ? this.formatDate(createDate, this.DATE_OUTPUT_SQL) : null);

    Map indexedFieldMap = new HashMap();
    indexedFieldMap.put(TopicIndexHelper.ID_TOPIC, idTopic != null ? idTopic.toString() : "unknown");
    indexedFieldMap.put(TopicIndexHelper.DESCRIPTION, topicDesc);
    indexedFieldMap.put(TopicIndexHelper.TOPIC_NAME, topicName);
    indexedFieldMap.put(TopicIndexHelper.ID_LAB, idLab != null ? idLab.toString() : "");
    indexedFieldMap.put(TopicIndexHelper.ID_INSTITUTION, idInstitution != null ? idInstitution.toString() : "");
    indexedFieldMap.put(TopicIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : "");
    indexedFieldMap.put(TopicIndexHelper.LAB_NAME, labName != null ? labName : "");
    indexedFieldMap.put(TopicIndexHelper.CODE_VISIBILITY, codeVisibility != null ? codeVisibility : "");

    StringBuffer buf = new StringBuffer();
    buf.append(topicName);
    buf.append(" ");
    buf.append(topicDesc);
    buf.append(" ");

    indexedFieldMap.put(DataTrackIndexHelper.TEXT, buf.toString());

    TopicIndexHelper.build(doc, nonIndexedFieldMap, indexedFieldMap);

    topicIndexWriter.addDocument(doc);

    Map globalNonIndexedFieldMap = new HashMap();
    Map globalIndexedFieldMap = new HashMap();
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NUMBER, "");
    globalNonIndexedFieldMap.put(GlobalIndexHelper.NAME, topicName);

    globalIndexedFieldMap.put(GlobalIndexHelper.OBJECT_TYPE, GlobalIndexHelper.TOPIC);
    globalIndexedFieldMap.put(GlobalIndexHelper.ID, idTopic != null ? idTopic.toString() : "unknown");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_LAB, idLab != null ? idLab.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_ORGANISM, "g1");
    globalIndexedFieldMap.put(GlobalIndexHelper.LAB_NAME,labName != null ? labName : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.CODE_VISIBILITY, codeVisibility != null ? codeVisibility : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_INSTITUTION, idInstitution != null ? idInstitution.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.ID_APPUSER, idAppUser != null ? idAppUser.toString() : "");
    globalIndexedFieldMap.put(GlobalIndexHelper.TEXT, buf.toString());

    Document globalDoc = new Document();
    GlobalIndexHelper.build(globalDoc, globalNonIndexedFieldMap, globalIndexedFieldMap);
    globalIndexWriter.addDocument(globalDoc);
  }

  // Bypassed dtd validation when reading data sources.
  public class DummyEntityRes implements EntityResolver
  {
    public InputSource resolveEntity(String publicId, String systemId)
    throws SAXException, IOException
    {
      return new InputSource(new StringReader(" "));
    }

  }

  private class DataTrackFolderPath {
    public Integer idDataTrackFolder;
    public Integer idParentDataTrackFolder;
    public String name = "";
    public String dataTrackFolderPath = "";
    public Boolean pathComplete = false;
  }
}