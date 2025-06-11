package org.telegram.messenger.partisan;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class KnownChatUsernameResolver implements AccountControllersProvider {
    public interface KnownChatUsernameResolverDelegate {
        void onResolved(boolean success);
    }

    private final int accountNum;
    private final String username;
    private final long knownId;
    private final KnownChatUsernameResolverDelegate delegate;

    public KnownChatUsernameResolver(int accountNum, String username, long knownId, KnownChatUsernameResolverDelegate delegate) {
        this.accountNum = accountNum;
        this.username = username;
        this.knownId = knownId;
        this.delegate = delegate;
    }

    public static void resolveUsername(int accountNum, String username, long knownId, KnownChatUsernameResolverDelegate callback) {
        new KnownChatUsernameResolver(accountNum, username, knownId, callback).resolveUsernameInternal();
    }

    private void resolveUsernameInternal() {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        getConnectionsManager().sendRequest(req, this::onChatUsernameResolved);
    }

    private void onChatUsernameResolved(TLObject response, TLRPC.TL_error error) {
        if (isValidUsernameResponse(response, knownId)) {
            saveResolvedChats(response);
            PartisanLog.d(
                    "[FindMessages] username " + username + " resolved." +
                            " delete messages from chatId " + knownId);
            delegate.onResolved(true);
        } else {
            PartisanLog.d("[FindMessages] username " + username + " resolving failed.");
            delegate.onResolved(false);
        }
    }

    private static boolean isValidUsernameResponse(TLObject response, Long targetChatId) {
        if (response == null) {
            return false;
        }
        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
        long peerId = MessageObject.getPeerId(res.peer);
        return peerId == targetChatId;
    }

    private void saveResolvedChats(TLObject response) {
        TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
        getMessagesController().putUsers(res.users, false);
        getMessagesController().putChats(res.chats, false);
        getMessagesStorage().putUsersAndChats(res.users, res.chats, false, true);
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
