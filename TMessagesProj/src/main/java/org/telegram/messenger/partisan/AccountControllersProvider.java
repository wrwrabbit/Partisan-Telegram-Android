package org.telegram.messenger.partisan;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroupProtocol;
import org.telegram.tgnet.ConnectionsManager;

public interface AccountControllersProvider {
    int getAccountNum();

    default UserConfig getUserConfig() {
        return UserConfig.getInstance(getAccountNum());
    }

    default ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(getAccountNum());
    }

    default MessagesStorage getMessagesStorage() {
        return MessagesStorage.getInstance(getAccountNum());
    }

    default MessagesController getMessagesController() {
        return MessagesController.getInstance(getAccountNum());
    }

    default NotificationCenter getNotificationCenter() {
        return NotificationCenter.getInstance(getAccountNum());
    }

    default SecretChatHelper getSecretChatHelper() {
        return SecretChatHelper.getInstance(getAccountNum());
    }

    default EncryptedGroupProtocol getEncryptedGroupProtocol() {
        return new EncryptedGroupProtocol(getAccountNum());
    }
}
