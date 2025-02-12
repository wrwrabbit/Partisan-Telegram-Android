package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class ConfirmJoinAction extends EncryptedGroupAction {
    public static final int constructor = 0xdd765d82;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {

    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
    }
}
