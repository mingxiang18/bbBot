package com.bb.bot.handler.pokemon.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 宝可梦数据实体
 * @author ren
 */
@Data
@AllArgsConstructor
public class PokemonData {
    /**
     * id
     */
    private int id;
    /**
     * 名称
     */
    private String name;
}
