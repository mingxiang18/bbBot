package com.bb.bot.database.generateUtil;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

public class GenerateUtil {

    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/bb_bot", "root", "root")
                .globalConfig(builder -> builder
                        .author("misu")
                        .outputDir("D:\\develop\\bot\\code")
                        .commentDate("yyyy-MM-dd")
                )
                .packageConfig(builder -> builder
                        .parent("com.bb.bot.database.aiKeywordAndClue")
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .xml("mapper.xml")
                )
                .strategyConfig(builder -> builder
                        .addInclude("ai_keyword_clue")
                        .entityBuilder()
                        .enableLombok()
                )
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
