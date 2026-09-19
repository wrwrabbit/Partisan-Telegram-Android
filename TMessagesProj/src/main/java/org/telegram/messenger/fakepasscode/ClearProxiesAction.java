package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.SharedConfig;

import java.util.ArrayList;
import java.util.List;

@FakePasscodeSerializer.EnabledSerialization
public class ClearProxiesAction implements Action {
    public boolean enabled = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (!enabled) {
            return;
        }
        List<SharedConfig.ProxyInfo> proxies = new ArrayList<>(SharedConfig.proxyList);
        for (SharedConfig.ProxyInfo proxy : proxies) {
            SharedConfig.deleteProxy(proxy);
        }
    }
}