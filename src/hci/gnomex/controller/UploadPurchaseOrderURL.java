package hci.gnomex.controller;

import hci.gnomex.model.PropertyDictionary;
import hci.gnomex.utility.HibernateSession;
import hci.gnomex.utility.PropertyDictionaryHelper;
import hci.gnomex.utility.ServletUtil;
import org.hibernate.Session;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UploadPurchaseOrderURL extends HttpServlet {

	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		// Restrict commands to local host if request is not secure
		if (!ServletUtil.checkSecureRequest(req)) {
			ServletUtil.reportServletError(res, "Secure connection is required. Prefix your request with 'https'");
			return;
		}

		Session sess = null;

		try {

			//
			// COMMENTED OUT CODE:
			// boolean isLocalHost = req.getServerName().equalsIgnoreCase("localhost") || req.getServerName().equals("127.0.0.1");
			// String baseURL = "http"+ (isLocalHost ? "://" : "s://") + req.getServerName() + req.getContextPath();
			//
			// To fix upload problem (missing session in upload servlet for FireFox, Safari), encode session in URL
			// for upload servlet. Also, use non-secure (http: rather than https:) when making http request;
			// otherwise, existing session is not accessible to upload servlet.
			//
			//

			sess = HibernateSession.currentReadOnlySession(req.getUserPrincipal().getName());
			String portNumber = PropertyDictionaryHelper.getInstance(sess).getQualifiedProperty(PropertyDictionary.HTTP_PORT, req.getServerName());
			if (portNumber == null) {
				portNumber = "";
			} else {
				portNumber = ":" + portNumber;
			}

			String baseURL = "http" + "://" + req.getServerName() + portNumber + req.getContextPath();
			String URL = baseURL + "/" + "UploadPurchaseOrder.gx";
			// Encode session id in URL so that session maintains for upload servlet when called from
			// Flex upload component inside FireFox, Safari
			URL += ";jsessionid=" + req.getRequestedSessionId();

			res.setContentType("application/xml");
			res.getOutputStream().println("<UploadPurchaseOrderURL url='" + URL + "'/>");

		} catch (Exception e) {
			System.out.println("An error has occured in UploadPurchaseOrderURL - " + e.toString());
		} finally {
			if (sess != null) {
				try {
					HibernateSession.closeSession();
				} catch (Exception e) {
				}
			}
		}
	}
}
