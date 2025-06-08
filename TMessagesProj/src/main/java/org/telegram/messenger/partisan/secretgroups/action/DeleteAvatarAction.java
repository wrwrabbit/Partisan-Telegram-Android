package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

public class DeleteAvatarAction extends EncryptedGroupAction {
    public static final int constructor = 0x85100ce8;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DeleteAvatarAction;
    }
}
