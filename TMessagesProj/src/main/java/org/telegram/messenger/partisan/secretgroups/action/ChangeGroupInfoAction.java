package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class ChangeGroupInfoAction extends EncryptedGroupAction {
    public static final int constructor = 0x1147fcc3;

    public int flags;
    public String name;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        flags = stream.readInt32(exception);
        name = stream.readString(exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt32(flags);
        stream.writeString(name);
    }
}
