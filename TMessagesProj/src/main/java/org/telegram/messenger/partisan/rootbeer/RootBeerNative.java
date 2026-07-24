/*
 * Vendored from RootBeer 0.1.2 (https://github.com/scottyab/rootbeer), Apache-2.0.
 * Upstream loaded a dedicated libtoolChecker.so; here the two native methods
 * are compiled into the main tmessages library (jni/partisan/rootbeer/toolChecker.cpp),
 * so there is no separate .so in the APK. "Loaded" therefore tracks the tmessages lib.
 */

package org.telegram.messenger.partisan.rootbeer;

import org.telegram.messenger.NativeLoader;

public class RootBeerNative {

    public boolean wasNativeLibraryLoaded() {
        return NativeLoader.loaded();
    }

    public native int checkForRoot(Object[] pathArray);

    public native int setLogDebugMessages(boolean logDebugMessages);

}
