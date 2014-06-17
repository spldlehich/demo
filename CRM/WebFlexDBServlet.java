package su.navitel.webflexdb;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Level;
import nextapp.echo.app.ApplicationInstance;
import nextapp.echo.webcontainer.WebContainerServlet;

import su.navitel.flexdb.db_control.FlexDbSession;


public class WebFlexDBServlet extends WebContainerServlet {
  public static final String kServletContainerPathAttributeName = "ServletContainerPath";
  
  public ApplicationInstance newApplicationInstance() {
    return new FlexDbApplication();
  }
  
  @Override protected void process(
      HttpServletRequest  request, 
      HttpServletResponse response) throws IOException, ServletException {
    String auth_page = request.getContextPath() + WebAuthServlet.kPageName;
    
    { // check for authentication
      HttpSession user_session = request.getSession();
      boolean is_authenticated = false;
      if (null == user_session) {
        WebLogger.get().log(Level.FATAL, "Can't create/open the user session");
        WebAuthServlet.writeSimpleResponse(response, "Can't create/open the user session");
        return;
      }
      user_session.setAttribute(kServletContainerPathAttributeName, request.getContextPath());
      
      {
        FlexDbSession db_session = WebAuthServlet.OpenFlexDbSession(user_session);
        if (db_session != null) {
          is_authenticated = db_session.isAuthenticated();
          WebAuthServlet.CloseFlexDbSession(db_session);
        }
      }
      
      if (is_authenticated == false) {
        WebLogger.get().log(Level.DEBUG, "Redirect to auth page"); 
        response.sendRedirect(auth_page);
        return;
      }
    }
    
    try {
      super.process(request, response);
    } catch (Exception ex) {
      WebLogger.get().log(Level.FATAL, "Fatal Exception: " + ex.getMessage());
      response.sendRedirect(auth_page);
    }
  }

  private static final long serialVersionUID = 1L;
}
