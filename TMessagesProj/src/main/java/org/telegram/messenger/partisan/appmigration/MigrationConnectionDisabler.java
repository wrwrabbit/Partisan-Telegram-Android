package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;

// The server detects two apps connecting with the same session and terminates that session,
// so the migrated data would be lost. The old app must stay offline from the moment it hands
// its data over until the migration is finished or cancelled.
public class MigrationConnectionDisabler {
    private static final String DEAD_PROXY_ADDRESS = "127.0.0.1";
    private static final int DEAD_PROXY_PORT = 9;  // discard port, nothing accepts connections there
    private static final int LEGACY_DEAD_PROXY_PORT = -1;

    private static ProxySettings enforcementProxySettings;
    private static boolean legacyDisablingProxyChecked;

    public static boolean isConnectionDisabled() {
        migrateLegacyDisablingProxyOnce();
        return AppMigratorPreferences.isConnectionDisabled();
    }

    public static synchronized ProxySettings getEnforcementProxySettings() {
        if (enforcementProxySettings == null) {
            enforcementProxySettings = ProxySettings.builder()
                    .setType(ProxySettings.Type.SOCKS5)
                    .setAddress(DEAD_PROXY_ADDRESS)
                    .setPort(DEAD_PROXY_PORT)
                    .build();
        }
        return enforcementProxySettings;
    }

    public static void applyToNative(int account) {
        ConnectionsManager.native_setProxySettings(account, DEAD_PROXY_ADDRESS, DEAD_PROXY_PORT, "", "", "");
    }

    public static void disableConnection() {
        if (isConnectionDisabled()) {
            return;
        }
        // Posted while the connection is still enabled, so that ProxyRotationController cancels an already scheduled proxy check
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        AppMigratorPreferences.setConnectionDisabled(true);
        ConnectionsManager.setProxySettings(false, null);  // rewritten to the dead proxy while the connection is disabled
    }

    public static void enableConnection() {
        if (!isConnectionDisabled()) {
            return;
        }
        AppMigratorPreferences.setConnectionDisabled(false);
        SharedPreferences preferences = getPrefs();
        ConnectionsManager.setProxySettings(preferences.getBoolean("proxy_enabled", false),
                ProxySettings.fromSharedPreferences(preferences));
        NotificationCenter.getGlobalInstance().postNotificationNameOnUIThread(NotificationCenter.proxySettingsChanged);
    }

    private static synchronized void migrateLegacyDisablingProxyOnce() {
        if (legacyDisablingProxyChecked) {
            return;
        }
        legacyDisablingProxyChecked = true;
        SharedPreferences preferences = getPrefs();
        if (!isLegacyDisablingProxyApplied(preferences)) {
            return;
        }
        try {
            AppMigratorPreferences.setConnectionDisabled(true);
            removeUserProxyPreferences(preferences);
            removeLegacyDisablingProxyFromList();
        } catch (Exception e) {
            PartisanLog.e("Legacy disabling proxy migration failed", e);
        }
    }

    private static boolean isLegacyDisablingProxyApplied(SharedPreferences preferences) {
        if (!preferences.getBoolean("proxy_enabled", false)) {
            return false;
        }
        return DEAD_PROXY_ADDRESS.equals(preferences.getString("proxy_ip", ""))
                && isDeadProxyPort(preferences.getInt("proxy_port", 0));
    }

    private static void removeUserProxyPreferences(SharedPreferences preferences) {
        preferences.edit()
                .remove("proxy_enabled")
                .remove("proxy_ip")
                .remove("proxy_port")
                .remove("proxy_user")
                .remove("proxy_pass")
                .remove("proxy_secret")
                .remove("proxy_type")
                .commit();
    }

    private static void removeLegacyDisablingProxyFromList() {
        SharedConfig.loadProxyList();
        if (SharedConfig.currentProxy != null && isLegacyDisablingProxy(SharedConfig.currentProxy)) {
            SharedConfig.currentProxy = null;
        }
        if (SharedConfig.proxyList.removeIf(MigrationConnectionDisabler::isLegacyDisablingProxy)) {
            SharedConfig.saveProxyList();
        }
    }

    private static boolean isLegacyDisablingProxy(SharedConfig.ProxyInfo proxyInfo) {
        return DEAD_PROXY_ADDRESS.equals(proxyInfo.settings.getAddress())
                && isDeadProxyPort(proxyInfo.settings.getPort());
    }

    private static boolean isDeadProxyPort(int port) {
        return port == DEAD_PROXY_PORT || port == LEGACY_DEAD_PROXY_PORT;
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }
}
