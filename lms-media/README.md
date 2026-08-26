# lms-media 媒资服务

> 文件 / 视频上传与媒资管理：统一存储抽象（当前本地磁盘，可扩展 OSS），支持 100MB 视频上传、我的媒资分页、删除。

## 职责定位

- 分层：**业务层**
- 依赖：lms-common（用户上下文，识别上传者）
- 协作：课程封面 / 学习课次视频引用其 url

## 运行信息

| 项 | 值 |
|---|---|
| 端口 | 8088 |
| 库 | `lms_media`（`media`） |
| Nacos 配置 | `nacos-config/lms-media.yaml`（multipart 上限 100MB、上传目录、url 前缀） |
| 网关路由 | `/medias/**` |

## 存储抽象

- `FileStorageService` 接口 → `LocalFileStorageService` 实现（上传目录 `uploads/`，已 gitignore）
- 静态访问：`url-prefix`（http://localhost:8088）+ `/uploads/**` 映射本地目录，浏览器媒体标签直连、不受 CORS 限制
- 生产环境可新增 OSS 实现（阿里云 / 七牛等）替换本地存储，业务代码无感知

## 核心接口

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/medias/upload` | 上传（multipart，≤100MB）→ MediaVO{id,url,name,type,size} | 登录 |
| GET | `/medias/page` | 我的媒资分页（MediaPageQuery） | 登录 |
| GET | `/medias/{id}` | 媒资详情 | 登录 |
| DELETE | `/medias/{id}` | 删除（仅本人） | 登录 |

## 数据模型

| 表 | 说明 |
|---|---|
| `media` | id / user_id（上传者）/ name（原文件名）/ type（1 图片 / 2 视频 / 3 其他）/ url / size（字节）/ mime / status |

## 目录结构

```
com/lms/media/
├── MediaApplication.java
└── media/
    ├── config/        # WebConfig（/uploads/** 静态映射）
    ├── constants/     # MediaErrorInfo
    ├── controller/    # MediaController
    ├── domain/        # po（Media）、query（MediaPageQuery）、vo（MediaVO）
    ├── enums/         # MediaType
    ├── mapper/        # MediaMapper
    ├── service/       # IMediaService + MediaServiceImpl
    └── storage/       # FileStorageService 抽象 + impl/LocalFileStorageService
```
