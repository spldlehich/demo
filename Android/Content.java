package com.offlineinstaller;

import android.os.AsyncTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

class NavitelContent {
	private static final String NC_NAME = "NavitelContent";
	private static final String LICENSE_NAME = "License";
	private final File m_licenseDir;
	private Md5DataStorage m_md5Data;
	private OfflineInstaller.ProgressListener m_progressListener;
	private ArrayList<String> m_missingMd5Sums;

	public NavitelContent() throws IOException, DataNotFoundException {
		m_md5Data = new Md5DataStorage();
		m_missingMd5Sums = new ArrayList<String>();

		final File root = new File(Settings.getInstance().getNCRoot());
		if (!root.exists())
			throw new IOException("Root folder " + root.toString() + " does not exist!");

		final File nc = new File(root, NC_NAME);
		if (!nc.exists() && !nc.mkdir())
			throw new IOException("Cannot create folder " + nc.toString());

		m_licenseDir = new File(nc, LICENSE_NAME);
		if (!m_licenseDir.exists() && !m_licenseDir.mkdir())
			throw new IOException("Cannot create folder " + m_licenseDir.toString());

		Logger.log("NavitelContent folder " + nc.toString() + " successfully created");
	}

	public void fillLicenseContent(KeyDataStorage.KeyData _key) throws IOException {
		Logger.log("NavitelContent::fillLicenseContent");

		File[] files = m_licenseDir.listFiles();
		if (files != null) for (File f : files) {
			if (!f.delete())
				throw new IOException("Cannot delete old file: " + f.toString());
		}

		_key.extractLicense(m_licenseDir.toString());
	}

	public void fillContent(final OfflineInstaller.IFinishListener _callback, OfflineInstaller.ProgressListener _progress) {
		class CopyFileTask extends AsyncTask<Void, Integer, Exception> {
			@Override
			protected void onPreExecute() {
				m_missingMd5Sums.clear();
				super.onPreExecute();
			}

			@Override
			protected Exception doInBackground(Void... voids) {
				try {
					_fillContentImpl();
				} catch (Exception e) {
					return e;
				}
				return null;
			}

			@Override
			protected void onPostExecute(Exception e) {
				if (m_progressListener != null)
					m_progressListener.setDone();
				_callback.onFinished(e);
				super.onPostExecute(e);
			}
		}
		m_progressListener = _progress;
		new CopyFileTask().execute();
	}

	public ArrayList<String> getMissingMd5Files() {
		return m_missingMd5Sums;
	}

	private void _fillContentImpl() throws Exception {
		Logger.log("NavitelContent::fillContent");

		File sourceDir = new File(Settings.getInstance().getNCDataPath());
		File destDir = new File(Settings.getInstance().getNCRoot());

		_copyFolder(sourceDir, destDir, true);
	}

	private void _copyFolder(File _folderToCopy, File _dirToCopyInto, boolean _bCleanDir) throws Exception {
		File newDir = new File(_dirToCopyInto, _folderToCopy.getName());
		if (!newDir.exists() && !newDir.mkdir())
			throw new IOException("Cannot create folder " + newDir.getAbsolutePath());

		if (_bCleanDir)
			Utils._clearFolderContent(newDir);

		File[] files = _folderToCopy.listFiles();
		if (files != null) for (File f : files) {
			if (f.isFile())
				_copyFile(f, newDir);
			else
				_copyFolder(f, newDir, false);
		}
	}

	private void _copyFile(File _fileToCopy, File _dirToCopyInto) throws Exception {
		File newFile = new File(_dirToCopyInto, _fileToCopy.getName());
		if (newFile.exists() && !newFile.delete())
			throw new IOException("Cannot delete file: " + newFile.getAbsolutePath());
		Utils.copyFile(_fileToCopy, newFile, m_progressListener);
		String strNeededMd5 = "";
		try {
			strNeededMd5 = m_md5Data.getMd5Value(_fileToCopy);
		} catch (DataNotFoundException e) {
			m_missingMd5Sums.add(_fileToCopy.toString());
			return;
		}
		String strRealMd5 = Utils.calcMd5(newFile, m_progressListener);
		if (!strNeededMd5.equals(strRealMd5)) {
			throw new IOException(
				"MD5 value " + strRealMd5 + " of file " + newFile.toString() + " doesn't match " + strNeededMd5 + " from settings."
			);
		}
	}
}
