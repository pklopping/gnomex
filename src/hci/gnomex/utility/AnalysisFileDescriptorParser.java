package hci.gnomex.utility;


import hci.framework.model.DetailObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jdom.Document;
import org.jdom.Element;


public class AnalysisFileDescriptorParser extends DetailObject implements Serializable {

  protected Document   doc;
  protected Map        fileDescriptorMap = new HashMap();

  public AnalysisFileDescriptorParser(Document doc) {
    this.doc = doc;

  }

  public void parse() throws Exception{

    Element root = this.doc.getRootElement();

    for(Iterator i = root.getChildren("FileDescriptor").iterator(); i.hasNext();) {
      Element node = (Element)i.next();      
      String analysisNumber = node.getAttributeValue("number");
      FileDescriptor fd = initializeFileDescriptor(node);

      List fileDescriptors = (List)fileDescriptorMap.get(analysisNumber);
      if (fileDescriptors == null) {
        fileDescriptors = new ArrayList();
        fileDescriptorMap.put(analysisNumber, fileDescriptors);
      }

      fileDescriptors.add(fd);
      getChildrenFileDescriptors(node, fd);

    }

  }

  private void getChildrenFileDescriptors(Element parentNode, FileDescriptor parentFileDescriptor) {

    if (parentNode.getChildren("children") != null && parentNode.getChildren("children").size() > 0) {
      for(Iterator i = parentNode.getChild("children").getChildren("FileDescriptor").iterator(); i.hasNext();) {
        Element node = (Element)i.next();      
        String analysisNumber = node.getAttributeValue("number");
        FileDescriptor fd = initializeFileDescriptor(node);

        List fileDescriptors = (List)fileDescriptorMap.get(analysisNumber);
        if (fileDescriptors == null) {
          fileDescriptors = new ArrayList();
          fileDescriptorMap.put(analysisNumber, fileDescriptors);
        }

        fileDescriptors.add(fd);

        getChildrenFileDescriptors(node, fd);

      }

    }
  }


  protected FileDescriptor initializeFileDescriptor(Element n){
    FileDescriptor fd = new FileDescriptor();

    fd.setId(n.getAttributeValue("idAnalysis") != null && !n.getAttributeValue("idAnalysis").equals("") ? Integer.valueOf(n.getAttributeValue("idAnalysis")) : null);
    fd.setFileName(n.getAttributeValue("fileName"));
    fd.setQualifiedFilePath(n.getAttributeValue("qualifiedFilePath"));
    fd.setBaseFilePath(n.getAttributeValue("baseFilePath"));
    fd.setZipEntryName(n.getAttributeValue("zipEntryName"));
    fd.setType(n.getAttributeValue("type"));
    if(n.getAttributeValue("fileSize").length() > 0) {
      long fileSize = Long.parseLong(n.getAttributeValue("fileSize"));
      fd.setFileSize(fileSize);    	
    }
    fd.setNumber(n.getAttributeValue("number"));

    return fd;

  }


  public Set getAnalysisNumbers() {
    return fileDescriptorMap.keySet();
  }


  public List getFileDescriptors(String analysisNumber) {
    return (List)fileDescriptorMap.get(analysisNumber);
  }



}
