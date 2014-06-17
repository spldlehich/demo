package su.navitel.webflexdb;

import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.log4j.Level;

import nextapp.echo.app.Alignment;
import nextapp.echo.app.Button;
import nextapp.echo.app.Column;
import nextapp.echo.app.ContentPane;
import nextapp.echo.app.Row;
import nextapp.echo.app.SplitPane;
import nextapp.echo.app.event.ActionEvent;
import nextapp.echo.app.event.ActionListener;
import nextapp.echo.extras.app.MenuBarPane;

import su.navitel.flexdb.types.FlexDbRevision;
import su.navitel.flexdb.types.FlexDbTable;
import su.navitel.webflexdb.widget.TablesListWidget;
import su.navitel.webflexdb.widget.simple.CaptionWidget;
import su.navitel.webflexdb.widget.simple.MenuWidget;
import su.navitel.webflexdb.widget.simple.MenuItemWidget;
import su.navitel.webflexdb.widget.simple.FlexDbUpdateListener;
import su.navitel.webflexdb.widget.simple.FlexDbUpdateWidget;
import webflexdb.resource.localization.FlexDbInterfaceStrings;
import webflexdb.resource.localization.FlexDbMenuStrings;
import webflexdb.resource.localization.ResourceManagement;

class FlexDbTablesListForm extends ContentPane {
  public FlexDbTablesListForm() {
    super();
    initComponents();
    
    update_widget_.doUpdateToLastRevision();
  }
  
  public void doResetContent() {
    tables_list_widget_.doResetContent();
  }
  
  private void initComponents() {
    WebLogger.get().log(Level.DEBUG, "init tables list from");
    
    FlexDbApplication app = FlexDbApplication.getApp(); 
    ResourceBundle bundle = app.getInterfaceResourceBundle();
    
    SplitPane spliter = new SplitPane(SplitPane.ORIENTATION_VERTICAL, true);
    add(spliter);
    Column main_area = new Column();
    MenuBarPane main_menu = BuildMenu(FlexDbApplication.getApp().getCurrentLocale());
    spliter.add(main_menu);
    spliter.add(main_area);
    
    {
      Row head_row = new Row();
      head_row.setAlignment(Alignment.ALIGN_CENTER);
      String caption_id = FlexDbInterfaceStrings.PAGE_CAPTION_TABLES_LIST_ID; 
      Row caption = new CaptionWidget(bundle, caption_id);
      head_row.add(caption);
      
      update_widget_ = new FlexDbUpdateWidget(update_listener_, false); 
      head_row.add(update_widget_);
      
      main_area.add(head_row);
    }
    {
      Row tables_row = new Row();
      tables_row.setAlignment(Alignment.ALIGN_CENTER);
      
      Column table_column = new Column();
      
      tables_list_widget_ = new TablesListWidget(this, false, is_self_open_,
                                                 is_show_revision_,
                                                 is_show_deleted_,
                                                 is_show_comment_);
      tables_list_widget_.setLayoutData(FlexDbApplication.getStdColumnLayout());
      table_column.add(tables_list_widget_);
      
      Row buttons_row = new Row();
      {
        String open_table_title = ResourceManagement.getStringResource(bundle, 
            FlexDbInterfaceStrings.TABLE_LIST_FORM_BUTTON_OPEN_ID);
        Button open_button = new Button(open_table_title);
        open_button.setActionCommand(FlexDbMenuStrings.MAIN_MENU_TABLE_OPEN_ID);
        open_button.addActionListener(menu_listener_);
        open_button.setStyleName("Default");
        buttons_row.add(open_button);
      }
      {
        String add_table_title = ResourceManagement.getStringResource(bundle, 
            FlexDbInterfaceStrings.TABLE_LIST_FORM_BUTTON_ADD_ID);
        Button add_button = new Button(add_table_title);
        add_button.setActionCommand(FlexDbMenuStrings.MAIN_MENU_TABLE_ADD_ID);
        add_button.addActionListener(menu_listener_);
        add_button.setStyleName("Default");
        buttons_row.add(add_button);
      }
      table_column.add(buttons_row);
      
      tables_row.add(table_column);
      
      main_area.add(tables_row);
    }
  }
  
  private MenuBarPane BuildMenu(Locale locale) {
    MenuItemWidget[] root_sub_menu = {
      FlexDbApplication.getApp().getPagesMenuItems(),  
      new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_ID, 
        new MenuItemWidget[] {
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_OPEN_ID, null),
          new MenuItemWidget(null, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_ADD_ID, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_RENAME_ID, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_DELETE_ID, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_RESTORE_ID, null),
          new MenuItemWidget(null, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_TABLE_HISTORY_ID, null)
        }
      ),
      new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_COMMENT_ID,  
        new MenuItemWidget[] {
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_COMMENT_SET_ID,  null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_COMMENT_DELETE_ID,  null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_COMMENT_RESTORE_ID, null),
          new MenuItemWidget(null, null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_COMMENT_HISTORY_ID, null)
        }
      ),
      new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_VIEW_ID,  
        new MenuItemWidget[] {
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_VIEW_COMMENTS_ID,        null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_VIEW_REVISION_INFO_ID,   null),
          new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_VIEW_DELETED_OBJECTS_ID, null)
        }
      )
    };
    MenuItemWidget root_menu = new MenuItemWidget(FlexDbMenuStrings.MAIN_MENU_ID, root_sub_menu);
    MenuBarPane menu_pane = MenuWidget.create(root_menu, menu_listener_, locale);
    return menu_pane;
  }
  
  private ActionListener menu_listener_ = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      FlexDbApplication app = FlexDbApplication.getApp();
      if (app.performedPagesMenuItems(e))
        return;
      String cmd = e.getActionCommand();
      
      if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_TABLE_OPEN_ID) == 0) {
        FlexDbTable db_table = tables_list_widget_.getSelectedTable();
        if (db_table != null) {
          app.showTableFrom(db_table);
        }
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_TABLE_ADD_ID) == 0) {
        tables_list_widget_.doNew();
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_TABLE_RENAME_ID) == 0) {
        tables_list_widget_.doModify();
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_TABLE_DELETE_ID) == 0) {
        tables_list_widget_.doDelete();
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_TABLE_RESTORE_ID) == 0) {
        tables_list_widget_.doRestore();
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_TABLE_HISTORY_ID) == 0) {
        tables_list_widget_.showHistory();
      } else // comments
      if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_COMMENT_SET_ID) == 0) {
        tables_list_widget_.doNewComment();
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_COMMENT_DELETE_ID) == 0) {
        tables_list_widget_.doDeleteComment();
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_COMMENT_RESTORE_ID) == 0) {
          ;
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_COMMENT_HISTORY_ID) == 0) {
          ;
      } else // view 
      if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_VIEW_REVISION_INFO_ID) == 0) {
        is_show_revision_ = (is_show_revision_ == true ? false : true);
        tables_list_widget_.showRevision(is_show_revision_);
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_VIEW_DELETED_OBJECTS_ID) == 0) {
        is_show_deleted_ = (is_show_deleted_ == true ? false : true);
        tables_list_widget_.showDeleted(is_show_deleted_);
      } else if (cmd.compareTo(FlexDbMenuStrings.MAIN_MENU_VIEW_COMMENTS_ID) == 0) {
        is_show_comment_ = (is_show_comment_ == true ? false : true);
        tables_list_widget_.showComments(is_show_comment_);
      }
    }
    private static final long serialVersionUID = 1L;
  };
  
  private FlexDbUpdateListener update_listener_ = new FlexDbUpdateListener() {
    public void doUpdate(FlexDbRevision db_revision) {
      tables_list_widget_.doUpdateContent(db_revision);
    }
  };
  
  private static final long serialVersionUID = 1L;
  private boolean is_self_open_ = true;
  
  private TablesListWidget   tables_list_widget_ = null;
  private FlexDbUpdateWidget update_widget_      = null;  
  private boolean is_show_revision_              = false;
  private boolean is_show_deleted_               = false;
  private boolean is_show_comment_               = false;
}
