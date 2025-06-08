package org.telegram.messenger.partisan.secretgroups;

import androidx.annotation.NonNull;

import org.telegram.messenger.DialogObject;

import java.util.Arrays;
import java.util.Optional;

public class InnerEncryptedChat {
    private final long userId;
    private Optional<Integer> encryptedChatId;
    private InnerEncryptedChatState state;

    public InnerEncryptedChat(long userId, @NonNull Optional<Integer> encryptedChatId) {
        this.userId = userId;
        this.encryptedChatId = encryptedChatId;
        this.state = InnerEncryptedChatState.CREATING_ENCRYPTED_CHAT;
    }

    public long getUserId() {
        return userId;
    }

    public Optional<Integer> getEncryptedChatId() {
        return encryptedChatId;
    }

    public Optional<Long> getDialogId() {
        return encryptedChatId.map(DialogObject::makeEncryptedDialogId);
    }

    public void setEncryptedChatId(int encryptedChatId) {
        this.encryptedChatId = Optional.of(encryptedChatId);
    }

    public InnerEncryptedChatState getState() {
        return state;
    }

    public void setState(InnerEncryptedChatState state) {
        this.state = state;
    }

    public boolean isInState(InnerEncryptedChatState... targetStates) {
        return Arrays.stream(targetStates).anyMatch(targetState -> state == targetState);
    }

    public boolean isNotInState(InnerEncryptedChatState... targetStates) {
        return Arrays.stream(targetStates).noneMatch(targetState -> state == targetState);
    }
}
