package com.macro.mall.service.impl;

import com.macro.mall.dao.PmsProductAttributeDao;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.model.PmsProductAttributeExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E3-03 规格测试：跨分类批量删除属性时按“分类 + 类型”分别扣减计数。
 * 该测试同时可在修复前/后运行：修复前走 selectByPrimaryKey，修复后走 selectByExample。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PmsProductAttributeDeleteSpecTest {

    @Mock
    private PmsProductAttributeMapper productAttributeMapper;
    @Mock
    private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    @Mock
    private PmsProductAttributeDao productAttributeDao;

    @InjectMocks
    private PmsProductAttributeServiceImpl service;

    private final Map<Long, PmsProductAttribute> attributeTable = new LinkedHashMap<>();
    private final Map<Long, PmsProductAttributeCategory> categoryTable = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        attributeTable.clear();
        categoryTable.clear();
        when(productAttributeMapper.selectByPrimaryKey(anyLong()))
                .thenAnswer(invocation -> attributeTable.get(invocation.getArgument(0, Long.class)));
        when(productAttributeMapper.selectByExample(any(PmsProductAttributeExample.class)))
                .thenAnswer(invocation -> selectRows(invocation.getArgument(0)));
        when(productAttributeMapper.deleteByExample(any(PmsProductAttributeExample.class)))
                .thenAnswer(invocation -> deleteRows(invocation.getArgument(0)));
        when(productAttributeCategoryMapper.selectByPrimaryKey(anyLong()))
                .thenAnswer(invocation -> categoryTable.get(invocation.getArgument(0, Long.class)));
        when(productAttributeCategoryMapper.updateByPrimaryKey(any(PmsProductAttributeCategory.class)))
                .thenAnswer(invocation -> {
                    PmsProductAttributeCategory category = invocation.getArgument(0);
                    categoryTable.put(category.getId(), category);
                    return 1;
                });
    }

    @Test
    @DisplayName("跨分类批量删除：每个分类各扣各的，不能只扣第一个分类")
    void mixedCategoryBatchDecrementsEachCategoryOnce() {
        category(10L, 1, 0);
        attribute(101L, 10L, 0);
        category(20L, 1, 0);
        attribute(201L, 20L, 0);

        int deleted = service.delete(List.of(101L, 201L));

        assertEquals(2, deleted, "应返回实际删除的 2 行");
        assertCategoryCounts(10L, 0, 0);
        assertCategoryCounts(20L, 0, 0);
        assertEquals(0, attributeTable.size(), "两条属性都应已删除");
    }

    @Test
    @DisplayName("同一分类同时删规格和参数：两个计数分别扣减")
    void sameCategorySpecAndParamDecrementSeparateCounters() {
        category(30L, 1, 1);
        attribute(301L, 30L, 0);
        attribute(302L, 30L, 1);

        int deleted = service.delete(List.of(301L, 302L));

        assertEquals(2, deleted);
        assertCategoryCounts(30L, 0, 0);
        assertEquals(0, attributeTable.size());
    }

    @Test
    @DisplayName("重复 ID 只算一次，不存在的 ID 不扣减，返回实际删除行数")
    void duplicateAndMissingIdsFollowDeletedRows() {
        category(40L, 3, 0);
        attribute(401L, 40L, 0);
        attribute(402L, 40L, 0);
        attribute(403L, 40L, 0);
        category(50L, 1, 0);
        attribute(501L, 50L, 0);

        int deleted = service.delete(List.of(401L, 401L, 999L, 501L));

        assertEquals(2, deleted);
        assertCategoryCounts(40L, 2, 0);
        assertCategoryCounts(50L, 0, 0);
        assertEquals(2, attributeTable.size(), "只应删掉 401 与 501，402/403 保留");
    }

    @Test
    @DisplayName("ID 全部不存在：返回 0，不删除、不更新分类")
    void allMissingIdsReturnZeroWithoutWriting() {
        category(60L, 2, 3);

        int deleted = service.delete(List.of(999L, 1000L));

        assertEquals(0, deleted);
        assertCategoryCounts(60L, 2, 3);
        verify(productAttributeMapper, never()).deleteByExample(any());
        verify(productAttributeCategoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    @DisplayName("空 ID 列表：返回 0，不访问任何 mapper")
    void emptyIdsReturnZeroWithoutTouchingMappers() {
        int deleted = service.delete(List.of());

        assertEquals(0, deleted);
        verify(productAttributeMapper, never()).selectByPrimaryKey(anyLong());
        verify(productAttributeMapper, never()).selectByExample(any());
        verify(productAttributeMapper, never()).deleteByExample(any());
        verify(productAttributeCategoryMapper, never()).selectByPrimaryKey(anyLong());
        verify(productAttributeCategoryMapper, never()).updateByPrimaryKey(any());
    }

    @Test
    @DisplayName("回归：同一个 ID 传两次只删一次、只扣一")
    void duplicateOnlyIdsAreCountedOnce() {
        category(70L, 2, 0);
        attribute(701L, 70L, 0);
        attribute(702L, 70L, 0);

        int deleted = service.delete(List.of(701L, 701L));

        assertEquals(1, deleted);
        assertCategoryCounts(70L, 1, 0);
        assertEquals(1, attributeTable.size());
    }

    @Test
    @DisplayName("回归：计数已为 0 时删除属性，计数保持 0 不出现负数")
    void countsNeverGoNegative() {
        category(80L, 0, 0);
        attribute(801L, 80L, 0);

        int deleted = service.delete(List.of(801L));

        assertEquals(1, deleted);
        assertCategoryCounts(80L, 0, 0);
    }

    private void attribute(long id, long categoryId, int type) {
        PmsProductAttribute attribute = new PmsProductAttribute();
        attribute.setId(id);
        attribute.setProductAttributeCategoryId(categoryId);
        attribute.setType(type);
        attributeTable.put(id, attribute);
    }

    private void category(long id, int attributeCount, int paramCount) {
        PmsProductAttributeCategory category = new PmsProductAttributeCategory();
        category.setId(id);
        category.setAttributeCount(attributeCount);
        category.setParamCount(paramCount);
        categoryTable.put(id, category);
    }

    private void assertCategoryCounts(long id, int attributeCount, int paramCount) {
        PmsProductAttributeCategory category = categoryTable.get(id);
        assertNotNull(category, "分类 " + id + " 应存在");
        assertEquals(Integer.valueOf(attributeCount), category.getAttributeCount(),
                "分类 " + id + " 的 attribute_count");
        assertEquals(Integer.valueOf(paramCount), category.getParamCount(),
                "分类 " + id + " 的 param_count");
    }

    private List<PmsProductAttribute> selectRows(PmsProductAttributeExample example) {
        List<Long> ids = idInValues(example);
        List<PmsProductAttribute> rows = new ArrayList<>();
        if (ids == null) {
            rows.addAll(attributeTable.values());
            return rows;
        }
        for (Long id : new LinkedHashSet<>(ids)) {
            PmsProductAttribute row = attributeTable.get(id);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private int deleteRows(PmsProductAttributeExample example) {
        List<Long> ids = idInValues(example);
        if (ids == null) {
            int size = attributeTable.size();
            attributeTable.clear();
            return size;
        }
        int removed = 0;
        for (Long id : new LinkedHashSet<>(ids)) {
            if (attributeTable.remove(id) != null) {
                removed++;
            }
        }
        return removed;
    }

    @SuppressWarnings("unchecked")
    private List<Long> idInValues(PmsProductAttributeExample example) {
        for (PmsProductAttributeExample.Criteria criteria : example.getOredCriteria()) {
            for (PmsProductAttributeExample.Criterion criterion : criteria.getCriteria()) {
                if ("id in".equals(criterion.getCondition())) {
                    return (List<Long>) criterion.getValue();
                }
            }
        }
        return null;
    }
}
