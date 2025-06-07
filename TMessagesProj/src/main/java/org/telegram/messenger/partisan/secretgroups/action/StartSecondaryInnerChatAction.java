package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class StartSecondaryInnerChatAction extends EncryptedGroupAction {
    public static final int constructor = 0x19fbd964;

    @ExternalGroupIdProvider()
    public long externalGroupId;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        externalGroupId = stream.readInt64(exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);

        stream.writeInt64(externalGroupId);
    }
}
