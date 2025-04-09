package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class DeleteMemberAction extends EncryptedGroupAction {
    public static final int constructor = 0x3af1dd30;

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
}
