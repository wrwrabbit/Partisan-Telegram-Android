package org.telegram.messenger.partisan.fileprotection;

public class RecentSearchCache extends AbstractFileProtectionExceptionCache {
    @Override
    protected String getLoadSqlQuery() {
        return "SELECT did FROM search_recent";
    }
}
