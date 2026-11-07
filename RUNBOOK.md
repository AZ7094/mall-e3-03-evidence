# E3-03 复现手册（RUNBOOK）

本目录所有日志均由下列命令在本机生成。仓库工作副本：`E:\大三作业\mall-e3`，分支 `e3-03-personal`。

## 0. 环境

| 项 | 值 |
|---|---|
| OS | Windows 11 |
| JDK | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`（Java 17） |
| Maven | 3.9.16（wrapper 缓存） |
| MySQL | 9.2.0，独立实例 `127.0.0.1:13306`，root 空密码，库 `mall` |
| 基线提交 | `dcaa93b3150352e5044708d7211b5bed0af4509f`（tag `baseline-dcaa93b3`） |
| 测试提交 | `4968126` |
| 修复提交 | `1c07a36` |

父 pom 设了 `skipTests=true`，且 `-pl mall-admin -am` 会带上没有匹配测试的模块，因此测试命令必须显式覆盖：

```bash
export JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
export PATH="$JAVA_HOME/bin:$PATH"
MVN='/c/Users/LENOVO/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn'
```

## 1. 改前红（重建对照，不是历史回放）

```bash
git checkout 4968126   # 只含规格测试，service 仍是基线
"$MVN" -o -B -pl mall-admin -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dtest=PmsProductAttributeDeleteSpecTest test
```

结果：`Tests run: 7, Failures: 3, Errors: 2, Skipped: 0`，见 `red-run-baseline.log`。
3 个 failure：`mixedCategoryBatchDecrementsEachCategoryOnce`、`sameCategorySpecAndParamDecrementSeparateCounters`、`duplicateAndMissingIdsFollowDeletedRows`。
2 个 error：`allMissingIdsReturnZeroWithoutWriting`（`PmsProductAttribute.getType()` NPE）、`emptyIdsReturnZeroWithoutTouchingMappers`（`Index 0 out of bounds for length 0`）。

## 2. 改后绿

```bash
git checkout 1c07a36
"$MVN" -o -B -pl mall-admin -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dtest=PmsProductAttributeDeleteSpecTest test
```

结果：`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`，见 `green-run.log`。

## 3. MySQL 验收（补充证据，不属于“改前红”）

启动临时实例（datadir 已初始化，root 空密码）：

```bash
mysqld --no-defaults \
  --basedir="C:/Program Files/MySQL/MySQL Server 9.2" \
  --datadir="C:/Users/LENOVO/AppData/Local/Temp/mysql-e3/data" \
  --port=13306 --bind-address=127.0.0.1 --mysqlx=0 --skip-log-bin --console
```

首次准备（本机已执行）：

```bash
mysql --no-defaults -h127.0.0.1 -P13306 -uroot \
  -e "CREATE DATABASE IF NOT EXISTS mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql --no-defaults -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4 \
  mall < document/sql/mall.sql
```

导入结果：76 张表、`pms_product_attribute_category` 12 行。

运行（测试类默认被 `@EnabledIfSystemProperty(e3.mysql=true)` 关闭，避免无库环境误红）：

```bash
"$MVN" -o -B -pl mall-admin -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -De3.mysql=true -Dtest=PmsProductAttributeDeleteMysqlTest test
```

结果：`Tests run: 4, Failures: 0, Errors: 0`，见 `mysql-run.log`。测试整体 `@Transactional`，结束回滚。

## 4. JaCoCo 覆盖率

临时在 `mall-admin/pom.xml` 的 surefire 配置中加入：

```xml
<configuration>
  <argLine>-javaagent:C:/Users/LENOVO/.m2/repository/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar=destfile=target/jacoco.exec</argLine>
  <useManifestOnlyJar>false</useManifestOnlyJar>
</configuration>
```

然后：

```bash
"$MVN" -o -B -pl mall-admin -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dtest=PmsProductAttributeDeleteSpecTest test \
  org.jacoco:jacoco-maven-plugin:0.8.15:report
git checkout -- mall-admin/pom.xml   # 还原，交付提交不含临时配置
```

报告在 `mall-admin/target/site/jacoco/`，已复制到 `coverage/`。数字与解释见 `coverage/COVERAGE.md`。

## 5. 断言未被放宽的核验

```bash
cd /e/大三作业/mall-e3
TEST=mall-admin/src/test/java/com/macro/mall/service/impl/PmsProductAttributeDeleteSpecTest.java
git show 4968126:$TEST | sha256sum
git show 1c07a36:$TEST | sha256sum
git diff 4968126 1c07a36 -- $TEST | wc -l
```

两个 SHA-256 均为 `1237d982e396ef788d25eab6084064f767c592f3434eb1b14826816786b81d5e`，diff 行数 0；见 `assertions-unchanged.log`。

## 6. 失败路径经过本次修改语句

- 改前红日志里 3 个 failure 都发生在 `productAttributeService.delete(...)` 返回后的分类计数断言；2 个 error 直接抛自 `delete` 内部（NPE / `ids.get(0)`）。
- 修复 diff 见 `fix.diff`：`delete` 的 78–128 行整体替换了基线的“取第一个 ID → delete → 按第一个分类扣全部 count”流程；分类扣减从 1 个分类扩展为按 `(categoryId,type)` 分组逐分类更新。
- 测试 4/5 用 `verify(..., never())` 把“全缺失/空列表不写库”钉在 mapper 调用边界，因此不是只靠返回值断言。
