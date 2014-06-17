package su.navitel.flexdb.db_control;

import java.sql.*;

import org.apache.log4j.Level;

import su.navitel.flexdb.FlexDbLogger;
import su.navitel.flexdb.types.FlexDbRevision;
import su.navitel.flexdb.types.FlexDbTable;
import su.navitel.flexdb.types.FlexDbUser;

class FlexDbControl {
  public FlexDbControl(Connection db_connection) {
    db_connection_ = db_connection;
  }
  
  public boolean RollbackTable(
      FlexDbUser     current_db_user,
      FlexDbTable    db_table,
      FlexDbRevision db_revision) {
    if (null == db_connection_ || null == current_db_user || null == db_revision)
      return false;
    
    int revision_id = db_revision.getRevisionId();
    String sql_query = 
      " SELECT Table_Content_Rollback(" + 
      "   " + current_db_user.getUserId() + ", " +
      "   " + db_table.getTableId()       + ", " +
      "   " + revision_id                 + ", " + 
      "   " + "'rollback'" + ")";
    
    int call_status = GetCallStatus(db_connection_, sql_query);
    if (call_status != revision_id)
      return false;
    return true;
  }
  
  public boolean RestoreTable(
      FlexDbUser     current_db_user,
      FlexDbTable    db_table,
      FlexDbRevision db_revision) {
    if (null == db_connection_ || null == current_db_user || null == db_revision)
      return false;
    
    int revision_id = db_revision.getRevisionId();
    String sql_query = 
      " SELECT Table_Content_Rollback(" + 
      "   " + current_db_user.getUserId() + ", " +
      "   " + db_table.getTableId()       + ", " +
      "   " + revision_id                 + ", " + 
      "   " + "'restore'" + ")";
    
    int call_status = GetCallStatus(db_connection_, sql_query);
    if (call_status != revision_id)
      return false;
    return true;
  }
  
  public boolean UndeleteTable(
      FlexDbUser     current_db_user,
      FlexDbTable    db_table,
      FlexDbRevision db_revision) {
    if (null == db_connection_ || null == current_db_user || null == db_revision)
      return false;
    
    int revision_id = db_revision.getRevisionId();
    String sql_query = 
      " SELECT Table_Content_Rollback(" + 
      "   " + current_db_user.getUserId() + ", " +
      "   " + db_table.getTableId()       + ", " +
      "   " + revision_id                 + ", " + 
      "   " + "'undelete'" + ")";
    
    int call_status = GetCallStatus(db_connection_, sql_query);
    if (call_status != revision_id)
      return false;
    return true;
  }
  
  public boolean RollbackDatabase(FlexDbUser current_db_user, FlexDbRevision db_revision) {
    if (null == db_connection_ || null == current_db_user || null == db_revision)
      return false;
    
    int revision_id = db_revision.getRevisionId();
    String sql_query = 
      " SELECT Database_Rollback(" + 
      "   " + current_db_user.getUserId() + ", " +
      "   " + revision_id                 + ", " + 
      "   " + "'rollback'" + ")";
    
    int call_status = GetCallStatus(db_connection_, sql_query);
    if (call_status != revision_id)
      return false;
    return true;
  }
  
  public boolean RestoreDatabase(FlexDbUser current_db_user, FlexDbRevision db_revision) {
    if (null == db_connection_ || null == current_db_user || null == db_revision)
      return false;
    
    int revision_id = db_revision.getRevisionId();
    String sql_query = 
      " SELECT Database_Rollback(" + 
      "   " + current_db_user.getUserId() + ", " +
      "   " + revision_id                 + ", " + 
      "   " + "'restore'" + ")";
    
    int call_status = GetCallStatus(db_connection_, sql_query);
    if (call_status != revision_id)
      return false;
    return true;
  }
  
  public boolean UndeleteDatabase(
      FlexDbUser current_db_user, 
      FlexDbRevision db_revision) throws FlexDbException {
    if (null == db_connection_ || null == current_db_user || null == db_revision)
      return false;
    
    int revision_id = db_revision.getRevisionId();
    String sql_query = 
      " SELECT Database_Rollback(" + 
      "   " + current_db_user.getUserId() + ", " +
      "   " + revision_id                 + ", " + 
      "   " + "'undelete'" + ")";
    
    int call_status = GetCallStatus(db_connection_, sql_query);
    if (call_status != revision_id)
      return false;
    return true;
  }
  
  static public int GetCallStatus(Connection db_connection, 
                                  String sql_query) throws FlexDbException {
    int call_status = 0;
    String error_message = FlexDbResources.FAILED;
    int revision_id = 0;
    
    if (null == db_connection) {
      return 0;
    }
    try {
      Statement sql_statement = db_connection.createStatement();
      ResultSet sql_result = sql_statement.executeQuery(sql_query);
      if (sql_result.next()) {
        call_status   = sql_result.getInt(1);
        error_message = sql_result.getString(2);
        revision_id   = sql_result.getInt(3);
        if (0 == call_status) {
          FlexDbLogger.get().log(Level.INFO, 
              "SQL request failed with error '" + error_message + "', " +
              "request '" + sql_query + "'");
        } else {
          FlexDbLogger.get().log(Level.INFO, 
              "SQL request returned code " + call_status + " and revision " + revision_id + ", " + 
              "request '" + sql_query + "'");
        }
      }
      sql_statement.close();
    } catch (SQLException se) {
      FlexDbLogger.get().log(Level.ERROR, 
          "SQL request raised exception, " + 
          "request '" + sql_query + "'");
      throw new FlexDbException(FlexDbResources.FAILED);
    }
    
    if (0 == call_status) {
      throw new FlexDbException(error_message);
    }
    return call_status;
  }
  
  static public String str2sql(String str) {
    if (null == str)
      return "''";
	  return " '" + str.replaceAll("'", "''").replaceAll("\\\\", "\\\\\\\\") + "' ";
  }
  
  private Connection db_connection_;
}
