package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.stream.Collectors;

public abstract class Item {
    protected final int accountNum;

    public abstract TLObject getProfileObject();
    public abstract long getId();
    protected abstract String getName();
    protected String getAlternativeName() {
        return null;
    }
    public CharSequence getDisplayName() {
        return UserConfig.getChatTitleOverride(accountNum, getId(), getName());
    }
    public abstract String getUsername();
    protected abstract CharSequence generateSearchName(String q);
    public boolean isSelf() {
        return false;
    }
    public boolean shouldBeEditedToo(Item selectedItem) {
        return false;
    }

    protected Item(int accountNum) {
        this.accountNum = accountNum;
    }

    public static Item tryCreateItemById(int accountNum, RemoveChatsAction action, Long id) {
        MessagesController messagesController = MessagesController.getInstance(accountNum);
        if (DialogObject.isUserDialog(id)) {
            TLRPC.User user = messagesController.getUser(id);
            if (user != null) {
                return new UserItem(accountNum, user);
            }
        } else if (DialogObject.isChatDialog(id)) {
            TLRPC.Chat chat = messagesController.getChat(-id);
            if (chat != null) {
                return new ChatItem(accountNum, chat);
            }
        } else if (DialogObject.isEncryptedDialog(id)) {
            int encryptedChatId = DialogObject.getEncryptedChatId(id);
            TLRPC.EncryptedChat encryptedChat = messagesController.getEncryptedChat(encryptedChatId);
            if (encryptedChat != null) {
                return new EncryptedChatItem(accountNum, encryptedChat);
            }
        }
        RemoveChatsAction.RemoveChatEntry removeChatEntry = action.get(id);
        return removeChatEntry != null ? new RemoveChatEntryItem(accountNum, removeChatEntry) : null;
    }

    public CharSequence getStatus() {
        String status = getMessagesController().dialogFilters
                .stream()
                .filter(f -> f.includesDialog(getAccountInstance(), getId()))
                .map(f -> f.name)
                .collect(Collectors.joining(", "));
        if ("".contentEquals(status)) {
            if (getMessagesController().getAllDialogs().stream().noneMatch(d -> d.id == getId())) {
                status = LocaleController.getString(R.string.ChatRemoved);
            }
        }
        return status;
    }

    private String getSearchName() {
        return getName();
    }

    public SearchItem search(String query) {
        if (matchesQueryByName(query)) {
            return new SearchItem(this, generateSearchName(query), null);
        } else if (matchesQueryByUsername(query)) {
            return new SearchItem(this, null, generateSearchUsername(query));
        } else {
            return null;
        }
    }

    private CharSequence generateSearchUsername(String query) {
        return AndroidUtilities.generateSearchName("@" + getUsername(), null, "@" + query);
    }

    private boolean matchesQueryByName(String query) {
        if (nameMatches(getSearchName(), query)) {
            return true;
        }
        String translitName = LocaleController.getInstance().getTranslitString(getSearchName());
        if (nameMatches(translitName, query)) {
            return true;
        }
        return nameMatches(getAlternativeName(), query);
    }

    private boolean matchesQueryByUsername(String query) {
        String username = getUsername();
        return username != null && username.toLowerCase().startsWith(query.toLowerCase());
    }

    private boolean nameMatches(String name, String query) {
        if (name == null || query == null) {
            return false;
        }
        String lowercaseName = name.toLowerCase();
        String lowercaseQuery = query.toLowerCase();
        return lowercaseName.startsWith(lowercaseQuery)
                || lowercaseName.contains(" " + lowercaseQuery);
    }

    private AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(accountNum);
    }

    protected MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    protected UserConfig getUserConfig() {
        return getAccountInstance().getUserConfig();
    }
}
