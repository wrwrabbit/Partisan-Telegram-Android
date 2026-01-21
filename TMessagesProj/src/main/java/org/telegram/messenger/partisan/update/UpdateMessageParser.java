package org.telegram.messenger.partisan.update;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.partisan.PartisanLog;
import org.telegram.messenger.partisan.Utils;
import org.telegram.tgnet.TLRPC;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class UpdateMessageParser {
    private UpdateData currentUpdate;
    private MessageObject currentMessage;
    private String lang = "en";
    private int langInaccuracy = 0;
    private MaskedUpdateType currentMaskedUpdateType = MaskedUpdateType.ALLOW;

    private final int currentAccount;
    private final Map<Long, List<MessageObject>> messagesByGroupId = new HashMap<>();

    public UpdateMessageParser(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public UpdateData processMessage(MessageObject message) {
        currentMaskedUpdateType = MaskedUpdateType.ALLOW;
        saveMessageByGroupId(message);
        return parseUpdateData(message);
    }

    private void saveMessageByGroupId(MessageObject message) {
        if (message.getGroupId() == 0) {
            return;
        }
        PartisanLog.d("UpdateChecker: save message by group id");
        if (!messagesByGroupId.containsKey(message.getGroupId())) {
            messagesByGroupId.put(message.getGroupId(), new ArrayList<>());
        }
        List<MessageObject> messages = messagesByGroupId.get(message.getGroupId());
        if (messages != null) {
            messages.add(message);
        }
    }

    private UpdateData parseUpdateData(MessageObject message) {
        if (!isUpdateSpecificationMessage(message)) {
            PartisanLog.d("UpdateChecker: don't need to parse message");
            return null;
        }
        MessageObject fileMessage = findFileMessage(message);
        if (fileMessage != null) {
            createUpdateData(message, fileMessage);
            return tryParseText(message.messageText);
        } else {
            PartisanLog.d("UpdateChecker: file message was null");
            return null;
        }
    }

    private static boolean isUpdateSpecificationMessage(MessageObject message) {
        return message.isReply()
                && message.replyMessageObject.getDocument() != null
                && message.messageText != null;
    }

    private MessageObject findFileMessage(MessageObject descriptionMessage) {
        MessageObject replyMessage = descriptionMessage.replyMessageObject;
        if (replyMessage == null) {
            return null;
        }
        if (isTargetDocument(replyMessage.getDocument())) {
            return replyMessage;
        } else if (replyMessage.getGroupId() != 0) {
            List<MessageObject> messagesFromGroup = messagesByGroupId.get(replyMessage.getGroupId());
            if (messagesFromGroup != null) {
                for (MessageObject message : messagesFromGroup) {
                    if (isTargetDocument(message.getDocument())) {
                        return message;
                    }
                }
            }
            return null;
        } else {
            return null;
        }
    }

    private void createUpdateData(MessageObject message, MessageObject fileMessage) {
        currentUpdate = new UpdateData();
        currentUpdate.accountNum = currentAccount;
        currentUpdate.message = fileMessage.messageOwner;
        currentUpdate.document = fileMessage.getDocument();
        currentMessage = message;
    }

    private boolean isTargetDocument(TLRPC.Document document) {
        return document != null
                && document.file_name_fixed != null
                && document.file_name_fixed.equals(getTargetFileName());
    }

    private String getTargetFileName() {
        return ApplicationLoader.isRealBuildStandaloneBuild() ? "PTelegram.apk" : "PTelegram_GooglePlay.apk";
    }

    private UpdateData tryParseText(CharSequence text) {
        try {
            return parseText(text);
        } catch (Exception e) {
            PartisanLog.e("UpdateChecker: message parsing error", e);
            return null;
        }
    }

    private UpdateData parseText(CharSequence text) {
        boolean isFirstCharInNewLine = true;
        boolean controlLine = true;
        int blockStart = 0;
        lang = "en";
        langInaccuracy = Integer.MAX_VALUE;
        for (int pos = 0; pos <= text.length(); pos++) {
            boolean textEnd = pos == text.length();
            char currentChar = !textEnd ? text.charAt(pos) : '\0';
            boolean controlLineBeginning = isFirstCharInNewLine && currentChar == '#';
            boolean lineEnd = currentChar == '\n';
            boolean controlLineEnding = (lineEnd || textEnd) && controlLine;
            boolean descriptionEnding = (controlLineBeginning || textEnd && !controlLine) && blockStart < pos;
            if (descriptionEnding) {
                processDescription(text, blockStart, pos);
            }
            if (controlLineEnding) {
                processControlLine(text.subSequence(blockStart, pos).toString());
            }
            if (controlLineBeginning || controlLineEnding) {
                controlLine = controlLineBeginning;
                blockStart = pos + 1;
            }
            isFirstCharInNewLine = lineEnd;
        }
        if (currentMaskedUpdateType == MaskedUpdateType.ONLY) {
            currentUpdate = null;
        }
        return currentUpdate;
    }

    private void processDescription(CharSequence text, int start, int end) {
        int inaccuracy = getLangInaccuracy(lang);
        if (inaccuracy < langInaccuracy) {
            currentUpdate.text = text.subSequence(start, end).toString();
            addMessageEntities(start, end);
            langInaccuracy = inaccuracy;
        }
    }

    private int getLangInaccuracy(String lang) {
        if (lang.equals(LocaleController.getInstance().getCurrentLocale().getLanguage())) {
            return 0;
        } else if (lang.equals("ru") && Utils.isRussianAppLanguage()) {
            return 1;
        } else if (lang.equals("en")) {
            return 2;
        } else {
            return 3;
        }
    }

    private void addMessageEntities(int start, int end) {
        currentUpdate.entities.clear();
        for (TLRPC.MessageEntity entity : currentMessage.messageOwner.entities) {
            if (start <= entity.offset && entity.offset < end) {
                TLRPC.MessageEntity newEntity = cloneMessageEntity(entity);
                if (newEntity != null) {
                    newEntity.offset -= start;
                    if (newEntity.length > end - start) {
                        newEntity.length = end - start;
                    }
                    currentUpdate.entities.add(newEntity);
                }
            }
        }
    }

    private static TLRPC.MessageEntity cloneMessageEntity(TLRPC.MessageEntity entity) {
        try {
            Class<?> clazz = entity.getClass();
            Object result = clazz.newInstance();
            for (Field field : clazz.getFields()) {
                field.set(result, field.get(entity));
            }
            return (TLRPC.MessageEntity)result;
        } catch (Exception ignore) {
            return null;
        }
    }

    private void processControlLine(String command) {
        String[] parts = command.split("=");
        String name = parts[0];
        String value = parts.length == 2 ? parts[1] : null;
        if (name.equals("version") || name.equals("appVersion")) {
            currentUpdate.version = AppVersion.parseVersion(value);
        } else if (name.equals("originalVersion")) {
            currentUpdate.originalVersion = AppVersion.parseVersion(value);
        } else if (name.equals("canNotSkip")) {
            currentUpdate.canNotSkip = value == null || value.equals("true");
        } else if (name.equals("lang")) {
            lang = value;
        } else if (name.equals("url")) {
            currentUpdate.url = value;
        } else if (name.equals("sticker")) {
            String[] stickerValueParts = value.split(",");
            if (stickerValueParts.length == 2) {
                currentUpdate.stickerPackName = stickerValueParts[0];
                currentUpdate.stickerEmoji = stickerValueParts[1];
            }
        } else if (name.equals("formatVersion")) {
            if (value != null) {
                try {
                    currentUpdate.formatVersion = Integer.parseInt(value);
                } catch (NumberFormatException ignore) {
                }
            }
        } else if (name.equals("masked")) {
            if ("allow".equalsIgnoreCase(value)) {
                currentMaskedUpdateType = MaskedUpdateType.ALLOW;
            } else if ("prohibit".equalsIgnoreCase(value)) {
                currentMaskedUpdateType = MaskedUpdateType.PROHIBIT;
            } else if ("only".equalsIgnoreCase(value)) {
                currentMaskedUpdateType = MaskedUpdateType.ONLY;
            } else {
                currentMaskedUpdateType = MaskedUpdateType.ALLOW;
            }
        }
    }
}
