# 07 开放问题

主路径见 [00-decisions.md](00-decisions.md)。这里只剩后议项。

- 编排第一版实现细节（引擎如何读 JSON、未实现节点失败还是忽略）。用户要求先跳过，其它完成后再讨论。
- Maven groupId。
- Admin 鉴权方式。
- hash(jobId) 的具体算法（保证改语言后仍稳定即可）。
- slot 锁 TTL / 看门狗间隔的默认值。
