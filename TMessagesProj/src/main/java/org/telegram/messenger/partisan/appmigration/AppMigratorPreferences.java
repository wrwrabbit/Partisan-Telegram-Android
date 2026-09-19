package org.telegram.messenger.partisan.appmigration;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public class AppMigratorPreferences {
    private static Step step;
    private static Long maxCancelledInstallationDate;
    private static String installedMaskedPtgPackageName;
    private static String installedMaskedPtgPackageSignature;
    private static String migratedPackageName;
    private static Long migratedDate;

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    public static synchronized void setStep(Step step) {
        Step oldStep = getStep(); // initialize old step if not initialized
        AppMigratorPreferences.step = step;
        if (oldStep.simplify() != step.simplify()) {
            getPrefs().edit()
                    .putString("ptgMigrationStep", step.simplify().toString())
                    .apply();
        }
    }

    public static synchronized Step getStep() {
        if (step == null) {
            String stepStr = getPrefs().getString("ptgMigrationStep", Step.NOT_STARTED.toString());
            step = Step.valueOf(stepStr);
        }
        return step;
    }

    public static boolean isConnectionDisabled() {
        return getPrefs().getBoolean("ptgMigrationConnectionDisabled", false);
    }

    public static void setConnectionDisabled(boolean disabled) {
        getPrefs().edit()
                .putBoolean("ptgMigrationConnectionDisabled", disabled)
                .commit();
    }

    public static synchronized long getMaxCancelledInstallationDate() {
        if (maxCancelledInstallationDate == null) {
            maxCancelledInstallationDate = getPrefs()
                    .getLong("ptgMigrationMaxCancelledInstallationDate", 0);
        }
        return maxCancelledInstallationDate;
    }

    public static void updateMaxCancelledInstallationDate() {
        maxCancelledInstallationDate = System.currentTimeMillis();
        getPrefs().edit()
                .putLong("ptgMigrationMaxCancelledInstallationDate", maxCancelledInstallationDate)
                .apply();
    }

    public static synchronized String getMigratedPackageName() {
        if (migratedPackageName == null) {
            migratedPackageName = getPrefs()
                    .getString("migratedPackageName", "");
        }
        return migratedPackageName;
    }

    public static synchronized long getMigratedDate() {
        if (migratedDate == null) {
            migratedDate = getPrefs()
                    .getLong("migratedDate", 0);
        }
        return migratedDate;
    }

    public static void setMigrationFinished(String packageName) {
        migratedPackageName = packageName;
        getPrefs().edit()
                .putString("migratedPackageName", migratedPackageName)
                .apply();

        migratedDate = System.currentTimeMillis();
        getPrefs().edit()
                .putLong("migratedDate", migratedDate)
                .apply();
    }

    public static void resetMigrationFinished() {
        migratedPackageName = null;
        getPrefs().edit()
                .remove("migratedPackageName")
                .apply();

        migratedDate = null;
        getPrefs().edit()
                .remove("migratedDate")
                .apply();
    }

    public static boolean isMigrationToMaskedPtg() {
        return getInstalledMaskedPtgPackageName() != null;
    }

    public static synchronized String getInstalledMaskedPtgPackageName() {
        if (installedMaskedPtgPackageName == null) {
            installedMaskedPtgPackageName = getPrefs()
                    .getString("installedMaskedPtgPackageName", null);
        }
        return installedMaskedPtgPackageName;
    }

    public static void setInstalledMaskedPtgPackageName(String packageName) {
        installedMaskedPtgPackageName = packageName;
        getPrefs().edit()
                .putString("installedMaskedPtgPackageName", installedMaskedPtgPackageName)
                .apply();
    }

    public static synchronized String getInstalledMaskedPtgPackageSignature() {
        if (installedMaskedPtgPackageSignature == null) {
            installedMaskedPtgPackageSignature = getPrefs()
                    .getString("installedMaskedPtgPackageSignature", null);
        }
        return installedMaskedPtgPackageSignature;
    }

    public static void setInstalledMaskedPtgPackageSignature(String packageName) {
        installedMaskedPtgPackageSignature = packageName;
        getPrefs().edit()
                .putString("installedMaskedPtgPackageSignature", installedMaskedPtgPackageSignature)
                .apply();
    }
}
