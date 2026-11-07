package com.macro.mall.service.impl;

import com.macro.mall.MallAdminApplication;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.service.PmsProductAttributeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * E3-03 跨分类批量删除属性：MySQL 真库验收。
 *
 * 规格 AC4 要求“ID 全部不存在时返回 0 且不删除、不更新分类”在 Mock 与 MySQL 各有一例；
 * 其余用例补充跨分类、同分类规格+参数、重复+缺失 ID 在真实 SQL / 外键无关场景下的行为。
 * 测试方法整体 @Transactional，结束回滚，不污染 mall 样例库。
 */
@SpringBootTest(
        classes = MallAdminApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:mysql://127.0.0.1:13306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                "spring.datasource.username=root",
                "spring.datasource.password=",
                "spring.datasource.druid.initial-size=1",
                "spring.datasource.druid.min-idle=1",
                "logging.level.root=warn",
                "logging.level.com.macro.mall=warn"
        })
@Transactional
@EnabledIfSystemProperty(named = "e3.mysql", matches = "true")
class PmsProductAttributeDeleteMysqlTest {

    @Autowired
    private PmsProductAttributeService productAttributeService;
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;

    @Test
    void crossCategoryBatchDeleteUpdatesEachCategoryInRealDatabase() {
        PmsProductAttributeCategory first = insertCategory(1, 0);
        PmsProductAttributeCategory second = insertCategory(1, 0);
        PmsProductAttribute firstAttribute = insertAttribute(first.getId(), 0);
        PmsProductAttribute secondAttribute = insertAttribute(second.getId(), 0);

        int deleted = productAttributeService.delete(
                Arrays.asList(firstAttribute.getId(), secondAttribute.getId()));

        assertEquals(2, deleted);
        assertEquals(Integer.valueOf(0), category(first.getId()).getAttributeCount());
        assertEquals(Integer.valueOf(0), category(second.getId()).getAttributeCount());
        assertNull(productAttributeMapper.selectByPrimaryKey(firstAttribute.getId()));
        assertNull(productAttributeMapper.selectByPrimaryKey(secondAttribute.getId()));
    }

    @Test
    void sameCategorySpecAndParamDecrementSeparateCountersInRealDatabase() {
        PmsProductAttributeCategory category = insertCategory(1, 1);
        PmsProductAttribute spec = insertAttribute(category.getId(), 0);
        PmsProductAttribute param = insertAttribute(category.getId(), 1);

        int deleted = productAttributeService.delete(Arrays.asList(spec.getId(), param.getId()));

        assertEquals(2, deleted);
        assertEquals(Integer.valueOf(0), category(category.getId()).getAttributeCount());
        assertEquals(Integer.valueOf(0), category(category.getId()).getParamCount());
    }

    @Test
    void duplicateAndMissingIdsFollowDeletedRowsInRealDatabase() {
        PmsProductAttributeCategory category = insertCategory(3, 0);
        PmsProductAttribute a1 = insertAttribute(category.getId(), 0);
        insertAttribute(category.getId(), 0);
        insertAttribute(category.getId(), 0);
        PmsProductAttributeCategory other = insertCategory(1, 0);
        PmsProductAttribute b1 = insertAttribute(other.getId(), 0);

        int deleted = productAttributeService.delete(
                Arrays.asList(a1.getId(), a1.getId(), 999999L, b1.getId()));

        assertEquals(2, deleted);
        assertEquals(Integer.valueOf(2), category(category.getId()).getAttributeCount());
        assertEquals(Integer.valueOf(0), category(other.getId()).getAttributeCount());
        assertNull(productAttributeMapper.selectByPrimaryKey(a1.getId()));
        assertNull(productAttributeMapper.selectByPrimaryKey(b1.getId()));
    }

    @Test
    void allMissingIdsReturnZeroWithoutDeletingOrUpdatingInRealDatabase() {
        PmsProductAttributeCategory category = insertCategory(2, 3);
        PmsProductAttribute existing = insertAttribute(category.getId(), 0);

        int deleted = productAttributeService.delete(Arrays.asList(999998L, 999999L));

        assertEquals(0, deleted);
        assertEquals(Integer.valueOf(2), category(category.getId()).getAttributeCount());
        assertEquals(Integer.valueOf(3), category(category.getId()).getParamCount());
        assertEquals(existing.getId(), productAttributeMapper.selectByPrimaryKey(existing.getId()).getId());
    }

    private PmsProductAttributeCategory insertCategory(int attributeCount, int paramCount) {
        PmsProductAttributeCategory category = new PmsProductAttributeCategory();
        category.setName("E3-MYSQL-" + System.nanoTime());
        category.setAttributeCount(attributeCount);
        category.setParamCount(paramCount);
        productAttributeCategoryMapper.insertSelective(category);
        return category;
    }

    private PmsProductAttribute insertAttribute(Long categoryId, int type) {
        PmsProductAttribute attribute = new PmsProductAttribute();
        attribute.setProductAttributeCategoryId(categoryId);
        attribute.setName("E3-MYSQL-ATTR-" + System.nanoTime());
        attribute.setType(type);
        productAttributeMapper.insertSelective(attribute);
        return attribute;
    }

    private PmsProductAttributeCategory category(Long id) {
        return productAttributeCategoryMapper.selectByPrimaryKey(id);
    }
}
