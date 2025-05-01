package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

import java.util.Objects;

public class AddMemberAction extends EncryptedGroupAction {
    public static final int constructor = 0x84176b82;

    public long userId;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        userId = stream.readInt64(exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt64(userId);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AddMemberAction)) return false;
        AddMemberAction that = (AddMemberAction) o;
        return userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}
