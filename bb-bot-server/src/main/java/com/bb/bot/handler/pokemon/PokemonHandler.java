package com.bb.bot.handler.pokemon;

import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.BbReplies;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.handler.pokemon.engine.PokemonCollectionStore;
import com.bb.bot.handler.pokemon.engine.PokemonEngine;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 宝可梦 handler：抓取 / 杂交 走 {@link PokemonEngine}，玩家收藏持久化在 {@link PokemonCollectionStore}。
 * handler 只负责消息构造与文件路径计算。
 *
 * @author ren
 */
@Slf4j
@BootEventHandler(botType = BotType.BB, name = "宝可梦")
public class PokemonHandler {

    private static final int MAX_OWNED = 2;

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private BbReplies bbReplies;

    @Autowired
    private ResourcesUtils resourcesUtils;

    @Autowired
    private PokemonEngine engine;

    @Autowired
    private PokemonCollectionStore collectionStore;

    private final Random random = new Random();

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?捕捉宝可梦"}, name = "捕捉宝可梦")
    public void capture(BbReceiveMessage msg) {
        if (!engine.isAvailable()) {
            bbReplies.atText(msg, "宝可梦数据未就绪");
            return;
        }
        List<Integer> collection = collectionStore.load(msg.getUserId());
        PokemonEngine.Outcome outcome = engine.capture(collection, MAX_OWNED, random);

        switch (outcome.getType()) {
            case FULL -> bbReplies.atText(msg, "最多只能捕捉到两只宝可梦哟，继续捕捉请先杂交");
            case CAPTURED -> {
                collectionStore.save(msg.getUserId(), outcome.getUpdatedCollection());
                BbSendMessage out = new BbSendMessage(msg);
                out.setMessageList(Arrays.asList(
                        BbMessageContent.buildAtMessageContent(msg.getUserId()),
                        BbMessageContent.buildTextContent("捕捉到宝可梦：" + outcome.getCaptured().getName()),
                        BbMessageContent.buildLocalImageMessageContent(
                                resourcesUtils.getStaticResource("pokemon/origin/" + outcome.getCaptured().getId() + ".png"))));
                bbMessageApi.sendMessage(out);
            }
            default -> bbReplies.atText(msg, "捕捉失败");
        }
    }

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX,
            keyword = {"^/?杂交宝可梦"}, name = "杂交宝可梦")
    public void breed(BbReceiveMessage msg) {
        if (!engine.isAvailable()) {
            bbReplies.atText(msg, "宝可梦数据未就绪");
            return;
        }
        List<Integer> collection = collectionStore.load(msg.getUserId());
        PokemonEngine.Outcome outcome = engine.breed(collection);

        switch (outcome.getType()) {
            case NOT_ENOUGH, UNAVAILABLE -> bbReplies.atText(msg, "捕捉的宝可梦未满两只，无法杂交");
            case BRED -> {
                collectionStore.save(msg.getUserId(), outcome.getUpdatedCollection());
                String combinedName = outcome.getBreedFrom().getName() + outcome.getBreedTo().getName();
                String imagePath = "pokemon/combine/" + outcome.getBreedTo().getId() + "-"
                        + outcome.getBreedFrom().getId() + ".png";
                BbSendMessage out = new BbSendMessage(msg);
                out.setMessageList(Arrays.asList(
                        BbMessageContent.buildAtMessageContent(msg.getUserId()),
                        BbMessageContent.buildTextContent("恭喜你，杂交出新宝可梦【" + combinedName + "】！！"),
                        BbMessageContent.buildLocalImageMessageContent(resourcesUtils.getStaticResource(imagePath))));
                bbMessageApi.sendMessage(out);
            }
            default -> bbReplies.atText(msg, "杂交失败");
        }
    }
}
