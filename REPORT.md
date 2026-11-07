# E3-03　跨分类批量删除属性的计数：规格先行与 AI 结对记录

> 课程作业 E3 ｜ 选题 Issue [meng004/mall#3](https://github.com/meng004/mall/issues/3) ｜ 基线 `dcaa93b` ｜ 测试提交 `4968126` ｜ 修复提交 `1c07a36` ｜ 提交人：________　学号：________

## 0. 结论速览

基线 `PmsProductAttributeServiceImpl.delete` 只按 `ids.get(0)` 的第一条属性所属分类和类型，把整批删除行数一次性扣到一个计数列；跨分类、同分类规格+参数、重复/缺失 ID、全不存在、空列表五类输入都会算错或抛异常。先写 7 个规格测试，在基线源码上得到 **7 tests, 3 failures, 2 errors**；最小修复后同一份测试 **7/7 通过**，测试文件 SHA-256 未变（diff 0 行）。另用独立 MySQL 9.2 实例做 4 例真库验收，**4/4 通过**。`delete` 行覆盖率 36/39=92.31%、分支 18/24=75.00%，只作记录，不设门槛。

## 1. 输入、期望、缺陷复现与实际修改位置

### 1.1 用户故事

后台运营在属性列表中勾选任意多条属性（可跨分类，也可同时含规格和参数）一次删除；系统必须按每条属性真实的 `(product_attribute_category_id, type)` 分别扣减分类表的 `attribute_count` / `param_count`，不能把整批数量都记到第一条属性所在分类。

### 1.2 输入与验收准则

输入：`POST /productAttribute/delete`，`ids: List<Long>`，可重复、可含不存在 ID、可空。

- **AC1** 每条被删属性只扣自己的分类与类型（type=0 扣 `attribute_count`，type=1 扣 `param_count`）。
- **AC2** 扣减后计数不小于 0。
- **AC3** 重复 ID 只算一次；不存在的 ID 不扣减；返回实际删除行数。
- **AC4** ID 全部不存在时返回 0，不执行删除，也不更新分类（Mock 与 MySQL 各一例）。
- **AC5** 空列表返回 0，且不访问任何 mapper；`ids == null` 由同一守卫短路，未单列断言（该分支在覆盖率中记为未覆盖）。

### 1.3 缺陷与复现

基线 `dcaa93b`，`PmsProductAttributeServiceImpl.java:71-95`：

- 第 73 行只取 `ids.get(0)`；
- 第 75 行只取第一条属性的分类；
- 第 78 行却删除整批；
- 第 80–93 行把整批 `count` 全扣到第一个分类，且只按第一条的 `type` 扣一种计数；
- 第 74 行在“ID 全不存在”时对 null 取 `getType()`，空指针；
- 第 73 行在空列表时 `ids.get(0)`，下标越界。

复现：cat10 有两条规格、cat20 有一条规格，删除 `[101,201]` 时 cat10 被多扣成 0、cat20 仍为 1；同分类删一规格一参数时只扣 `attribute_count`；ID 全不存在时 NPE；空列表时越界。

### 1.4 实际修改位置

只改 `mall-admin/src/main/java/com/macro/mall/service/impl/PmsProductAttributeServiceImpl.java`：`delete` 位于 77–129 行，新增私有 `decrease` 位于 131–136 行（提交 `1c07a36`，+58/−17）。

## 2. 调用 / 持久化数据流与不得修改的部分

### 2.1 调用链

`PmsProductAttributeController.java:79-80`（`POST /productAttribute/delete`）→ `PmsProductAttributeService.java:41-42`（`@Transactional int delete(List<Long>)`）→ 实现类去重并剔除 null → `selectByExample(andIdIn)` 取真实存在的属性 → `deleteByExample` 删行 → 按 `(categoryId,type)` 分两张 map → 逐分类 `selectByPrimaryKey` → `setAttributeCount/ParamCount(decrease(...))` → `updateByPrimaryKey`。

### 2.2 持久化语义

物品行按 ID 真删；分类表只更新这次确有行被删的 `attribute_count` / `param_count`，且用 `current >= delta ? current - delta : 0` 保证不低于 0。只动这两张表，不改表结构、不写历史表。

### 2.3 不得修改（本次均未改）

- `document/sql/mall.sql` 的表结构、字段与初始化数据；
- MyBatis Generator 生成的 `PmsProductAttributeMapper` / `PmsProductAttributeCategoryMapper` / `PmsProductAttributeExample` 与 model；
- `PmsProductAttributeService` 接口签名；
- controller 的 `POST /productAttribute/delete` 契约（`count>0` success，否则 failed）；
- `create` 的既有加计数语义。

修复提交 `1c07a36` 的 `--stat` 显示主代码只动了这一个 impl 文件，其余是证据文件。

## 3. 规格如何落到测试与 AI 协作

### 3.1 第一个失败测试固定了什么

`mixedCategoryBatchDecrementsEachCategoryOnce`：cat10、cat20 各一条 type=0 属性，计数各 1；删除 `[101,201]` 必须返回 2，两个分类的 `attribute_count` 都归 0。

改前必须失败的原因：基线只按第一条属性所属分类扣减，cat20 的 `attribute_count` 从 1 不变，而测试断言它应为 0。红日志原文为 `expected: <0> but was: <1>`。这正是 Issue 的核心缺陷，不是环境问题。

### 3.2 测试先行时间线（不是历史回放）

- **10:41:15** 在基线源码上跑新测试：7 tests、3 failures、2 errors（`logs/red-run-baseline.log`）；
- **10:41:34** 提交测试 `4968126`，只含 SPEC、测试与红日志；
- **10:43:35** 才提交修复 `1c07a36`；
- 随后同一份测试 7/7 通过（`logs/green-run.log`）。

提交时间、提交内容与红日志都在证据仓中，可核对。RUNBOOK 中从 `4968126` 重新 checkout 跑红，只是重建对照，不冒充当时首次运行。

### 3.3 7 个规格测试与基线结果

| # | 测试 | 固定的行为 | 基线结果 |
|---|---|---|---|
| 1 | `mixedCategoryBatchDecrementsEachCategoryOnce` | 跨分类各扣自己的数量 | **FAIL**：cat20 expected 0, was 1 |
| 2 | `sameCategorySpecAndParamDecrementSeparateCounters` | 同分类规格/参数各扣对应列 | **FAIL**：param_count expected 0, was 1 |
| 3 | `duplicateAndMissingIdsFollowDeletedRows` | 重复只一次、缺失不扣、返回真实行数 | **FAIL**：cat40 expected 2, was 1 |
| 4 | `allMissingIdsReturnZeroWithoutWriting` | 全缺失返回 0，不删、不更新分类 | **ERROR**：第 74 行 NPE |
| 5 | `emptyIdsReturnZeroWithoutTouchingMappers` | 空列表返回 0，不触 mapper | **ERROR**：第 73 行下标越界 |
| 6 | `duplicateOnlyIdsAreCountedOnce` | 重复 ID 只扣一次 | PASS（回归） |
| 7 | `countsNeverGoNegative` | 计数已为 0 时不为负 | PASS（回归） |

业务断言全部用 `assertEquals` 的具体数值；唯一的 `assertNotNull` 是测试辅助方法读取夹具时的前置条件，不用它冒充业务结果。

### 3.4 AI 初稿的采纳、驳回与人工修改

AI 初稿保存为 `logs/ai-draft-delete-service.java.txt`，单独运行结果为 7 tests、2 failures（`logs/ai-draft-run.log`）。

1. **采纳**“先去重并过滤 null、先查真实存在的行再决定扣减”——从根上解决“不存在 ID 不能扣”，也消除全不存在时的 NPE。
2. **驳回**“只有一张 `countMap`、统一扣 `attribute_count`”——`create` 里 type=0/1 分别加不同列，AC1 也要求按类型；初稿实测 `sameCategory...` 失败：`param_count expected: <0> but was: <1>`。
3. **驳回**“无条件先 `deleteByExample`”——AC4 要求全不存在时不删行；初稿实测 `allMissing...` 的 `verify(deleteByExample, never())` 失败。
4. **人工补强**：`categoryId == null` 跳过、分类查不到时跳过、`decrease` 对 `current == null` 返回 0、只更新确有计数变化的分类；依据是 DDL 中两列允许 NULL，且脏数据不应让批量删除整体 NPE。
5. **最小修复取舍**：不加锁、不改表、不新增接口；并发竞争与事务代理回滚明确留在“未验证”。

## 4. 验证结果与覆盖率记录

### 4.1 红绿与验收

| 验证 | 结果 | 证据 |
|---|---|---|
| 改前红（基线 + 规格测试） | 7 tests, 3 failures, 2 errors | [red-run-baseline.log](logs/red-run-baseline.log) |
| 改后绿（同一组测试） | 7/7，BUILD SUCCESS | [green-run.log](logs/green-run.log) |
| 断言未被放宽 | 两提交测试 SHA-256 均为 `1237d982…b81d5e`，diff 0 行 | [assertions-unchanged.log](logs/assertions-unchanged.log) |
| 边界/相邻行为回归 | 重复 ID 绿、计数下限绿、空列表绿 | 同上 |
| MySQL 真库验收 | 4/4，BUILD SUCCESS | [mysql-run.log](logs/mysql-run.log) |
| 目标方法覆盖率 | `delete` 行 36/39=92.31%、分支 18/24=75.00%；`decrease` 行 2/3、分支 3/4；两方法合计 38/42、21/28 | [coverage/COVERAGE.md](coverage/COVERAGE.md) |

### 4.2 失败路径确实经过本次修改的语句

红日志的 5 个失败全部是业务断言或业务路径异常：3 个 failure 发生在 `service.delete(...)` 返回后的分类计数断言；2 个 error 直接抛自 `delete` 内部（NPE 在第 74 行、下标越界在第 73 行）。没有编译、数据库或环境错误；构建失败仅由测试失败触发，未把环境问题当业务红灯。

修复把基线的 71–95 行改为现在的 77–129 行，主体 78–128 行正是把“首 ID、单分类、单计数”替换成按 `(categoryId,type)` 分组、逐分类更新。测试 4/5 另用 `verify(..., never())` 把边界钉在 mapper 调用层，避免只看返回值。

### 4.3 覆盖率证明了什么、没证明什么

- **证明**：7 个用例确实执行了 `delete` 主体和新增 `decrease`；业务路径的计数扣减、分类更新、早返回都被执行到。
- **未覆盖**：85 行（全 null 元素）、100 行（属性无分类）、116 行（分类不存在）、133 行（计数列为 NULL），以及 78 行 `ids == null` 的另一侧。它们都是规格未定义的防御分支，不作为缺陷断言。
- **不证明**：并发删除的计数竞争、`@Transactional` 代理在异常时的回滚、HTTP 鉴权链，也不证明 `type` 为 null 或 0/1 之外值时的业务语义。
- 类级行覆盖 39/64=60.94%，被本次未改动的 CRUD 方法拉低，不能用来评价这次修复。

## 5. 证据索引与复现

- 证据仓：<https://github.com/AZ7094/mall-e3-03-evidence>
- 规格：[SPEC.md](SPEC.md)｜复现手册：[RUNBOOK.md](RUNBOOK.md)｜录屏讲稿：[SCREENCAST-SCRIPT.md](SCREENCAST-SCRIPT.md)
- 改前/改后 patch：[patch/0001-test.patch](patch/0001-test.patch)、[patch/0002-fix.patch](patch/0002-fix.patch)
- 源码快照：[source/](source/)｜JaCoCo 原始产物：[coverage/](coverage/)｜一页说明 PDF：[REPORT.pdf](REPORT.pdf)
- 环境：JDK 17.0.20、Maven 3.9.16、MySQL 9.2.0（独立实例 127.0.0.1:13306，库 `mall`，76 张表）。MySQL 测试默认由系统属性 `e3.mysql=true` 关闭，避免无库环境误红。

复现命令见 RUNBOOK。教师示例的登录 / 改密链路未被沿用，本报告只检查实际修改的 `delete`。
