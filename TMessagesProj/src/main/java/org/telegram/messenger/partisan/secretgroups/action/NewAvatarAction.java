package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

import java.util.Arrays;
import java.util.Objects;

public class NewAvatarAction extends EncryptedGroupAction {
    public static final int constructor = 0x37572575;

    public byte[] avatarBytes;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        avatarBytes = stream.readByteArray(exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeByteArray(avatarBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NewAvatarAction)) return false;
        NewAvatarAction that = (NewAvatarAction) o;
        return Objects.deepEquals(avatarBytes, that.avatarBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(avatarBytes);
    }
}
