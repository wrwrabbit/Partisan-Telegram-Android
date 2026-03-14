package org.telegram.messenger.fakepasscode;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.fakepasscode.results.HideAccountResult;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.Stories.LivePlayer;

@FakePasscodeSerializer.ToggleSerialization
public class HideAccountAction extends AccountAction {
    public boolean strictHiding;

    @Override
    public void execute(FakePasscode fakePasscode) {
        if (fakePasscode.replaceOriginalPasscode) {
            return;
        }
        fakePasscode.actionsResult.hiddenAccountEntries.add(new HideAccountResult(accountNum, strictHiding));
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.accountHidingChanged);
        getNotificationsController().removeAllNotifications();

        cancelCurrentStoryStream();
    }

    private void cancelCurrentStoryStream() {
        long dialogId = getUserConfig().clientUserId;
        TL_stories.PeerStories userStories = getStoriesController().getStories(dialogId);
        for (TL_stories.StoryItem storyItem : userStories.stories) {
            if (storyItem == null || !(storyItem.media instanceof TLRPC.TL_messageMediaVideoStream)) {
                continue;
            }
            TLRPC.InputGroupCall call = ((TLRPC.TL_messageMediaVideoStream) storyItem.media).call;
            if (call != null) {
                if (LivePlayer.recording != null && LivePlayer.recording.equals(call)) {
                    LivePlayer.recording.destroy();
                    if (LivePlayer.recording != null) {
                        LivePlayer.recording = null;
                    }
                }
                getStoriesController().deleteStory(dialogId, storyItem);
                getNotificationCenter().postNotificationName(NotificationCenter.liveStoryUpdated, call.id);
            }
        }
    }
}
