package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class ConfirmGroupInitializationAction extends EncryptedGroupAction {
    public static final int constructor = 0xb9a8d756;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {

    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
    }
}
