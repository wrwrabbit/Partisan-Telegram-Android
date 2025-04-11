package org.telegram.messenger.partisan.secretgroups.action;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.TLObject;

public abstract class EncryptedGroupAction extends TLObject {

    public static EncryptedGroupAction TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
        EncryptedGroupAction result = null;
        switch (constructor) {
            case 0x44498b93:
                result = new CreateGroupAction();
                break;
            case 0xdd765d82:
                result = new ConfirmJoinAction();
                break;
            case 0xb9a8d756:
                result = new ConfirmGroupInitializationAction();
                break;
            case 0x19fbd964:
                result = new StartSecondaryInnerChatAction();
                break;
            case 0x6ffdc230:
                result = new AllSecondaryChatsInitializedAction();
                break;
            case 0xcdc18852:
                result = new GroupCreationFailedAction();
                break;
            case 0x1147fcc3:
                result = new ChangeGroupInfoAction();
                break;
            case 0x3af1dd30:
                result = new DeleteMemberAction();
                break;
            case 0x37572575:
                result = new NewAvatarAction();
                break;
        }
        if (result == null && exception) {
            throw new RuntimeException(String.format("can't parse magic %x in EncryptedGroupAction", constructor));
        }
        if (result != null) {
            result.readParams(stream, exception);
        }
        return result;
    }
}
