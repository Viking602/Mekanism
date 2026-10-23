# 26.1 热量存档修复

用户反馈重新进入存档时热力蒸馏塔触发 `Expected value to be non-negative`。原生代码复现后确认，`IHeatCapacitor.CapacitorState.CODEC` 把 `heat()` 同时用于 `heat` 和 `heat_capacity`；构造器也错误地用热量校验热容。17 层塔原本热量约 510000、热容 1700，保存后热容变成 510000，重新成型按错误热容调整热量后得到约 −1.52 亿，导致服务端方块实体 tick 崩溃。

修复直接位于本 Mekanism 移植源码：

- 热容字段使用 `heatCapacity()`，构造器校验实际的热容参数，允许合法的零热量和小于 1 的热量。
- 已有蒸馏塔读取到旧编码造成的相同热量与热容时，在原生结构成型阶段按已校验高度恢复热容，保留原有热量、物品和流体。正常热容更新仍调用原有事务逻辑。
- 保持 `10.8.0-viking602.1` 接口版本，以兼容 AppMek / MoreMachine 等精确版本依赖；发行 JAR 用 `projecte.1.heat.1` 后缀和 `Viking602-Heat-Persistence-Fix: 1` 清单标识本次源码修复。保留原来的 ProjectE 联动。

修复不依赖 Compat 补丁，也不编辑玩家存档文件。旧数据在游戏正常加载和保存时恢复。原始问题存档的备份、旧版原生复现、修复后的热量编解码与事务检查、存档副本两次保存重进记录位于实例 `.work/verification/mekanism-heat-20261017/`。测试探针只在 `verification/heat/`，不打入发行 JAR。
