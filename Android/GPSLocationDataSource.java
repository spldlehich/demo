package ru.just.justtracker.db;

import java.util.ArrayList;
import java.util.List;

import ru.just.justtracker.GPSLocation;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class GPSLocationDataSource {
	private SQLiteDatabase database;
	private GPSDataSQLHelper dbHelper;
	private String[] allColumns = { GPSDataSQLHelper.COLUMN_ID,
									GPSDataSQLHelper.COLUMN_LATITUDE,
									GPSDataSQLHelper.COLUMN_LONGITUDE,
									GPSDataSQLHelper.COLUMN_ACCURACY,
									GPSDataSQLHelper.COLUMN_ALTITUDE,
									GPSDataSQLHelper.COLUMN_BEARING,
									GPSDataSQLHelper.COLUMN_SPEED,
									GPSDataSQLHelper.COLUMN_PROVIDER,
									GPSDataSQLHelper.COLUMN_ISUP,
									GPSDataSQLHelper.COLUMN_TIME,
									GPSDataSQLHelper.COLUMN_SATELLITE,

									GPSDataSQLHelper.COLUMN_IMEI,
									GPSDataSQLHelper.COLUMN_VIN,
									GPSDataSQLHelper.COLUMN_VBAT,
									GPSDataSQLHelper.COLUMN_STARTAUTO,
									GPSDataSQLHelper.COLUMN_ADC,
									GPSDataSQLHelper.COLUMN_TEMPERATURE};

	public GPSLocationDataSource(Context context) {
		dbHelper = new GPSDataSQLHelper(context);
	}

	public void open() throws SQLException {
		database = dbHelper.getWritableDatabase();
	}

	public void close() {
		dbHelper.close();
	}

	public long createNewMessage(String message){
		ContentValues values = new ContentValues();
		values.put(GPSDataSQLHelper.NOTSENDED_TEMP_COLUMN_STRING, message);
		long insertId = database.insert(GPSDataSQLHelper.TABLE_NOTSENDED_TEMP, null, values);
		return insertId;
	}

	public void deleteNotSendedMessage(String id) {
		database.delete(GPSDataSQLHelper.TABLE_NOTSENDED_TEMP, GPSDataSQLHelper.NOTSENDED_TEMP_COLUMN_ID + " = " + id, null);
	}

	public int getNotSendedCount(){
		String countQuery = "SELECT  * FROM " + GPSDataSQLHelper.TABLE_LOCATION + " WHERE " + GPSDataSQLHelper.COLUMN_ISUP + "='0'";
		Cursor cursor = database.rawQuery(countQuery, null);
		int count = cursor.getCount();
		cursor.close();
		return count;
	}

	public long createGPSLocation(GPSLocation location){
		ContentValues values = new ContentValues();

		values.put(GPSDataSQLHelper.COLUMN_LATITUDE, location.getLatitude());
		values.put(GPSDataSQLHelper.COLUMN_LONGITUDE, location.getLongitude());
		values.put(GPSDataSQLHelper.COLUMN_ACCURACY, location.getAccuracy());
		values.put(GPSDataSQLHelper.COLUMN_ALTITUDE, location.getAltitude());
		values.put(GPSDataSQLHelper.COLUMN_BEARING, location.getBearing());
		values.put(GPSDataSQLHelper.COLUMN_SPEED, location.getSpeed());
		values.put(GPSDataSQLHelper.COLUMN_PROVIDER, location.getProvider());
		values.put(GPSDataSQLHelper.COLUMN_TIME, String.valueOf(location.getTime()));
		values.put(GPSDataSQLHelper.COLUMN_SATELLITE, location.getSatelite());
		values.put(GPSDataSQLHelper.COLUMN_ISUP, 0);

		values.put(GPSDataSQLHelper.COLUMN_IMEI, location.getImei());
		values.put(GPSDataSQLHelper.COLUMN_VIN, location.getVin());
		values.put(GPSDataSQLHelper.COLUMN_VBAT, location.getVbat());
		values.put(GPSDataSQLHelper.COLUMN_STARTAUTO, location.getStartauto());
		values.put(GPSDataSQLHelper.COLUMN_ADC, location.getAdc());
		values.put(GPSDataSQLHelper.COLUMN_TEMPERATURE, location.getTemperature());

		long insertId = database.insert(GPSDataSQLHelper.TABLE_LOCATION, null, values);
		return insertId;
	}

	public void deleteLocation(GPSLocation location) {
		long id = location.getId();
		database.delete(GPSDataSQLHelper.TABLE_LOCATION, GPSDataSQLHelper.COLUMN_ID + " = " + String.valueOf(id), null);
	}

	public void deleteLocation(String id) {
		database.delete(GPSDataSQLHelper.TABLE_LOCATION, GPSDataSQLHelper.COLUMN_ID + " = " + id, null);
	}

	public List<GPSLocation> getAllGPSLocation(){
		List<GPSLocation> list = new ArrayList<GPSLocation>();
		Cursor cursor = database.query(GPSDataSQLHelper.TABLE_LOCATION, allColumns, null, null, null, null, null);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			GPSLocation location = cursorToGPSLocation(cursor);
			list.add(location);
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	public List<GPSLocation> getNotUp(){
		List<GPSLocation> list = new ArrayList<GPSLocation>();
		Cursor cursor = database.query(GPSDataSQLHelper.TABLE_LOCATION, allColumns, GPSDataSQLHelper.COLUMN_ISUP + "=?", new String[]{"0"}, null, null, null);
		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			GPSLocation location = cursorToGPSLocation(cursor);
			list.add(location);
			cursor.moveToNext();
		}
		cursor.close();
		return list;
	}

	public GPSLocation getLocation(String id){
		Cursor cursor = database.query(GPSDataSQLHelper.TABLE_LOCATION, allColumns, GPSDataSQLHelper.COLUMN_ID + "=?", new String[]{id}, null, null, null);
		cursor.moveToFirst();
		if (!cursor.isAfterLast()) {
			return cursorToGPSLocation(cursor);
		}
		cursor.close();
		return null;
	}

	public int setUpload(GPSLocation location){
		ContentValues values = new ContentValues();
		values.put(GPSDataSQLHelper.COLUMN_ISUP, 1);
		return database.update(GPSDataSQLHelper.TABLE_LOCATION, values, GPSDataSQLHelper.COLUMN_ID + " = ?", new String[] { String.valueOf(location.getId())});
	}

	private GPSLocation cursorToGPSLocation(Cursor cursor){
		GPSLocation location = new GPSLocation();
		location.setId(cursor.getLong(0));
		location.setLatitude(cursor.getDouble(1));
		location.setLongitude(cursor.getDouble(2));
		location.setAccuracy(cursor.getFloat(3));
		location.setAltitude(cursor.getDouble(4));
		location.setBearing(cursor.getFloat(5));
		location.setSpeed(cursor.getFloat(6));
		location.setProvider(cursor.getString(7));
		location.setIsup(cursor.getInt(8));
		location.setTime(Long.valueOf(cursor.getString(9)));
		location.setSatelite(cursor.getInt(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_SATELLITE)));

		location.setImei(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_IMEI)));
		location.setVin(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_VIN)));
		location.setVbat(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_VBAT)));
		location.setStartauto(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_STARTAUTO)));
		location.setAdc(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_ADC)));
		location.setTemperature(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.COLUMN_TEMPERATURE)));

		return location;
	}

	public boolean isCarStarted(){
		Cursor cursor = database.query(GPSDataSQLHelper.TABLE_SETTINGS, null, GPSDataSQLHelper.SETTINGS_COLUMN_NAME + "=?", new String[]{GPSDataSQLHelper.SETTINGS_COLUMN_STARTED_NAME_VALUE}, null, null, null);
		cursor.moveToFirst();
		if (!cursor.isAfterLast()) {
			if(cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.SETTINGS_COLUMN_VALUE)).equals("1"))
				return true;
			return false;
		}
		cursor.close();
		return false;
	}

	public synchronized int setCarStarted(boolean val){
		ContentValues values = new ContentValues();
		values.put(GPSDataSQLHelper.SETTINGS_COLUMN_VALUE, val ? "1" : "0");
		if(!database.isOpen())
			open();
		return database.update(GPSDataSQLHelper.TABLE_SETTINGS, values, GPSDataSQLHelper.SETTINGS_COLUMN_NAME + " = ?", new String[] { GPSDataSQLHelper.SETTINGS_COLUMN_STARTED_NAME_VALUE });
	}

	public String getCarVoltage(){
		String res = null;
		Cursor cursor = database.query(GPSDataSQLHelper.TABLE_SETTINGS, null, GPSDataSQLHelper.SETTINGS_COLUMN_NAME + "=?", new String[]{GPSDataSQLHelper.SETTINGS_COLUMN_VOLTAGE_NAME_VALUE}, null, null, null);
		cursor.moveToFirst();
		if (!cursor.isAfterLast()) {
			res = cursor.getString(cursor.getColumnIndex(GPSDataSQLHelper.SETTINGS_COLUMN_VALUE));
		}
		cursor.close();
		return res;
	}

	public synchronized int setCarVoltage(String value){
		ContentValues values = new ContentValues();
		values.put(GPSDataSQLHelper.SETTINGS_COLUMN_VALUE, value);
		if(!database.isOpen())
			open();
		return database.update(GPSDataSQLHelper.TABLE_SETTINGS, values, GPSDataSQLHelper.SETTINGS_COLUMN_NAME + " = ?", new String[] { GPSDataSQLHelper.SETTINGS_COLUMN_VOLTAGE_NAME_VALUE });
	}
}