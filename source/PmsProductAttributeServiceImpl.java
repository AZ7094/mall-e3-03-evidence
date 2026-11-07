package com.macro.mall.service.impl;

import com.github.pagehelper.PageHelper;
import com.macro.mall.dao.PmsProductAttributeDao;
import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.dto.ProductAttrInfo;
import com.macro.mall.mapper.PmsProductAttributeCategoryMapper;
import com.macro.mall.mapper.PmsProductAttributeMapper;
import com.macro.mall.model.PmsProductAttribute;
import com.macro.mall.model.PmsProductAttributeCategory;
import com.macro.mall.model.PmsProductAttributeExample;
import com.macro.mall.service.PmsProductAttributeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 商品属性管理Service实现类
 * Created by macro on 2018/4/26.
 */
@Service
public class PmsProductAttributeServiceImpl implements PmsProductAttributeService {
    @Autowired
    private PmsProductAttributeMapper productAttributeMapper;
    @Autowired
    private PmsProductAttributeCategoryMapper productAttributeCategoryMapper;
    @Autowired
    private PmsProductAttributeDao productAttributeDao;

    @Override
    public List<PmsProductAttribute> getList(Long cid, Integer type, Integer pageSize, Integer pageNum) {
        PageHelper.startPage(pageNum, pageSize);
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.setOrderByClause("sort desc");
        example.createCriteria().andProductAttributeCategoryIdEqualTo(cid).andTypeEqualTo(type);
        return productAttributeMapper.selectByExample(example);
    }

    @Override
    public int create(PmsProductAttributeParam pmsProductAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        BeanUtils.copyProperties(pmsProductAttributeParam, pmsProductAttribute);
        int count = productAttributeMapper.insertSelective(pmsProductAttribute);
        //新增商品属性以后需要更新商品属性分类数量
        PmsProductAttributeCategory pmsProductAttributeCategory = productAttributeCategoryMapper.selectByPrimaryKey(pmsProductAttribute.getProductAttributeCategoryId());
        if(pmsProductAttribute.getType()==0){
            pmsProductAttributeCategory.setAttributeCount(pmsProductAttributeCategory.getAttributeCount()+1);
        }else if(pmsProductAttribute.getType()==1){
            pmsProductAttributeCategory.setParamCount(pmsProductAttributeCategory.getParamCount()+1);
        }
        productAttributeCategoryMapper.updateByPrimaryKey(pmsProductAttributeCategory);
        return count;
    }

    @Override
    public int update(Long id, PmsProductAttributeParam productAttributeParam) {
        PmsProductAttribute pmsProductAttribute = new PmsProductAttribute();
        pmsProductAttribute.setId(id);
        BeanUtils.copyProperties(productAttributeParam, pmsProductAttribute);
        return productAttributeMapper.updateByPrimaryKeySelective(pmsProductAttribute);
    }

    @Override
    public PmsProductAttribute getItem(Long id) {
        return productAttributeMapper.selectByPrimaryKey(id);
    }

    @Override
    public int delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        //重复 ID 只算一次，null 不参与查询
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(ids));
        distinctIds.removeIf(Objects::isNull);
        if (distinctIds.isEmpty()) {
            return 0;
        }
        PmsProductAttributeExample example = new PmsProductAttributeExample();
        example.createCriteria().andIdIn(distinctIds);
        //先取实际存在的属性：不存在的 ID 不扣减，全部不存在时不产生任何写操作
        List<PmsProductAttribute> attributes = productAttributeMapper.selectByExample(example);
        if (attributes.isEmpty()) {
            return 0;
        }
        //按“分类 + 类型”分别汇总，规格进 attributeCountMap，参数进 paramCountMap
        Map<Long, Integer> attributeCountMap = new HashMap<>();
        Map<Long, Integer> paramCountMap = new HashMap<>();
        for (PmsProductAttribute attribute : attributes) {
            Long categoryId = attribute.getProductAttributeCategoryId();
            if (categoryId == null) {
                continue;
            }
            if (Integer.valueOf(0).equals(attribute.getType())) {
                attributeCountMap.merge(categoryId, 1, Integer::sum);
            } else if (Integer.valueOf(1).equals(attribute.getType())) {
                paramCountMap.merge(categoryId, 1, Integer::sum);
            }
        }
        int count = productAttributeMapper.deleteByExample(example);
        //删除完成后逐分类扣减，只更新确实有计数变化的分类
        Set<Long> categoryIds = new LinkedHashSet<>();
        categoryIds.addAll(attributeCountMap.keySet());
        categoryIds.addAll(paramCountMap.keySet());
        for (Long categoryId : categoryIds) {
            PmsProductAttributeCategory category = productAttributeCategoryMapper.selectByPrimaryKey(categoryId);
            if (category == null) {
                continue;
            }
            Integer attributeCount = attributeCountMap.get(categoryId);
            if (attributeCount != null) {
                category.setAttributeCount(decrease(category.getAttributeCount(), attributeCount));
            }
            Integer paramCount = paramCountMap.get(categoryId);
            if (paramCount != null) {
                category.setParamCount(decrease(category.getParamCount(), paramCount));
            }
            productAttributeCategoryMapper.updateByPrimaryKey(category);
        }
        return count;
    }

    private int decrease(Integer current, int delta) {
        if (current == null) {
            return 0;
        }
        return current >= delta ? current - delta : 0;
    }

    @Override
    public List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId) {
        return productAttributeDao.getProductAttrInfo(productCategoryId);
    }
}
