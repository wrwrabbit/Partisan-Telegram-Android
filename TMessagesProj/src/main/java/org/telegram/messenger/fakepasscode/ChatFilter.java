package org.telegram.messenger.fakepasscode;

import org.telegram.tgnet.TLRPC;

public interface ChatFilter {
    boolean isRemoveNewMessagesFromChat(long chatId);
    boolean isHideChat(long chatId, boolean strictHiding);
    default boolean isHideChat(long chatId) {
        return isHideChat(chatId, false);
    }
    default boolean isHidePeer(TLRPC.Peer peer, boolean strictHiding) {
        return isHideChat(peer.chat_id, strictHiding)
                || isHideChat(peer.channel_id, strictHiding)
                || isHideChat(peer.user_id, strictHiding);
    }
    default boolean isHidePeer(TLRPC.Peer peer) {
        return isHidePeer(peer, false);
    }
    boolean isHideFolder(int folderId);
}
