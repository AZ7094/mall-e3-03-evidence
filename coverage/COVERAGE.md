# E3-03 目标方法覆盖率记录（JaCoCo 0.8.15）

## 1. 采集方式与原始产物

- 运行：`mvn -o -B -pl mall-admin -am -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=PmsProductAttributeDeleteSpecTest test org.jacoco:jacoco-maven-plugin:0.8.15:report`
- 为让 JaCoCo agent 进入 surefire fork，本次采集临时在 `mall-admin/pom.xml` 增加了 surefire `<argLine>`；采集完成后已 `git checkout -- mall-admin/pom.xml` 还原，交付提交中不含该临时改动。
- 原始产物：`coverage/jacoco.exec`（102,299 字节）、`coverage/jacoco.xml`、`coverage/jacoco.csv`、`coverage/PmsProductAttributeServiceImpl.java.html`。
- 采集时工作区源码与提交 `1c07a36` 一致；测试文件与红/绿运行同一份（SHA-256 `1237d982…b81d5e`）。

## 2. 本次修改方法的数据（JaCoCo method 级）

| 方法 | 行覆盖 | 分支覆盖 | 指令覆盖 | 复杂度覆盖 |
|---|---|---|---|---|
| `delete(List<Long>)`（源码 77–129 行） | 36/39 = 92.31% | 18/24 = 75.00% | 168/172 = 97.67% | 7/13 = 53.85% |
| `decrease(Integer,int)`（源码 131–136 行） | 2/3 = 66.67% | 3/4 = 75.00% | 13/15 = 86.67% | 2/3 = 66.67% |
| 两方法合计（本次修改范围） | 38/42 = 90.48% | 21/28 = 75.00% | 181/187 = 96.79% | 9/16 = 56.25% |
| 整个 `PmsProductAttributeServiceImpl` 类 | 39/64 = 60.94% | 21/32 = 65.63% | 184/291 = 63.23% | 10/24 = 41.67% |

类级数字被本次未触及的 `getList/create/update/getItem/getProductAttrInfo` 拉低，不能用来评价本次修复；报告以 method 级为准。

## 3. 未覆盖的源行/分支及原因

| 行 | 内容 | 为什么没覆盖 |
|---|---|---|
| 85 | `ids` 去重并剔除 null 后为空的 `return 0` | 需要传入“只含 null”的列表；规格只要求 null 列表与空列表，未把“全 null 元素”定义为业务场景 |
| 100 | `attribute.getProductAttributeCategoryId() == null` 时 `continue` | 规格未定义无分类属性的计数归属；MySQL 表该列可空，但删除无分类属性时无分类可扣 |
| 116 | `category == null` 时 `continue` | 需要属性引用了不存在的分类（脏数据/并发删分类）；规格未定义 |
| 133 | `decrease` 中 `current == null` 返回 0 | 需要分类计数列为 NULL；测试数据均按正常 0/1/2/3 构造 |
| 78 分支 | `ids == null` 真分支 | 只测了空列表，未测 null 列表 |
| 84/99/104/115/119/132 分支 | 防御性判空的另一侧 | 与上表同源；为避免“为覆盖率造数据”，未强行补齐 |

这些都不是业务验收断言，不能因为未覆盖就推断行为正确；反过来，覆盖率 92.31% 也不能证明并发、事务代理回滚、HTTP 鉴权链或 type 为 null/其它值时的行为。

## 4. 这个覆盖率证明了什么、没证明什么

**证明：**
1. 7 个规格测试确实执行到了 `delete` 的主体（36 行）和新增的 `decrease` helper（2 行），不是只调用 controller 或 mock 旁路。
2. 跨分类、同分类两种类型、重复+缺失、全缺失、空列表、重复-only、计数下限这 7 条路径都进入了被修改的语句块。
3. 未覆盖部分集中在规格未定义的防御分支，而不是本次修复的主路径。

**没有证明：**
1. 并发删除同一分类时计数不丢失（未加锁、未做并发测试）。
2. `@Transactional` 代理在真实异常回滚时分类计数会回滚（单测直接 new 对象，MySQL 测试整体回滚但未注入删除中途异常）。
3. HTTP 层鉴权、参数绑定、controller 的 `count > 0 ? success : failed` 契约。
4. `type` 为 null 或 0/1 之外的值时的业务语义（规格未定义，代码选择不扣任何计数）。
5. 分类计数列为 NULL 时的行为。
