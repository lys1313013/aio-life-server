# 文件内容安全

上传和远程导入按真实字节确认文件类型。预览使用服务端在写入时确认的对象 MIME，并遵守附件业务权限与公开资格。[文件内容策略](../src/main/java/top/aiolife/core/util/FileContentPolicy.java) [附件访问规则](附件与来源表设计.md#预览下载与公开访问)

## 写入校验

- 通用文件上传限 10 MiB。要求图片的入口接受可解码的 PNG、JPEG、GIF、BMP 或 WebP，最多 1600 万像素。银行卡卡面接受 5 MiB 以内的 PNG 或 JPEG，系统卡面按业务规则重编码为 PNG。
- 支持普通附件的业务将无法确认为受支持图片的内容保存为 `application/octet-stream`。服务端检测结果决定对象 MIME、尺寸和对象后缀，展示文件名与客户端声明不参与类型判定。
- 远程封面按可信来源和读流上限下载，禁止重定向，图片通过同一内容策略校验。CBTI 人格图片也在上传或初始化写入时确认格式与类型。[上传校验](../src/main/java/top/aiolife/record/service/impl/AttachmentServiceImpl.java) [远程下载](../src/main/java/top/aiolife/record/service/VideoCoverDownloader.java)

## 2026-11-16 预览校验调整

权限校验后按 `storage_object.content_type` 流式返回原始字节。仅受支持的栅格 MIME 可内联。显式下载及其他类型返回 `application/octet-stream` 和 `Content-Disposition: attachment`。响应统一设置 `X-Content-Type-Options: nosniff` 与 `Content-Security-Policy: default-src 'none'; sandbox`。

历史合法图片按已保存的 MIME 与原始尺寸预览，无需重新解码或复用新上传的像素上限。公开响应在 ETag 校验前确认公开资格，私有响应使用 `private, no-store`。[私有访问](../src/main/java/top/aiolife/record/service/FileAccessService.java) [公开响应](../src/main/java/top/aiolife/record/service/PublicImageService.java)

2026-10-03 的修复清单及 2026-11-16 的实际接口和历史大图验证记录，见 [Git 历史](https://github.com/lys1313013/aio-life-server/blob/e550bc58b5b9b3ce06875c496aa16bfd552b2cd5/docs/file-content-security.md)。
