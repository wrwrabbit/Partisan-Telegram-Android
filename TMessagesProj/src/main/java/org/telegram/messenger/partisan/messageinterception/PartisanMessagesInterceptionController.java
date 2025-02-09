package org.telegram.messenger.partisan.messageinterception;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PartisanMessagesInterceptionController {
    private List<MessageInterceptor> interceptors = new ArrayList<>();

    private static final PartisanMessagesInterceptionController instance = new PartisanMessagesInterceptionController();

    private PartisanMessagesInterceptionController() {
        interceptors.add(new FakePasscodeActivationInterceptor());
        interceptors.add(new HiddenByPasscodeMessageInterceptor());
        interceptors.add(new NotInitializedEncryptedGroupMessagesInterceptor());
    }

    public static PartisanMessagesInterceptionController getInstance() {
        return instance;
    }

    public synchronized void addInterceptor(MessageInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public synchronized void removeInterceptor(MessageInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    public static InterceptionResult intercept(int accountNum, TLRPC.Message message) {
        return instance.interceptInternal(accountNum, message);
    }

    private InterceptionResult interceptInternal(int accountNum, TLRPC.Message message) {
        InterceptionResult finalResult = new InterceptionResult(false);
        for (MessageInterceptor interceptor : interceptors) {
            InterceptionResult newResult = interceptor.interceptMessage(accountNum, message);
            finalResult = finalResult.merge(newResult);
        }
        return finalResult;
    }

    public static ArrayList<TLRPC.Message> filterMessages(int accountNum, ArrayList<TLRPC.Message> arr) {
        return arr.stream()
                .filter(message -> PartisanMessagesInterceptionController.intercept(accountNum, message).isAllowMessageSaving())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
