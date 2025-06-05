package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fakepasscode.RemoveChatsAction;
import org.telegram.messenger.partisan.AccountControllersProvider;
import org.telegram.messenger.partisan.secretgroups.EncryptedGroup;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.Optional;
import java.util.stream.Collectors;

public abstract class Item implements AccountControllersProvider {
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
            } else {
                EncryptedGroup encryptedGroup = messagesController.getEncryptedGroup(encryptedChatId);
                if (encryptedGroup != null) {
                    return new EncryptedGroupItem(accountNum, encryptedGroup);
                }
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

    public void applyAvatar(BackupImageView avatarImageView, AvatarDrawable avatarDrawable) {
        if (getProfileObject() != null) {
            avatarDrawable.setInfo(accountNum, getProfileObject());
            avatarImageView.setForUserOrChat(getProfileObject(), avatarDrawable);
        } else {
            avatarDrawable.setInfo(getId(), getDisplayName().toString(), "");
            avatarImageView.setForUserOrChat(null, avatarDrawable);
        }
    }

    public OptionPermission getDeletePermission() {
        return OptionPermission.ALLOW;
    }

    public OptionPermission getDeleteFromCompanionPermission() {
        return isUserId() && !isSelf() || isEncryptedDialogId() ? OptionPermission.ALLOW : OptionPermission.DENY;
    }

    public OptionPermission getDeleteNewMessagesPermission() {
        if (isUserId() && !isSelf()) {
            return OptionPermission.ALLOW;
        } else if (isEncryptedDialogId()) {
            return OptionPermission.INDIFFERENT;
        } else {
            return OptionPermission.DENY;
        }
    }

    public OptionPermission getDeleteAllMyMessagesPermission() {
        return isChatId() ? OptionPermission.ALLOW : OptionPermission.DENY;
    }

    public OptionPermission getHidingPermission() {
        return !isSelf() ? OptionPermission.ALLOW : OptionPermission.DENY;
    }

    public OptionPermission getStrictHidingPermission() {
        return getHidingPermission();
    }

    private boolean isUserId() {
        return DialogObject.isUserDialog(getId());
    }

    private boolean isChatId() {
        return DialogObject.isChatDialog(getId());
    }

    private boolean isEncryptedDialogId() {
        return DialogObject.isEncryptedDialog(getId());
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
