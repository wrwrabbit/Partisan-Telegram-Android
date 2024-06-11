package org.telegram.messenger.partisan.update;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.BuildVars;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppVersion {
    public int major;
    public int minor;
    public int patch;

    private static AppVersion currentVersion;

    public AppVersion() {}

    public AppVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static synchronized AppVersion getCurrentVersion() {
        if (currentVersion == null) {
            currentVersion = parseVersion(BuildVars.PARTISAN_VERSION_STRING, "(\\d+).(\\d+).(\\d+)");
        }
        return currentVersion;
    }

    public static synchronized AppVersion getCurrentOriginalVersion() {
        return parseVersion(BuildVars.BUILD_VERSION_STRING, "(\\d+).(\\d+).(\\d+)");
    }

    public static AppVersion parseVersion(String versionString, String regex) {
        return parseVersion(versionString, Pattern.compile(regex));
    }

    public static AppVersion parseVersion(String versionString, Pattern regex) {
        Matcher currentVersionMatcher = regex.matcher(versionString);
        if (currentVersionMatcher.find() && currentVersionMatcher.groupCount() >= 3) {
            return new AppVersion(
                    Integer.parseInt(currentVersionMatcher.group(1)),
                    Integer.parseInt(currentVersionMatcher.group(2)),
                    Integer.parseInt(currentVersionMatcher.group(3)));
        }
        return null;
    }

    public boolean greater(AppVersion other) {
        if (other == null) {
            return true;
        }
        return major > other.major || major == other.major && minor > other.minor
                || major == other.major && minor == other.minor && patch > other.patch;
    }

    public static boolean greater(AppVersion version, AppVersion other) {
        return version != null && version.greater(other);
    }

    public boolean greaterOrEquals(AppVersion other) {
        return this.greater(other) || this.equals(other);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        final AppVersion other = (AppVersion) obj;
        return this.major == other.major && this.minor == other.minor && this.patch == other.patch;
    }

    @NonNull
    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
