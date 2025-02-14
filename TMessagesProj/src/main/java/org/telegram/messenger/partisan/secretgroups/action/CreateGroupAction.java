package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

import java.util.ArrayList;
import java.util.List;

public class CreateGroupAction extends EncryptedGroupAction {
    public static final int constructor = 0x44498b93;

    public long externalGroupId;
    public String name;
    public List<Long> memberIds = new ArrayList<>();
    public Long ownerUserId;

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
}
