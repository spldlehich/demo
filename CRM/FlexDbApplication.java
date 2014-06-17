package su.navitel.webflexdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.servlet.http.HttpSession;
import org.apache.log4j.Level;

import nextapp.echo.app.Alignment;
import nextapp.echo.app.ApplicationInstance;
import nextapp.echo.app.Component;
import nextapp.echo.app.ContentPane;
import nextapp.echo.app.Window;
import nextapp.echo.app.event.ActionEvent;
import nextapp.echo.app.layout.ColumnLayoutData;
import nextapp.echo.filetransfer.app.DownloadCommand;
import nextapp.echo.filetransfer.app.DownloadProvider;
import nextapp.echo.webcontainer.ContainerContext;
import nextapp.echo.webcontainer.command.BrowserRedirectCommand;

import su.navitel.flexdb.db_control.FlexDbSession;
import su.navitel.flexdb.types.FlexDbCellTable;
import su.navitel.flexdb.types.FlexDbTable;
import su.navitel.webflexdb.widget.simple.MenuItemWidget;
import su.navitel.webflexdb.widget.simple.WindowWidget;
import webflexdb.resource.localization.FlexDbMenuStrings;


/**
 * Application instance implementation.
 */
public class FlexDbApplication extends ApplicationInstance {
  public static final String kEngineVersion = "0.0.2.06";
  
  public FlexDbApplication() {
    super();
  }
  
  public static FlexDbApplication getApp() {
    FlexDbApplication app = (FlexDbApplication) getActive();
    if (null == app) {
      WebLogger.get().log(Level.FATAL, "getApp() is NULL");
      return null;
    }
    return app;
  }

  public Window init() {
    setStyleSheet(Styles.DEFAULT_STYLE_SHEET);
    main_window_ = new Window();
    
    showTablesListFrom();
    return main_window_;
  }
  
  public void goToAuthPage() {
    showTablesListFrom();
    
    if (tables_list_form_ != null)
      tables_list_form_.doResetContent();
    if (table_form_ != null)
      table_form_.doResetContent();
    if (table_import_form_ != null)
      table_import_form_.doResetContent();
    if (rights_form_ != null)
      rights_form_.doResetContent();
    if (users_form_ != null)
      users_form_.doResetContent();
    revisions_form_ = null;
    about_form_     = null;
    
    // redirect
    HttpSession user_session = getHttpSession();
    String context_path = 
      (String) user_session.getAttribute(WebFlexDBServlet.kServletContainerPathAttributeName);
    String auth_page = context_path + WebAuthServlet.kPageName;
    
    enqueueCommand(new BrowserRedirectCommand(auth_page));
  }
  
  public void showMessage(String title, String text) {
    if (null == current_form_)
      return;
    MessageDialog dlg = 
      new MessageDialog(title, text, MessageDialog.CONTROLS_OK);
    current_form_.add(dlg);
  }
  
  public void showWindow(String title, Component[] content) {
    if (null == current_form_)
      return;
    WindowWidget wnd = new WindowWidget();  
    wnd.setTitle(title);
    wnd.setContent(content);
    current_form_.add(wnd);
  }
  
  public FlexDbSession OpenFlexDbSession() {
    HttpSession user_session = getHttpSession();
    if (null == user_session)
      return null;
    FlexDbSession db_session = WebAuthServlet.OpenFlexDbSession(user_session);
    if (null == db_session)
      return null;
    return db_session;
  }
  
  public void CloseFlexDbSession(FlexDbSession db_session) {
    if (null == db_session)
      return;
    WebAuthServlet.CloseFlexDbSession(db_session);
  }
  
  public boolean downloadFile(
      File    downloading_file,
      String  file_name,
      boolean is_delete_downloaded_file) {
    if (null == downloading_file || null == file_name)
      return false;
    if (0 == file_name.length())
      return false;
    if (downloading_file.exists() != true) {
      WebLogger.get().log(Level.WARN, "downloadFile(): file '" + downloading_file.getName() + "' not exist");
      return false;
    }
    
    downloading_file_          = downloading_file;
    downloading_file_name_     = file_name;
    is_delete_downloaded_file_ = is_delete_downloaded_file;
    DownloadCommand download_command = new DownloadCommand(download_provider_);
    enqueueCommand(download_command);
    return true;
  }
  
  public boolean downloadFile(File downloading_file, String file_name) {
    return downloadFile(downloading_file, file_name, false);
  }
  
  public Locale getCurrentLocale() { return current_locale_; } 
  public void   setCurrentLocale(Locale locale) { current_locale_ = locale; }
  
  public ResourceBundle getInterfaceResourceBundle() {
    ResourceBundle bundle = null;
    try {
      bundle = ResourceBundle.getBundle(kInterfaceResourceBundleName, current_locale_);
    } catch (MissingResourceException e) {
      bundle = null;
    }
    return bundle; 
  }
  
  //
  // Pages
  //
   
  // TODO: refactor to page menu widget
  public MenuItemWidget getPagesMenuItems() {
    MenuItemWidget pages_menu = 
      new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_ID,  
        new MenuItemWidget[] {
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_TABLES_ID, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_RIGHTS_ID, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_USERS_ID,  null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_PASSWORD_CHANGING_ID, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_REVISIONS_ID, null),
          new MenuItemWidget(null, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_ABOUT_ID, null),
          new MenuItemWidget(null, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_PAGE_EXIT_ID, null)
        }
      );
    return pages_menu;
  }
  public boolean performedPagesMenuItems(ActionEvent e) {
    String cmd = e.getActionCommand();
    if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_TABLES_ID) == 0) {
      showTablesListFrom();
    } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_RIGHTS_ID) == 0) {
      showRightsFrom();
    } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_USERS_ID) == 0) {
      showUsersFrom();
    } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_PASSWORD_CHANGING_ID) == 0) {
      showPasswordChangingFrom();
    } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_REVISIONS_ID) == 0) {
      showRevisionsFrom();
    } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_ABOUT_ID) == 0) {
      showAboutFrom();
    } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_PAGE_EXIT_ID) == 0) {
      goToAuthPage();
    } else {
      return false; // other action
    }
    return true; // action performed 
  }
  
  public static ColumnLayoutData getStdColumnLayout() { return kColumnLayout_; }
  
  public void showTablesListFrom() { 
    if (null == tables_list_form_)
      tables_list_form_ = new FlexDbTablesListForm();
    showForm(tables_list_form_);
  }
  public void showTableFrom(FlexDbTable db_table) { 
    if (null == table_form_)
      table_form_ = new FlexDbTableForm(db_table);
    else
      table_form_.update(db_table);
    showForm(table_form_);
  }
  public void showTableImportFrom(FlexDbCellTable cell_table) { 
    if (null == table_import_form_)
      table_import_form_ = new FlexDbTableImportForm(cell_table);
    else
      table_import_form_.update(cell_table);
    showForm(table_import_form_);
  }
  public void showRightsFrom() { 
    if (null == rights_form_)
      rights_form_ = new FlexDbRightsForm();
    showForm(rights_form_);
  }
  public void showUsersFrom() { 
    if (null == users_form_)
      users_form_ = new FlexDbUsersForm();
    showForm(users_form_);
  }
  public void showPasswordChangingFrom() { 
    if (null == password_changing_form_)
      password_changing_form_ = new PasswordChangingForm();
    showForm(password_changing_form_);
  }
  public void showRevisionsFrom() { 
    if (null == revisions_form_)
      revisions_form_ = new FlexDbRevisionsForm();
    showForm(revisions_form_);
  }
  public void showAboutFrom() { 
    if (null == about_form_)
      about_form_ = new AboutForm();
    showForm(about_form_);
  }
   
  protected void showForm(ContentPane new_form) {
    if (current_form_ != new_form) {
      current_form_ = new_form;
      main_window_.setContent(new_form);
    }
  }
  
  protected HttpSession getHttpSession() {
    ContainerContext container = 
      (ContainerContext) getContextProperty(ContainerContext.CONTEXT_PROPERTY_NAME);
    if (null == container)
      return null;
    HttpSession user_session = container.getSession();
    if (null == user_session)
      return null;
    return user_session;
  }
  
  private DownloadProvider download_provider_ = new DownloadProvider() {
    public String getContentType() { return "application/octet-stream"; }
    public String getFileName() { return downloading_file_name_; }
    public long getSize() { return downloading_file_.length(); }
    public void writeFile(OutputStream download_stream) {
      if (null == download_stream || null == downloading_file_)
        return;
      try {
        FileInputStream file = new FileInputStream(downloading_file_);
        byte[] buffer = new byte[16 * 1024];
        int part_size = 0; 
        while ((part_size = file.read(buffer)) > 0) {
          download_stream.write(buffer, 0, part_size);
        }
        if (is_delete_downloaded_file_) {
          downloading_file_.delete();
          is_delete_downloaded_file_ = false;
        }
      } catch (FileNotFoundException e) {
        WebLogger.get().log(Level.ERROR, "Nothing to download: " + e.getMessage());
      } catch (IOException e) {
        WebLogger.get().log(Level.ERROR, "Downloading failed: " + e.getMessage());
      }
    }
  };
  
  private static final long serialVersionUID = 1L;
  private static final String kInterfaceResourceBundleName = 
    "webflexdb.resource.localization.FlexDbInterfaceStrings";
  
  private Window   main_window_              = null; // Main window of user interface.
  private Locale   current_locale_           = Locale.US; 
  
  private ContentPane           current_form_      = null;
  private FlexDbTablesListForm  tables_list_form_  = null;
  private FlexDbTableForm       table_form_        = null;
  private FlexDbTableImportForm table_import_form_ = null;
  private FlexDbRightsForm      rights_form_       = null;
  private FlexDbUsersForm       users_form_        = null;
  private PasswordChangingForm  password_changing_form_ = null;
  private FlexDbRevisionsForm   revisions_form_    = null;
  private AboutForm             about_form_        = null;
  
  private File    downloading_file_          = null;
  private String  downloading_file_name_     = "";
  private boolean is_delete_downloaded_file_ = false;
  
  private static final ColumnLayoutData kColumnLayout_ = new ColumnLayoutData();
  static {
    kColumnLayout_.setAlignment(Alignment.ALIGN_LEFT);
  }
}
