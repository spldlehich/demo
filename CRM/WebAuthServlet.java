package su.navitel.webflexdb;

import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.*;
import javax.servlet.http.*;
import org.apache.log4j.Level;

import su.navitel.flexdb.FlexDbLogger;
import su.navitel.flexdb.FlexDbProvider;
import su.navitel.flexdb.db_control.FlexDbSession;

public class WebAuthServlet extends HttpServlet {
  public static final String kPageName = "/auth";
  
  public void doGet(HttpServletRequest request, 
                    HttpServletResponse response) throws ServletException, 
                                                         java.io.IOException {
    doPost(request, response);
  }
  
  public void doPost(HttpServletRequest request, 
                     HttpServletResponse response) throws ServletException, 
                                                          java.io.IOException {
    request.setCharacterEncoding("utf-8");

    if (WebProperties.isLoaded() != true) {
      if (WebProperties.load() != true) {
        writeSimpleResponse(response, "Can't load the main config file");
        return;
      }
    }
    
    String login           = request.getParameter("login");
    String password        = request.getParameter("password");
    String user_ip_address = request.getRemoteAddr();
    boolean is_authenticated = false;
    
    {
      HttpSession user_session = request.getSession(false);
      if (user_session != null) {
        user_session.invalidate();
      }
    }

    HttpSession user_session = request.getSession();
    FlexDbSession db_session = new FlexDbSession();
    user_session.setAttribute(kFlexDbSessionAttributeName, db_session);
    
    // Initialize the database
    if (null == WebAuthServlet.OpenFlexDbSession(user_session)) {
      writeSimpleResponse(response, "Can't open the database");
      return;
    }
    
    // Work with the database
    if (db_session.logon(login, password, user_ip_address) == true)
      is_authenticated = true;

    // Close the database
    WebAuthServlet.CloseFlexDbSession(db_session);
    db_session = null;
   
    if (is_authenticated == true) {
      String main_page = request.getContextPath() + kMainPage;
      WebLogger.get().log(Level.DEBUG, "Redirect to '" + main_page + "' main page");
      response.sendRedirect(main_page);
      return;
    } else {
      String auth_page = request.getContextPath() + kAuthenticationHtml;
      WebLogger.get().log(Level.DEBUG, "Redirect to '" + auth_page + "' auth page");
      response.sendRedirect(auth_page);
      return;
    }
  }
  
  static public FlexDbSession OpenFlexDbSession(HttpSession user_session) {
    FlexDbSession db_session = (FlexDbSession) user_session.getAttribute(kFlexDbSessionAttributeName);
    if (null == db_session) {
      WebLogger.get().log(Level.DEBUG, "Attribute is missing '" + kFlexDbSessionAttributeName + "'");
      return null;
    }
    
    Connection db_connection = null; 
    synchronized (db_provider_) {
      if (db_provider_.isInitialized() != true) {
        if (db_provider_.Create() != true) {
          FlexDbLogger.get().log(Level.FATAL, "db_provider_.Create() failed");
          return null;
        }
      }
      db_connection = db_provider_.GetConnection();
      if (null == db_connection) {
        FlexDbLogger.get().log(Level.ERROR, "db_provider_.GetConnection() failed");
        return null;
      }
    }
    
    if (db_session.open(db_connection) != true) {
      FlexDbLogger.get().log(Level.INFO, "db_session.Open() failed");
      try {
        db_connection.close();
      } 
      catch (SQLException se) {}
      return null;
    }
    return db_session;
  }
  
  static public void CloseFlexDbSession(FlexDbSession db_session) {
    if (null == db_session)
      return;
    Connection db_connection = db_session.getConnection();
    db_session.close();
    try {
      db_connection.close();
    }
    catch(SQLException se) {}
  }
  
  static public void DeleteFlexDbSession(HttpSession user_session) {
    user_session.removeAttribute(kFlexDbSessionAttributeName);
  }
  
  static public void writeSimpleResponse(
      HttpServletResponse response,
      String message) throws ServletException, java.io.IOException {
    String title = "message";
    response.setContentType("text/html");
    java.io.PrintWriter out = response.getWriter();
    String html_page = 
      "<html>" + 
      "  <head><title>" + title + "</title>" + "</head>" + 
      "  <body>" + 
      "    <h1>" + message + "</h1>"+
      "  </body>"+ 
      "</html>"; 
    out.println(html_page);
  }
  
  private static final long serialVersionUID = 1L;
  
  private static final String kMainPage = "/pnddb";
  private static final String kAuthenticationHtml = "/auth.html";
  private static final String kFlexDbSessionAttributeName = "su.navitel.flexdb.db_control.FlexDbSession";
  
  private static FlexDbProvider db_provider_ = new FlexDbProvider();
}
