# E3-03 跨分类批量删除属性的计数 —— 规格与验收准则

## 1. 用户故事

作为商城后台的商品运营人员，我需要在商品属性列表里勾选任意多条属性（可能来自不同属性分类，也可能同时包含规格和参数）后一次删除；系统必须按每条被删属性真实的“所属分类 + 类型”分别扣减分类上的 `attribute_count` / `param_count`，不能把整批数量都算到第一条属性所在的分类上。

## 2. 现状与缺陷（Issue #3）

`PmsProductAttributeServiceImpl#delete(List<Long> ids)` 当前只查 `ids.get(0)` 得到一条属性，以它的分类与类型代表整批：

- 跨分类删除时，只有第一条属性所在分类被扣减，其它分类计数不扣；
- 同一分类同时删规格与参数时，只扣其中一种计数；
- 列表中有不存在的 ID 时，返回的删除行数可能大于真实删除数，扣减也会多算；
- `ids` 全部不存在、或 `ids` 为空时，分别触发空指针 / 下标越界异常。

## 3. 验收准则（以 Issue #3 为准）

- AC1：每个被删除的属性只扣减它自己的 `product_attribute_category_id` 对应分类、且按它自己的 `type`（0=规格、1=参数）扣减对应计数。
- AC2：任一分类的 `attribute_count` / `param_count` 扣减后不得小于 0。
- AC3：重复 ID 只计一次；不存在的 ID 不产生扣减；方法返回数据库实际删除的行数。
- AC4：ID 全部不存在时返回 0，且不执行删除、不更新任何分类（Mock 与 MySQL 各验证一例）。
- AC5：空列表（含 null 列表）返回 0，不访问任何 mapper。

## 4. 不得修改的部分（硬约束）

- 不修改数据库表结构、字段与初始化数据（`document/sql/mall.sql`）。
- 不修改 MyBatis Generator 生成的 `PmsProductAttributeMapper` / `PmsProductAttributeCategoryMapper` / `PmsProductAttributeExample` 及 model 类。
- 不修改 `PmsProductAttributeService` 接口签名与 `PmsProductAttributeController` 的 HTTP 契约（`POST /productAttribute/delete`）。
- 不改变 `create` 的既有计数语义；本次只修 `delete`。

## 5. 规格测试（先行编写，改前应失败）

1. `mixedCategoryBatchDecrementsEachCategoryOnce`：跨两个分类各删一条规格，两分类都归 0；基线只扣第一个分类 → 红。
2. `sameCategorySpecAndParamDecrementSeparateCounters`：同一分类删一条规格 + 一条参数，两个计数都归 0；基线只扣规格计数 → 红。
3. `duplicateAndMissingIdsFollowDeletedRows`：重复 ID + 不存在 ID 混合，返回 2，只扣真实存在的两条；基线按首条属性扣 2 → 红。
4. `allMissingIdsReturnZeroWithoutWriting`：全部不存在，返回 0，不删除、不更新分类；基线 NPE → 红。
5. `emptyIdsReturnZeroWithoutTouchingMappers`：空列表返回 0，不访问 mapper；基线下标越界 → 红。
6. `duplicateOnlyIdsAreCountedOnce`：同一 ID 传两次只删一次、只扣一；改前改后都应为绿（回归）。
7. `countsNeverGoNegative`：计数已为 0 时删除一条，仍为 0；改前改后都应为绿（回归）。
8. MySQL 集成测试：真实数据库、真实事务中验证 AC1–AC4（另见 `PmsProductAttributeDeleteMysqlTest`）。

## 6. 验证边界（明确不证明什么）

- 不验证并发删除下的计数竞争（无行锁/乐观锁设计）。
- 不验证 `@Transactional` 代理在异常回滚时的行为（单元测试直接 new service，集成测试用测试事务）。
- 不验证 HTTP 认证/鉴权链，只验证 service 方法契约。
- 不覆盖 `type` 为 null 或其它值时的业务语义（规格未定义，行为保持“不扣减任何计数”）。
