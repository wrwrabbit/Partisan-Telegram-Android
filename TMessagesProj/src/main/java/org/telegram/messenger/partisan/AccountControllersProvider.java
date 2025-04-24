package org.telegram.messenger.partisan;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
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

    default AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(getAccountNum());
    }

    default NotificationsController getNotificationsController() {
        return NotificationsController.getInstance(getAccountNum());
    }

    default SendMessagesHelper getSendMessagesHelper() {
        return SendMessagesHelper.getInstance(getAccountNum());
    }

    default MediaDataController getMediaDataController() {
        return MediaDataController.getInstance(getAccountNum());
    }
}
