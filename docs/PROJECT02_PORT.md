# project02 Mekanism 26.1.2

目标：Minecraft 26.1.2、NeoForge 26.1.2.114、Java 25。本地版本为
`10.8.0-project02.1`，沿用上游 Alpha 状态，不视为官方稳定发布。

## 源码来源

- 用户指定 Fork：https://github.com/Viking602/Mekanism
- Fork 初始 `1.21.x` 提交：`bcd7a8bf594cff9614eb12238fe3776f19da24d9`。
  与核查时上游 `1.21.x` 相同，无额外玩法代码差异。
- 采用上游 `26.1` 目标源码：
  https://github.com/mekanism/Mekanism/tree/26.1
- 固定上游提交：`4718860a0c98598cf1b34e9f631b24eaa7c7ff47`。
  该提交明确使用 Minecraft 26.1.2、Java 25、NeoForge 26.1.2.74。
- 工程保留 `origin` 为 Viking602，`upstream` 为 Mekanism。
  `codex/source-1.21.x` 保留初始源码指针。
- 已在用户 Fork 创建 `26.1.2` 分支：
  https://github.com/Viking602/Mekanism/tree/26.1.2
  远程分支与上述上游提交一致。本地开发在 `26.1.2` 分支进行，
  构建适配改动在验证期间保留于本地。

## 本地改动

1. 将编译用 NeoForge 更新为 project02 的 26.1.2.114。
2. 添加独立本地版本标识，保留模组 ID、注册名和 MIT 协议。
3. 为本体、Generators、Tools、Additions、API 和源码 JAR
   打包原有 MIT 许可文件，路径为 `META-INF/licenses/mekanism/LICENSE`。
4. 使用已有 Java 25 与 Gradle 9.5 构建；开发缓存独立于游戏安装文件。
5. 在测试中按改道前实际承诺的目的地核对物品守恒，避免依赖固定 tick
   恰好抽取八件物品的假设；掉落按物品数量核对，保留丢失／复制检测。
6. 增加四项真实机器测试：能量立方通过通用线缆供电的冶炼与合金加工、
   电解水的 2:1 氢氧产出、生物燃料发电。新版灌注合金配方使用铜锭。
7. 对 Iris `1.11.4+mc26.1.2` 使用现有 MixinSquared 的取消 API，
   精确阻止三个引用旧 Mekanism 渲染签名的补丁。仅在类变换阶段运行，
   不增加每帧／每 tick 的反射或扫描，不改写 Iris 或 Minecraft 文件。
   project02 的 C2ME／AsyncParticles 已提供该可选 API；不使用 Iris 时
   Mekanism 无需该 API。新版本 Iris 不自动应用此例外。

只通过模组源码、模组构建产物和模组配置适配。
不更改 Minecraft 本体、原始 GLFW/LWJGL、公共 libraries、assets 或版本元数据。

## 验证范围

- 四模块、API、源码 JAR 构建成功，原有 71 项 JUnit／属性测试通过。
- 最终独立 NeoForge GameTestServer 的 67 项必需测试通过：原 Mekanism
  62 项、新机器测试 4 项、NeoForge 内建 1 项。
- 完整优化底座和拔刀剑混合 GameTest 曾完成 152 项，其中仍有失败。
  C2ME 环境下直接操作原版区块调度内部方法的 `reloadIntermediary`
  无法观察到预期卸载；去除 C2ME 的诊断运行通过了该项，但部分混跑
  运输测试仍失败。没有修改 C2ME 或游戏本体，也不把混跑视为全部通过。
- 实际 project02 客户端保留全部原 49 个 JAR，新增四个 Mekanism JAR，
  启动成功并新建 `Mekanism 26.1.2 Verification` 创造测试世界。
  实测创造标签／物品模型、冶炼炉 GUI、能量平板供电、单份矿石产出铁锭、
  F3+T 资源重载、保存退出并重载后的物品和能量持久化。
- 日志和未改动截图保存在实例的 `outputs/mekanism-26.1.2-20261008/`，
  原始诊断过程在本工程 `verification/`。Minecraft 固定文件完整性
  单独按安装前快照核对，JNA 的临时文件按原库中 DLL 字节校验。

## 尚未完成的范围

当前是可供继续开发的 Alpha 原型，不能称为功能完整迁移版。
上游 `MekaSuitArmor` 的四肢／装甲模型、`MekanismRenderType` 的部分
渲染管线和 SPS 特效仍有禁用代码／TODO。Iris 启动修复没有替代这些缺失
渲染。尚未完成完整底座的所有 GameTest、核裂变／聚变等大型系统的实玩
验收、光影包验证、FPS／TPS 对照或长期压力测试。

原移植排除了 CraftTweaker、EMI、The One Probe、ProjectE 联动代码。
2026-10-11 的 `projecte.1` 补丁已恢复 ProjectE 化学序列化注册、14 种
配方映射实现与容器内容处理器，并修复容器内容计算丢弃物品组件的问题。
PRC 和锯木机保留上游默认关闭状态，其余 12 种默认启用。其他上述排除项不变。
补丁基于本地 ProjectE project02.3 API 构建；保留 10.8.0-project02.1
接口版本，兼容 AppMek/MoreMachine 的精确版本依赖，通过 JAR 的
`projecte.1` 后缀及 `Project02-Integration-Patch` 清单属性标识改动。
验证记录在 `../verification/projecte-followup/REPORT.txt`。
其余可编译联动也不因编译通过而视为已完成功能测试。

mrqx 全部模组不加入；龙之研究及其依赖附属暂缓。
该选型仅适用于 project02，不修改 project01 已发布包及正式版本目录。

## 附属接入期间的补充回归

2026-10-08：MoreMachine 的作者 `26.1` 分支针对本地本体构建，取消其
捆绑的另一份 Mekanism，接入时重新运行 67 项基础 GameTest。
初次运行的运输升级用例在记录目的地数量后、网络重建结束前又抽取了一件
物品。测试现在在升级期间暂时关闭抽取，两 tick 后恢复，再严格核对
既有路径、后续改道以及全部 20 件物品守恒。此处只调整测试夹具，
没有修改本体的生产运输逻辑。最终回归通过。
