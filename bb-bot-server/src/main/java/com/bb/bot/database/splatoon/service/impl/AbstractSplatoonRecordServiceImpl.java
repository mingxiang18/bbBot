package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.common.util.ResourcesUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 斯普拉遁记录 Service 的公共基类。
 *
 * <p>承载对战(battle)/打工(coop) ServiceImpl 中同构的「把 JSON 节点里的图片 url 下载落到本地静态目录」
 * 骨架逻辑（T5.4 上提，源自 T6.1/T6.2 在各 impl 内抽好的 {@code saveGearResource}/{@code saveWeaponResource}）。
 *
 * <p>两边的差异仅在于：静态资源子目录前缀（category）、文件名来源、以及图片 url 所在的 JSON 键
 * （对战装备走 {@code originalImage}，其余多数走 {@code image}）。这些差异通过方法参数外置，
 * 骨架（null 防御 + 路径拼接 {@code nso_splatoon/{category}/{name}.png} + 委托 {@link ResourcesUtils}）保持单一实现。
 *
 * @param <M> MyBatis-Plus Mapper 类型
 * @param <T> 实体类型
 */
public abstract class AbstractSplatoonRecordServiceImpl<M extends BaseMapper<T>, T>
        extends ServiceImpl<M, T> {

    @Autowired
    protected ResourcesUtils resourcesUtils;

    /**
     * 静态资源根前缀，所有斯普拉遁图片资源都落在此目录下。
     */
    protected static final String RESOURCE_ROOT = "nso_splatoon/";

    /**
     * 把一个 JSON 节点里的图片资源下载并落到本地静态目录的同构骨架。
     *
     * <p>等价于：{@code getOrAddStaticResourceFromNet("nso_splatoon/" + category + "/" + name + ".png",
     * node.getJSONObject(imageKey).getString("url"))}，并在 {@code node} 为 null 时跳过。
     *
     * @param category 资源子目录（如 {@code user/gear}、{@code coop/weapon}）
     * @param name     文件名（不含后缀，落库为 {@code {name}.png}）
     * @param node     承载图片信息的 JSON 节点；为 null 时不做任何操作
     * @param imageKey 图片 url 所在的子对象键（对战装备为 {@code originalImage}，其余多为 {@code image}）
     */
    protected void saveResourceFromImage(String category, String name, JSONObject node, String imageKey) {
        if (node == null) {
            return;
        }
        resourcesUtils.getOrAddStaticResourceFromNet(
                RESOURCE_ROOT + category + "/" + name + ".png",
                node.getJSONObject(imageKey).getString("url"));
    }
}
