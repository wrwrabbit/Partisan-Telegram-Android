package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.messenger.partisan.secretgroups.EncryptedGroupState;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCreateGroupAction extends EncryptedGroupAction {
    public long externalGroupId;
    public String name;
    public List<Long> memberIds = new ArrayList<>();
    public Long ownerUserId;

    public abstract EncryptedGroupState getInitialEncryptedGroupState();
}
