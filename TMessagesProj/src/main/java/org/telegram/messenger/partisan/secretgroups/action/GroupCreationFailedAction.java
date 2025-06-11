package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class GroupCreationFailedAction extends EncryptedGroupAction {
    public static final int constructor = 0xcdc18852;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {

    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
    }
}
