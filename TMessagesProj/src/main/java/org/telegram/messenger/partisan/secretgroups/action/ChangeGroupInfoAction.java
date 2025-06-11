package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;

import java.util.Objects;

public class ChangeGroupInfoAction extends EncryptedGroupAction {
    public static final int constructor = 0x1147fcc3;

    public static final int FLAG_NAME = 0x01;

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChangeGroupInfoAction)) return false;
        ChangeGroupInfoAction that = (ChangeGroupInfoAction) o;
        return flags == that.flags && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags, name);
    }
}
