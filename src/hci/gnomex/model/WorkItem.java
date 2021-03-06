package hci.gnomex.model;



import hci.hibernate5utils.HibernateDetailObject;

import java.sql.Date;



public class WorkItem extends HibernateDetailObject {
  
  private Integer         idWorkItem;
  private String          codeStepNext;
  private Date            createDate;
  private Integer         idRequest;
  private Request         request;
  private Sample          sample;
  private LabeledSample   labeledSample;
  private Hybridization   hybridization;
  private SequenceLane    sequenceLane;
  private FlowCellChannel flowCellChannel;
  private String          status;
  private Integer         idCoreFacility;
  
 
  public String getCodeStepNext() {
    return codeStepNext;
  }
  
  public void setCodeStepNext(String codeStepNext) {
    this.codeStepNext = codeStepNext;
  }

  
  public Hybridization getHybridization() {
    return hybridization;
  }

  
  public void setHybridization(Hybridization hybridization) {
    this.hybridization = hybridization;
  }

  
  public Integer getIdRequest() {
    return idRequest;
  }

  
  public void setIdRequest(Integer idRequest) {
    this.idRequest = idRequest;
  }

  
  public Integer getIdWorkItem() {
    return idWorkItem;
  }

  
  public void setIdWorkItem(Integer idWorkItem) {
    this.idWorkItem = idWorkItem;
  }

  
  public LabeledSample getLabeledSample() {
    return labeledSample;
  }

  
  public void setLabeledSample(LabeledSample labeledSample) {
    this.labeledSample = labeledSample;
  }

  
  public Sample getSample() {
    return sample;
  }

  
  public void setSample(Sample sample) {
    this.sample = sample;
  }

  
  public Date getCreateDate() {
    return createDate;
  }

  
  public void setCreateDate(Date createDate) {
    this.createDate = createDate;
  }

  
  public SequenceLane getSequenceLane() {
    return sequenceLane;
  }

  
  public void setSequenceLane(SequenceLane sequenceLane) {
    this.sequenceLane = sequenceLane;
  }

  
  public Request getRequest() {
    return request;
  }

  
  public void setRequest(Request request) {
    this.request = request;
  }

  
  public FlowCellChannel getFlowCellChannel() {
    return flowCellChannel;
  }

  
  public void setFlowCellChannel(FlowCellChannel flowCellChannel) {
    this.flowCellChannel = flowCellChannel;
  }

  
  public String getStatus() {
    return status;
  }

  
  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getIdCoreFacility()
  {
    return idCoreFacility;
  }

  public void setIdCoreFacility(Integer idCoreFacility)
  {
    this.idCoreFacility = idCoreFacility;
  }
}