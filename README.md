# mall-e3-03-evidence

E3 作业 3「规格先行与 AI 结对编程」——**E3-03 跨分类批量删除属性的计数**（Issue [meng004/mall#3](https://github.com/meng004/mall/issues/3)）的证据仓。基线 `dcaa93b3150352e5044708d7211b5bed0af4509f`，只修改 `PmsProductAttributeServiceImpl.delete`，不改变表结构、生成代码、接口签名和 controller 契约。

## 先看什么

| 文件 | 内容 |
|---|---|
| [REPORT.pdf](REPORT.pdf) | 一页说明（A4，1 页），作业正文 |
| [REPORT.md](REPORT.md) | 同内容的 Markdown 版 |
| [SPEC.md](SPEC.md) | 用户故事、AC1–AC5、不得修改的部分 |
| [SCREENCAST-SCRIPT.md](SCREENCAST-SCRIPT.md) | ≤2 分钟讲解录屏时间轴与口播稿 |
| [RUNBOOK.md](RUNBOOK.md) | 环境、红/绿、MySQL、JaCoCo、断言核验的完整命令 |
| [patch/](patch/) | `0001-test.patch`（先行的规格测试）、`0002-fix.patch`（最小修复） |
| [logs/](logs/) | 红/绿、AI 初稿、MySQL、断言未放宽、修复 diff、提交记录 |
| [coverage/](coverage/) | JaCoCo XML/CSV/HTML 与 `COVERAGE.md` |
| [source/](source/) | 修复后实现、两个测试类、controller 与 service 接口快照 |

## 结果

| 验证 | 结果 |
|---|---|
| 改前红（基线 + 规格测试） | **7 tests, 3 failures, 2 errors** |
| 改后绿（同一份测试） | **7/7，BUILD SUCCESS** |
| 断言未放宽 | 两次提交测试 SHA-256 均为 `1237d982e396ef788d25eab6084064f767c592f3434eb1b14826816786b81d5e`，diff 0 行 |
| MySQL 9.2 真库验收 | **4/4** |
| `delete` 覆盖率 | 行 36/39=92.31%，分支 18/24=75.00%（只记录，不设门槛） |
| `decrease` 覆盖率 | 行 2/3，分支 3/4 |

失败路径：3 个 failure 在 `delete` 返回后的分类计数断言；2 个 error 直接抛自 `delete` 内部（基线第 74 行 NPE、第 73 行下标越界）。没有编译、数据库或环境错误。

## 目录结构

```text
.
├── README.md
├── REPORT.md / REPORT.pdf
├── SPEC.md
├── SCREENCAST-SCRIPT.md
├── RUNBOOK.md
├── source/
│   ├── PmsProductAttributeServiceImpl.java      # 修复后
│   ├── PmsProductAttributeDeleteSpecTest.java   # 7 个 Mockito 规格测试
│   ├── PmsProductAttributeDeleteMysqlTest.java  # 4 个真库验收测试
│   ├── PmsProductAttributeController.java
│   └── PmsProductAttributeService.java
├── patch/
│   ├── 0001-test.patch
│   └── 0002-fix.patch
├── logs/
│   ├── red-run-baseline.log
│   ├── green-run.log
│   ├── mysql-run.log
│   ├── ai-draft-run.log
│   ├── ai-draft-delete-service.java.txt
│   ├── assertions-unchanged.log
│   ├── fix.diff
│   ├── git-log.txt
│   ├── coverage-run.log
│   └── test-file.sha256
└── coverage/
    ├── COVERAGE.md
    ├── jacoco.xml / jacoco.csv / jacoco.exec
    └── PmsProductAttributeServiceImpl.java.html
```

## 复现（摘要）

```bash
# 1) 在 mall 的 dcaa93b 上依次应用测试、修复两个 patch
git am patch/0001-test.patch
git am patch/0002-fix.patch

# 2) 规格测试（需 JDK 17；父 pom 默认 skipTests=true）
mvn -o -B -pl mall-admin -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -Dtest=PmsProductAttributeDeleteSpecTest test

# 3) MySQL 真库验收（需本地 13306 实例与 mall 库）
mvn -o -B -pl mall-admin -am \
  -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -De3.mysql=true -Dtest=PmsProductAttributeDeleteMysqlTest test
```

改前红的重建：只应用 `0001-test.patch`、不应用 `0002-fix.patch`（service 仍是基线），再跑第 2 条命令；这是**重建对照**，不是历史上已测试先行。首次红的真实时间是 2026-11-07 10:41:15，早于测试提交 10:41:34 和修复提交 10:43:35。

## 范围与未验证

- 不验证并发删除时的计数竞争；
- 不验证 `@Transactional` 代理在异常时的实际回滚；
- 不验证 HTTP 鉴权链；
- 不定义 `type` 为 null 或 0/1 之外值时的业务语义；相关防御分支未覆盖。

教师示例的登录 / 改密链路未被沿用；本仓只检查实际修改的 `delete`。
