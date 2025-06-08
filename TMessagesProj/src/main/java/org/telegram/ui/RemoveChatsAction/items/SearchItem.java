package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.tgnet.TLObject;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

import java.util.Optional;

public class SearchItem extends Item {
    Item innerItem;
    CharSequence displayNameOverride;
    CharSequence statusOverride;

    SearchItem(Item innerItem, CharSequence displayNameOverride, CharSequence statusOverride) {
        super(innerItem.accountNum);
        this.innerItem = innerItem;
        this.displayNameOverride = displayNameOverride;
        this.statusOverride = statusOverride;
    }

    @Override
    public TLObject getProfileObject() {
        return innerItem.getProfileObject();
    }

    @Override
    public long getId() {
        return innerItem.getId();
    }

    @Override
    protected String getName() {
        return innerItem.getName();
    }

    @Override
    public CharSequence getDisplayName() {
        return displayNameOverride != null
                ? displayNameOverride
                : innerItem.getDisplayName();
    }

    @Override
    public String getUsername() {
        return innerItem.getUsername();
    }

    @Override
    protected CharSequence generateSearchName(String q) {
        return null;
    }

    @Override
    public boolean isSelf() {
        return innerItem.isSelf();
    }

    @Override
    public boolean shouldBeEditedToo(Item selectedItem) {
        return innerItem.shouldBeEditedToo(selectedItem);
    }

    @Override
    public CharSequence getStatus() {
        return statusOverride != null
                ? statusOverride
                : innerItem.getStatus();
    }

    @Override
    public void applyAvatar(BackupImageView avatarImageView, AvatarDrawable avatarDrawable) {
        innerItem.applyAvatar(avatarImageView, avatarDrawable);
    }

    @Override
    public OptionPermission getDeletePermission() {
        return innerItem.getDeletePermission();
    }

    @Override
    public OptionPermission getDeleteFromCompanionPermission() {
        return innerItem.getDeleteFromCompanionPermission();
    }

    @Override
    public OptionPermission getDeleteNewMessagesPermission() {
        return innerItem.getDeleteNewMessagesPermission();
    }

    @Override
    public OptionPermission getDeleteAllMyMessagesPermission() {
        return innerItem.getDeleteAllMyMessagesPermission();
    }

    @Override
    public OptionPermission getHidingPermission() {
        return innerItem.getHidingPermission();
    }

    @Override
    public OptionPermission getStrictHidingPermission() {
        return innerItem.getStrictHidingPermission();
    }
}
