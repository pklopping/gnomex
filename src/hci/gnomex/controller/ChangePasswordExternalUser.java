package hci.gnomex.controller;

import hci.framework.control.Command;
import hci.framework.control.RollBackCommandException;
import hci.gnomex.model.AppUser;
import hci.gnomex.security.EncrypterService;
import hci.gnomex.utility.HibernateSession;

import java.io.Serializable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.hibernate.Session;

/**
 *
 *@author
 *@created
 *@version    1.0
 * Generated by the CommandBuilder tool - Kirt Henrie
 */

public class ChangePasswordExternalUser extends GNomExCommand implements Serializable {

  // the static field for logging in Log4J
  private static org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(ChangePasswordExternalUser.class);

  private String    userName;
  private String    password;
  private String    passwordNew1;
  private String    passwordNew2;

  
  private static final String CHANGE_PASSWORD_JSP         = "/change_password_external_user.jsp";
  private static final String SUCCESS_CHANGE_PASSWORD_JSP = "/change_password_external_user_success.jsp";
  

  /**
   *  The method in which you can do any final validation and add any additional
   *  validation entries into the invalidField hashmap, this should be called in
   *  the loadCommand prior to setting the response jsp
   */
  public void validate() {
  }

  /**
   *  The callback method in which any pre-processing of the command takes place
   *  before the execute method is called. This method is where you would want
   *  to load objects from the HttpServletRequest (passed in), do form
   *  validation, etc. The HttpSession is also available in this method in case
   *  any session data is necessary.
   *
   *@param  request  The HttpServletRequest object
   *@param  session  The HttpSession object
   */
  public void loadCommand(HttpServletRequest request, HttpSession session) {
    this.validate();
    
    userName     = (String) request.getParameter("userName");
    password     = (String) request.getParameter("password");
    passwordNew1 = (String) request.getParameter("passwordNew1");
    passwordNew2 = (String) request.getParameter("passwordNew2");
    

    if ( userName == null || userName.equals("")) {
      this.addInvalidField("userName", "User name required");
    } 
    if ( password == null || password.equals("")) {
      this.addInvalidField("passwordExternal", "Old password required");
    } 
    if ( passwordNew1 == null || passwordNew1.equals("")) {
      this.addInvalidField("passwordExternalNew1", "New password required");
    } 
    if ( passwordNew2 == null || passwordNew2.equals("")) {
      this.addInvalidField("passwordExternalNew2", "New password (enter again) required");
    } 
    
    if (!passwordNew1.equals(passwordNew2)) {
      this.addInvalidField("password new mismatch", "The new passwords do not match");
    }
    
    // see if we have a valid form
    if (isValid()) {
      setResponsePage(this.SUCCESS_CHANGE_PASSWORD_JSP);
    } else {
      setResponsePage(this.CHANGE_PASSWORD_JSP);
    }
  }

  /**
   *  The callback method where your business logic should be placed. This
   *  method is either called from the FrontController servlet or from the
   *  RequestProcessor Session Bean (if EJB is used). Any data resulting from
   *  the execution of this method should be put into instance variables in this
   *  class.
   *
   *@return                               Returns this command with the results
   *      of the execute method
   *@exception  RollBackCommandException  Description of the Exception
   */
  public Command execute() throws RollBackCommandException {
    
    
    
    try {
      Session sess = HibernateSession.currentSession(userName);
      
      AppUser appUser = null;
      StringBuffer queryBuf = new StringBuffer();
      queryBuf.append("SELECT a from AppUser as a WHERE a.userNameExternal = '");
      queryBuf.append(userName);
      queryBuf.append("'");
      List appUsers = (List)sess.createQuery(queryBuf.toString()).list();
      if (appUsers.size() > 0) {
        appUser = (AppUser)appUsers.get(0);
      }
      
      if(appUser == null) {
        this.addInvalidField("missing app user", "Invalid user name");
      }
      
      
      if (this.isValid()) {
        String oldPasswordEncrypted = EncrypterService.getInstance().encrypt(password);        
        if (!oldPasswordEncrypted.equals(appUser.getPasswordExternal())) {
          this.addInvalidField("wrong password", "Password does not match password stored in database.");
        }
      }
      
      if (this.isValid()) {
        appUser.setPasswordExternal(EncrypterService.getInstance().encrypt(passwordNew1));
      }
      
      sess.flush();
        
      
      this.xmlResult =  "<SUCCESS/>";

    }
    catch (Exception ex) {
      ex.printStackTrace();
      log.fatal(ex.getClass().toString() + " occurred in ChangePasswordExternalUser " + ex);
      throw new RollBackCommandException();
    }
    finally {
      try {
        HibernateSession.closeSession();
      }
      catch (Exception ex) {
        log.error("Exception trying to close the Hibernate session: "+ ex);
      }
    }
    
    if (isValid()) {
      setResponsePage(this.SUCCESS_CHANGE_PASSWORD_JSP);
    } else {
      setResponsePage(this.CHANGE_PASSWORD_JSP);
    }
    return this;
  }


}

