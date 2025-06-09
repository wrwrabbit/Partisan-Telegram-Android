package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.messenger.partisan.secretgroups.EncryptedGroupState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class AbstractCreateGroupAction extends EncryptedGroupAction implements ExternalGroupIdProvider {
    public long externalGroupId;
    public String name;
    public List<Long> memberIds = new ArrayList<>();
    public Long ownerUserId;

    public abstract EncryptedGroupState getInitialEncryptedGroupState();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AbstractCreateGroupAction)) return false;
        AbstractCreateGroupAction that = (AbstractCreateGroupAction) o;
        return externalGroupId == that.externalGroupId && Objects.equals(name, that.name) && Objects.equals(memberIds, that.memberIds) && Objects.equals(ownerUserId, that.ownerUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalGroupId, name, memberIds, ownerUserId);
    }

    @Override
    public long getExternalGroupId() {
        return externalGroupId;
    }
}
