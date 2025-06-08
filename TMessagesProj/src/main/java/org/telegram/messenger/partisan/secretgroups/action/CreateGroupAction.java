package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.messenger.partisan.secretgroups.EncryptedGroupState;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class CreateGroupAction extends AbstractCreateGroupAction {
    public static final int constructor = 0x44498b93;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        externalGroupId = stream.readInt64(exception);
        name = stream.readString(exception);
        ownerUserId = stream.readInt64(exception);
        int count = stream.readInt32(exception);
        for (int i = 0; i < count; i++) {
            memberIds.add(stream.readInt64(exception));
        }
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);

        stream.writeInt64(externalGroupId);
        stream.writeString(name);
        stream.writeInt64(ownerUserId);
        int count = memberIds.size();
        stream.writeInt32(count);
        for (int i = 0; i < count; i++) {
            stream.writeInt64(memberIds.get(i));
        }
    }

    @Override
    public EncryptedGroupState getInitialEncryptedGroupState() {
        return EncryptedGroupState.JOINING_NOT_CONFIRMED;
    }
}
