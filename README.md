# notch-fatigue-service

缺口根部弹塑性应力/应变核算服务（HTTP，无网页）。把单调 **Ramberg–Osgood** 本构

```
ε = σ/E + (σ/K)^(1/n)
```

与 **Neuber 双曲线**

```
σ·ε = (Kt·σn)² / E
```

联立，对每个名义应力 σn 自写二分法求根，得到缺口根的真实应力 σ 与真实应变 ε，并同时给出纯弹性外推 Kt·σn 作为对照。

仅处理单调加载下的缺口塑性这一计算内核；不是材料牌号库，也不涉及晶粒屈服、稳态蠕变等模型。卸载迟滞只在批量序列层做很薄的一层（按运行最高点弹性回弹标记，不跟踪迟滞回线）。

## 职责分层（各自成类成文件）

| 包 | 职责 |
|---|---|
| `constitutive` | `RambergOsgood`：本构关系、弹/塑应变分量、0.2% 比例极限 |
| `solver` | `RootFinder` 接口 + `BisectionRootFinder`（自写，无外部数学库）、`ConvergenceException` |
| `solver.neuber` | `NeuberHyperbola`（双曲线与残差）、`NeuberPointSolver`（联立求根） |
| `solver.sequence` | `LoadingSequenceSolver`：递增名义应力序列，逐点独立求解 + 薄卸载层 |
| `service` | `NotchAssessmentService`：组装结果与弹性对照 |
| `validation` | `InputValidator` / `ValidationException`：带原因的输入校验 |
| `web` | `NotchController`、`GlobalExceptionHandler`（HTTP 接口层） |
| `example` | 内置 Kt=3 圆角缺口算例，启动时载入并打印弹塑性 vs 弹性对照 |

## 关键数值行为

- 名义应力在弹性范围内（Kt·σn 未超过 0.2% 比例极限）：**精确**有 σ = Kt·σn、ε = σ/E；
- 进入塑性后：真实应力增长放缓且 **σ < Kt·σn**，真实应变加快增长，塑性分量非零；
- Kt 越大，同一名义应力下缺口越苛刻（应力、应变都更大）；
- **Kt = 1 时 Neuber 退化回单轴 Ramberg–Osgood**：σ = σn、ε = RO(σn)；
- 塑性幂次统一取自 `RambergOsgood.plasticExponent()`，即 **1/n**（误写成 n 会让整段塑性分支变歪）；
- 进入塑性后绝不再沿用 σ = Kt·σn，而是求解联立根，否则应变不再放大、Neuber 交叉失效；
- 求根不收敛或残差校核不过：抛 `ConvergenceException` → HTTP **422**，绝不返回可疑数值。

## 输入校验（HTTP 400，返回全部原因）

- E、K、n 必须为有限值且严格为正（非正即拒绝，附原因）；
- Kt 必须有限且 ≥ 1；
- 单点名义应力必须有限；批量序列非空、每个元素有限。

## 构建与运行

```bash
mvn clean package          # 编译并跑全部测试
java -jar target/notch-fatigue-service-1.0.0.jar
```

Docker（构建阶段在容器内执行 `mvn package`，测试在容器内跑过才出镜像；固定端口 8080）：

```bash
docker build -t notch-fatigue-service .
docker run --rm -p 8080:8080 notch-fatigue-service
```

## HTTP 接口

### 单点核算
```bash
curl -s -X POST localhost:8080/api/notch/assess \
  -H 'Content-Type: application/json' \
  -d '{"material":{"elasticModulus":200000,"strengthCoefficient":1200,"hardeningExponent":0.2},
       "kt":3.0,"nominalStress":400}'
```

### 批量递增序列（弹性 → 塑性整段响应，各点互不影响）
```bash
curl -s -X POST localhost:8080/api/notch/sequence \
  -H 'Content-Type: application/json' \
  -d '{"material":{"elasticModulus":200000,"strengthCoefficient":1200,"hardeningExponent":0.2},
       "kt":3.0,"nominalStresses":[50,100,120,150,200,250,300,350,400]}'
```

### 内置 Kt=3 算例（服务启动时也会自动载入并打印到日志）
```bash
curl -s localhost:8080/api/notch/example
```

内置算例参数：E = 200000 MPa，K = 1200 MPa，n = 0.20，Kt = 3，比例极限 σp = K·0.002^n ≈ 359 MPa。名义应力越过该门槛后，输出中的 `trueStress` 将明显低于 `elasticStress`（= Kt·σn），`truePlasticStrain` 由 0 转为正值。

## 测试

```bash
mvn test
```

重点覆盖：弹性极限内 σ = Kt·σn 的精确关系、Kt = 1 退回单轴 R-O、进入塑性后应力放缓/应变加快/Neuber 等式成立、Kt 越大越苛刻、非法输入带原因 400，以及求根不收敛时经二分器与完整 HTTP 栈返回 422 的报错路径。
