package com.bb.bot.handler.pokemon;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bb.bot.api.BbMessageApi;
import com.bb.bot.common.annotation.BootEventHandler;
import com.bb.bot.common.annotation.Rule;
import com.bb.bot.common.constant.EventType;
import com.bb.bot.common.constant.RuleType;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.constant.BotType;
import com.bb.bot.entity.bb.BbMessageContent;
import com.bb.bot.entity.bb.BbReceiveMessage;
import com.bb.bot.entity.bb.BbSendMessage;
import com.bb.bot.handler.pokemon.entity.PokemonData;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 宝可梦事件处理器
 * @author ren
 */
@BootEventHandler(botType = BotType.BB, name = "宝可梦")
public class PokemonHandler {

    @Autowired
    private BbMessageApi bbMessageApi;

    @Autowired
    private ResourcesUtils resourcesUtils;

    private Map<String, List<Integer>> userPokemonMap = new HashMap<>();

    @SneakyThrows
    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?捕捉宝可梦"}, name = "捕捉宝可梦")
    public void getPokemonHandle(BbReceiveMessage bbReceiveMessage) {
        String pokemonJson = new String(resourcesUtils.getStaticResourceToByte("pokemon/pokemon_data.json"), StandardCharsets.UTF_8);
        List<PokemonData> pokemonDataList = JSON.parseObject(pokemonJson, new TypeReference<List<PokemonData>>() {});

        Random random = new Random();
        int index = random.nextInt(pokemonDataList.size());

        List<Integer> pokemonList = userPokemonMap.getOrDefault(bbReceiveMessage.getUserId(), new ArrayList<>());
        pokemonList.add(index);
        if (pokemonList.size() > 2) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("最多只能捕捉到两只宝可梦哟，继续捕捉请先杂交")));
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }
        userPokemonMap.put(bbReceiveMessage.getUserId(), pokemonList);

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("捕捉到宝可梦：" + pokemonDataList.get(index).getName()),
                BbMessageContent.buildLocalImageMessageContent(resourcesUtils.getStaticResource("pokemon/origin/" + pokemonDataList.get(index).getId() + ".png"))));
        bbMessageApi.sendMessage(bbSendMessage);
    }

    @Rule(eventType = EventType.MESSAGE, needAtMe = true, ruleType = RuleType.REGEX, keyword = {"^/?杂交宝可梦"}, name = "杂交宝可梦")
    @SneakyThrows
    public void combinePokemonHandle(BbReceiveMessage bbReceiveMessage) {
        List<Integer> pokemonList = userPokemonMap.get(bbReceiveMessage.getUserId());
        if (CollectionUtils.isEmpty(pokemonList) || pokemonList.size() != 2) {
            BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
            bbSendMessage.setMessageList(Arrays.asList(
                    BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                    BbMessageContent.buildTextContent("捕捉的宝可梦未满两只，无法杂交")));
            bbMessageApi.sendMessage(bbSendMessage);
            return;
        }

        userPokemonMap.put(bbReceiveMessage.getUserId(), new ArrayList<>());

        String beforeNameJson = new String(resourcesUtils.getStaticResourceToByte("pokemon/before_name.json"), StandardCharsets.UTF_8);
        List<PokemonData> beforeNameList = JSON.parseObject(beforeNameJson, new TypeReference<List<PokemonData>>() {});

        String afterNameJson = new String(resourcesUtils.getStaticResourceToByte("pokemon/after_name.json"), StandardCharsets.UTF_8);
        List<PokemonData> afterNameList = JSON.parseObject(afterNameJson, new TypeReference<List<PokemonData>>() {});

        Integer id = pokemonList.get(0);
        Integer id2 = pokemonList.get(1);

        BbSendMessage bbSendMessage = new BbSendMessage(bbReceiveMessage);
        bbSendMessage.setMessageList(Arrays.asList(
                BbMessageContent.buildAtMessageContent(bbReceiveMessage.getUserId()),
                BbMessageContent.buildTextContent("恭喜你，杂交出新宝可梦【" + beforeNameList.get(id).getName() + afterNameList.get(id2).getName() + "】！！"),
                BbMessageContent.buildLocalImageMessageContent(resourcesUtils.getStaticResource("pokemon/combine/" + afterNameList.get(id2).getId() + "-" + beforeNameList.get(id).getId() + ".png"))));
        bbMessageApi.sendMessage(bbSendMessage);
    }
}
