# 文件上传与公开预览脚本执行链修复

日期：2026-10-03。

## 执行清单

- [x] 检查普通上传、远程封面上传、按 ID 预览与历史桶名/路径预览。
- [x] 按实际内容解码图片，统一服务端 MIME 和图片后缀。
- [x] 两种预览入口共用安全响应策略，覆盖历史文件。
- [x] 补充伪装内容、历史文件、WebP、尺寸限制及权限回归测试。
- [x] 完成受影响测试并记录结果。

## 行为

- 头像、衣柜图片、影视/阅读封面必须是可解码的 PNG、JPEG、GIF、BMP 或 WebP；上传最大 10 MiB、图片最大 1600 万像素。银行卡保留仅 PNG/JPEG、最大 5 MiB 的限制，系统卡面继续重新编码为 PNG。
- 反馈、荣誉、活动、设备等附件业务保留普通文件上传。可验证图片使用真实图片 MIME，其余类型统一存储为 `application/octet-stream`，不采纳客户端声明。
- 对象存储 MIME、文件表 MIME、图片对象后缀均由检测结果决定。远程封面也校验真实内容，禁止重定向且限量读取响应流。
- 新旧两种预览 URL 都按对象真实内容判定。只有在限额内且解码成功的栅格图片内联；HTML、SVG、PDF、未知内容、损坏或超限图片改为附件下载。
- 统一返回 `X-Content-Type-Options: nosniff` 与 `Content-Security-Policy: default-src 'none'; sandbox`，下载使用 `application/octet-stream` 和 `Content-Disposition: attachment`。超过图片预览限额的历史文件仍完整流式下载。
- 不需要修改历史文件记录；通过应用预览接口访问时重新验证。原有公开/私有访问判断、银行卡菜单锁、上传事务及回滚清理保持原逻辑。
- 添加 TwelveMonkeys ImageIO WebP 解码器，保留已有 WebP 支持；测试中的 2×2 WebP 样本由本地生成的纯色 PNG 使用 `cwebp` 编码，分别覆盖有损和无损格式。

## 验证与部署边界

按影响分批执行，最终 9 个测试类共 65 项全部通过，无跳过：

| 测试类 | 数量 |
| --- | ---: |
| FileContentPolicyTest | 13 |
| FileUploadContentSecurityTest | 14 |
| FilePreviewSecurityTest | 8 |
| FileServiceImplTest | 4 |
| FileControllerTest | 9 |
| CoverUploadSecurityTest | 7 |
| StorageUploadCoordinationTest | 6 |
| BankCardTemplateUploadTest | 2 |
| BankCardCoverAuthorizationTest | 2 |

可使用以下命令复验全部相关测试（本次分批执行，未重复运行全部已通过项目）：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn -q -Dtest=FileContentPolicyTest,FileUploadContentSecurityTest,FilePreviewSecurityTest,FileServiceImplTest,FileControllerTest,CoverUploadSecurityTest,StorageUploadCoordinationTest,BankCardTemplateUploadTest,BankCardCoverAuthorizationTest test
```

首次新增测试有 3 项失败，原因是测试替身未显式返回匿名用户的 `null` 和 Hutool 泛型 `header()` 的链式对象；修正后受影响测试通过。没有跳过或放宽安全断言。测试日志保存在 `/tmp/aio-life-file-security-tests.log`、`/tmp/aio-life-file-security-retest.log` 与 `/tmp/aio-life-file-security-final.log`，详细结果在 `target/surefire-reports/`。

MockMvc 使用真实控制器路由，MinIO 使用替身；事务协调用例使用 H2 与真实 MyBatis 事务。未执行线上恶意文件上传或真实浏览器攻击。

仓库 `docker-compose.yml` 未包含 MinIO 服务或桶公开策略，应用生成的文件 URL 经后端预览接口。此次不修改远端存储策略；上线时须确认业务桶没有另行开放未经应用控制的直接访问地址，若存在则关闭公开访问或隔离到无业务凭据的独立来源。已被浏览器/CDN 缓存的旧危险响应还需失效，修复仅对重新经过后端的请求生效。

设计参考：[OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)、[TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys)。
