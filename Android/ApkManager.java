package com.offlineinstaller;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import java.io.File;
import java.io.IOException;

class ApkManager {
	private final Activity m_activity;
	private AppInstallReceiver m_receiver;
	private OfflineInstaller.IFinishListener m_finishListener = null;
	private static final String INTENT_INSTALL_TYPE = "application/vnd.android.package-archive";
	private static final String NAVITEL_PKG = "com.navitel";
	private static final String OFFLINE_INSTALLER_PKG = "package:com.offlineinstaller";

	public class AppInstallReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context _context, Intent _intent) {
			Uri uri = _intent.getData();
			if (uri == null)
				return;
			String strPackage = uri.getSchemeSpecificPart();

			Logger.log("Found new installed package " + strPackage);

			if (!strPackage.equals(NAVITEL_PKG))
				return;

			if (m_finishListener != null) {
				m_finishListener.onFinished(null);
				m_finishListener = null;
			}
		}
	}

	public ApkManager(Activity _activity) {
		m_activity = _activity;
		_registerIntentReceiver();
	}
	private void _registerIntentReceiver() {
		IntentFilter appInstalledFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
		appInstalledFilter.addDataScheme("package");
		m_receiver = new AppInstallReceiver();
		m_activity.registerReceiver(m_receiver, appInstalledFilter);
	}

	public void unRegisterReceiver() {
		m_activity.unregisterReceiver(m_receiver);
	}

	public void installNavitel(OfflineInstaller.IFinishListener _callback) throws DataNotFoundException, IOException {
		Logger.log("ApkManager::installNavitel");
		m_finishListener = _callback;

		File apk = new File(Settings.getInstance().getNavitelApkFile());
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setDataAndType(
			Uri.fromFile(apk),
			INTENT_INSTALL_TYPE
		);
		m_activity.startActivity(intent);
	}

	public void uninstallThis() {
		Logger.log("ApkManager::uninstallThis");

		Uri thisApp = Uri.parse(OFFLINE_INSTALLER_PKG);
		Intent intent = new Intent(Intent.ACTION_DELETE, thisApp);
		m_activity.startActivity(intent);
	}
}
