package org.telegram.messenger.partisan.secretgroups;

import org.telegram.messenger.partisan.secretgroups.action.EncryptedGroupAction;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.TLRPC;

public class EncryptedGroupsServiceMessage extends TLRPC.DecryptedMessage {
    public static final int constructor = 0xaff56539;
    public EncryptedGroupAction encryptedGroupAction;

    public void readParams(AbstractSerializedData stream, boolean exception) {
        random_id = stream.readInt64(exception);
        encryptedGroupAction = EncryptedGroupAction.TLdeserialize(stream, stream.readInt32(exception), exception);
    }

    public void serializeToStream(AbstractSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt64(random_id);
        encryptedGroupAction.serializeToStream(stream);
    }
}
