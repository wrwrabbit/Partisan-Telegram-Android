package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.partisan.AccountControllersProvider;

public abstract class AccountAction implements Action, AccountControllersProvider {
    @JsonIgnore
    protected int accountNum = 0;

    @JsonProperty(value = "accountNum", access = JsonProperty.Access.WRITE_ONLY)
    public void setAccountNum(int accountNum) {
        this.accountNum = accountNum;
    }

    @Override
    public int getAccountNum() {
        return accountNum;
    }
}
