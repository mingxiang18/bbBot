package com.bb.bot.handler.stardew;

import java.util.List;

public interface StardewWikiClient {

    List<StardewWikiPage> search(String query, int maxResults);
}
