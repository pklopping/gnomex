package hci.gnomex.model;


import hci.framework.model.DetailObject;
import hci.gnomex.security.SecurityAdvisor;

public class ProjectExperimentReportFilter extends DetailObject {


  // Criteria
  private Integer               idAppUser;
  private Integer               idLab;
  private Integer				idCoreFacility;

  private StringBuffer          queryBuf;
  private boolean               addWhere = true;
  private SecurityAdvisor       secAdvisor;

  public static final int       COL_LAB_LASTNAME = 0;
  public static final int       COL_LAB_FIRSTNAME = 1;
  public static final int       COL_SUBMITTER_LASTNAME = 2;
  public static final int       COL_SUBMITTER_FIRSTNAME = 3;
  public static final int       COL_IDREQUEST = 4;
  public static final int       COL_REQUEST_NUMBER = 5;
  public static final int       COL_CODE_REQUEST_CATEGORY = 6;
  public static final int       COL_CODE_REQUEST_APPLICATION = 7;
  public static final int       COL_CREATE_DATE = 8;
  public static final int       COL_MODIFY_DATE = 9;
  public static final int       COL_CODE_VISIBILITY = 10;
  public static final int       COL_COMPLETED_DATE = 11;
  public static final int       COL_DESCRIPTION = 12;
  public static final int       COL_ORGANISM = 13;
  public static final int       COL_NUMBER_SAMPLES = 14;
  public static final int       COL_OWNER_LASTNAME = 15;
  public static final int       COL_OWNER_FIRSTNAME = 16;
  public static final int       COL_REQUEST_NAME = 17;
  public static final int       COL_PROJECT_NAME = 18;
  public static final int       COL_PROJECT_DESCRIPTION = 19;
  public static final int       COL_PRIVACY_EXPIRATION_DATE = 20;
  public static final int       COL_EXPERIMENT_COST = 21;

  public static final int       COL_COST_IDREQUEST = 0;
  public static final int       COL_COST_COST = 1;

  public StringBuffer getQuery(SecurityAdvisor secAdvisor) {
    addWhere = true;
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();

    queryBuf.append(" SELECT DISTINCT ");
    queryBuf.append(" min(lab.lastName), ");
    queryBuf.append(" min(lab.firstName), ");
    queryBuf.append(" min(submitter.lastName), ");
    queryBuf.append(" min(submitter.firstName), ");
    queryBuf.append(" req.idRequest, ");
    queryBuf.append(" min(req.number), ");
    queryBuf.append(" min(req.codeRequestCategory), ");
    queryBuf.append(" min(req.codeApplication), ");
    queryBuf.append(" min(req.createDate), ");
    queryBuf.append(" min(req.lastModifyDate), ");
    queryBuf.append(" min(req.codeVisibility), ");
    queryBuf.append(" min(req.completedDate), ");
    queryBuf.append(" min(req.description), ");
    queryBuf.append(" min(sample.idOrganism), ");
    queryBuf.append(" count(distinct sample.idSample), ");
    queryBuf.append(" min(owner.lastName), ");
    queryBuf.append(" min(owner.firstName), ");
    queryBuf.append(" min(req.name), ");
    queryBuf.append(" min(project.name), ");
    queryBuf.append(" min(project.description), ");
    queryBuf.append(" min(req.privacyExpirationDate), ");
    queryBuf.append(" sum(bi.invoicePrice) ");

    getQueryBody(queryBuf);

    queryBuf.append(" group by req.idRequest ");
    queryBuf.append(" order by min(lab.firstName), min(lab.lastName), req.idRequest ");

    return queryBuf;

  }

  public void getQueryBody(StringBuffer queryBuf) {

    queryBuf.append(" FROM        Request as req ");
    queryBuf.append(" JOIN        req.samples as sample ");
    queryBuf.append(" JOIN        req.submitter as submitter ");
    queryBuf.append(" JOIN        req.lab as lab ");
    queryBuf.append(" JOIN        req.appUser as owner ");
    queryBuf.append(" JOIN        req.project as project ");
    queryBuf.append(" LEFT JOIN   req.collaborators as collab ");
    queryBuf.append(" LEFT JOIN   req.billingItems as bi ");

    addRequestCriteria();
    addSecurityCriteria();

  }

  public StringBuffer getCostQuery(SecurityAdvisor secAdvisor) {
    addWhere = true;
    this.secAdvisor = secAdvisor;
    queryBuf = new StringBuffer();

    queryBuf.append(" SELECT ");
    queryBuf.append(" req.idRequest, ");
    queryBuf.append(" sum(bi.invoicePrice) ");

    getCostQueryBody(queryBuf);

    queryBuf.append(" group by req.idRequest ");
    queryBuf.append(" order by min(lab.firstName), min(lab.lastName), req.idRequest ");

    return queryBuf;

  }

  public void getCostQueryBody(StringBuffer queryBuf) {

    queryBuf.append(" FROM        Request as req ");
    queryBuf.append(" JOIN        req.lab as lab ");
    queryBuf.append(" LEFT JOIN   req.billingItems as bi ");

    addRequestCriteria();
    secAdvisor.buildSecurityCriteria(queryBuf, "req", null, addWhere, false, true);
  }

  private void addRequestCriteria() {
	// Search by core 
	if (idCoreFacility != null){
	  this.addWhereOrAnd();
	  queryBuf.append(" req.idCoreFacility = ");
	  queryBuf.append(idCoreFacility + " ");
	}
    // Search by request number 
    if (idLab != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idLab =");
      queryBuf.append(idLab);
    } 
    // Search by user 
    if (idAppUser != null){
      this.addWhereOrAnd();
      queryBuf.append(" req.idAppUser = ");
      queryBuf.append(idAppUser);
    }

    if(idLab == null){
      this.addWhereOrAnd();
      queryBuf.append(" case when lab.excludeUsage is null then 'N' else lab.excludeUsage end != 'Y' ");
    }
  }

  private void addSecurityCriteria() {
    secAdvisor.buildSecurityCriteria(queryBuf, "req", "collab", addWhere, false, true);
  }

  protected boolean addWhereOrAnd() {
    if (addWhere) {
      queryBuf.append(" WHERE ");
      addWhere = false;
    } else {
      queryBuf.append(" AND ");
    }
    return addWhere;
  }

  public Integer getIdLab() {
    return idLab;
  }
  
  public Integer getIdCoreFacility() {
	  return idCoreFacility;
  }


  public Integer getIdUser() {
    return idAppUser;
  }

  public void setIdLab(Integer idLab) {
    this.idLab = idLab;
  }
  
  public void setIdCoreFacility(Integer idCoreFacility) {
	  this.idCoreFacility = idCoreFacility;
  }

  public void setIdUser(Integer idAppUser) {
    this.idAppUser = idAppUser;
  }
}
