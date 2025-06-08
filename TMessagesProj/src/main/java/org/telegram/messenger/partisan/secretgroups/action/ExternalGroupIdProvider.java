package org.telegram.messenger.partisan.secretgroups.action;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface ExternalGroupIdProvider {
    long getExternalGroupId();
}
